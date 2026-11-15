package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.settings.PromptFile
import com.wangzan101.mika.settings.PromptFiles
import com.wangzan101.mika.settings.VoiceEngine
import com.wangzan101.mika.settings.VoiceEngineSettings
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.RadioRow
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.delay

/** The live call engine's short name, for the Settings row. */
@Composable
fun voiceEngineLabel(e: VoiceEngine): String = stringResource(
    when (e) {
        VoiceEngine.Builtin -> R.string.voice_engine_builtin
        VoiceEngine.GeminiLive -> R.string.voice_engine_gemini
        VoiceEngine.OpenAiRealtime -> R.string.voice_engine_openai
    },
)

private enum class VoiceEdit { Key, Model, Voice }

/** Settings › Voice: the live call engine, each engine's key, model and voice, and speech-to-text. */
@Composable
fun VoiceSettingsScreen(onOpenSpeech: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { VoiceEngineSettings.get(context) }
    val engine by settings.engine.collectAsState()
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<VoiceEngine, VoiceEdit>?>(null) }
    PageScaffold(stringResource(R.string.voice_title), onBack) {
        GroupLabel(stringResource(R.string.voice_engine_group))
        GroupCard {
            listOf(
                Triple(VoiceEngine.Builtin, R.string.voice_engine_builtin, R.string.voice_engine_builtin_sub),
                Triple(VoiceEngine.GeminiLive, R.string.voice_engine_gemini, R.string.voice_engine_gemini_sub),
                Triple(VoiceEngine.OpenAiRealtime, R.string.voice_engine_openai, R.string.voice_engine_openai_sub),
            ).forEachIndexed { i, (e, title, sub) ->
                RadioRow(stringResource(title), selected = engine == e, onClick = { settings.setEngine(e) },
                    subtitle = stringResource(sub), first = i == 0)
            }
        }
        FooterNote(stringResource(R.string.voice_engine_foot))

        for (e in listOf(VoiceEngine.GeminiLive, VoiceEngine.OpenAiRealtime)) {
            // Re-read after an edit; keys are decrypted on demand.
            val key = remember(version, e) { settings.key(e) }
            val own = remember(version, e) { settings.ownKey(e).isNotBlank() }
            val model = remember(version, e) { settings.model(e) }
            val voice = remember(version, e) { settings.voice(e) }
            val from = key?.fromProvider
            GroupLabel(voiceEngineLabel(e))
            GroupCard {
                ListRow(
                    MikaIcons.Key, stringResource(R.string.voice_api_key),
                    when {
                        own -> null
                        from != null -> stringResource(R.string.voice_key_from_model, from)
                        else -> stringResource(if (e == VoiceEngine.GeminiLive) R.string.voice_key_hint_gemini else R.string.voice_key_hint_openai)
                    },
                    first = true, onClick = { editing = e to VoiceEdit.Key },
                ) {
                    if (key != null) RowValue(stringResource(R.string.voice_key_saved), ValueTone.Ok)
                    else RowValue(stringResource(R.string.voice_key_add), ValueTone.Link)
                    RowChevron()
                }
                ListRow(MikaIcons.Spark, stringResource(R.string.voice_model), onClick = { editing = e to VoiceEdit.Model }) {
                    RowValue(model); RowChevron()
                }
                ListRow(MikaIcons.Sound, stringResource(R.string.voice_voice), onClick = { editing = e to VoiceEdit.Voice }) {
                    RowValue(voice.ifEmpty { stringResource(R.string.voice_default) }); RowChevron()
                }
            }
            FooterNote(stringResource(if (e == VoiceEngine.GeminiLive) R.string.voice_gemini_foot else R.string.voice_openai_foot))
        }

        GroupLabel(stringResource(R.string.voice_typing_group))
        GroupCard {
            ListRow(MikaIcons.Mic, stringResource(R.string.voice_speech_to_text), stringResource(R.string.voice_speech_to_text_sub),
                first = true, onClick = onOpenSpeech) { RowChevron() }
        }
    }

    editing?.let { (e, what) ->
        val done = { editing = null; version++ }
        when (what) {
            VoiceEdit.Key -> TextEditDialog(
                title = "${voiceEngineLabel(e)} · ${stringResource(R.string.voice_api_key)}",
                initial = settings.ownKey(e),
                hint = stringResource(if (e == VoiceEngine.GeminiLive) R.string.voice_key_hint_gemini else R.string.voice_key_hint_openai),
                secret = true,
                onSave = { settings.setKey(e, it); done() },
                onRemove = if (settings.ownKey(e).isNotBlank()) ({ settings.setKey(e, ""); done() }) else null,
                onDismiss = { editing = null },
            )
            VoiceEdit.Model -> TextEditDialog(
                title = "${voiceEngineLabel(e)} · ${stringResource(R.string.voice_model)}",
                initial = settings.model(e), hint = VoiceEngineSettings.defaultModel(e),
                onSave = { settings.setModel(e, it); done() }, onRemove = null, onDismiss = { editing = null },
            )
            VoiceEdit.Voice -> AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(R.string.voice_voice)) },
                text = {
                    Column {
                        val current = settings.voice(e)
                        (listOf("") + VoiceEngineSettings.voices(e)).forEachIndexed { i, v ->
                            RadioRow(v.ifEmpty { stringResource(R.string.voice_default) }, selected = v == current,
                                onClick = { settings.setVoice(e, v); done() }, first = i == 0)
                        }
                    }
                },
                confirmButton = { TextButton({ editing = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initial: String,
    hint: String,
    secret: Boolean = false,
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OrbitTextField(text, { text = it }, label = title, placeholder = hint,
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text)
        },
        confirmButton = { TextButton({ onSave(text) }) { Text(stringResource(R.string.voice_save)) } },
        dismissButton = {
            Row {
                onRemove?.let { TextButton(it) { Text(stringResource(R.string.voice_key_remove), color = MikaTheme.colors.bad) } }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Whether a prompt file is empty, the shipped default, or changed by the user. */
@Composable
internal fun promptFileState(files: PromptFiles, f: PromptFile): String = stringResource(
    when {
        files.edited(f) -> R.string.prompts_edited
        f == PromptFile.Instructions -> R.string.prompts_empty
        else -> R.string.prompts_default
    },
)

/** Settings › Instructions & system prompt: the two markdown files Orbit reads at the start of every task. */
@Composable
fun PromptFilesScreen(onOpen: (PromptFile) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember { PromptFiles.get(context) }
    PageScaffold(stringResource(R.string.settings_prompts), onBack) {
        GroupLabel(stringResource(R.string.prompts_group))
        GroupCard {
            ListRow(MikaIcons.Doc, PromptFile.Instructions.fileName, stringResource(R.string.prompts_instructions_sub), first = true,
                tint = SettingsTint.Orange, onClick = { onOpen(PromptFile.Instructions) }) {
                RowValue(promptFileState(files, PromptFile.Instructions)); RowChevron()
            }
            ListRow(MikaIcons.Doc, PromptFile.SystemPrompt.fileName, stringResource(R.string.prompts_system_sub),
                tint = SettingsTint.Gray, onClick = { onOpen(PromptFile.SystemPrompt) }) {
                RowValue(promptFileState(files, PromptFile.SystemPrompt)); RowChevron()
            }
        }
        FooterNote(stringResource(R.string.prompts_editor_foot))
    }
}

/** A plain markdown editor for one prompt file; saves as you type and on leaving, with Reset to the default. */
@Composable
fun PromptEditorScreen(file: PromptFile, onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember { PromptFiles.get(context) }
    var text by remember { mutableStateOf(files.read(file)) }
    var dirty by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (!dirty) return@LaunchedEffect
        delay(500)
        files.write(file, text)
        dirty = false
    }
    DisposableEffect(Unit) { onDispose { if (dirty) files.write(file, text) } }
    val c = MikaTheme.colors
    PageScaffold(file.fileName, onBack) {
        val label = file.fileName
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(20.dp)).padding(16.dp),
        ) {
            if (text.isEmpty()) Text(stringResource(R.string.prompts_instructions_placeholder),
                style = MikaType.Body.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp), color = c.ink3)
            BasicTextField(
                text, { text = it; dirty = true },
                Modifier.fillMaxWidth().heightIn(min = 240.dp).semantics { contentDescription = label }.testTag("prompt-editor"),
                textStyle = MikaType.Body.copy(color = c.ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(c.accent),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
            MikaButton(stringResource(R.string.prompts_reset), onClick = {
                files.reset(file); dirty = false; text = files.read(file)
            }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
        FooterNote(stringResource(R.string.prompts_editor_foot))
    }
}
