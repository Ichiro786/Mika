package com.wangzan101.mika.spaces

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.TriggerSource
import com.wangzan101.mika.task.TriggerRuntime

/**
 * A space's background refresh: an ordinary repeating plan in the space's own conversation that sends
 * [SpacePanel.refreshPrompt], so it runs, records and recovers like any other plan. It is off until the
 * user picks an interval, because every refresh is a model run. To stay light on battery and usage it
 * waits until no other task is running, skips a slot when the battery is low, saver mode is on or there is
 * no working network, and a manual Refresh pushes the next run a full interval out.
 */
object SpaceSchedule {
    private const val HOUR = 60L * 60 * 1000
    /** The choices offered, shortest first; null (off) is the default. */
    val INTERVALS = listOf(3 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR)
    private const val PREFIX = "space-refresh-"
    /** Below this, unless charging, a scheduled refresh waits for the next slot. */
    private const val MIN_BATTERY = 20
    const val SKIP_REASON = "space refresh skipped: battery low, saver on or offline"

    fun planId(space: Space) = PREFIX + space.id

    fun isSpacePlan(trigger: TriggerDefinition) = trigger.id.startsWith(PREFIX)

    /** The interval the space refreshes on, or null when it only refreshes by hand. */
    fun interval(context: Context, space: Space): Long? {
        val id = space.refreshPlanId ?: return null
        return TriggerRuntime.list(context).firstOrNull { it.id == id && it.enabled }?.intervalMs
    }

    /** Sets or clears the background refresh; [intervalMs] null turns it off. */
    fun set(context: Context, store: SpaceStore, space: Space, intervalMs: Long?) {
        val id = planId(space)
        val existing = TriggerRuntime.list(context).firstOrNull { it.id == id }
        if (intervalMs == null) {
            if (existing != null) TriggerRuntime.remove(context, id)
            store.setRefreshPlan(space.id, null)
            return
        }
        val plan = TriggerDefinition(
            id = id, threadId = space.conversationId, goal = SpacePanel.refreshPrompt(space),
            nextAtMs = System.currentTimeMillis() + intervalMs, intervalMs = intervalMs,
            idleOnly = true, source = TriggerSource.User,
        )
        TriggerRuntime.schedule(context, plan, replaceExisting = existing != null)
        store.setRefreshPlan(space.id, id)
    }

    /** After a manual refresh, the next scheduled one is a full interval away, so the work is not done twice. */
    fun postpone(context: Context, space: Space) {
        val id = space.refreshPlanId ?: return
        val interval = TriggerRuntime.list(context).firstOrNull { it.id == id }?.intervalMs ?: return
        runCatching { TriggerRuntime.reschedule(context, id, System.currentTimeMillis() + interval) }
    }

    /** Keeps the plan's message in step with a renamed space. */
    fun renamed(context: Context, store: SpaceStore, space: Space) {
        val interval = interval(context, space) ?: return
        runCatching { set(context, store, space, interval) }
    }

    fun removeFor(context: Context, space: Space) {
        val id = space.refreshPlanId ?: planId(space)
        if (TriggerRuntime.list(context).any { it.id == id }) TriggerRuntime.remove(context, id)
    }

    /** True when a scheduled refresh may run now: enough battery (or charging), saver off, a working network. */
    fun deviceReady(context: Context): Boolean = runCatching {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val power = context.getSystemService(PowerManager::class.java)
        val net = context.getSystemService(ConnectivityManager::class.java)
        val caps = net.getNetworkCapabilities(net.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val batteryOk = battery.isCharging || level <= 0 || level >= MIN_BATTERY
        batteryOk && !power.isPowerSaveMode && online
    }.getOrDefault(true)
}
