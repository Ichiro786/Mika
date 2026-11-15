package com.wangzan101.mika.backup

import android.content.Context
import android.net.Uri
import com.wangzan101.mika.MikaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

/** 备份导出/导入的 Android 一侧:读写用户选的文档、调用 [BackupArchive]/[BackupCrypto],并让对话列表刷新。都在 IO 线程。 */
object BackupService {
    class ImportResult(val added: Int, val skipped: Int)

    /** 返回备份里的文件数。 */
    suspend fun export(context: Context, uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val entries = BackupArchive.collect(context.filesDir)
        val raw = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("No destination")
        val sink = BackupCrypto.encrypt(BufferedOutputStream(raw), password)
        sink.use { BackupArchive.writeZip(it, entries) }
        entries.size
    }

    suspend fun import(context: Context, uri: Uri, password: CharArray): ImportResult = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "backup-import-${System.currentTimeMillis()}")
        try {
            stage.mkdirs()
            val raw = context.contentResolver.openInputStream(uri) ?: throw IOException("No source")
            BackupCrypto.decrypt(BufferedInputStream(raw), password).use { BackupArchive.extract(it, stage) }
            val merged = BackupArchive.merge(stage, context.filesDir)
            (context.applicationContext as MikaApp).conversations.notifyImported(merged.conversationIds)
            ImportResult(merged.addedFiles, merged.skippedFiles)
        } finally {
            stage.deleteRecursively()
        }
    }
}
