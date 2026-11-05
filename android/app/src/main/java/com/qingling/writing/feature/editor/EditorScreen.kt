package com.qingling.writing.feature.editor

import com.qingling.writing.core.design.panelGestures
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.animation.togetherWith
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.sp
import com.qingling.writing.core.design.WritingCanvas
import com.qingling.writing.core.design.textColor
import com.qingling.writing.core.design.family
import com.qingling.writing.core.model.EditorAppearance
import kotlinx.coroutines.delay
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import com.qingling.writing.core.design.ChromeAction
import com.qingling.writing.core.design.LocalMotionEnabled
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.CenterMessage
import com.qingling.writing.core.design.Dimens
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TouchTarget
import com.qingling.writing.core.model.LocalSaveState
import com.qingling.writing.core.model.SaveFailure
import com.qingling.writing.feature.outline.OutlineBrowser
import com.qingling.writing.core.model.QuickNote
import com.qingling.writing.core.model.PlanningEntry
import com.qingling.writing.core.model.OutlineChapter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.then
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily

/**
 * UX v0.2 编辑器:同页切章 + 自定义双侧面板 + 真实保存状态。
 * 替代旧 ModalNavigationDrawer/AnimatedVisibility(A01/A03/A05)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    chapterId: String,
    onBack: () -> Unit,
    onOpenExport: (String) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    initialOffset:Int = -1, initialEndOffset:Int = -1,
    onOpenInspiration:()->Unit = {},
    onOpenExportScope:(String,String,String)->Unit={bid,_,_->onOpenExport(bid)},
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val viewModel: EditorViewModel = viewModel(
        key = "editor",
        factory = viewModelFactory {
            initializer { EditorViewModel(container, container.outlineRepository, chapterId) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookId by viewModel.bookId.collectAsStateWithLifecycle()
    val currentSession by viewModel.editorSession.collectAsStateWithLifecycle()
    val switching by viewModel.switching.collectAsStateWithLifecycle()
    val chapterOrder by viewModel.chapterOrder.collectAsStateWithLifecycle()
    val appearance by container.settingsStore.appearance.collectAsStateWithLifecycle(initialValue = EditorAppearance())
    val customFontId by container.settingsStore.customFontId.collectAsStateWithLifecycle(initialValue="")
    val editorFontFamily=com.qingling.writing.core.design.rememberFontFamily(appearance.font,customFontId)
    val editorTextColor = appearance.textColor()
    val punctuation by container.settingsStore.punctuationBar.collectAsStateWithLifecycle(initialValue = true)
    val currentLine by container.settingsStore.currentLine.collectAsStateWithLifecycle(initialValue = false)
    val keepAwake by container.settingsStore.keepAwake.collectAsStateWithLifecycle(initialValue = false)
    val typewriter by container.settingsStore.typewriter.collectAsStateWithLifecycle(initialValue = false)
    var bodyFocused by remember { mutableStateOf(false) }
    var titleFocused by remember { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }
    var drawerLevel by rememberSaveable { mutableStateOf(0) }
    var drawerLoaded by remember { mutableStateOf(false) }
    var fullPresentation by remember { mutableStateOf(false) }
    var frozenBody by remember { mutableStateOf(false) }
    var drawerTransition by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var heldImePx by remember { mutableStateOf(0) }
    var ownedIme by remember { mutableStateOf(false) }
    var frozenChapter by remember { mutableStateOf<String?>(null) }
    var frozenScroll by remember { mutableStateOf(0) }
    var frozenSelection by remember { mutableStateOf(TextRange.Zero) }
    val chrome=remember(currentSession){EditorChromeState()}
    var leftStartsInLibrary by rememberSaveable { mutableStateOf(false) }
    var leftRequest by rememberSaveable { mutableStateOf(0) }
    var topChromeHeight by remember { mutableStateOf(0) }
    val toolMotion = rememberUpdatedState(LocalMotionEnabled.current)
    val toolDrawer = remember { WritingToolDrawerState(scope) { toolMotion.value } }
    var accessoryOpen by toolDrawer
    var accessoryHeightPx by remember { mutableStateOf(800f) }
    var scopedOutlineOpen by remember { mutableStateOf(false) }
    var bodyHeight by remember { mutableStateOf(0) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var wideOutlineVisible by rememberSaveable { mutableStateOf(true) }
    val localView = androidx.compose.ui.platform.LocalView.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(currentSession,lifecycle) {
        val session=currentSession
        session?.setWritingActive(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
            session?.setWritingActive(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) session?.checkpoint()
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);session?.setWritingActive(false)}
    }
    androidx.compose.runtime.DisposableEffect(localView,lifecycle,keepAwake) {
        val previous = localView.keepScreenOn
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            localView.keepScreenOn = keepAwake && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        localView.keepScreenOn = keepAwake && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        onDispose { lifecycle.removeObserver(observer); localView.keepScreenOn = previous }
    }
    var formattingSource by remember { mutableStateOf<FormattingSource?>(null) }
    var batchMode by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var bookSearchOpen by remember { mutableStateOf(false) }
    var planningOpen by remember { mutableStateOf(false) }
    var splitPreview by remember { mutableStateOf<com.qingling.writing.core.model.StructurePlan?>(null) }
    var mergeOpen by remember { mutableStateOf(false) }
    var historyOpen by rememberSaveable{mutableStateOf(false)}
    var chapterStatsOpen by rememberSaveable{mutableStateOf(false)}
    var chapterInfoOpen by rememberSaveable{mutableStateOf(false)}
    var chapterRenameOpen by remember { mutableStateOf(false) }
    var chapterRenameError by remember { mutableStateOf<com.qingling.writing.domain.validation.TitleError?>(null) }
    var writingStatsOpen by rememberSaveable { mutableStateOf(false) }
    var proofSource by remember(currentSession) { mutableStateOf<String?>(null) }
    var sourceRequest by remember { mutableStateOf<Triple<String,String,SearchMatch>?>(null) }
    var searchTarget by remember(currentSession) { mutableStateOf<SearchMatch?>(null) }
    var searchNavigation by remember { mutableStateOf(0) }
    var searchHighlights by remember(currentSession) { mutableStateOf<Pair<String, List<SearchMatch>>?>(null) }
    var textLayout by remember(currentSession) { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(currentSession,sourceRequest) {
        val request=sourceRequest;val session=currentSession
        if(request!=null && session?.chapterId==request.first && session.editorState.text.toString()==request.second) {
            if(session.editorState.composition==null)session.editorState.edit{selection=TextRange(request.third.start,request.third.end)}
            searchTarget=request.third;searchNavigation++
        }
    }
    val editorScroll = androidx.compose.runtime.key(currentSession) { rememberScrollState() }
    val typewriterSelection = currentSession?.editorState?.selection
    val typewriterLength = currentSession?.editorState?.text?.length
    val snackbar = remember { SnackbarHostState() }
    val formattedMessage = stringResource(R.string.format_applied)
    val ready = uiState is EditorUiState.Ready
    val gestureGuideSeen by container.settingsStore.editorGestureGuideSeen.collectAsStateWithLifecycle(initialValue = true)
    var gestureGuideDismissed by rememberSaveable { mutableStateOf(false) }
    val saveFailed by remember(viewModel) {
        viewModel.saveState.map { it is LocalSaveState.Failed }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    val motionEnabled by rememberUpdatedState(LocalMotionEnabled.current)
    val panels = remember { SidePanelState(scope) { motionEnabled } }
    LaunchedEffect(typewriter,typewriterSelection,typewriterLength,textLayout,bodyHeight,bodyFocused,searchOpen,panels.side,frozenBody,drawerLevel) {
        val session=currentSession
        if(typewriter && bodyFocused && !frozenBody && drawerLevel != 2 && !searchOpen && panels.side == null && session != null && bodyHeight > 0) {
            androidx.compose.runtime.withFrameNanos { }
            val cursor=textLayout?.getCursorRect(session.editorState.selection.end.coerceIn(0,session.editorState.text.length))
            if(cursor!=null) editorScroll.scrollTo((cursor.center.y-bodyHeight*.45f).toInt().coerceIn(0,editorScroll.maxValue))
        }
    }
    LaunchedEffect(motionEnabled) { if (!motionEnabled) panels.finishMotion() }
    LaunchedEffect(ready){if(!ready)panels.close()}
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(imeInsets, density) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrimBase = if (isDark) Dimens.Panels.scrimAlphaDark else Dimens.Panels.scrimAlphaLight
    val currentChapterId = currentSession?.chapterId ?: chapterId
    var selectionMode by rememberSaveable(currentChapterId) { mutableStateOf(false) }
    var proofBar by rememberSaveable(currentChapterId){mutableStateOf(false)}
    var proofIgnored by rememberSaveable(currentChapterId){mutableStateOf(setOf<String>())}
    var proofKey by rememberSaveable(currentChapterId){mutableStateOf<String?>(null)}
    var proofSnapshot by remember(currentChapterId){mutableStateOf<String?>(null)}
    var proofIssues by remember(currentChapterId){mutableStateOf(emptyList<ProofIssue>())}
    var proofBusy by remember(currentChapterId){mutableStateOf(false)}
    val visibleProof=proofIssues.filterNot{it.stableKey in proofIgnored}
    val activeProof=visibleProof.firstOrNull{it.stableKey==proofKey} ?: visibleProof.firstOrNull()
    fun locateProof(issue:ProofIssue){currentSession?.editorState?.let{state->if(state.composition==null){state.edit{selection=TextRange(issue.start,issue.end)};searchTarget=SearchMatch(issue.start,issue.end);searchNavigation++}}}
    fun advanceProof(move:Boolean=true){if(proofBusy)return;proofBusy=true;scope.launch{
        try{val session=currentSession ?: return@launch;val body=session.editorState.text.toString()
            if(session.editorState.composition!=null){snackbar.showSnackbar("请先确认输入法候选词");return@launch}
            val old=activeProof
            val issues=if(body==proofSnapshot)proofIssues else {
                val words=container.settingsStore.proofWords.first().lines().filter{it.isNotBlank()}
                withContext(Dispatchers.Default){Proofreader.scan(body,words)}.issues
            }
            if(session!==viewModel.session || session.editorState.text.toString()!=body){snackbar.showSnackbar("正文仍在变化，请停笔后重试");return@launch}
            proofSnapshot=body;proofIssues=issues
            val available=issues.filterNot{it.stableKey in proofIgnored}
            val index=available.indexOfFirst{it.stableKey==proofKey}
            val next=if(index>=0)available.getOrNull(if(move)(index+1)%available.size else index)
                else available.firstOrNull{it.start >= (old?.start ?: 0)} ?: available.firstOrNull()
            proofKey=next?.stableKey;next?.let{locateProof(it)}
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){snackbar.showSnackbar("校对更新未完成，请返回列表重试")}finally{proofBusy=false}
    }}
    LaunchedEffect(proofBar,currentSession){if(proofBar&&proofSnapshot==null)advanceProof(false)}
    fun startProof(){scope.launch{if(viewModel.requestClose()){proofBar=false;proofIgnored=emptySet();proofKey=null;proofSource=currentSession?.editorState?.text?.toString()}}}

    var noteQuote by rememberSaveable { mutableStateOf<String?>(null) }
    var noteForeshadow by rememberSaveable { mutableStateOf(false) }
    var noteOffset by rememberSaveable { mutableStateOf(0) }
    var noteChapter by rememberSaveable { mutableStateOf("") }
    var noteBook by rememberSaveable { mutableStateOf("") }
    var noteSourceTitle by rememberSaveable { mutableStateOf("") }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    val swipePanels by container.settingsStore.swipePanels.collectAsStateWithLifecycle(initialValue=true)
    val summaryEnabled by container.settingsStore.chapterSummary.collectAsStateWithLifecycle(initialValue=false)
    var completion by remember { mutableStateOf<CompletionData?>(null) }
    var summaryFull by remember { mutableStateOf(false) }
    val sessionStart=remember(currentSession) { android.os.SystemClock.elapsedRealtime() }
    val noteEntries by remember {container.planning.inbox()}.collectAsStateWithLifecycle(initialValue=emptyList())
    val selectedText=currentSession?.editorState?.let { state -> if(state.selection.collapsed) "" else state.text.subSequence(state.selection.min,state.selection.max).toString() }.orEmpty()
    fun exitSelection() { currentSession?.editorState?.let { state -> if(state.composition==null) state.edit { selection=TextRange(selection.max) } };selectionMode=false }
    fun captureNote(foreshadow:Boolean) { val offset=currentSession?.editorState?.selection?.min ?: 0;val quote=selectedText;keyboard?.hide();scope.launch {if(viewModel.requestClose()){noteOffset=offset;noteChapter=currentChapterId.orEmpty();noteBook=bookId.orEmpty();noteSourceTitle=(uiState as? EditorUiState.Ready)?.let {it.bookTitle+" · "+it.chapterTitle}.orEmpty();noteForeshadow=foreshadow;noteQuote=quote}else snackbar.showSnackbar("请先处理正文保存失败，再建立引用")}}
    fun completed(chapter:OutlineChapter,fromEditor:Boolean=false) { scope.launch {
        val today=container.writingProgress.days().first().firstOrNull {it.day==java.time.LocalDate.now().toString()}?.netWords ?: 0
        completion=CompletionData(chapter.title,chapter.wordCount,if(chapter.id==currentChapterId) ((currentSession?.sessionMetrics()?.activeMillis ?: 0)/60000).toInt() else 0,today)
        summaryFull=fromEditor && summaryEnabled
    } }

    var anchorApplied by rememberSaveable(chapterId,initialOffset,initialEndOffset){mutableStateOf(false)}
    LaunchedEffect(currentSession,initialOffset,initialEndOffset) {
        val session=currentSession
        if(!anchorApplied && initialOffset>=0 && session?.chapterId==chapterId) {
            val point=initialOffset.coerceIn(0,session.editorState.text.length);val end=initialEndOffset.coerceIn(point,session.editorState.text.length);session.editorState.edit{selection=TextRange(point,end)}
            sourceRequest=Triple(session.chapterId,session.editorState.text.toString(),SearchMatch(point,end));anchorApplied=true
        }
    }
    val referenceEnabled by container.settingsStore.referenceDrawerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val savedDrawerLevel by container.settingsStore.referenceDrawerLevel.collectAsStateWithLifecycle(initialValue = -1)
    val referenceTab by container.settingsStore.referenceTab.collectAsStateWithLifecycle(initialValue = 0)
    val accessoryPage by container.settingsStore.accessoryPage.collectAsStateWithLifecycle(initialValue = 0)
    val coordinateMode by container.settingsStore.coordinateMode.collectAsStateWithLifecycle(initialValue = 0)
    val progressDays by remember { container.writingProgress.days() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val todayWords = progressDays.firstOrNull { it.day == java.time.LocalDate.now().toString() }?.netWords ?: 0L
    val bookEntries by remember(bookId) { bookId?.let { container.planning.observe(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val previousId = chapterOrder.getOrNull(chapterOrder.indexOf(currentChapterId) - 1)
    val previousEnding by produceState<Pair<String?, String?>>(null to null, currentChapterId, previousId) {
        value = null to null
        value = if (previousId == null) "" to null else withContext(Dispatchers.IO) {
            try { container.writingRepository.readChapter(previousId).body.takeLast(1600) to null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { "" to "上章结尾暂时无法读取，请重新打开参考。" }
        }
    }
    val referenceData = EditorReferenceData(
        chapterId = currentChapterId,
        outline = bookEntries.firstOrNull { it.kind == com.qingling.writing.core.model.PlanningKind.CHAPTER_OUTLINE.name && it.chapterId == currentChapterId },
        people = bookEntries.filter { it.kind == com.qingling.writing.core.model.PlanningKind.CHARACTER.name },
        notes = bookEntries.filter { it.kind == com.qingling.writing.core.model.PlanningKind.INSPIRATION.name && QuickNote.decode(it.body).let { n -> n.foreshadow && !n.deleted && n.chapterId == currentChapterId } },
        previousEnding = previousEnding.first.orEmpty(), previousLoading = previousEnding.first == null, error = previousEnding.second)
    var referenceBusy by remember { mutableStateOf(false) }
    var referenceError by remember(currentChapterId) { mutableStateOf<String?>(null) }
    val imePixels = imeInsets.getBottom(density)
    val thinTop = ready && chrome.topMode == EditorTopMode.THIN
    val titleBlockVisible = !thinTop || titleFocused
    LaunchedEffect(bodyFocused,titleFocused,imeVisible,focusMode,selectionMode,editorScroll.value,editorScroll.maxValue,frozenBody){
        if(!frozenBody)chrome.environment(imeVisible,bodyFocused,titleFocused,focusMode,selectionMode,editorScroll.value==0,editorScroll.maxValue==0)
    }
    val chromeScrollAllowed by rememberUpdatedState(ready && !frozenBody && !focusMode && !selectionMode && !titleFocused)
    val chromeThreshold=with(density){24.dp.toPx()}
    val topScrollConnection=remember(currentSession,chromeThreshold) {
        EditorChromeScrollConnection(chromeThreshold,{chromeScrollAllowed},{full->chrome.scroll(full)},
            {editorScroll.value==0},{chrome.reachedTop()})
    }
    val effectiveDrawerLevel = if (referenceEnabled && !focusMode) drawerLevel else 0
    val blockManuscript = effectiveDrawerLevel == 2 || frozenBody
    LaunchedEffect(savedDrawerLevel) {
        if (!drawerLoaded && savedDrawerLevel >= 0) {
            drawerLevel = savedDrawerLevel; fullPresentation = savedDrawerLevel == 2; drawerLoaded = true
        }
    }
    fun cycleCoordinates() { scope.launch { try { container.settingsStore.setCoordinateMode((coordinateMode + 1) % 3) } catch (_: java.io.IOException) { snackbar.showSnackbar("显示方式未保存，请重试") } } }
    fun selectReferenceTab(tab: Int) { scope.launch { try { container.settingsStore.setReferenceTab(tab) } catch (_: java.io.IOException) { snackbar.showSnackbar("参考页签未保存，请重试") } } }
    fun selectAccessoryPage(page: Int) { scope.launch { try { container.settingsStore.setAccessoryPage(page) } catch (_: java.io.IOException) { snackbar.showSnackbar("工具页签未保存，请重试") } } }
    fun setReferenceLevel(next: Int, restoreKeyboard: Boolean = true, persist:Boolean = true) {
        if (next == drawerLevel && !frozenBody) return
        drawerTransition?.cancel()
        val previous = drawerLevel
        if (previous == 2 && !frozenBody) {
            frozenBody = true; heldImePx = imePixels
            frozenChapter = currentSession?.chapterId; frozenScroll = editorScroll.value
            frozenSelection = currentSession?.editorState?.selection ?: TextRange.Zero
        }
        if (next == 2 && previous != 2) {
            frozenChapter = currentSession?.chapterId; frozenScroll = editorScroll.value
            frozenSelection = currentSession?.editorState?.selection ?: TextRange.Zero
            heldImePx = maxOf(heldImePx, imePixels); frozenBody = true; fullPresentation = true
            val activity = generateSequence(localView.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<android.app.Activity>().firstOrNull()
            val regularKeyboard = localView.resources.configuration.keyboard == android.content.res.Configuration.KEYBOARD_NOKEYS
            ownedIme = imeVisible && bodyFocused && activity?.isInMultiWindowMode != true && regularKeyboard
            if (ownedIme) keyboard?.hide()
            panels.close(); accessoryOpen = false
        }
        drawerLevel = next
        if(persist)scope.launch { try { container.settingsStore.setReferenceDrawerLevel(next) } catch (_: java.io.IOException) { snackbar.showSnackbar("抽屉状态未保存，请重试") } }
        if (previous == 2 || (next != 2 && frozenBody)) {
            val originalSession = currentSession; val sameChapter = originalSession?.chapterId == frozenChapter
            val scroll = frozenScroll; val selection = frozenSelection; val expectedIme = heldImePx
            val returnIme = ownedIme && restoreKeyboard && sameChapter
            ownedIme = false
            if (returnIme) { bodyFocusRequester.requestFocus(); keyboard?.show() }
            drawerTransition = scope.launch {
                if (motionEnabled) delay(300)
                if (returnIme) kotlinx.coroutines.withTimeoutOrNull(1_000) { androidx.compose.runtime.snapshotFlow { imeInsets.getBottom(density) }.first { it >= expectedIme && it > 0 } }
                if (restoreKeyboard && sameChapter && originalSession === viewModel.session) {
                    originalSession?.editorState?.let { state -> if (state.composition == null) state.edit {
                        this.selection = TextRange(selection.start.coerceIn(0, length), selection.end.coerceIn(0, length))
                    } }
                    editorScroll.scrollTo(scroll.coerceIn(0, editorScroll.maxValue))
                }
                heldImePx = 0; frozenBody = false
                if (next != 2) fullPresentation = false
            }
        }
    }
    LaunchedEffect(referenceEnabled) {
        if (!referenceEnabled && frozenBody) { ownedIme = false; heldImePx = 0; frozenBody = false; fullPresentation = false }
    }
    LaunchedEffect(imeVisible) { if (!imeVisible) accessoryOpen = false }
    LaunchedEffect(blockManuscript, imeVisible) {
        if (blockManuscript && frozenChapter == currentChapterId) editorScroll.scrollTo(frozenScroll.coerceIn(0, editorScroll.maxValue))
    }
    fun editChapterOutline() { setReferenceLevel(0, false,false); accessoryOpen = false; keyboard?.hide(); scopedOutlineOpen = true }
    fun advanceScene(scene: ReferenceScene) {
        val entry = referenceData.outline ?: return
        if (referenceBusy) return
        referenceBusy = true; referenceError = null
        scope.launch { try { container.planning.save(entry.copy(body = ChapterReference.advance(entry.body, scene))) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { referenceError = "分场状态未保存，请重试；若已在其他地方修改，请重新打开细纲。" }
            finally { referenceBusy = false } }
    }
    fun openNoteSource(id:String,start:Int,end:Int) { scope.launch {
        if(viewModel.switchToChapter(id)) {
            val state=viewModel.session?.editorState ?: return@launch
            val from=start.coerceIn(0,state.text.length);val to=end.coerceIn(from,state.text.length)
            if(state.composition==null)state.edit{selection=TextRange(from,to)}
            sourceRequest=Triple(id,state.text.toString(),SearchMatch(from,to))
            inboxOpen=false;planningOpen=false;scopedOutlineOpen=false;panels.close()
        } else snackbar.showSnackbar("来源章节无法打开，请检查回收站")
    } }
    fun locateReference(note: QuickNote) {
        setReferenceLevel(0, false); accessoryOpen = false
        scope.launch {
            if (motionEnabled) delay(300)
            if (viewModel.switchToChapter(note.chapterId)) {
                val state=viewModel.session?.editorState ?: return@launch
                val point=if(note.sourceQuote.isBlank()) note.offset.coerceIn(0,state.text.length) else com.qingling.writing.core.model.QuoteLocator.find(state.text.toString(),note)
                if(point==null) {snackbar.showSnackbar("原文已变化或有多处匹配，请重新关联");return@launch}
                val end=if(note.sourceQuote.isBlank()) point else point+note.sourceQuote.length
                if(state.composition==null)state.edit{selection=TextRange(point,end)}
                sourceRequest=Triple(note.chapterId,state.text.toString(),SearchMatch(point,end))
            } else snackbar.showSnackbar("来源章节无法打开，请检查保存状态或回收站")
        }
    }
    fun insertReferenceName(name: String) {
        val leavingFullReference = effectiveDrawerLevel == 2
        val returnIme = leavingFullReference && ownedIme
        if (leavingFullReference) setReferenceLevel(0, false)
        scope.launch {
            if (leavingFullReference && motionEnabled) delay(320)
            if (!viewModel.insertPlanningText(name)) snackbar.showSnackbar("人名未插入，请先结束输入法组词或检查保存状态")
            else if (returnIme) { bodyFocusRequester.requestFocus(); keyboard?.show() }
        }
    }
    fun openScopedExport(bid:String,scopeName:String,id:String){scope.launch{if(viewModel.requestClose()){panels.close();accessoryOpen=false;chapterInfoOpen=false;onOpenExportScope(bid,scopeName,id)}}}
    var formatBusy by remember{mutableStateOf(false)}
    fun oneClickFormat(){if(formatBusy)return;val source=viewModel.formattingSource()?:return;formatBusy=true;scope.launch{
        try{val rules=container.settingsStore.formatOptions.first();val replacement=withContext(Dispatchers.Default){ChapterFormatter.format(source.body,rules)}
            if(replacement==source.body)snackbar.showSnackbar("当前正文已符合排版规则")
            else when(viewModel.applyFormatting(source,replacement)){
                FormatApplyResult.APPLIED->snackbar.showSnackbar(formattedMessage)
                FormatApplyResult.STALE->snackbar.showSnackbar("正文或章节已变化，本次未排版，请重试")
                FormatApplyResult.SAVE_FAILED->snackbar.showSnackbar("请先处理保存失败，再排版")
            }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){snackbar.showSnackbar("排版未完成，当前正文保留")}finally{formatBusy=false}
    }}
    fun writingAction(action: WritingAction) {
        when (action) {
            WritingAction.REFERENCE -> { selectReferenceTab(0); selectAccessoryPage(1) }
            WritingAction.PEOPLE -> { selectReferenceTab(3); selectAccessoryPage(2) }
            WritingAction.FORESHADOW -> { selectReferenceTab(2); selectAccessoryPage(3) }
            WritingAction.NOTE -> { accessoryOpen = false; captureNote(false) }
            WritingAction.FIND -> { accessoryOpen = false; searchOpen = true }
            WritingAction.FORMAT -> { accessoryOpen = false; oneClickFormat() }
            WritingAction.PROOF -> { accessoryOpen = false; startProof() }
            WritingAction.CHAPTER -> { accessoryOpen = false; chapterInfoOpen = true }
        }
    }
    fun closePanels() = panels.close()
    fun openRight() { setReferenceLevel(0, false); accessoryOpen = false; keyboard?.hide(); panels.open(SidePanelSide.RIGHT) }
    fun requestBack() { scope.launch { if (viewModel.requestClose()) onBack() } }

    val containerTitleDescription = "打开左侧书库"
    BackHandler {
        when {
            accessoryOpen -> accessoryOpen = false
            effectiveDrawerLevel != 0 -> setReferenceLevel(0)
            selectionMode -> exitSelection()
            panels.side != null -> closePanels()
            imeVisible -> keyboard?.hide()
            searchOpen -> { searchOpen = false; searchTarget = null }
            focusMode -> focusMode = false
            else -> requestBack()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val editorHeight = maxHeight
        val wideLayout = maxWidth >= 720.dp
        val showWideOutline = wideLayout && wideOutlineVisible && !focusMode && bookId != null
        fun openLeft() {
            leftStartsInLibrary=false; leftRequest++
            setReferenceLevel(0, false); accessoryOpen = false
            keyboard?.hide()
            if (wideLayout) wideOutlineVisible = !wideOutlineVisible
            else panels.open(SidePanelSide.LEFT)
        }
        fun openLibrary() {
            leftStartsInLibrary=true; leftRequest++
            setReferenceLevel(0,false);accessoryOpen=false;keyboard?.hide()
            if(wideLayout) wideOutlineVisible=true else panels.open(SidePanelSide.LEFT)
        }
        LaunchedEffect(wideLayout) { if (wideLayout && panels.side == SidePanelSide.LEFT) panels.close() }
        // Keep a real tappable strip outside the drawer, even on narrow phones.
        val editorWidth = maxWidth - if (showWideOutline) 320.dp else 0.dp
        val availableWidth = (editorWidth - 48.dp).coerceAtLeast(0.dp)
        val leftWidth = minOf(availableWidth, editorWidth * 0.82f, 420.dp)
        val rightWidth = minOf(availableWidth, editorWidth * 0.82f, 420.dp)
        val leftPx = with(density) { leftWidth.toPx() }
        val rightPx = with(density) { rightWidth.toPx() }
        val es = currentSession?.editorState
        val allowBodyDrag = !imeVisible && es?.composition == null &&
            (es == null || es.selection.collapsed) && !selectionMode && swipePanels
        Box(Modifier.fillMaxSize().windowInsetsPadding(imeInsets.union(WindowInsets(bottom = heldImePx)))) {
        Row(Modifier.fillMaxSize()) {
        if (showWideOutline) {
            Surface(modifier = Modifier.width(320.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainer) {
                OutlineBrowser(onExport=::openScopedExport,onOpenSource=::openNoteSource,bookId = bookId!!, currentChapterId = currentChapterId,
                    onClose = { wideOutlineVisible = false }, beforeCopy = { viewModel.requestClose() }, onCompleted={completed(it)},startInLibrary=leftStartsInLibrary,requestKey=leftRequest,
                    onOpenChapter = { next -> scope.launch { viewModel.switchToChapter(next) } },
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().then(if((swipePanels && !selectionMode)||panels.side!=null) Modifier.panelGestures(panels, leftPx, rightPx, allowBodyDrag && !wideLayout) else Modifier)) {
            Scaffold(
                modifier = Modifier.fillMaxSize().graphicsLayer{translationX=if(motionEnabled && panels.side!=null) (if(panels.side==SidePanelSide.LEFT)1 else -1)*8.dp.toPx()*panels.progress else 0f},
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (!focusMode || ready || saveFailed || searchOpen) {
                    // Reference drags belong to its handle/peek/panel; body scrolling owns chrome.
                    Column(Modifier.onSizeChanged { topChromeHeight = it.height }.statusBarsPadding()) {
                    androidx.compose.animation.AnimatedContent(targetState=thinTop, transitionSpec={
                        val dy=if(targetState)-1 else 1
                        val transform = if(motionEnabled) (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(durationMillis=140,delayMillis=60))+androidx.compose.animation.slideInVertically(com.qingling.writing.core.design.AppMotion.panelSpring()){dy*it/3}) togetherWith
                            (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))+androidx.compose.animation.slideOutVertically(com.qingling.writing.core.design.AppMotion.panelSpring()){-dy*it/3})
                        else androidx.compose.animation.fadeIn(snap()) togetherWith androidx.compose.animation.fadeOut(snap())
                        transform.using(androidx.compose.animation.SizeTransform(clip=true, sizeAnimationSpec={_,_->if(motionEnabled)com.qingling.writing.core.design.AppMotion.panelSpring()else snap()}))
                    },label="editorTopMode") { slim ->
                    if (slim && uiState is EditorUiState.Ready) EditorThinStatusLine(viewModel, uiState as EditorUiState.Ready, ::openLibrary)
                    else {
                    // AnimatedContent stacks direct children. Keep the toolbar and status in one column.
                    Column(Modifier.fillMaxWidth()) {
                    TopAppBar(
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        expandedHeight = 48.dp,
                        title = {
                            when (val s = uiState) {
                                is EditorUiState.Ready -> Column(Modifier
                                    .heightIn(min = TouchTarget.min).fillMaxWidth()
                                    .semantics { contentDescription = containerTitleDescription }
                                    .clickable(role = Role.Button, onClick = ::openLibrary)) {
                                    Text(s.bookTitle, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(searchHighlight(s.chapterTitle, if (searchOpen) searchQuery else ""), style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                is EditorUiState.EmptyBook -> Text(s.title,maxLines=1,overflow=TextOverflow.Ellipsis)
                                EditorUiState.Loading -> Text(stringResource(R.string.editor_loading))
                                else -> Text(stringResource(R.string.app_name))
                            }
                        },
                        navigationIcon = { BackButton(onClick = ::requestBack) },
                        actions = {
                            if(ready) {
                            ChromeAction(SelectionModeIcon, if(selectionMode) "退出选区模式" else "选区模式", { if(selectionMode) exitSelection() else {keyboard?.hide();chrome.selectionEntered();selectionMode=true} }, active = selectionMode)
                            ChromeAction(Icons.Filled.Menu, "工具", { accessoryOpen = !accessoryOpen })
                            ChromeAction(Icons.Filled.MoreVert, "更多", ::openRight)
                            }
                        },
                    )
                    if(ready)EditorSaveStrip(viewModel,true,selectionMode)
                    }
                    }
                    }
                    }
                    }
                },
                bottomBar = {
                    Column {
                    if(proofBar && !focusMode && !selectionMode)ProofReviewBar(activeProof,(visibleProof.indexOf(activeProof)+1).coerceAtLeast(0),visibleProof.size,
                        currentSession?.editorState?.text?.toString()!=proofSnapshot,proofBusy,
                        activeProof?.let{Proofreader.removeRepeated(proofSnapshot.orEmpty(),it)!=null}==true,
                        onNext={advanceProof()},onIgnore={activeProof?.let{proofIgnored=proofIgnored+it.stableKey};advanceProof()},
                        onFix={val issue=activeProof;val source=proofSnapshot;val session=currentSession
                            if(issue!=null&&source!=null&&session!=null&&session.editorState.text.toString()==source){val replacement=Proofreader.removeRepeated(source,issue)
                                if(replacement!=null)scope.launch{when(viewModel.applyFormatting(FormattingSource(session.chapterId,source),replacement)){
                                    FormatApplyResult.APPLIED->{advanceProof(false);snackbar.showSnackbar("已删去相邻重复，可撤销；保存状态见底栏")}
                                    else->snackbar.showSnackbar("正文已变化或尚未保存，本次未处理")
                                }}
                            }},onList={proofSource=currentSession?.editorState?.text?.toString();proofBar=false},onClose={proofBar=false})
                    val candidate=if(selectedText.length<4) null else noteEntries.firstOrNull { entry -> val n=QuickNote.decode(entry.body)
                        entry.bookId==bookId && n.foreshadow && !n.resolved && selectedText.length>=4 && entry.title.length>=4 &&
                        (selectedText.contains(entry.title) || n.text.contains(selectedText)) }
                    if(candidate!=null) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("这可能回收了「"+candidate.title+"」？",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,maxLines=2)
                        QuietTextButton(onClick={scope.launch {try {
                            val n=QuickNote.decode(candidate.body);val saved=container.planning.updateNote(candidate,n.resolve(currentChapterId,(uiState as? EditorUiState.Ready)?.let { it.bookTitle+" · "+it.chapterTitle }.orEmpty()))
                            if(snackbar.showSnackbar("已标记回收","撤销")==androidx.compose.material3.SnackbarResult.ActionPerformed) container.planning.updateNote(saved,n)
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){snackbar.showSnackbar("伏笔状态未保存，请重试")}}}){Text("回收")}
                    }
                    if(selectionMode && !focusMode) SelectionActions(selectedText,onExit=::exitSelection,onNote={captureNote(it)},onCopy={
                        val clipboard=localView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("轻灵写作",selectedText));exitSelection()
                    },onSearch={searchQuery=selectedText.take(200);exitSelection();bookSearchOpen=true},
                        coordinates={currentSession?.let { EditorCoordinates(it, coordinateMode, todayWords, ::cycleCoordinates) }})
                    if(!focusMode && !selectionMode && currentSession!=null) {
                        Column(Modifier.navigationBarsPadding()) {
                            CompactEditorBar(currentSession!!,if (frozenBody) heldImePx > 0 else imeVisible,punctuation,
                                chapterOrder.indexOf(currentChapterId)>0 && !switching,
                                chapterOrder.indexOf(currentChapterId).let{it>=0&&it<chapterOrder.lastIndex} && !switching,
                                switching || titleFocused || blockManuscript,{viewModel.undo()},{viewModel.redo()},::openLeft,{offset ->
                                    chapterOrder.getOrNull(chapterOrder.indexOf(currentChapterId)+offset)?.let{next->scope.launch{if(viewModel.switchToChapter(next)){searchOpen=false;closePanels()}}}
                                }, coordinateMode = coordinateMode, today = todayWords, onCycleCoordinates = ::cycleCoordinates,
                                onAccessories = { accessoryOpen = !accessoryOpen }, accessoryDrag = Modifier.toolDrawerDrag(toolDrawer) { accessoryHeightPx },
                                modifier = if (blockManuscript) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics { } else Modifier)
                            if(saveFailed) SaveStatusText(viewModel.saveState.collectAsStateWithLifecycle().value,true,{viewModel.retrySave()},{closePanels();viewModel.reloadLatest()},Modifier.padding(horizontal=16.dp,vertical=6.dp))
                        }

                    }
                    }
                },
            ) { padding ->
                WritingCanvas(appearance, Modifier.padding(padding).fillMaxSize()) {
                    when (uiState) {
                        is EditorUiState.Ready -> {
                            val es = currentSession?.editorState
                            val firstParagraphMarkerColor=MaterialTheme.colorScheme.primary.copy(alpha=.45f)
                            if (es != null) {
                                val empty by remember(es) { derivedStateOf { es.text.isEmpty() || es.text.toString()==ParagraphIndentation.PREFIX } }
                                Column(Modifier.fillMaxSize()) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible=titleBlockVisible,
                                    enter=androidx.compose.animation.expandVertically(animationSpec=if(motionEnabled) com.qingling.writing.core.design.AppMotion.panelSpring() else snap()) +
                                        androidx.compose.animation.fadeIn(animationSpec=if(motionEnabled) tween(durationMillis=140,delayMillis=60) else snap()),
                                    exit=androidx.compose.animation.shrinkVertically(animationSpec=if(motionEnabled) com.qingling.writing.core.design.AppMotion.panelSpring() else snap()) +
                                        androidx.compose.animation.fadeOut(animationSpec=if(motionEnabled) tween(durationMillis=120) else snap())
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(horizontal=appearance.gutter.dp,vertical=8.dp),verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    BasicTextField(state=currentSession!!.titleState,
                                            readOnly=switching || selectionMode || blockManuscript,
                                            lineLimits=androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                                            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Next),
                                            onKeyboardAction={currentSession?.checkpoint();bodyFocusRequester.requestFocus()},
                                            inputTransformation=InputTransformation {
                                                val value=asCharSequence()
                                                if(blockManuscript || Character.codePointCount(value,0,value.length)>com.qingling.writing.domain.validation.TitleValidator.SECTION_MAX_CODE_POINTS || value.any { it=='\n' || it=='\r' }) revertAllChanges()
                                            }.then(currentSession!!.titleInputProtection),
                                            textStyle=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,lineHeight=24.sp,color=appearance.textColor(),textAlign=androidx.compose.ui.text.style.TextAlign.Center),
                                            cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                            decorator={inner->Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                                                if(currentSession!!.titleState.text.isEmpty()) Text("未命名章节",modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,lineHeight=24.sp),color=appearance.textColor().copy(alpha=.4f))
                                                inner()
                                            }},
                                            modifier=Modifier.fillMaxWidth().onFocusChanged{titleFocused=it.isFocused}
                                                .semantics{contentDescription="章节标题，自动同步目录"}.padding(horizontal=14.dp,vertical=8.dp))
                                }
                                if(titleFocused)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(enabled=!switching,onClick={currentSession?.checkpoint();bodyFocusRequester.requestFocus()}){Text("写正文 ›",fontSize=11.sp)}}
                                androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal=appearance.gutter.dp),thickness=1.dp,color=appearance.textColor().copy(alpha=.18f))
                                    }
                                }
                                Box(Modifier.weight(1f).drawBehind {
                                    val first=es.text.indexOfFirst { !it.isWhitespace() }.let { if(it<0) es.selection.min else it }
                                    val layout=textLayout
                                    val line=layout?.getLineForOffset(first.coerceAtMost(layout.layoutInput.text.length)) ?: 0
                                    val middle=if(layout!=null) (layout.getLineTop(line)+layout.getLineBottom(line))/2f
                                        else (appearance.fontSize*appearance.lineHeightPercent/100f).sp.toPx()/2f
                                    val y=Spacing.s16.toPx()+middle-editorScroll.value
                                    if(y>=4.dp.toPx() && y<=size.height-4.dp.toPx()) {
                                        val x=(appearance.gutter.dp.toPx()-8.dp.toPx()).coerceAtLeast(2.dp.toPx())
                                        drawLine(firstParagraphMarkerColor,androidx.compose.ui.geometry.Offset(x,y-4.dp.toPx()),androidx.compose.ui.geometry.Offset(x,y+4.dp.toPx()),2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
                                    }
                                }) {
                                androidx.compose.runtime.CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primary.copy(alpha=.25f))) {
                                BasicTextField(state = es,
                                    readOnly = switching || selectionMode,
                                    inputTransformation = if (blockManuscript) InputTransformation { revertAllChanges() } else ParagraphIndentation.onInput.then(currentSession!!.bodyInputProtection),
                                    scrollState = editorScroll,
                                    onTextLayout = { layout -> textLayout = layout() },
                                    outputTransformation = if (!searchOpen && !typewriter && sourceRequest?.first!=currentChapterId) null else OutputTransformation {
                                        val highlights = searchHighlights
                                        if (highlights != null && toString() == highlights.first) {
                                            highlights.second.forEach { range ->
                                                val selected = range == searchTarget
                                                addStyle(SpanStyle(background = if (selected) Color(0x66A7D6B7) else Color(0x33A7D6B7),
                                                    color = editorTextColor), range.start, range.end)
                                            }
                                        }
                                        val quote=sourceRequest
                                        if(quote!=null && quote.first==currentChapterId && quote.second==toString() && quote.third.end>quote.third.start) {
                                            addStyle(SpanStyle(background=Color(0x44A7D6B7)),quote.third.start,quote.third.end)
                                        }
                                        if(typewriter) append("\n".repeat(24)) // Visual breathing room only; never enters saved text.
                                    },
                                    modifier = Modifier.fillMaxSize().semantics{contentDescription="正文，首段有起始标记，回车自动缩进两格"}.focusRequester(bodyFocusRequester).onPreviewKeyEvent { blockManuscript }.nestedScroll(topScrollConnection)
                                        .pointerInput(topScrollConnection) { awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed=false,pass=androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            topScrollConnection.beginGesture()
                                            do { val event=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial) } while(event.changes.any {it.pressed})
                                            topScrollConnection.endGesture()
                                        } }
                                        .then(Modifier.onFocusChanged { bodyFocused=it.isFocused })
                                        .then(Modifier.onSizeChanged { bodyHeight=it.height })
                                        .padding(horizontal = appearance.gutter.dp, vertical = Spacing.s16)
                                        .selectParagraphOnTap(selectionMode,es,editorScroll,{textLayout})
                                        .writingCursor(es, editorScroll, { textLayout }, MaterialTheme.colorScheme.primary,
                                            appearance.fontSize, LocalMotionEnabled.current, currentLine),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = appearance.textColor(),
                                        fontSize = appearance.fontSize.sp, lineHeight = (appearance.fontSize * appearance.lineHeightPercent / 100f).sp,
                                        fontFamily = editorFontFamily),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(if (LocalMotionEnabled.current) Color.Transparent else MaterialTheme.colorScheme.primary))
                                }
                                if (empty) {
                                    Text("　　首段已缩进两格，回车自动延续", style = MaterialTheme.typography.bodyLarge,
                                        color = appearance.textColor().copy(alpha = 0.35f),
                                        modifier = Modifier.padding(horizontal = appearance.gutter.dp, vertical = Spacing.s16))
                                }
                                }
                                }
                            }
                        }
                        EditorUiState.Loading -> EditorStateCard("正在打开稿件…","可随时返回，超过 10 秒将提供重试",onBack=::requestBack)
                        is EditorUiState.EmptyBook -> {
                            val emptyBook=uiState as EditorUiState.EmptyBook
                            if(emptyBook.showOutline) OutlineBrowser(onExport=::openScopedExport,onOpenSource=::openNoteSource,bookId=emptyBook.bookId,currentChapterId=null,
                                onOpenChapter={next->scope.launch{viewModel.switchToChapter(next)}},onClose=::requestBack,beforeCopy={true},modifier=Modifier.fillMaxSize())
                            else EditorStateCard("还没有章节","创建后会显示在目录中，点按章节即可打开",onBack=::requestBack,action="新建第一章",onAction={viewModel.seedFirstChapter()})
                        }
                        is EditorUiState.Failure -> EditorStateCard("暂时无法打开",(uiState as EditorUiState.Failure).message,onBack=::requestBack,action="重试",onAction={viewModel.reloadLatest()})
                        EditorUiState.Missing -> EditorStateCard("章节已被移入回收站","可回到书架查看回收站或打开其他章节",onBack=::requestBack)
                    }



                }
            }
            if (focusMode && panels.side == null) {
                QuietTextButton(onClick = { focusMode = false },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Text("退出专注") }
            }
                    if (ready && panels.side == SidePanelSide.RIGHT) {
                        Box(Modifier.fillMaxSize()
                            .drawBehind { drawRect(Color.Black.copy(alpha = scrimBase * panels.progress)) }
                            .pointerInput(panels) { detectTapGestures { closePanels() } })
                    }
                    if (ready && panels.side == SidePanelSide.RIGHT) {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, shadowElevation = 18.dp,
                            shape = RectangleShape,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(rightWidth)
                                .graphicsLayer { translationX = if(motionEnabled)rightPx * (1f - panels.progress)else 0f;alpha=if(motionEnabled)1f else panels.progress }) {
                            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
                                Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget.min)
                                    .padding(start = Spacing.s24, end = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { closePanels() }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                                    }
                                }
                                (uiState as? EditorUiState.Ready)?.let { ready -> Text("${ready.bookTitle} · ${ready.chapterTitle}",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                ToolsContent(bookId = bookId, chapterId = currentChapterId,
                                    onChapterStats={closePanels();chapterStatsOpen=true},
                                    onQuickNote={closePanels();captureNote(false)},onForeshadow={closePanels();captureNote(true)},
                                    onInbox={scope.launch{if(viewModel.requestClose()){closePanels();inboxOpen=true}else snackbar.showSnackbar("请先处理正文保存失败")}},
                                    onComplete={scope.launch { if(viewModel.requestClose()) {
                                        try { container.writingProgress.complete(currentChapterId,true); val chapter=container.outlineRepository.observeOutline(bookId!!).first().flatMap{it.chapters}.first{it.id==currentChapterId};closePanels();completed(chapter,true) }
                                        catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){snackbar.showSnackbar("完成状态未保存，请重试")}
                                    } }},
                                    onFormatRules={formattingSource=viewModel.formattingSource()},
                                    onProofread = { scope.launch { if(viewModel.requestClose() && panels.dismiss())startProof() } },
                                    onWritingStats = { scope.launch { if(viewModel.requestClose() && panels.dismiss()) writingStatsOpen=true } },
                                    onSplit = { scope.launch {
                                        try { val plan = viewModel.previewSplit();if (panels.dismiss()) splitPreview = plan }
                                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (e: Exception) { panels.dismiss();snackbar.showSnackbar(e.message ?: "无法拆分，请检查保存状态和光标位置") }
                                    } },
                                    onMerge = { scope.launch { if (viewModel.requestClose() && panels.dismiss()) mergeOpen = true } },
                                    onPlanning = { scope.launch {
                                        if (viewModel.requestClose() && panels.dismiss()) planningOpen = true
                                    } },
                                    focusMode = focusMode, onFocusMode = { value -> focusMode = value; closePanels() },
                                    onBatchReplace = { scope.launch {
                                        if (viewModel.requestClose() && panels.dismiss()) batchMode = "replace"
                                    } },
                                    onBatchFormat = { scope.launch {
                                        if (viewModel.requestClose() && panels.dismiss()) batchMode = "format"
                                    } },
                                    onFind = { scope.launch {
                                        if (panels.dismiss()) {
                                            if (searchQuery.isEmpty()) searchQuery = container.settingsStore.recentSearch.first()
                                            searchOpen = true
                                        }
                                    } },
                                    onSearchBook = { scope.launch {
                                        if (viewModel.requestClose() && panels.dismiss()) bookSearchOpen = true
                                    } },
                                    onFormat = { scope.launch {
                                        if (panels.dismiss()) oneClickFormat()
                                    } },
                                    onOpenAppearance = { scope.launch { if (viewModel.requestClose()) {
                                        if (panels.dismiss()) onOpenAppearance()
                                    } } },
                                    onOpenExport = { scope.launch { if (viewModel.requestClose()) {
                                        if (panels.dismiss()) bookId?.let(onOpenExport)
                                    } } },
                                    onOpenBackup = { scope.launch { if (viewModel.requestClose()) {
                                        if (panels.dismiss()) onOpenBackup()
                                    } } },
                                    onOpenHistory = { next -> scope.launch { if (viewModel.requestClose()) {
                                        if (panels.dismiss()) historyOpen=true
                                    } } })
                            }
                        }
                    }
            if (ready && panels.side == SidePanelSide.LEFT) {
                Box(Modifier.fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = scrimBase * panels.progress)) }
                    .pointerInput(panels) { detectTapGestures { closePanels() } })
            }
            if (ready && panels.side == SidePanelSide.LEFT) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, shadowElevation = 18.dp,
                    shape = RectangleShape,
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(leftWidth)
                        .graphicsLayer { translationX = if(motionEnabled)-leftPx * (1f - panels.progress)else 0f;alpha=if(motionEnabled)1f else panels.progress }) {
                    val bid = bookId
                    if (bid == null) CenterMessage(stringResource(R.string.editor_loading))
                    else OutlineBrowser(onExport=::openScopedExport,onOpenSource=::openNoteSource,bookId = bid, currentChapterId = currentChapterId, onClose = ::closePanels,
                        beforeCopy = { viewModel.requestClose() }, onCompleted={completed(it)},startInLibrary=leftStartsInLibrary,requestKey=leftRequest,
                        onOpenChapter = { next ->
                            scope.launch {
                                if (viewModel.switchToChapter(next)) closePanels()
                            }
                        }, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)))
                }
            }
        }
        }
        }
        val topInset = with(density) { topChromeHeight.toDp() }
        val navigationInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val referenceHeight = (maxHeight - topInset - 48.dp - navigationInset).coerceAtLeast(110.dp)
        val drawerHeight by animateDpAsState(when (effectiveDrawerLevel) { 1 -> 110.dp; 2 -> referenceHeight; else -> 0.dp },
            if (motionEnabled) com.qingling.writing.core.design.AppMotion.panelSpring() else snap(), label = "referenceHeight")
        if (ready && panels.side == null && !focusMode) {
            Box(Modifier.align(Alignment.TopEnd).width(editorWidth).fillMaxHeight()) {
                if (fullPresentation && drawerHeight > 0.dp && referenceEnabled) {
                    Box(Modifier.fillMaxSize().padding(top = topInset, bottom = navigationInset + 48.dp)
                        .background(Color.Black.copy(alpha = .35f * (drawerHeight / referenceHeight).coerceIn(0f, 1f)))
                        .clickable { setReferenceLevel(0) })
                }
                if (referenceEnabled && drawerHeight > 0.dp) {
                    if (fullPresentation) Surface(Modifier.padding(top = topInset).fillMaxWidth().height(drawerHeight)
                        .semantics { contentDescription = "顶栏细纲抽屉：全展开" }, color = MaterialTheme.colorScheme.surface) {
                        Column {
                            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp)
                                .drawerDrag { if (it < -24) setReferenceLevel(0) }, verticalAlignment = Alignment.CenterVertically) {
                                Text("参考 · 本章细纲", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { setReferenceLevel(0) }) { Text("收起") }
                            }
                            ReferencePanel(referenceData, referenceTab, ::selectReferenceTab, ::editChapterOutline,
                                ::advanceScene, ::locateReference, ::insertReferenceName, referenceBusy, referenceError,
                                Modifier.weight(1f), onEndUp = { setReferenceLevel(0) })
                            Box(Modifier.fillMaxWidth().height(28.dp).drawerDrag { if (it < -20) setReferenceLevel(0) }
                                .clickable { setReferenceLevel(0) }, contentAlignment = Alignment.Center) {
                                Text("上滑收起", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else ReferencePeek(referenceData, ::editChapterOutline, { setReferenceLevel(2) }, { setReferenceLevel(0) },
                        Modifier.padding(top = topInset).height(drawerHeight))
                }
                if (referenceEnabled && effectiveDrawerLevel == 0 && drawerHeight == 0.dp) {
                    ReferenceHandle(bookId, container.settingsStore, referenceData.hasOutline, true, { setReferenceLevel(1) },
                        Modifier.align(Alignment.TopCenter).padding(top = topInset).graphicsLayer { translationY = -3.dp.toPx() }.drawerDrag { drag ->
                            if (drag > 130) setReferenceLevel(2) else if (drag > 24) setReferenceLevel(1)
                        })
                }
                if (fullPresentation && referenceEnabled) currentSession?.let { session ->
                    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                        CompactEditorBar(session, false, punctuation, chapterOrder.indexOf(currentChapterId) > 0 && !switching,
                            chapterOrder.indexOf(currentChapterId).let { it >= 0 && it < chapterOrder.lastIndex } && !switching,
                            true, { }, { }, ::openLeft,
                            { offset -> chapterOrder.getOrNull(chapterOrder.indexOf(currentChapterId) + offset)?.let { next -> scope.launch { viewModel.switchToChapter(next) } } },
                            coordinateMode, todayWords, ::cycleCoordinates, { }, Modifier)
                    }
                }
                if (toolDrawer.visible && !blockManuscript) {
                    val keyboardHeight = if (imeVisible) with(density) { imePixels.toDp() } else navigationInset
                    Box(Modifier.fillMaxSize().padding(top=topInset,bottom=keyboardHeight+(if(imeVisible)30.dp else 48.dp))
                        .background(Color.Black.copy(alpha=.30f*toolDrawer.progress)).clickable { accessoryOpen=false })
                    AccessoryDrawer(accessoryPage, ::selectAccessoryPage, { accessoryOpen = false }, ::writingAction,
                        onFormatRules={accessoryOpen=false;formattingSource=viewModel.formattingSource()},
                        dragModifier=Modifier.toolDrawerDrag(toolDrawer) { accessoryHeightPx },
                        pendingCount=referenceData.notes.count { !QuickNote.decode(it.body).resolved }, fontSize=appearance.fontSize,
                        chapterWords=viewModel.wordCount.collectAsStateWithLifecycle().value, onGlobalFind={accessoryOpen=false;bookSearchOpen=true},
                        reference = { ReferencePanel(referenceData, referenceTab, ::selectReferenceTab, ::editChapterOutline,
                            ::advanceScene, ::locateReference, ::insertReferenceName, referenceBusy, referenceError, Modifier.fillMaxSize()) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = keyboardHeight + if (imeVisible) 30.dp else 48.dp)
                            .heightIn(max = (editorHeight - keyboardHeight - (if (imeVisible) 30.dp else 48.dp) - topInset).coerceAtLeast(0.dp))
                            .onSizeChanged { accessoryHeightPx=it.height.toFloat().coerceAtLeast(1f) }
                            .graphicsLayer { translationY=(1f-toolDrawer.progress)*size.height })
                }
            }
        }
        if (ready && swipePanels && !gestureGuideSeen && !gestureGuideDismissed && !focusMode && panels.side == null && !imeVisible && !frozenBody) {
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "关闭手势引导") {
                gestureGuideDismissed = true
                scope.launch { try { container.settingsStore.dismissEditorGestureGuide() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { snackbar.showSnackbar("引导关闭状态未保存") } }
            }, contentAlignment = Alignment.BottomCenter) {
                Surface(Modifier.padding(horizontal = 24.dp, vertical = 90.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text("从左缘右滑打开目录，从右缘左滑打开工具\n轻触任意处关闭", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        completion?.let { data -> CompletionNotice(data,summaryFull,{summaryFull=true},{completion=null},
            {scope.launch{container.settingsStore.setChapterSummary(false)}},Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) }
    }
    noteQuote?.let { quote -> com.qingling.writing.feature.planning.QuickNoteSheet(onDismiss={noteQuote=null},bookId=noteBook,
        chapterId=noteChapter,sourceTitle=noteSourceTitle,
        offset=noteOffset,initialText="",sourceQuote=quote,foreshadow=noteForeshadow,onSaved={exitSelection();scope.launch{snackbar.showSnackbar(if(noteForeshadow) "伏笔已保存" else "便签已保存")}}) }
    if (scopedOutlineOpen) bookId?.let { bid -> com.qingling.writing.feature.planning.ScopedPlanningSheet(bid, currentChapterId, null, onDismiss = { scopedOutlineOpen = false },onSaved={if(referenceEnabled)setReferenceLevel(1,false,false)}) }
    if(inboxOpen) com.qingling.writing.feature.planning.NoteInboxDialog(onDismiss={inboxOpen=false},bookId=bookId,currentChapterId=currentChapterId,onCapture={captureNote(it)},currentSourceTitle=(uiState as? EditorUiState.Ready)?.let {it.bookTitle+" · "+it.chapterTitle}.orEmpty(),onOpenGlobal={scope.launch{if(viewModel.requestClose())onOpenInspiration()}},
        onInsert={viewModel.insertPlanningText(it)},onOpenSource=::openNoteSource)
    if(searchOpen) currentSession?.let {session -> com.qingling.writing.core.design.WritingSheet("查找与替换",onDismiss={searchOpen=false;searchTarget=null}) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {ChapterSearchPanel(session, searchQuery,
                        onQuery = { searchQuery = it },
                        onTarget = { target ->
                            if (session.editorState.composition == null) {
                                session.editorState.edit { selection = TextRange(target.start, target.end) }
                                searchTarget = target
                                searchNavigation++
                            }
                        },
                        onHighlights = { body, ranges -> searchHighlights = body to ranges },
                        onClose = { searchOpen = false; searchTarget = null },
                        onApply = { source, replacement -> viewModel.applyFormatting(source, replacement) })
        }
    }}
    if(historyOpen) com.qingling.writing.feature.history.HistorySheet(currentChapterId,onDismiss={historyOpen=false},onRestored={viewModel.reloadLatest()})
    if(chapterInfoOpen) ChapterInfoSheet(currentChapterId,(uiState as? EditorUiState.Ready)?.chapterTitle.orEmpty(),
        viewModel.wordCount.collectAsStateWithLifecycle().value,onDismiss={chapterInfoOpen=false},onExport={bookId?.let{openScopedExport(it,"CHAPTER",currentChapterId)}})
    if(chapterStatsOpen) com.qingling.writing.core.design.WritingSheet("本章统计",compact=true,onDismiss={chapterStatsOpen=false}){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            com.qingling.writing.core.design.StatRow(listOf(
                com.qingling.writing.core.design.StatData("本章字数",com.qingling.writing.feature.bookshelf.formatWords(viewModel.wordCount.collectAsStateWithLifecycle().value),"字"),
                com.qingling.writing.core.design.StatData("本次输入",com.qingling.writing.feature.bookshelf.formatWords(currentSession?.sessionMetrics()?.inserted ?: 0),"字"),
                com.qingling.writing.core.design.StatData("活跃时长",((currentSession?.sessionMetrics()?.activeMillis ?: 0)/60000).toString(),"分钟")
            ),Modifier.padding(horizontal=20.dp))
            com.qingling.writing.core.design.ToolRow(Icons.Filled.Edit,"重命名章节","",onClick={chapterStatsOpen=false;chapterRenameOpen=true})
            com.qingling.writing.core.design.ToolRow(com.qingling.writing.core.design.WritingIcons.Target,"全局统计与目标","净增趋势 · 活动日历 · 整书目标",onClick={chapterStatsOpen=false;writingStatsOpen=true})
        }
    }
    if(chapterRenameOpen) com.qingling.writing.core.design.TitleInputDialog(
        title="重命名章节",label="章节名称",initialValue=currentSession?.titleState?.text?.toString()?.ifBlank {"未命名章节"} ?: (uiState as? EditorUiState.Ready)?.chapterTitle.orEmpty(),
        error=chapterRenameError,maxCodePoints=com.qingling.writing.domain.validation.TitleValidator.SECTION_MAX_CODE_POINTS,
        onDismiss={chapterRenameOpen=false;chapterRenameError=null},onConfirm={ title -> scope.launch {
            try {
                if(!viewModel.requestClose()) { snackbar.showSnackbar("请先处理保存失败，再修改章节名");return@launch }
                container.outlineRepository.renameChapter(currentChapterId,title);chapterRenameOpen=false;chapterRenameError=null
            }
            catch(e:com.qingling.writing.domain.TitleValidationException) { chapterRenameError=e.error }
            catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:Exception) { snackbar.showSnackbar("章节名称未保存，请重试") }
        } })
    if(writingStatsOpen) com.qingling.writing.feature.bookshelf.WritingDashboard(onDismiss={writingStatsOpen=false},preferredBookId=bookId)
    if (splitPreview != null || mergeOpen) bookId?.let { bid ->
        StructureDialog(bid,splitPreview,onDismiss = { splitPreview = null;mergeOpen = false },
            onApply = { plan,title -> viewModel.applyStructure(plan,title) })
    }
    proofSource?.let { source -> ProofreadDialog(source,onDismiss={proofSource=null},initialIgnored=proofIgnored,onIgnored={proofIgnored=it},onLocatedSession={result,ignored,issue->proofSnapshot=source;proofIssues=result.issues;proofIgnored=ignored;proofKey=issue.stableKey;proofBar=true},onLocate={issue->
        val state=currentSession?.editorState
        if(state==null || state.composition!=null || state.text.toString()!=source) false else {
            state.edit {selection=androidx.compose.ui.text.TextRange(issue.start,issue.end)}
            searchTarget=SearchMatch(issue.start,issue.end);searchNavigation++;true
        }
    }) }
    if (planningOpen) bookId?.let { bid ->
        com.qingling.writing.feature.planning.PlanningDialog(bid,currentChapterId,
            onDismiss = { planningOpen = false },onOpenSource=::openNoteSource,onInsert = { viewModel.insertPlanningText(it) },
            onOpenChapter = { next -> scope.launch { viewModel.switchToChapter(next) } })
    }
    batchMode?.let { mode -> bookId?.let { bid ->
        BatchEditDialog(bid, mode == "format", onDismiss = { batchMode = null }, onApplied = {
            batchMode = null
            viewModel.reloadLatest()
            scope.launch { snackbar.showSnackbar("批量修改已保存，原文可在各章历史中查看") }
        })
    } }
    formattingSource?.let { source ->
        FormattingDialog(source, onDismiss = { formattingSource = null },
            onApply = { viewModel.applyFormatting(source, it) }, onApplied = {
                formattingSource = null
                scope.launch { snackbar.showSnackbar(formattedMessage) }
            })
    }
    // Search navigation scrolls to the selected match without focusing the body or raising its keyboard.
    LaunchedEffect(searchNavigation) {
        val target = searchTarget ?: return@LaunchedEffect
        val layout = androidx.compose.runtime.snapshotFlow { textLayout }.filterNotNull().first()
        if (target.start <= layout.layoutInput.text.length) {
            val top = layout.getCursorRect(target.start).top.toInt()
            editorScroll.scrollTo((top - editorScroll.viewportSize / 3).coerceAtLeast(0))
        }
    }
    if (bookSearchOpen) bookId?.let { bid ->
        BookSearchDialog(bid, container.searchRepository, searchQuery,
            onDismiss = { bookSearchOpen = false }, onOpen = { next, query ->
                if (viewModel.switchToChapter(next) && viewModel.uiState.value is EditorUiState.Ready) {
                    searchQuery = query
                    searchOpen = true
                    bookSearchOpen = false
                    true
                } else false
            })
    }
}

/** Keep per-keystroke saving and word count changes out of the editor composition. */
@Composable
private fun EditorSaveStrip(viewModel: EditorViewModel, ready: Boolean, selectionMode: Boolean) {
    val count by viewModel.wordCount.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        EditorSaveIndicator(viewModel)
        Spacer(Modifier.weight(1f))
        Text("本章 " + count + " 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditorFooter(viewModel: EditorViewModel, editorReady: Boolean, previous: Boolean, next: Boolean, onOutline: () -> Unit, onStep: (Int) -> Unit, onReload: () -> Unit) {
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val wordCount by viewModel.wordCount.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuietTextButton(enabled = previous, onClick = { onStep(-1) }) { Text("‹ 上一章") }
                QuietTextButton(onClick = onOutline) { Text("目录",color=MaterialTheme.colorScheme.onSurface) }
                QuietTextButton(enabled = next, onClick = { onStep(1) }) { Text("下一章 ›") }
            }
            if(saveState is LocalSaveState.Failed) Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s24, vertical = Spacing.s8),
                verticalAlignment = Alignment.CenterVertically) {
                SaveStatusText(saveState, editorReady,
                    onRetry = { viewModel.retrySave() }, onReloadLatest = onReload,
                    modifier = Modifier.weight(1f))
                if (viewModel.session != null) {
                    Text(stringResource(R.string.editor_word_count_format, wordCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SaveStatusText(state: LocalSaveState, editorReady: Boolean,
    onRetry: () -> Unit, onReloadLatest: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.s12)) {
        when (state) {
            LocalSaveState.Idle -> Text(if (editorReady) stringResource(R.string.editor_save_idle) else stringResource(R.string.editor_loading),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LocalSaveState.Saving -> Text(stringResource(R.string.editor_save_saving),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LocalSaveState.Saved -> Text(stringResource(R.string.editor_save_saved_format,
                com.qingling.writing.core.design.formatTimestamp(state.savedAt)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LocalSaveState.Failed -> {
                Text(stringResource(R.string.editor_save_failed_format, when (state.reason) {
                    SaveFailure.DISK_FULL -> stringResource(R.string.save_failure_disk_full)
                    SaveFailure.STORAGE_UNAVAILABLE -> stringResource(R.string.save_failure_storage_unavailable)
                    SaveFailure.DELETED -> stringResource(R.string.save_failure_deleted)
                    SaveFailure.STALE_SEQUENCE -> stringResource(R.string.save_failure_stale)
                    SaveFailure.UNKNOWN -> stringResource(R.string.save_failure_unknown)
                }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                if (state.reason == SaveFailure.STALE_SEQUENCE || state.reason == SaveFailure.DELETED) {
                    QuietTextButton(onClick = onReloadLatest, modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min)) {
                        Text(stringResource(R.string.editor_save_reload_latest)) }
                } else {
                    QuietTextButton(onClick = onRetry, modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min)) {
                        Text(stringResource(R.string.editor_save_retry)) }
                }
            }
        }
    }
}
