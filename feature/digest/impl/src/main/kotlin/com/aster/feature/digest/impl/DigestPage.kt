package com.aster.feature.digest.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.DigestAgentRepository
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.design_system.component.AppSideBySidePane
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.aster.core.design_system.modifier.onPointerDown
import com.aster.core.model.NewsSource
import com.aster.core.ui.AgentConversationPanel
import com.aster.core.ui.DigestSettingsDialog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun DigestPage(
    contentPadding: PaddingValues,
    settingsRepository: DigestSettingsRepository,
    agentRepository: DigestAgentRepository,
    sources: List<NewsSource>,
    onOpenArticle: (String) -> Unit,
    onOverlayChanged: (Boolean) -> Unit = {},
    inputDismissRequest: Int = 0
) {
    val settingsFactory = remember(settingsRepository) { DigestSettingsViewModel.factory(settingsRepository) }
    val settingsVm: DigestSettingsViewModel = viewModel(factory = settingsFactory)
    val settings by settingsVm.uiState.collectAsStateWithLifecycle()
    val agentFactory = remember(agentRepository) { DigestAgentViewModel.factory(agentRepository) }
    val agentVm: DigestAgentViewModel = viewModel(factory = agentFactory)
    val state by agentVm.uiState.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var composerHeight by remember { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    // 页面退出后不恢复展开态，未发送文字仍随会话保存
    var expanded by remember { mutableStateOf(false) }
    var draft by rememberSaveable(state.conversationId) { mutableStateOf("") }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }
    var actionBounds by remember { mutableStateOf(Rect.Zero) }
    var pageCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val overlayChanged by rememberUpdatedState(onOverlayChanged)
    DisposableEffect(settingsOpen) {
        overlayChanged(settingsOpen)
        onDispose { overlayChanged(false) }
    }
    LaunchedEffect(historyOpen, settingsOpen) {
        if (historyOpen || settingsOpen) expanded = false
    }
    LaunchedEffect(inputDismissRequest) { expanded = false }
    LaunchedEffect(expanded) { if (!expanded) inputBounds = Rect.Zero }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) expanded = false }
    BackHandler(enabled = historyOpen && !settingsOpen) { historyOpen = false }
    val background = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop { drawRect(background); drawContent() }
    val density = LocalDensity.current
    val bottom = contentPadding.calculateBottomPadding()
    val chatPadding = PaddingValues(start = 12.dp, end = 12.dp, top = contentPadding.calculateTopPadding() + 64.dp,
        bottom = maxOf(bottom + 80.dp, with(density) { composerHeight.toDp() } + 12.dp))
    val configured = settings.settings?.let { it.hasApiKey && it.sourceIds.isNotEmpty() } == true && !settings.readFailed
    val ready = configured && !state.loading && !state.readFailed
    val switchable = !state.running && !state.loading && !state.saveFailed && !state.readFailed
    val send: (String) -> Boolean = { prompt ->
        if (ready && agentVm.send(prompt)) { draft = ""; expanded = false; true } else false
    }
    val newConversation = {
        expanded = false
        agentVm.newConversation()
        draft = ""
        historyOpen = false
    }
    val openArticle: (String) -> Unit = { expanded = false; onOpenArticle(it) }

    Box(Modifier.fillMaxSize().imePadding().onGloballyPositioned { pageCoordinates = it }
        .onPointerDown { position ->
            val windowPosition = pageCoordinates?.takeIf { it.isAttached }?.localToWindow(position)
            if (expanded && windowPosition != null && !inputBounds.contains(windowPosition) && !actionBounds.contains(windowPosition)) {
                expanded = false
            }
        }) {
        AppSideBySidePane(
            expanded = historyOpen,
            onExpandedChange = { expanded = false; historyOpen = it },
            gesturesEnabled = !settingsOpen && !expanded,
            onInteraction = { expanded = false },
            modifier = Modifier.testTag("digest_side_by_side"),
            sideContent = {
                AgentConversationPanel(state.conversations, state.conversationId, switchable,
                    state.historyReadFailed, contentPadding,
                    onOpenConversation = { id -> expanded = false; agentVm.load(id); historyOpen = false },
                    onNewConversation = newConversation)
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    DigestChatContent(state, configured, settings.readFailed, chatPadding, list, openArticle,
                        send, { expanded = false; agentVm.retry() }, { expanded = false; agentVm.load() },
                        { expanded = false; agentVm.saveAgain() }, active = !historyOpen && !settingsOpen)
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = contentPadding.calculateTopPadding()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(if (historyOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.List,
                        stringResource(if (historyOpen) R.string.agent_back else R.string.agent_history_open),
                        { expanded = false; historyOpen = !historyOpen }, backdrop, Modifier.testTag("digest_history"))
                    Spacer(Modifier.weight(1f))
                    AppIconButton(Icons.Default.Add, stringResource(R.string.agent_new), newConversation,
                        backdrop, Modifier.testTag("agent_new"), enabled = switchable)
                    AppIconButton(Icons.Default.Settings, stringResource(R.string.digest_settings),
                        { expanded = false; settingsOpen = true }, backdrop, Modifier.testTag("digest_open_settings"))
                }
                if (!settingsOpen) DigestComposer(state, ready, list, backdrop, bottom,
                    draft, { if (it.length <= 4000) draft = it }, expanded,
                    { expanded = it; if (it) historyOpen = false }, send, agentVm::cancel,
                    onHeightChanged = { composerHeight = it },
                    onInputBoundsChanged = { inputBounds = it }, onActionBoundsChanged = { actionBounds = it })
            }
        }
        if (settingsOpen) DigestSettingsDialog(settings.settings, sources, settings.saving, settings.readFailed,
            settings.savedRevision, settings.message?.let { stringResource(it) }, backdrop,
            settingsVm::save, { settingsOpen = false })
    }
}
