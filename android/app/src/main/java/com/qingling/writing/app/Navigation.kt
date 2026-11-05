package com.qingling.writing.app

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.qingling.writing.core.design.SidePanelState
import com.qingling.writing.core.design.SidePanelSide
import com.qingling.writing.feature.settings.SettingsDrawer
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import com.qingling.writing.core.design.LocalMotionEnabled
import com.qingling.writing.core.design.AppMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.qingling.writing.R
import com.qingling.writing.core.design.CenterMessage
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qingling.writing.feature.bookshelf.HomeScreen
import com.qingling.writing.feature.bookshelf.BookshelfScreen
import com.qingling.writing.feature.bookshelf.RecycleBinScreen
import com.qingling.writing.feature.editor.EditorScreen
import com.qingling.writing.feature.export.ExportScreen
import com.qingling.writing.feature.history.HistoryScreen
import com.qingling.writing.feature.outline.OutlineScreen
import com.qingling.writing.feature.recovery.BackupScreen
import com.qingling.writing.feature.settings.WritingAppearanceScreen

/**
 * 导航目的地(UX v0.2):冷启动续写 + 点书续写 + 同页切章。
 * startDestination = bookshelf;冷启动在 MainActivity 检查 lastActive 后跳转 editor。
 * 书卡 tap → editor(不经过 outline);outline 仅从编辑器左面板或更多菜单进入。
 * tools 并入编辑器右面板;recycle/history 为独立目的地。
 */
object Routes {
    const val BOOKSHELF = "bookshelf"
    const val INSPIRATION = "inspiration"
    const val WORKS = "works"
    const val EDITOR = "editor/{chapterId}?anchor={anchor}&end={end}"
    const val OUTLINE = "outline/{bookId}"
    const val EXPORT = "export/{bookId}?scope={scope}&scopeId={scopeId}"
    const val BACKUP = "backup"
    const val APPEARANCE = "appearance"
    const val RECYCLE = "recycle"
    const val HISTORY = "history/{chapterId}"

    fun editor(chapterId: String,anchor:Int = -1,end:Int = -1): String = "editor/$chapterId" + if(anchor>=0) "?anchor=$anchor&end=$end" else ""
    fun outline(bookId: String): String = "outline/$bookId"
    fun export(bookId: String,scope:String="BOOK",scopeId:String=""): String = "export/$bookId?scope=$scope&scopeId=${android.net.Uri.encode(scopeId)}"
    fun history(chapterId: String): String = "history/$chapterId"
}

private val chapterIdArg = listOf(
    navArgument("end") {type=NavType.IntType;defaultValue=-1},
    navArgument("anchor") {type=NavType.IntType;defaultValue=-1},
    navArgument("chapterId") {
        type = NavType.StringType
    },
)

private val bookIdArg = listOf(
    navArgument("bookId") {
        type = NavType.StringType
    },
)

@Composable
fun QinglingWritingApp(modifier: Modifier = Modifier, resumeRequest: Int = 0) {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    var startupResolved by rememberSaveable { mutableStateOf(false) }
    var startupChapter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!startupResolved) {
            startupChapter = container.writingRepository.resolveLastActiveChapter()
            startupResolved = true
        }
    }
    if (!startupResolved) {
        CenterMessage(stringResource(R.string.editor_loading))
        return
    }

    LaunchedEffect(resumeRequest) {
        if (resumeRequest > 0 && navController.currentDestination?.route != Routes.EDITOR) {
            val chapter = container.writingRepository.resolveLastActiveChapter()
            if (chapter != null && navController.currentDestination?.route != Routes.EDITOR) {
                navController.navigate(Routes.editor(chapter)) { launchSingleTop = true }
            }
        }
    }

    val motionEnabled = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val currentMotion by rememberUpdatedState(motionEnabled)
    val settings = remember { SidePanelState(scope) { currentMotion } }
    LaunchedEffect(motionEnabled) { if (!motionEnabled) settings.finishMotion() }
    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startupChapter?.let(Routes::editor) ?: Routes.BOOKSHELF,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(tween(if(motionEnabled)220 else 120)) },
            exitTransition = { fadeOut(tween(if(motionEnabled)160 else 120)) },
            popEnterTransition = { fadeIn(tween(if(motionEnabled)220 else 120)) },
            popExitTransition = { fadeOut(tween(if(motionEnabled)160 else 120)) },
        ) {
            composable(Routes.BOOKSHELF) {
                HomeScreen(onOpenWorks = { navController.navigate(Routes.WORKS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenSource={id,offset,end->navController.navigate(Routes.editor(id,offset,end))},
                    onOpenBook = { navController.navigate(Routes.editor("book:$it")) },
                    onOpenSettings = { settings.open(SidePanelSide.RIGHT) })
            }
            composable(Routes.INSPIRATION){HomeScreen(onOpenWorks={navController.navigate(Routes.WORKS)},onOpenBook={navController.navigate(Routes.editor("book:$it"))},onOpenSettings={settings.open(SidePanelSide.RIGHT)},onOpenSource={id,offset,end->navController.navigate(Routes.editor(id,offset,end))},onOpenBackup={navController.navigate(Routes.BACKUP)},initialPage=1)}
            composable(Routes.WORKS) {
                BookshelfScreen(
                    onBack = { navController.navigateUp() },
                    onOpenBook = { bookId ->
                        navController.navigate(Routes.editor("book:$bookId"))
                    },
                    onContinueWriting = { chapterId -> navController.navigate(Routes.editor(chapterId)) },
                    onOpenRecycleBin = { navController.navigate(Routes.RECYCLE) },
                    onOpenSettings = { settings.open(SidePanelSide.RIGHT) },
                )
            }
    
            composable(Routes.EDITOR, arguments = chapterIdArg) { entry ->
                val chapterId = entry.arguments?.getString("chapterId").orEmpty()
                EditorScreen(
                    onOpenInspiration={navController.navigate(Routes.INSPIRATION)},
                    chapterId = chapterId, initialOffset=entry.arguments?.getInt("anchor") ?: -1,initialEndOffset=entry.arguments?.getInt("end") ?: -1,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.BOOKSHELF) {
                                popUpTo(navController.graph.id) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenExport = { bookId -> navController.navigate(Routes.export(bookId)) },
                    onOpenExportScope={bid,scope,id->navController.navigate(Routes.export(bid,scope,id))},
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenHistory = { targetChapterId -> navController.navigate(Routes.history(targetChapterId)) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                )
            }
    
            composable(Routes.OUTLINE, arguments = bookIdArg) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                OutlineScreen(
                    onExport={scope,id->navController.navigate(Routes.export(bookId,scope,id))},
                    bookId = bookId,
                    onBack = { navController.navigateUp() },
                    onOpenChapter = { chapterId -> navController.navigate(Routes.editor(chapterId)) },
                    onOpenSource={id,start,end->navController.navigate(Routes.editor(id,start,end))},
                )
            }
    
            composable(Routes.EXPORT, arguments = bookIdArg+listOf(navArgument("scope"){defaultValue="BOOK"},navArgument("scopeId"){defaultValue=""})) { entry ->
                val bookId = entry.arguments?.getString("bookId").orEmpty()
                ExportScreen(
                    preset=com.qingling.writing.core.model.ExportPreset(com.qingling.writing.core.model.ExportScope.entries.firstOrNull{it.name==entry.arguments?.getString("scope")}?:com.qingling.writing.core.model.ExportScope.BOOK,entry.arguments?.getString("scopeId").orEmpty()),
                    bookId = bookId,
                    onBack = { navController.navigateUp() },
                )
            }
    
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { navController.navigateUp() })
            }
    
            composable(Routes.APPEARANCE) {
                WritingAppearanceScreen(onBack = { navController.navigateUp() })
            }
    
            composable(Routes.RECYCLE) {
                RecycleBinScreen(onBack = { navController.navigateUp() })
            }
    
            composable(Routes.HISTORY, arguments = chapterIdArg) { entry ->
                val chapterId = entry.arguments?.getString("chapterId").orEmpty()
                HistoryScreen(
                    chapterId = chapterId,
                    onBack = { navController.navigateUp() },
                )
            }
        }
        SettingsDrawer(settings,
            onOpenAppearance = { scope.launch { if (settings.dismiss()) navController.navigate(Routes.APPEARANCE) } },
            onOpenBackup = { scope.launch { if (settings.dismiss()) navController.navigate(Routes.BACKUP) } })
    }
}
