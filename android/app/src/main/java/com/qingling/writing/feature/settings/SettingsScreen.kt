package com.qingling.writing.feature.settings
import com.qingling.writing.core.design.ConfirmAction

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.datastore.ThemeMode
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.ListRow
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TouchTarget
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role

/**
 * 设置页(目的地「settings」,从书架进入)。
 * 主题三态已接通(G04):深色(默认)/浅色/跟随系统;选择立即经 DataStore 持久化,重启保持;
 * 行为在页内文案如实说明。其余条目未接入(文案明示)。系统返回不拦截。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAppearance: () -> Unit, onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier, asPanel: Boolean = false) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsStore) }
        },
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val chapterSummary by container.settingsStore.chapterSummary.collectAsStateWithLifecycle(initialValue=false)
    val referenceDrawerEnabled by container.settingsStore.referenceDrawerEnabled.collectAsStateWithLifecycle(initialValue=true)
    val swipePanels by container.settingsStore.swipePanels.collectAsStateWithLifecycle(initialValue=true)
    val reduceMotion by container.settingsStore.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val historyLimit by container.settingsStore.autoHistoryLimit.collectAsStateWithLifecycle(initialValue = 20)
    val lock = LocalAppLockControl.current
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showHistoryLimit by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var historyLimitError by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<Int?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showRecovery by remember {mutableStateOf(false)}

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (asPanel) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (asPanel) IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    } else BackButton(onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s16),
            verticalArrangement = Arrangement.spacedBy(Spacing.s16),
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(stringResource(R.string.settings_appearance_group), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s16))
            ListRow(
                title = stringResource(R.string.settings_theme),
                trailing = when (themeMode) {
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_value_dark)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_value_light)
                    ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_value_system)
                },
                onClick = { showThemeDialog = true },
            )
            Text(
                text = stringResource(R.string.settings_theme_behavior),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s24, vertical = Spacing.s8),
            )
            }
            Card(shape = RoundedCornerShape(20.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(stringResource(R.string.settings_interaction_group), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s16))
                Row(Modifier.fillMaxWidth().padding(Spacing.s16),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("写完一章显示小结页");Text("默认只出小结条；目录标记始终不弹全屏",style=MaterialTheme.typography.bodySmall)}
                    Switch(chapterSummary,{scope.launch{container.settingsStore.setChapterSummary(it)}})
                }
                Row(Modifier.fillMaxWidth().padding(Spacing.s16),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("顶栏下拉显示细纲");Text("默认开启；收起抽屉不会关闭此开关",style=MaterialTheme.typography.bodySmall)}
                    Switch(referenceDrawerEnabled,{scope.launch{container.settingsStore.setReferenceDrawerEnabled(it)}})
                }
                Row(Modifier.fillMaxWidth().padding(Spacing.s16),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("滑动打开面板");Text("关闭后仍可用底栏目录和更多按钮",style=MaterialTheme.typography.bodySmall)}
                    Switch(swipePanels,{scope.launch{container.settingsStore.setSwipePanels(it)}})
                }
                Row(Modifier.fillMaxWidth().padding(Spacing.s16), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reduce_motion), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_reduce_motion_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = { value ->
                        scope.launch { container.settingsStore.setReduceMotion(value) }
                    })
                }
            }
            Card(shape = RoundedCornerShape(20.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            ListRow(title = stringResource(R.string.settings_body_layout), onClick = onOpenAppearance)
            ListRow(title = stringResource(R.string.backup_title), onClick = onOpenBackup)
            ListRow(title = "自动历史保留", trailing = "每章 $historyLimit 份", onClick = { showHistoryLimit = true })
            ListRow(title="未保存草稿",subtitle="查看异常退出留下的本机保护副本",onClick={showRecovery=true})
            ListRow(title = "历史与存储", onClick = { showStorage = true })
            ListRow(title = "应用锁", trailing = if (lock.enabled) "已开启" else "未开启", onClick = { lock.requestChange(!lock.enabled) })
            Text("使用设备屏幕锁；系统支持时也可使用已设置的指纹。应用锁不会加密稿件和备份。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.s24, vertical = Spacing.s8))
            ListRow(title = "导入 TXT 稿件", onClick = { showImport = true })
            ListRow(title = stringResource(R.string.settings_about), onClick = { infoDialog = R.string.settings_about })
            }
            Card(shape = RoundedCornerShape(20.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                ListRow(title = "隐私政策", trailing = "查看全文", onClick = { showPrivacy = true })
                Text(com.qingling.writing.core.privacy.PrivacyPolicy.SUMMARY, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Spacing.s24))
                Text("开发者不收集或上传个人信息与稿件，写作数据保存在本机。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.s24, vertical = Spacing.s16))
            }
        }
    }

    if(showRecovery) com.qingling.writing.feature.planning.RecoveryDraftsSheet{showRecovery=false}
    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    infoDialog?.let { title ->
        InformationDialog(initialPrivacy=title==R.string.settings_privacy,onDismiss={infoDialog=null})
    }
    if (showImport) com.qingling.writing.feature.recovery.TxtImportDialog(onDismiss = { showImport = false })
    if (showStorage) StorageDialog(onDismiss={showStorage=false},onBackup=onOpenBackup)
    if (showHistoryLimit) AlertDialog(onDismissRequest = { showHistoryLimit = false }, title = { Text("自动历史保留") },
        text = { Column {
            Text("调整后在该章下一次生成自动版本时生效，超出上限的旧自动版本将被清理；手动版本和恢复前版本不会自动清理。")
            if (historyLimitError) Text("设置未保存，请检查存储空间后重试。", color = MaterialTheme.colorScheme.error)
            listOf(20, 50, 100).forEach { count -> Row(Modifier.fillMaxWidth().sizeIn(minHeight=TouchTarget.min).selectable(selected=historyLimit==count,role=Role.RadioButton,onClick={ scope.launch {
                    try { container.settingsStore.setAutoHistoryLimit(count); historyLimitError = false; showHistoryLimit = false }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { historyLimitError = true }
                } }),verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = historyLimit == count, onClick = null)
                Text("$count 份")
            } }
        } }, confirmButton = { ConfirmAction(onClick = { showHistoryLimit = false }) { Text("关闭") } })
    if (showThemeDialog) {
        ThemeModeDialog(
            current = themeMode,
            onSelect = { mode ->
                showThemeDialog = false
                viewModel.setThemeMode(mode)
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_theme_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = TouchTarget.min).selectable(selected = mode == current,
                            role = Role.RadioButton, onClick = { onSelect(mode) }),
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = null,
                        )
                        Text(
                            text = when (mode) {
                                ThemeMode.DARK -> stringResource(R.string.settings_theme_value_dark)
                                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_value_light)
                                ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_value_system)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            ConfirmAction(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min),
            ) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}
