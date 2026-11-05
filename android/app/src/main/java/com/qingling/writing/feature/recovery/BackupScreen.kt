package com.qingling.writing.feature.recovery
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppTextField

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.formatTimestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val model: BackupViewModel = viewModel(factory = viewModelFactory {
        initializer { BackupViewModel(container.backupRepository, container.backupFiles) }
    })
    val ui by model.ui.collectAsStateWithLifecycle()
    val idle by container.idleBackup.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contentAccessible = com.qingling.writing.feature.settings.LocalAppLockControl.current.contentAccessible
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val bookFlow = remember { container.writingRepository.observeBooks(com.qingling.writing.core.model.GuestSpace.ID) }
    val books by bookFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedBook by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseBook by remember { mutableStateOf(false) }
    var encryptDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordAgain by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    val blocked = ui.busy || ui.awaitingSave
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        model.finishSave(if (uri == null) null else { { context.contentResolver.openOutputStream(uri, "wt") } })
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.inspect { context.contentResolver.openInputStream(uri) }
    }
    BackHandler(enabled = blocked) { /* Keep the pending transaction/document result associated with this page. */ }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.backup_title)) },
            navigationIcon = { BackButton(onClick = { if (!blocked) onBack() }) })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { AutoBackupCard(enabled = !blocked) }
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("停笔备份 · 本机最近 20 份", style = MaterialTheme.typography.titleLarge)
                    Text("正文保存完成、连续 5 秒无新改动后生成独立副本，并尝试已授权目录的追加备份。新副本校验成功后才清理超出 20 份的旧副本。")
                    Text("本机副本与应用一起保存，卸载或清除数据会丢失；请同时配置外部目录。未加密，大小限制与手动备份相同。", style = MaterialTheme.typography.bodySmall)
                    if (idle.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    idle.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    idle.files.forEach { file ->
                        QuietTextButton(enabled = !blocked && !idle.busy, onClick = { model.inspect { file.inputStream() } }) {
                            Text("查看并恢复 · " + formatTimestamp(java.time.Instant.ofEpochMilli(file.lastModified()).toString()))
                        }
                    }
                } }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.backup_protect_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.backup_scope), style = MaterialTheme.typography.bodyMedium)
                        AppSecondaryButton(enabled = !blocked,onClick = { chooseBook = true }) {
                            Text("手动备份范围："+(if(selectedBook == null) "全部作品" else books.find { it.id == selectedBook }?.title ?: "所选作品已删除，请重选"))
                        }
                        if (selectedBook != null) Text("单书备份包含该书全部卷章、历史、回收站内容和设定便签；不会带入其他作品。自动备份仍保护全部作品。",style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !blocked && (selectedBook == null || books.any { it.id == selectedBook }), modifier = Modifier.fillMaxWidth(), onClick = {
                            scope.launch {
                                val name = model.beginSave(selectedBook) ?: return@launch
                                try { save.launch(name) } catch (_: Exception) { model.pickerFailed() }
                            }
                        }) { Text(stringResource(R.string.backup_create)) }
                        AppSecondaryButton(enabled = !blocked && (selectedBook == null || books.any { it.id == selectedBook }), modifier = Modifier.fillMaxWidth(), onClick = { encryptDialog = true }) { Text("创建密码加密备份") }
                        Text("加密备份密码无法找回，请妥善保管；自动备份仍未加密。", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.backup_external_note), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                AppSecondaryButton(enabled = !blocked, modifier = Modifier.fillMaxWidth(), onClick = {
                    try { open.launch(arrayOf("*/*")) } catch (_: Exception) { model.pickerFailed() }
                }) {
                    Text(stringResource(R.string.backup_select))
                }
            }
            if (blocked) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ui.notice?.let { notice -> item {
                Text(when (notice) {
                    BackupNotice.SAVED -> stringResource(R.string.backup_saved)
                    BackupNotice.CANCELLED -> stringResource(R.string.backup_cancelled)
                    BackupNotice.FAILED -> stringResource(R.string.backup_failed)
                    BackupNotice.INVALID -> stringResource(R.string.backup_invalid)
                    BackupNotice.EMPTY -> stringResource(R.string.backup_empty)
                    BackupNotice.MISSING -> stringResource(R.string.backup_missing)
                    BackupNotice.RESTORED -> stringResource(R.string.backup_restored, ui.restoredBooks, ui.restoredChapters)+" · 灵感与设定 "+ui.restoredPlanning+" 条"
                    BackupNotice.PASSWORD_REQUIRED -> "此备份需要密码。"
                    BackupNotice.PASSWORD_ERROR -> "密码不正确，或文件已损坏。"
                }, color = if (notice in listOf(BackupNotice.FAILED, BackupNotice.INVALID, BackupNotice.MISSING))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            } }
            ui.preview?.let { backup -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.backup_preview), style = MaterialTheme.typography.titleLarge)
                        Text("构思与设定 ${backup.planning.size} 条",style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.backup_created_at, formatTimestamp(backup.createdAt)))
                        Text(stringResource(R.string.backup_counts, backup.books.size, backup.chapters.size, backup.revisions.size))
                        Text(stringResource(R.string.backup_deleted_count, backup.chapters.count { it.deletedAt != null }),
                            style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        backup.books.take(20).forEach { book ->
                            Text(if (book.deletedAt == null) book.title else stringResource(R.string.backup_deleted_book, book.title))
                        }
                        if (backup.books.size > 20) Text(stringResource(R.string.backup_more_books))
                        Button(enabled = !blocked && (backup.books.isNotEmpty() || backup.planning.isNotEmpty()), modifier = Modifier.fillMaxWidth(),
                            onClick = { confirmRestore = true }) { Text(stringResource(R.string.backup_restore_copies)) }
                    }
                }
            } }
            item { Text(stringResource(R.string.backup_not_cloud), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (contentAccessible && encryptDialog) AlertDialog(onDismissRequest = { encryptDialog = false; newPassword = ""; newPasswordAgain = "" },
        title = { Text("设置备份密码") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("至少 8 个字符，最多 128 个字符。密码无法找回；请另存于安全位置。")
            AppTextField(newPassword, { newPassword = it.take(128) }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            AppTextField(newPasswordAgain, { newPasswordAgain = it.take(128) }, label = { Text("再次输入") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        } }, confirmButton = { ConfirmAction(enabled = newPassword.length >= 8 && newPassword == newPasswordAgain, onClick = {
            val password = newPassword.toCharArray(); newPassword = ""; newPasswordAgain = ""; encryptDialog = false
            scope.launch { val name = model.beginSave(selectedBook, password) ?: return@launch; try { save.launch(name) } catch (_: Exception) { model.pickerFailed() } }
        }) { Text("创建备份") } }, dismissButton = { QuietTextButton(onClick = { encryptDialog = false; newPassword = ""; newPasswordAgain = "" }) { Text("取消") } })
    if (contentAccessible && (ui.notice == BackupNotice.PASSWORD_REQUIRED || ui.notice == BackupNotice.PASSWORD_ERROR)) AlertDialog(
        onDismissRequest = { restorePassword = ""; model.cancelPassword() }, title = { Text("输入备份密码") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.notice == BackupNotice.PASSWORD_ERROR) Text("密码不正确，或文件已损坏。", color = MaterialTheme.colorScheme.error)
            AppTextField(restorePassword, { restorePassword = it.take(128) }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        } }, confirmButton = { ConfirmAction(enabled = restorePassword.length >= 8, onClick = {
            val password = restorePassword.toCharArray(); restorePassword = ""; model.retryPassword(password)
        }) { Text("验证") } }, dismissButton = { QuietTextButton(onClick = { restorePassword = ""; model.cancelPassword() }) { Text("取消") } })
    if (contentAccessible && chooseBook) AlertDialog(onDismissRequest = { chooseBook = false },title = { Text("选择手动备份范围") },
        text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 400.dp)) {
            item { QuietTextButton(onClick = { selectedBook = null;chooseBook = false }) { Text("全部作品（含回收站）") } }
            items(books.size) { i -> QuietTextButton(onClick = { selectedBook = books[i].id;chooseBook = false }) { Text(books[i].title) } }
        } },confirmButton = { ConfirmAction(onClick = { chooseBook = false }) { Text("取消") } })
    if (contentAccessible && confirmRestore && ui.preview != null) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = { Text(stringResource(R.string.backup_confirm_body)) },
        confirmButton = { ConfirmAction(onClick = { confirmRestore = false; model.restore() }) {
            Text(stringResource(R.string.backup_restore_copies)) } },
        dismissButton = { QuietTextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.action_cancel)) } })
}
