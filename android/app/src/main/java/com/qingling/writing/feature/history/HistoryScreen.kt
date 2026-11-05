package com.qingling.writing.feature.history
import com.qingling.writing.core.design.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.qingling.writing.core.design.ConfirmAction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.CenterMessage
import com.qingling.writing.core.design.ConfirmDialog
import com.qingling.writing.core.design.Radii
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TouchTarget
import com.qingling.writing.core.design.formatTimestamp
import com.qingling.writing.core.model.Revision

/**
 * 历史版本页(P06/M2):本章快照列表(新→旧)、预览、手动快照、恢复。
 * 恢复带确认对话框,明示「当前内容会先保存为『恢复前』历史,不会丢失」。
 * 恢复后 edit_seq 前进;若编辑器会话仍在,其下次保存会得到 STALE_SEQUENCE 并提供「载入最新」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    chapterId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        key = chapterId,
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.historyRepository, chapterId)
            }
        },
    )
    val revisions by viewModel.revisions.collectAsStateWithLifecycle()
    val restored by viewModel.restored.collectAsStateWithLifecycle()
    var restoreTarget by remember { mutableStateOf<Revision?>(null) }
    var previewTarget by remember { mutableStateOf<Revision?>(null) }
    var diffTarget by remember { mutableStateOf<Revision?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.history_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    QuietTextButton(
                        onClick = { viewModel.createManualSnapshot(chapterId) },
                        modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min),
                    ) {
                        Text(text = stringResource(R.string.history_manual))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (restored) {
                Text(
                    text = stringResource(R.string.history_restored_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s24, vertical = Spacing.s8),
                )
            }
            if (revisions.isEmpty()) {
                EmptyState(title=stringResource(R.string.history_empty),subtitle="自动保存的快照会显示在这里，也可以保存当前版本",icon=WritingIcons.History,modifier=Modifier.fillMaxSize().wrapContentHeight())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.s24),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s8),
                ) {
                    items(revisions, key = { it.id }) { revision ->
                        RevisionCard(
                            revision = revision,
                            onPreview = { previewTarget = revision },
                            onCompare = { diffTarget = revision },
                            onRestore = { restoreTarget = revision },
                        )
                    }
                }
            }
        }
    }

    diffTarget?.let { target -> RevisionDiffDialog(chapterId,target) { diffTarget = null } }
    restoreTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.history_restore_confirm_title),
            body = stringResource(R.string.history_restore_confirm_body),
            confirmText = stringResource(R.string.history_restore),
            onConfirm = {
                viewModel.restore(chapterId, target.id)
                restoreTarget = null
            },
            onDismiss = { restoreTarget = null },
        )
    }

    previewTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { previewTarget = null },
            title = {
                Text(
                    text = stringResource(
                        R.string.history_preview_title_format,
                        formatTimestamp(target.createdAt),
                    ),
                )
            },
            text = {
                Text(
                    text = target.body.ifEmpty { stringResource(R.string.history_preview_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                ConfirmAction(onClick = { previewTarget = null }) {
                    Text(text = stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun RevisionCard(
    revision: Revision,
    onPreview: () -> Unit,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    Column {
        ListRow(title=formatTimestamp(revision.createdAt),subtitle=revisionSourceLabel(revision.source)+" · "+revision.body.ifBlank {"（空章）"},icon=WritingIcons.History,onClick=onPreview)
        Row(Modifier.fillMaxWidth().padding(horizontal=Spacing.s16),horizontalArrangement=Arrangement.End) {
            QuietTextButton(onClick=onCompare){Text("对比")}
            QuietTextButton(onClick=onRestore){Text(stringResource(R.string.history_restore))}
        }
        androidx.compose.material3.HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
    }
}
