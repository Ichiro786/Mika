package com.wangzan101.mika.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** Prevents repeated choices while the existing service processes this confirmation. */
internal class ApprovalSubmission(pending: Boolean = false, failed: Boolean = false) {
    var pending by mutableStateOf(pending)
        private set
    var failed by mutableStateOf(failed)
        private set

    fun send(action: () -> Unit) {
        if (pending) return
        pending = true
        failed = false
        try {
            action()
        } catch (_: Exception) {
            pending = false
            failed = true
        }
    }

    companion object {
        val Saver: Saver<ApprovalSubmission, Any> = listSaver(
            save = { listOf(it.pending, it.failed) },
            restore = { ApprovalSubmission(it[0], it[1]) },
        )
    }
}
