package com.wangzan101.mika.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.wangzan101.mika.R
import com.wangzan101.mika.backup.BackupException
import com.wangzan101.mika.backup.BackupService
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.theme.MikaIcons
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.wangzan101.mika.ui.theme.MikaSpace

private const val MIN_PASSWORD = 8

/** 设置 › 高级与其他 里的「备份」一组:导出加密备份、导入(合并)备份。 */
@Composable
internal fun BackupSettingsGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) {
            busy = true
            message = null
            scope.launch {
                message = try {
                    tr(R.string.backup_export_done, BackupService.export(context, uri, password.toCharArray()))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    tr(R.string.backup_export_failed)
                } finally {
                    busy = false
                }
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }

    GroupLabel(stringResource(R.string.backup_group))
    GroupCard {
        ListRow(MikaIcons.Download, stringResource(R.string.backup_export), stringResource(R.string.backup_export_sub),
            first = true, onClick = if (busy) null else ({ exportDialog = true })) { RowChevron() }
        ListRow(MikaIcons.Archive, stringResource(R.string.backup_import), stringResource(R.string.backup_import_sub),
            onClick = if (busy) null else ({ open.launch(arrayOf("*/*")) })) { RowChevron() }
    }
    FooterNote(stringResource(R.string.backup_note))
    if (busy) FooterNote(stringResource(R.string.backup_working))
    message?.let { FooterNote(it) }

    if (exportDialog) {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        val mismatch = second.isNotEmpty() && first != second
        val valid = first.length >= MIN_PASSWORD && first == second
        MikaDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.backup_export_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_password_hint))
                    OutlinedTextField(first, { first = it }, Modifier.padding(top = MikaSpace.S12), singleLine = true,
                        label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(second, { second = it }, Modifier.padding(top = MikaSpace.S8), singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(stringResource(R.string.backup_password_mismatch)) }) else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    exportPassword = first
                    exportDialog = false
                    create.launch("orbit-backup-${SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())}.orbitbak")
                }) { Text(stringResource(R.string.backup_continue)) }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    importUri?.let { uri ->
        var password by remember(uri) { mutableStateOf("") }
        MikaDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.backup_import_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_import_dialog_body))
                    OutlinedTextField(password, { password = it }, Modifier.padding(top = MikaSpace.S12), singleLine = true,
                        label = { Text(stringResource(R.string.backup_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotEmpty(), onClick = {
                    val chars = password.toCharArray()
                    importUri = null
                    busy = true
                    message = null
                    scope.launch {
                        message = try {
                            val r = BackupService.import(context, uri, chars)
                            tr(R.string.backup_import_done, r.added, r.skipped)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: BackupException) {
                            tr(when (e.kind) {
                                BackupException.Kind.WRONG_PASSWORD -> R.string.backup_err_password
                                BackupException.Kind.NOT_A_BACKUP -> R.string.backup_err_format
                                BackupException.Kind.UNSUPPORTED_VERSION -> R.string.backup_err_version
                                BackupException.Kind.UNSAFE -> R.string.backup_err_unsafe
                                BackupException.Kind.TOO_LARGE -> R.string.backup_err_too_large
                                BackupException.Kind.CORRUPT -> R.string.backup_err_corrupt
                            })
                        } catch (_: Exception) {
                            tr(R.string.backup_import_failed)
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(R.string.backup_continue)) }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
