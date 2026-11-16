package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.theme.MikaType
import androidx.compose.foundation.ScrollState
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wangzan101.mika.settings.ProviderConnection
import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import com.wangzan101.mika.ui.components.MikaDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wangzan101.mika.BuildConfig
import com.wangzan101.mika.cap.sandbox.SandboxState
import com.wangzan101.mika.capsule.CapsuleMode
import com.wangzan101.mika.capsule.CapsuleSetup
import com.wangzan101.mika.cap.phone.a11y.ScreenAccessibility
import com.wangzan101.mika.core.agent.SpeedFlags
import com.wangzan101.mika.core.cap.sandbox.SandboxStatus
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.record.RunStore
import com.wangzan101.mika.settings.AccessibilityState
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.settings.Problem
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.NetProxyStore
import com.wangzan101.mika.settings.WebSearchConfig
import com.wangzan101.mika.settings.WebSearchSettings
import com.wangzan101.mika.settings.VoiceEngineSettings
import com.wangzan101.mika.settings.PromptFile
import com.wangzan101.mika.cap.web.WebProxy
import com.wangzan101.mika.settings.SystemStatus
import com.wangzan101.mika.settings.notificationOverview
import com.wangzan101.mika.task.ParallelCapability
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.PillTone
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.StateCard
import com.wangzan101.mika.ui.components.StatusPill
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.i18n.AppLanguage
import com.wangzan101.mika.whatsapp.WhatsappStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置里能进入的子页面;[Home] 是设置首页,[Advanced] 集中放置连接、维护和工程能力。 */
private enum class SettingsPage { Home, Advanced, AppAccess, PhoneOps, Calls, Whatsapp, DuringWork, Capsule, Language, Appearance, Voice, Prompts, PromptInstructions, PromptSystem, Notifications, Recommendations, Sandbox, Recovery, SelfCheck, ComputerAccess, McpServers, Root, Developer, RunStats }

/**
 * 设置页:总状态卡 + 常用 / 我的数据 / 其他。高级与其他只增加一层,各子页返回来时的页面。
 * 模型页由 [onOpenModels]、记忆与数据由 [onOpenMemory]、关于页由 [onOpenAbout] 交给 Activity。
 * 修复动作由 [onFix] 交给 Activity 去做(请求权限、跳转系统设置)。
 */
@Composable
fun SettingsScreen(
    store: ProviderStore,
    status: SystemStatus,
    androidRelease: String,
    onFix: (Fix) -> Unit,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenMemory: (() -> Unit)? = null,
    /** 任务运行里的「自主模式」:当前值和换它的办法。 */
    autonomous: Boolean = false,
    onAutonomous: (Boolean) -> Unit = {},
    /** 查到的新版本号;有它时「关于 Mika」一行写「x 可更新」带红点。 */
    updateVersion: String? = null,
    onOpenAbout: () -> Unit = {},
    /** Opens the first-run flow again; nothing it remembers is reset. */
    onReplayIntro: (() -> Unit)? = null,
    /** 外部 MCP server 设置(outbound);为空时不显示入口。 */
    mcpServers: com.wangzan101.mika.settings.McpServerStore? = null,
    onOpenSpeech: () -> Unit = {},
) {
    val configs by store.configs.collectAsState()
    val roles by store.roles.collectAsState()
    // 开发者选项里的「看图定位已验证」跟着看图模型(看图用主模型时是主模型那条配置)
    val visionConfig = remember(configs, roles) { store.configFor(ModelRole.Vision) }
    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    var explaining by remember { mutableStateOf<Problem?>(null) }
    // 「完全退出」先问一句:任务、胶囊和通知都会停
    var confirmingQuit by remember { mutableStateOf(false) }

    fun fix(f: Fix) = if (f == Fix.AddModel) onOpenModels() else onFix(f)
    val onProblem: (Problem) -> Unit = { p -> StatusCopy.directFix(p)?.let(::fix) ?: run { explaining = p } }

    // 并行任务的上限只在实验开关 parallelTasks 打开时出现;设备能力按此刻算,第二块屏建失败过就按失败算
    val context = LocalContext.current
    val recommendedTasks = remember { (context.applicationContext as com.wangzan101.mika.MikaApp).recommendedTasks }
    val root = remember { com.wangzan101.mika.cap.phone.PhoneServices.of(context).shellManager.root }
    val rootState by root.state.collectAsState()
    LaunchedEffect(Unit) { root.refresh() }
    val whatsapp = remember { (context.applicationContext as com.wangzan101.mika.MikaApp).whatsapp }
    val whatsappStatus by whatsapp.status.collectAsState()
    val flags = remember { FeatureFlags.get(context) }
    var personalStart by remember { mutableStateOf(flags.personalStart()) }
    var parallelMax by remember { mutableIntStateOf(flags.parallelMax()) }
    var keepScreenOn by remember { mutableStateOf(flags.keepScreenOn()) }
    var muteTarget by remember { mutableStateOf(flags.muteTarget()) }
    var operatingPace by remember { mutableStateOf(flags.operatingPace()) }
    var stepLimit by remember { mutableStateOf(flags.taskStepLimit()) }
    var tokenBudget by remember { mutableStateOf(flags.taskTokenBudget()) }
    var selfConfig by remember { mutableStateOf(flags.selfConfig()) }
    var selfOperate by remember { mutableStateOf(flags.selfOperate()) }
    var pipOperate by remember { mutableStateOf(flags.pipOperate()) }
    var externalSchedule by remember { mutableStateOf(flags.externalSchedule()) }
    var autoArchiveDays by remember { mutableStateOf(flags.autoArchiveDays()) }
    var editingAutoArchive by remember { mutableStateOf(false) }
    val capsuleMode by remember(flags) { flags.capsuleModeChanges() }.collectAsState(flags.capsuleMode())
    val themeMode by remember(flags) { flags.themeModeChanges() }.collectAsState(flags.themeMode())
    val voiceEngine by remember { VoiceEngineSettings.get(context) }.engine.collectAsState()
    var capsuleIdleOpacity by remember { mutableStateOf(flags.capsuleIdleOpacity()) }
    var capsuleCardScale by remember { mutableStateOf(flags.capsuleCardScale()) }
    // 换语言时界面会重建,这里每次进来读一次即可
    val language = remember { AppLanguage.current(context) }
    val runtimeLimit by (context.applicationContext as com.wangzan101.mika.MikaApp).taskState.parallelLimit.collectAsState()
    var speed by remember { mutableStateOf(flags.speedFlags()) }
    val scope = rememberCoroutineScope()
    val sandboxStatus by remember { SandboxState.get(context) }.status.collectAsState()
    // 网络代理:只在这里改;读设置要解密、看浏览器支不支持要加载 WebView,都放到后台
    val proxies = remember { NetProxyStore.get(context) }
    var proxy by remember { mutableStateOf<String?>(null) }
    var browserProxy by remember { mutableStateOf<WebProxy.Mode?>(null) }
    var editingProxy by remember { mutableStateOf(false) }
    LaunchedEffect(proxy) {
        if (proxy == null) proxy = withContext(Dispatchers.IO) { proxies.get() }
        browserProxy = withContext(Dispatchers.IO) { WebProxy.mode() }
    }
    val searchSettings = remember { WebSearchSettings.get(context) }
    var webSearch by remember { mutableStateOf<WebSearchConfig?>(null) }
    var editingSearch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { webSearch = withContext(Dispatchers.IO) { searchSettings.get() } }
    val mcp = remember { (context.applicationContext as com.wangzan101.mika.MikaApp).mcp }
    val mcpNetworkOn by mcp.settings.enabled.collectAsState()
    val mcpUsbOn by mcp.usb.enabled.collectAsState()
    val mcpOn = mcpNetworkOn || mcpUsbOn
    val parallel = if (!speed.parallelTasks) null else ParallelSetting(
        parallelMax,
        ParallelCapability.detect(
            status.sdkInt, context.getSystemService(ActivityManager::class.java).isLowRamDevice, parallelMax,
            secondDisplayFailed = runtimeLimit.reason == ParallelCapability.SECOND_DISPLAY_FAILED,
        ),
    )
    // 开发者选项只在调试版和性能测试版里列出:发布版的各开关按评测结果统一定默认值
    val showDeveloper = com.wangzan101.mika.record.DetailedTraces.supportedBuild
    val toHome = { page = SettingsPage.Home }
    val toAdvanced = { page = SettingsPage.Advanced }
    val back = {
        page = when (page) {
            SettingsPage.RunStats -> SettingsPage.Developer
            SettingsPage.SelfCheck -> SettingsPage.Recovery
            SettingsPage.PromptInstructions, SettingsPage.PromptSystem -> SettingsPage.Prompts
            SettingsPage.Sandbox, SettingsPage.ComputerAccess,
            SettingsPage.McpServers, SettingsPage.Root, SettingsPage.Developer -> SettingsPage.Advanced
            else -> SettingsPage.Home
        }
    }

    if (page != SettingsPage.Home) BackHandler { back() }

    val visiblePage = when {
        !showDeveloper && (page == SettingsPage.Developer || page == SettingsPage.RunStats) -> SettingsPage.Home
        page == SettingsPage.Root && !rootState.suPresent -> SettingsPage.Advanced
        else -> page
    }
    when (visiblePage) {
        SettingsPage.Home -> SettingsContent(
            status = status, androidRelease = androidRelease,
            mainModel = roles.main?.let { id -> configs.firstOrNull { it.id == id } }?.let(ProviderConnection::displayName),
            onBack = onBack, onProblem = onProblem, onOpenModels = onOpenModels,
            onOpenPhoneOps = { page = SettingsPage.PhoneOps },
            onOpenCalls = { page = SettingsPage.Calls },
            whatsappStatus = whatsappStatus,
            onOpenWhatsapp = { page = SettingsPage.Whatsapp },
            onOpenAppAccess = { page = SettingsPage.AppAccess },
            onOpenDuringWork = { page = SettingsPage.DuringWork },
            capsuleMode = capsuleMode, onOpenCapsule = { page = SettingsPage.Capsule },
            onOpenNotifications = { page = SettingsPage.Notifications },
            language = language, onOpenLanguage = { page = SettingsPage.Language },
            personalStart = personalStart,
            onPersonalStart = { flags.setPersonalStart(it); personalStart = it },
            autoArchiveDays = autoArchiveDays,
            onOpenAutoArchive = { editingAutoArchive = true },
            onOpenMemory = onOpenMemory,
            onOpenRecommendations = { page = SettingsPage.Recommendations },
            onOpenAdvanced = { page = SettingsPage.Advanced },
            appearance = appearanceLabel(themeMode), onOpenAppearance = { page = SettingsPage.Appearance },
            voiceEngine = voiceEngineLabel(voiceEngine), onOpenVoice = { page = SettingsPage.Voice },
            onOpenPrompts = { page = SettingsPage.Prompts },
            onOpenFix = if (status.sdkInt >= Prerequisites.MIN_SDK) ({ page = SettingsPage.Recovery }) else null,
            onQuit = { confirmingQuit = true },
            versionName = BuildConfig.VERSION_NAME, updateVersion = updateVersion, onOpenAbout = onOpenAbout, onReplayIntro = onReplayIntro,
        )
        SettingsPage.Advanced -> AdvancedSettingsContent(
            proxy = proxy, proxyNote = proxy?.let { NetProxyCopy.subtitle(it, browserProxy) }, onOpenProxy = { editingProxy = true },
            webSearch = webSearch, onOpenWebSearch = { editingSearch = true },
            sandbox = sandboxStatus, onOpenSandbox = { page = SettingsPage.Sandbox },
            showRecovery = status.sdkInt >= Prerequisites.MIN_SDK, onOpenRecovery = { page = SettingsPage.Recovery },
            computerAccess = mcpOn, onOpenComputerAccess = { page = SettingsPage.ComputerAccess },
            mcpServersCount = mcpServers?.servers?.value?.size, onOpenMcpServers = { page = SettingsPage.McpServers },
            showRoot = rootState.suPresent, rootCommands = rootState.commands, onOpenRoot = { page = SettingsPage.Root },
            showDeveloper = showDeveloper, onOpenDeveloper = { page = SettingsPage.Developer },
            onBack = toHome,
            onQuit = { confirmingQuit = true },
        )
        SettingsPage.AppAccess -> AppAccessScreen(onBack = toHome)
        SettingsPage.Whatsapp -> WhatsappSettingsScreen(whatsapp, onBack = toHome)
        SettingsPage.PhoneOps -> PhoneOpsScreen(
            status, androidRelease, onProblem,
            onRepair = { onFix(Fix.RepairPhoneHelper) }, onUnpair = { onFix(Fix.UnpairPhoneHelper) }, onBack = toHome,
        )
        SettingsPage.Calls -> CallSettingsScreen((context.applicationContext as com.wangzan101.mika.MikaApp).calls, onBack = toHome)
        SettingsPage.Notifications -> NotificationsScreen(status, onFix = ::fix, onBack = back)
        SettingsPage.DuringWork -> DuringWorkScreen(
            keepScreenOn, onKeepScreenOn = { flags.setKeepScreenOn(it); keepScreenOn = it },
            muteTarget, onMuteTarget = { flags.setMuteTarget(it); muteTarget = it },
            parallel, onParallelMax = { flags.setParallelMax(it); parallelMax = it }, onBack = toHome,
            autonomous = autonomous, onAutonomous = onAutonomous,
            operatingPace = operatingPace, onOperatingPace = { flags.setOperatingPace(it); operatingPace = it },
            stepLimit = stepLimit, onStepLimit = { flags.setTaskStepLimit(it); stepLimit = it },
            tokenBudget = tokenBudget, onTokenBudget = { flags.setTaskTokenBudget(it); tokenBudget = it },
            selfConfig = selfConfig, onSelfConfig = { flags.setSelfConfig(it); selfConfig = it },
            selfOperate = selfOperate, onSelfOperate = { flags.setSelfOperate(it); selfOperate = it },
            pipOperate = pipOperate, onPipOperate = { flags.setPipOperate(it); pipOperate = it },
            externalSchedule = externalSchedule, onExternalSchedule = { flags.setExternalSchedule(it); externalSchedule = it },
        )
        SettingsPage.Language -> LanguageScreen(language, onLanguage = { AppLanguage.set(context, it) }, onBack = toHome, languageOnly = true)
        SettingsPage.Appearance -> LanguageScreen(language, onLanguage = { AppLanguage.set(context, it) }, onBack = toHome, languageOnly = false)
        SettingsPage.Voice -> VoiceSettingsScreen(onOpenSpeech = onOpenSpeech, onBack = toHome)
        SettingsPage.Prompts -> PromptFilesScreen(onOpen = { page = if (it == PromptFile.Instructions) SettingsPage.PromptInstructions else SettingsPage.PromptSystem }, onBack = toHome)
        SettingsPage.PromptInstructions -> PromptEditorScreen(PromptFile.Instructions, onBack = back)
        SettingsPage.PromptSystem -> PromptEditorScreen(PromptFile.SystemPrompt, onBack = back)
        SettingsPage.Recommendations -> RecommendedTasksScreen(recommendedTasks, onBack = toHome)
        SettingsPage.Capsule -> CapsuleScreen(
            capsuleMode, onMode = {
                flags.setCapsuleMode(it)
                if (CapsuleSetup.needsAccessibility(it, ScreenAccessibility.instance.value != null)) onFix(Fix.OpenAccessibility)
            }, onBack = toHome,
            idleOpacity = capsuleIdleOpacity,
            onIdleOpacity = { flags.setCapsuleIdleOpacity(it); capsuleIdleOpacity = it },
            cardScale = capsuleCardScale,
            onCardScale = { flags.setCapsuleCardScale(it); capsuleCardScale = it },
        )
        SettingsPage.Sandbox -> SandboxScreen(onBack = toAdvanced)
        SettingsPage.Recovery -> RecoveryScreen(onBack = toHome, onOpenSelfCheck = { page = SettingsPage.SelfCheck })
        SettingsPage.SelfCheck -> SelfCheckScreen(status, onBack = back)
        SettingsPage.ComputerAccess -> McpScreen(mcp, onBack = toAdvanced)
        SettingsPage.McpServers -> mcpServers?.let { McpServersScreen(it, onBack = toAdvanced) }
        SettingsPage.Root -> RootScreen(helperRow = status.sdkInt >= Prerequisites.MIN_SDK, onBack = toAdvanced)
        SettingsPage.Developer -> DeveloperOptionsScreen(
            speed, onSpeed = { flags.setSpeed(it); speed = it }, onBack = toAdvanced,
            grounding = visionConfig?.let { GroundingSetting(it.model, it.capabilities.groundingVerified) },
            onGrounding = { verified -> scope.launch { withContext(Dispatchers.IO) { store.setVisionGroundingVerified(verified) } } },
            onOpenRunStats = { page = SettingsPage.RunStats },
        )
        SettingsPage.RunStats -> RunStatsScreen(remember { RunStore(context).metrics }, onBack = back)
    }

    if (editingProxy) NetProxyDialog(
        initial = proxy.orEmpty(),
        onDismiss = { editingProxy = false },
        onSave = { v ->
            scope.launch {
                withContext(Dispatchers.IO) { proxies.set(v) }
                proxy = v.trim()
                editingProxy = false
            }
        },
    )

    if (editingSearch) WebSearchDialog(
        initial = webSearch ?: WebSearchConfig(),
        onDismiss = { editingSearch = false },
        onSave = { config ->
            scope.launch {
                withContext(Dispatchers.IO) { searchSettings.set(config) }
                webSearch = searchSettings.get()
                editingSearch = false
            }
        },
    )

    explaining?.let { p ->
        val e = StatusCopy.explain(p, androidRelease)
        MikaDialog(
            onDismissRequest = { explaining = null },
            title = { Text(e.title) },
            text = {
                Column {
                    Text(e.body)
                    e.secondaryAction?.let { (label, destination) ->
                        TextButton(onClick = {
                            explaining = null
                            fix(destination)
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {
                if (e.action != null && e.fix != null) {
                    TextButton({ explaining = null; fix(e.fix) }) { Text(e.action) }
                } else {
                    TextButton({ explaining = null }) { Text(stringResource(R.string.ok_got_it)) }
                }
            },
            dismissButton = if (e.fix != null) ({ TextButton({ explaining = null }) { Text(stringResource(R.string.cancel)) } }) else null,
        )
    }

    if (confirmingQuit) {
        AlertDialog(
            onDismissRequest = { confirmingQuit = false },
            title = { Text(stringResource(R.string.quit_title)) },
            text = { Text(stringResource(R.string.quit_confirm_body)) },
            confirmButton = {
                TextButton({ confirmingQuit = false; com.wangzan101.mika.quit.MikaQuit.quit(context) }) {
                    Text(stringResource(R.string.quit_confirm_action), color = MikaTheme.colors.bad)
                }
            },
            dismissButton = { TextButton({ confirmingQuit = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editingAutoArchive) {
        AutoArchiveDialog(
            current = autoArchiveDays,
            onPick = { days ->
                flags.setAutoArchiveDays(days)
                autoArchiveDays = days
                editingAutoArchive = false
                // 选完马上按新设置跑一次,老对话立刻进归档
                (context.applicationContext as com.wangzan101.mika.MikaApp).autoArchiveNow()
            },
            onDismiss = { editingAutoArchive = false },
        )
    }
}

/** Solid icon-tile colours on the Settings home, so a row can be found by eye. */
internal object SettingsTint {
    val Blue = Color(0xFF0A7CFF)
    val Purple = Color(0xFF9B5CF5)
    val Green = Color(0xFF25B35F)
    val Orange = Color(0xFFF08A1C)
    val Red = Color(0xFFE5483D)
    val Teal = Color(0xFF1AA5A5)
    val WhatsApp = Color(0xFF1FA855)
    val Pink = Color(0xFFE0306A)
    val Indigo = Color(0xFF5856D6)
    val Gray = Color(0xFF636366)
    val Yellow = Color(0xFFC99700)
}

/** One row on the Settings home; [keywords] are extra words the search box matches. */
private class SettingsEntry(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String?,
    val keywords: String = "",
    val onClick: (() -> Unit)?,
    val trailing: @Composable RowScope.() -> Unit = { RowChevron() },
) {
    fun matches(q: String): Boolean = listOf(title, subtitle.orEmpty(), keywords).any { it.contains(q, ignoreCase = true) }
}

/** 不带状态的设置首页,便于测试。六组:大脑与声音、能做什么、联系 Orbit、外观、你的数据、更多;顶上能搜。 */
@Composable
fun SettingsContent(
    status: SystemStatus,
    androidRelease: String,
    /** 当前主模型的名字;没有可用的主模型时为空。 */
    mainModel: String?,
    onBack: () -> Unit,
    onProblem: (Problem) -> Unit,
    onOpenModels: () -> Unit,
    onOpenPhoneOps: () -> Unit = {},
    onOpenCalls: () -> Unit = {},
    whatsappStatus: WhatsappStatus = WhatsappStatus.NotLinked,
    onOpenWhatsapp: () -> Unit = {},
    onOpenAppAccess: () -> Unit = {},
    onOpenDuringWork: () -> Unit = {},
    /** 悬浮胶囊此刻的选择,写在「悬浮胶囊」一行的右边。 */
    capsuleMode: CapsuleMode = CapsuleMode.DEFAULT,
    onOpenCapsule: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    /** 此刻选的界面语言,写在「语言」一行的右边。 */
    language: AppLanguage = AppLanguage.System,
    onOpenLanguage: () -> Unit = {},
    onOpenMemory: (() -> Unit)? = null,
    onOpenRecommendations: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    versionName: String = "",
    updateVersion: String? = null,
    onOpenAbout: () -> Unit = {},
    /** Opens the first-run flow again; nothing it remembers is reset. */
    onReplayIntro: (() -> Unit)? = null,
    personalStart: Boolean = false,
    onPersonalStart: ((Boolean) -> Unit)? = null,
    /** 自动归档:多久没有新消息的对话自动归档;0 表示关闭。 */
    autoArchiveDays: Int = 0,
    onOpenAutoArchive: () -> Unit = {},
    /** The light/dark choice, shown on the Appearance row. */
    appearance: String? = null,
    onOpenAppearance: () -> Unit = onOpenLanguage,
    /** The live call engine's name, shown on the Voice row. */
    voiceEngine: String? = null,
    onOpenVoice: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    /** Null hides "Fix a problem" (phones too old for the repair tools). */
    onOpenFix: (() -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
) {
    val check = Prerequisites.check(status, hasMainModel = mainModel != null)
    var query by rememberSaveable { mutableStateOf("") }
    val notSet = stringResource(R.string.settings_not_set)
    val (notifText, notifTone) = StatusCopy.notificationSummary(status.notificationOverview().summary)
    val groups: List<Pair<String, List<SettingsEntry>>> = listOf(
        stringResource(R.string.settings_group_brain) to listOf(
            SettingsEntry(MikaIcons.Spark, SettingsTint.Blue, stringResource(R.string.settings_model), stringResource(R.string.settings_model_sub),
                "provider api key token usage gpt gemini claude", onOpenModels) { RowValue(mainModel ?: notSet); RowChevron() },
            SettingsEntry(MikaIcons.Mic, SettingsTint.Purple, stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_sub),
                "speech call live gemini openai realtime tts microphone", onOpenVoice) { voiceEngine?.let { RowValue(it) }; RowChevron() },
            SettingsEntry(MikaIcons.Doc, SettingsTint.Orange, stringResource(R.string.settings_prompts), stringResource(R.string.settings_prompts_sub),
                "system prompt instructions personality rules", onOpenPrompts),
        ),
        stringResource(R.string.settings_group_abilities) to listOf(
            SettingsEntry(MikaIcons.Phone, SettingsTint.Green, stringResource(R.string.settings_phone_ops), stringResource(R.string.settings_phone_ops_sub),
                "accessibility shizuku adb root control screen", onOpenPhoneOps) { PhoneOpsPill(status); RowChevron() },
            SettingsEntry(MikaIcons.Clock, SettingsTint.Orange, stringResource(R.string.settings_during_work), stringResource(R.string.settings_during_work_sub),
                "autonomous pace step limit keep screen on mute speed", onOpenDuringWork),
            SettingsEntry(MikaIcons.Shield, SettingsTint.Red, stringResource(R.string.appaccess_title), stringResource(R.string.appaccess_row_sub),
                "app access block privacy screen sharing", onOpenAppAccess),
        ),
        stringResource(R.string.orbit_settings_reach) to listOf(
            SettingsEntry(MikaIcons.Call, SettingsTint.Teal, stringResource(R.string.calls_title), stringResource(R.string.calls_entry_sub),
                "phone calls answer cellular sim volcengine", onOpenCalls),
            SettingsEntry(MikaIcons.Chats, SettingsTint.WhatsApp, stringResource(R.string.whatsapp_title), stringResource(R.string.whatsapp_settings_sub),
                "whatsapp link message chat", onOpenWhatsapp) { WhatsappEntryStatus(whatsappStatus); RowChevron() },
            SettingsEntry(MikaIcons.Bell, SettingsTint.Red, StatusCopy.NOTIFICATIONS, stringResource(R.string.settings_notif_sub),
                "notifications alerts heads-up", onOpenNotifications) { RowValue(notifText, notifTone); RowChevron() },
        ),
        stringResource(R.string.orbit_settings_look) to listOfNotNull(
            SettingsEntry(MikaIcons.Moon, SettingsTint.Indigo, stringResource(R.string.appearance_group), stringResource(R.string.settings_appearance_sub),
                "theme dark light amoled accent colour color background wallpaper identity orb", onOpenAppearance) { appearance?.let { RowValue(it) }; RowChevron() },
            SettingsEntry(MikaIcons.Globe, SettingsTint.Blue, stringResource(R.string.language_title), stringResource(R.string.settings_language_sub),
                "language", onOpenLanguage) { RowValue(languageLabel(language)); RowChevron() },
            SettingsEntry(MikaIcons.Message, SettingsTint.Gray, CapsuleSettingsCopy.TITLE, CapsuleSettingsCopy.SUBTITLE,
                "capsule bubble floating overlay", onOpenCapsule) { RowValue(CapsuleSettingsCopy.label(capsuleMode)); RowChevron() },
            SettingsEntry(MikaIcons.Star, SettingsTint.Yellow, stringResource(R.string.recommendations_title), stringResource(R.string.recommendations_sub),
                "recommended tasks prompts home", onOpenRecommendations),
            onPersonalStart?.let { change ->
                val label = stringResource(R.string.settings_personal_start)
                SettingsEntry(MikaIcons.Home, SettingsTint.Gray, label, stringResource(R.string.settings_personal_start_sub),
                    "personal assistant start launch", null) {
                    MikaSwitch(personalStart, change, Modifier.semantics { contentDescription = label })
                }
            },
        ),
        stringResource(R.string.settings_privacy) to listOfNotNull(
            onOpenMemory?.let {
                SettingsEntry(MikaIcons.Heart, SettingsTint.Pink, stringResource(R.string.settings_memory_data), stringResource(R.string.settings_memory_sub),
                    "memory skills routes sign-ins websites data", it)
            },
            SettingsEntry(MikaIcons.Archive, SettingsTint.Gray, stringResource(R.string.settings_auto_archive), stringResource(R.string.settings_auto_archive_sub),
                "archive auto-archive chats", onOpenAutoArchive) {
                RowValue(
                    if (autoArchiveDays <= 0) stringResource(R.string.state_off)
                    else pluralStringResource(R.plurals.settings_auto_archive_days, autoArchiveDays, autoArchiveDays),
                )
                RowChevron()
            },
        ),
        stringResource(R.string.settings_other) to listOfNotNull(
            SettingsEntry(MikaIcons.Link, SettingsTint.Gray, stringResource(R.string.settings_advanced), stringResource(R.string.settings_advanced_sub),
                "advanced web search proxy network sandbox files computing mcp computer root developer", onOpenAdvanced),
            onOpenFix?.let {
                SettingsEntry(MikaIcons.Wrench, SettingsTint.Gray, stringResource(R.string.settings_recovery), stringResource(R.string.settings_recovery_sub),
                    "repair recovery self-check reset problem", it)
            },
            onReplayIntro?.let {
                SettingsEntry(MikaIcons.Refresh, SettingsTint.Gray, stringResource(R.string.settings_replay_intro), stringResource(R.string.settings_replay_intro_sub),
                    "intro welcome onboarding setup replay tour first run", it)
            },
            SettingsEntry(MikaIcons.Info, SettingsTint.Gray, stringResource(R.string.settings_about), stringResource(R.string.settings_about_sub),
                "about version update licence license", onOpenAbout) { TrailingValue(AboutCopy.settingsValue(updateVersion, versionName)); RowChevron() },
        ),
    )
    // A fresh scroll position each time Settings opens, so the status card at the top is never half under the bar.
    PageScaffold(stringResource(R.string.settings_title), onBack, scrollState = remember { ScrollState(0) }) {
        SettingsSearchField(query) { query = it }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val found = groups.flatMap { it.second }.filter { it.matches(q) }
            if (found.isEmpty()) {
                FooterNote(stringResource(R.string.settings_search_none, q))
            } else GroupCard {
                found.forEachIndexed { i, e -> SettingsEntryRow(e, first = i == 0) }
            }
            return@PageScaffold
        }
        StatusSummary(check, onProblem)
        groups.forEach { (label, entries) ->
            if (entries.isEmpty()) return@forEach
            GroupLabel(label)
            GroupCard { entries.forEachIndexed { i, e -> SettingsEntryRow(e, first = i == 0) } }
        }
        onQuit?.let { quit ->
            GroupCard {
                ListRow(
                    MikaIcons.Power, stringResource(R.string.quit_title), stringResource(R.string.quit_row_sub),
                    first = true, titleColor = MikaTheme.colors.bad, tint = SettingsTint.Red, onClick = quit,
                )
            }
        }
    }
}

@Composable
private fun SettingsEntryRow(e: SettingsEntry, first: Boolean) {
    ListRow(e.icon, e.title, e.subtitle, first = first, tint = e.tint, onClick = e.onClick, trailing = e.trailing)
}

/** The search box at the top of Settings: matches row titles, their explanations and a few extra words. */
@Composable
private fun SettingsSearchField(value: String, onChange: (String) -> Unit) {
    val c = MikaTheme.colors
    val label = stringResource(R.string.settings_search)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 46.dp)
            .background(c.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MikaIcons.Search, null, Modifier.size(18.dp), tint = c.ink3)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(label, style = MikaType.Body, color = c.ink3, maxLines = 1)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = label }.testTag("settings-search"),
                singleLine = true, textStyle = MikaType.Body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            )
        }
        if (value.isNotEmpty()) {
            Icon(MikaIcons.Close, stringResource(R.string.settings_search_clear),
                Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") }.padding(7.dp), tint = c.ink3)
        }
    }
}

/** 高级与其他:网络与连接、维护、开发者三组;各能力仍使用原来的完整子页。 */
@Composable
fun AdvancedSettingsContent(
    onBack: () -> Unit,
    proxy: String? = null,
    proxyNote: String? = null,
    onOpenProxy: () -> Unit = {},
    webSearch: WebSearchConfig? = null,
    onOpenWebSearch: () -> Unit = {},
    sandbox: SandboxStatus? = null,
    onOpenSandbox: () -> Unit = {},
    showRecovery: Boolean = true,
    onOpenRecovery: () -> Unit = {},
    computerAccess: Boolean? = null,
    onOpenComputerAccess: () -> Unit = {},
    mcpServersCount: Int? = null,
    onOpenMcpServers: () -> Unit = {},
    showRoot: Boolean = false,
    rootCommands: Boolean = false,
    onOpenRoot: () -> Unit = {},
    showDeveloper: Boolean = false,
    onOpenDeveloper: () -> Unit = {},
    onQuit: () -> Unit = {},
) {
    PageScaffold(stringResource(R.string.settings_advanced), onBack) {
        val sandboxRow = sandbox?.takeIf { it !is SandboxStatus.Unsupported }
        if (proxy != null || webSearch != null || sandboxRow != null || computerAccess != null || mcpServersCount != null || showRoot) {
            GroupLabel(stringResource(R.string.settings_connections))
            GroupCard {
                var first = true
                fun take(): Boolean = first.also { first = false }
                proxy?.let {
                    ListRow(MikaIcons.Link, NetProxyCopy.TITLE, proxyNote, first = take(), onClick = onOpenProxy) {
                        RowValue(NetProxyCopy.value(it))
                        RowChevron()
                    }
                }
                webSearch?.let { config ->
                    ListRow(MikaIcons.Globe, stringResource(R.string.web_search_title), stringResource(R.string.web_search_row_sub), first = take(), onClick = onOpenWebSearch) {
                        RowValue(WebSearchCopy.value(config)); RowChevron()
                    }
                }
                if (sandboxRow != null) {
                    ListRow(MikaIcons.Terminal, stringResource(R.string.settings_sandbox), stringResource(R.string.settings_sandbox_sub), first = take(), onClick = onOpenSandbox) {
                        RowValue(sandboxValue(sandboxRow)); RowChevron()
                    }
                }
                computerAccess?.let { on ->
                    ListRow(MikaIcons.Desktop, stringResource(R.string.mcp_title), stringResource(R.string.settings_mcp_sub), first = take(), onClick = onOpenComputerAccess) {
                        RowValue(stringResource(if (on) R.string.state_on else R.string.state_off)); RowChevron()
                    }
                }
                mcpServersCount?.let { count ->
                    ListRow(MikaIcons.Link, stringResource(R.string.mcp_servers_title), stringResource(R.string.settings_mcp_servers_sub), first = take(), onClick = onOpenMcpServers) {
                        RowValue(stringResource(R.string.mcp_servers_count, count)); RowChevron()
                    }
                }
                if (showRoot) {
                    ListRow(MikaIcons.Key, PhoneSetupCopy.ROOT_GROUP, PhoneSetupCopy.rootEntryLine(rootCommands), first = take(), onClick = onOpenRoot) {
                        RowChevron()
                    }
                }
            }
        }
        BackupSettingsGroup()
        // Repair and Quit now sit on the Settings home ("Fix a problem", "Quit Orbit").
        if (showDeveloper) {
            GroupLabel(stringResource(R.string.settings_developer_group))
            GroupCard {
                ListRow(MikaIcons.Code, stringResource(R.string.settings_developer), stringResource(R.string.settings_developer_sub), first = true, onClick = onOpenDeveloper) { RowChevron() }
            }
        }

    }
}

/** 「手机操作」一行尾部的状态胶囊:无障碍和内置助手都就绪才是「可用」。 */
@Composable
private fun PhoneOpsPill(status: SystemStatus) {
    when {
        status.sdkInt < Prerequisites.MIN_SDK -> StatusPill(stringResource(R.string.pill_unsupported), PillTone.Neutral)
        status.builtinShell == BuiltinShellStatus.Running && status.accessibility == AccessibilityState.On -> StatusPill(stringResource(R.string.pill_available), PillTone.Ok)
        else -> StatusPill(stringResource(R.string.pill_needs_attention), PillTone.Warn)
    }
}

private fun sandboxValue(s: SandboxStatus): String = when (s) {
    is SandboxStatus.Ready -> tr(R.string.sandbox_ready)
    is SandboxStatus.Installing -> tr(R.string.sandbox_preparing)
    is SandboxStatus.Broken -> tr(R.string.pill_needs_attention)
    else -> tr(R.string.sandbox_not_prepared)
}

/**
 * 页面顶上的总状态:一切正常是「一切就绪」;否则变成第一个问题的提示,带修复按钮。
 * 焦点提醒改不了,只在手机操作页的「系统版本」一行里提示,不占这里。
 */
@Composable
private fun StatusSummary(check: Prerequisites.Check, onProblem: (Problem) -> Unit) {
    val problem = check.problems.firstOrNull { it != Problem.FocusMayLeak }
    if (problem == null) {
        StateCard(MikaIcons.Check, stringResource(R.string.all_ready), stringResource(R.string.settings_all_ready_detail), TileTone.Ok, Modifier.testTag("settings-status"))
    } else {
        val copy = StatusCopy.banner(problem)
        StateCard(
            MikaIcons.Exclaim, copy.title, copy.detail,
            if (problem in check.blockers) TileTone.Bad else TileTone.Warn,
            Modifier.testTag("settings-status"),
        ) {
            MikaButton(copy.link, { onProblem(problem) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
        }
    }
}
