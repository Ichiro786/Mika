package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.gate.Irreversible
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import kotlinx.serialization.json.JsonObject

/**
 * Android's runtime permission dialog: tapping an "Allow" choice grants an app access to location, contacts, the
 * microphone and the like. In Ask mode that waits for the user's OK (see [Risk.confirm]); "Don't allow" never does.
 */
object PermissionPrompt {
    val PACKAGES = setOf(
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
    )

    private val ALLOW_LABEL = Regex(
        "^(allow|allow all the time|allow all|allow only while using the app|while using the app|only this time|" +
            "allow limited access|allow access to all|allow all photos|允许|始终允许|仅在使用该应用期间允许|使用时允许|仅限这一次|本次允许)$",
        RegexOption.IGNORE_CASE,
    )
    private val ALLOW_ID = Regex("permission_allow|allow_button|allow_foreground|allow_always|allow_one_time|allow_all", RegexOption.IGNORE_CASE)

    /** The tap grants a permission: the screen is the permission dialog and the target is one of its Allow choices. */
    fun grant(verb: Verb?, args: JsonObject, screen: ScreenSnapshot?): Irreversible? {
        val s = screen ?: return null
        if (s.packageName !in PACKAGES || verb != Verb.Tap) return null
        val e = ClassifiedRisk.target(verb, args, s) ?: return null
        val label = e.name.trim().trimEnd('.', '。')
        val byLabel = label.isNotEmpty() && ALLOW_LABEL.matches(label)
        val byId = e.resourceId?.let { ALLOW_ID.containsMatchIn(it) && !it.contains("deny", ignoreCase = true) } == true
        if (!byLabel && !byId) return null
        return Irreversible(IrrevAction.other, label.ifEmpty { "Allow" })
    }
}
