package com.qingling.writing.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qingling.writing.R
import com.qingling.writing.core.design.ListRow
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.PanelGroup
import com.qingling.writing.core.design.ToolRow
import com.qingling.writing.core.design.WritingIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.EditorAppearance
import com.qingling.writing.core.model.AutoBackupState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

/**
 * 右面板内容(P04/P06):查找/外观 M2+ 接入(文案明示,行不可点);
 * 历史版本(M2)跳转本章历史页;「合并导出」「备份与同步」跳转对应页。
 * 用于编辑器右滑面板(M1 起),不再有独立目的地。
 */
@Composable
fun ToolsContent(
    bookId: String?,
    chapterId: String,
    onOpenExport: (String) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenAppearance: () -> Unit,
    onFormat: () -> Unit,
    onFind: () -> Unit,
    onSearchBook: () -> Unit,
    onBatchReplace: () -> Unit,
    onBatchFormat: () -> Unit,
    modifier: Modifier = Modifier,
    focusMode: Boolean = false,
    onFocusMode: (Boolean) -> Unit = {},
    onPlanning: () -> Unit = {},
    onSplit: () -> Unit = {},
    onMerge: () -> Unit = {},
    onWritingStats: () -> Unit = {},
    onProofread: () -> Unit = {},
    onQuickNote: () -> Unit = {},
    onForeshadow: () -> Unit = {},
    onInbox: () -> Unit = {},
    onComplete: () -> Unit = {},
    onChapterStats: () -> Unit = {},
    onFormatRules:()->Unit=onFormat,
) {
    val container = LocalAppContainer.current
    val appearance by container.settingsStore.appearance.collectAsStateWithLifecycle(initialValue = EditorAppearance())
    val backup by container.autoBackupStore.state.collectAsStateWithLifecycle(initialValue = AutoBackupState())
    val punctuation by container.settingsStore.punctuationBar.collectAsStateWithLifecycle(initialValue = true)
    val line by container.settingsStore.currentLine.collectAsStateWithLifecycle(initialValue = false)
    val keepAwake by container.settingsStore.keepAwake.collectAsStateWithLifecycle(initialValue = false)
    val typewriter by container.settingsStore.typewriter.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = Spacing.s8),
    ) {
        PanelGroup("创作辅助")
        ToolRow(WritingIcons.Bulb,"灵感速记","先记下来，稍后整理",onClick=onQuickNote)
        ToolRow(Icons.Filled.List,"便签与伏笔","本书灵感与伏笔欠账",onClick=onInbox)
        ToolRow(WritingIcons.Scroll,"构思与设定","章节细纲、分卷概要、设定本与灵感便签",onClick=onPlanning)
        ToolRow(WritingIcons.Replace,"查找与替换","在当前章内查找并替换文字",onClick=onFind)
        ToolRow(Icons.Filled.Search,"全书搜索","跨全部章节搜索关键词",onClick=onSearchBook)
        ToolRow(Icons.Filled.Edit,"全书替换","整书或单卷，先预览再确认",onClick=onBatchReplace)
        PanelGroup("状态与进程")
        ToolRow(Icons.Filled.CheckCircle,"完成本章","手动标记完成，展示安静的小结",onClick=onComplete)
        ToolRow(WritingIcons.Chart,"本章统计","本章字数、本次输入与活跃时长",onClick=onChapterStats)
        ToolRow(WritingIcons.Target,"统计与目标","日周月净增、活动日历、整书目标与预计完稿",onClick=onWritingStats)
        ToolRow(Icons.Filled.Check,"本地校对","标点、重复字、长段落与自定义词表",onClick=onProofread)
        ToolRow(WritingIcons.History,"历史版本","自动快照，误删也能找回",onClick={onOpenHistory(chapterId)})
        PanelGroup("章节结构")
        ToolRow(WritingIcons.Split,"按光标拆分章节","光标前后分成两章，完整原文存历史",onClick=onSplit)
        ToolRow(WritingIcons.Merge,"合并所选章节","先预览，原章保留历史与回收站副本",onClick=onMerge)
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Box(Modifier.weight(1f)){ToolRow(WritingIcons.Format,"一键排版","使用上次规则 · 可撤销",onClick=onFormat)};TextButton(onClick=onFormatRules){Text("规则 ›")}}
        ToolRow(WritingIcons.Format,"批量排版","整卷／整书排版，保留原文历史",onClick=onBatchFormat)
        PanelGroup("外观")
        ToolRow(WritingIcons.Type, "字号与背景", "字号、行距、底色与背景图", "${appearance.fontSize} sp", onOpenAppearance)
        PanelGroup("文件与数据")
        if (bookId != null) ToolRow(WritingIcons.Export, "合并导出", "按目录顺序导出 TXT / DOCX", onClick = { onOpenExport(bookId) })
        ToolRow(WritingIcons.Backup, "备份与恢复", "独立备份文件与新副本恢复", if (backup.enabled) "自动开启" else "手动", onOpenBackup)
        PanelGroup("常用开关")
        Row(Modifier.padding(Spacing.s24), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("打字机定位");Text("输入时将光标行保持在编辑区中部",style=MaterialTheme.typography.bodySmall) }
            Switch(typewriter,{ value -> scope.launch { container.settingsStore.setTypewriter(value) } })
        }
        Row(Modifier.padding(Spacing.s24), verticalAlignment = Alignment.CenterVertically) {
            Text("专注模式", Modifier.weight(1f))
            Switch(focusMode,onFocusMode)
        }
        Row(Modifier.padding(Spacing.s24), verticalAlignment = Alignment.CenterVertically) {
            Text("编辑时屏幕常亮", Modifier.weight(1f))
            Switch(keepAwake,{ value -> scope.launch { container.settingsStore.setKeepAwake(value) } })
        }
        Row(Modifier.padding(Spacing.s24), verticalAlignment = Alignment.CenterVertically) {
            Text("标点快捷栏", Modifier.weight(1f))
            Switch(punctuation, { value -> scope.launch { container.settingsStore.setPunctuationBar(value) } })
        }
        Row(Modifier.padding(Spacing.s24), verticalAlignment = Alignment.CenterVertically) {
            Text("当前行高亮", Modifier.weight(1f))
            Switch(line, { value -> scope.launch { container.settingsStore.setCurrentLine(value) } })
        }
        HorizontalDivider(modifier = Modifier.padding(Spacing.s24))
        Text("所有数据只存本机 · 不联网", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s24))
    }
}
