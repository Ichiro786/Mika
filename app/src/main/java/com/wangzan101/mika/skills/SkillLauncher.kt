package com.wangzan101.mika.skills

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.view.View
import android.widget.RemoteViews
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.MainActivity
import com.wangzan101.mika.ui.SkillRow
import com.wangzan101.mika.ui.SkillsUi

/**
 * Saved skills on the home screen: a pinned shortcut per skill and a small glass widget with the top skills.
 *
 * Both only open Orbit with the skill's request typed into the message bar, through the same draft path a share
 * uses. Nothing starts until you press send, so a stray tap on the home screen can't run a task, and every
 * approval inside the task still asks as it always does.
 */
object SkillLauncher {
    const val ACTION_USE_SKILL = "com.wangzan101.mika.action.USE_SKILL"
    const val EXTRA_SKILL_NAME = "skill_name"
    const val EXTRA_SKILL_TITLE = "skill_title"

    /** How many skills the widget lists. */
    const val WIDGET_SKILLS = 3

    fun intent(context: Context, name: String, title: String): Intent =
        Intent(context, MainActivity::class.java).setAction(ACTION_USE_SKILL)
            .putExtra(EXTRA_SKILL_NAME, name).putExtra(EXTRA_SKILL_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The request the message bar is filled with: the skill named so the agent picks it up. */
    fun prompt(context: Context, title: String): String = context.getString(R.string.skill_use_prompt, title)

    fun canPin(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** Ask the launcher to pin [row]; the launcher shows its own confirmation. False when it can't. */
    fun pin(context: Context, row: SkillRow): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val title = row.display.ifBlank { row.name }
        val info = ShortcutInfo.Builder(context, "skill-${row.name}")
            .setShortLabel(title.take(SHORT_LABEL_MAX))
            .setLongLabel(title.take(LONG_LABEL_MAX))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent(context, row.name, title))
            .build()
        return runCatching { sm.requestPinShortcut(info, null) }.getOrDefault(false)
    }

    /** Skills for the widget: enabled ones, most recently used first. */
    fun top(rows: List<SkillRow>): List<SkillRow> =
        rows.filterNot { it.suspended }.sortedByDescending { it.recent.size }.take(WIDGET_SKILLS)

    /** Redraw every placed widget; call when the skills change. Cheap when none is placed. */
    fun refreshWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, SkillsWidget::class.java))
        if (ids.isNotEmpty()) SkillsWidget.draw(context, manager, ids)
    }

    private const val SHORT_LABEL_MAX = 10
    private const val LONG_LABEL_MAX = 25
}

/** The home-screen widget: a frosted card titled "Orbit skills" with up to three skills; empty, it opens Orbit. */
class SkillsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = draw(context, manager, ids)

    companion object {
        private val ROWS = intArrayOf(R.id.widget_skill_1, R.id.widget_skill_2, R.id.widget_skill_3)

        fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val skills = runCatching { SkillLauncher.top(SkillsUi.of(PersonalSkillFiles(context.filesDir)).installed) }
                .getOrDefault(emptyList())
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_skills)
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                views.setOnClickPendingIntent(R.id.widget_title, open)
                views.setViewVisibility(R.id.widget_empty, if (skills.isEmpty()) View.VISIBLE else View.GONE)
                views.setOnClickPendingIntent(R.id.widget_empty, open)
                ROWS.forEachIndexed { i, row ->
                    val s = skills.getOrNull(i)
                    if (s == null) {
                        views.setViewVisibility(row, View.GONE)
                    } else {
                        val title = s.display.ifBlank { s.name }
                        views.setViewVisibility(row, View.VISIBLE)
                        views.setTextViewText(row, title)
                        views.setContentDescription(row, context.getString(R.string.skill_widget_use, title))
                        views.setOnClickPendingIntent(row, PendingIntent.getActivity(
                            context, 100 + i, SkillLauncher.intent(context, s.name, title),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ))
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
