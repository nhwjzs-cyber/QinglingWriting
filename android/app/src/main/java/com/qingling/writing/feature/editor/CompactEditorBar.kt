package com.qingling.writing.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.model.PlanningKind

@Composable
internal fun CompactEditorBar(session: EditorSession, writing: Boolean, punctuation: Boolean, previous: Boolean, next: Boolean,
    switching: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onOutline: () -> Unit, onStep: (Int) -> Unit,
    coordinateMode: Int = 0, today: Long = 0, onCycleCoordinates: () -> Unit = {}, onAccessories: () -> Unit = {}, modifier: Modifier = Modifier, accessoryDrag: Modifier = Modifier) {
    val undo by session.canUndo.collectAsStateWithLifecycle(); val redo by session.canRedo.collectAsStateWithLifecycle()
    val repo = LocalAppContainer.current.planning
    val people by remember(session.bookId) { repo.search(session.bookId, "", PlanningKind.CHARACTER.name) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val height = if (writing) 30.dp else 48.dp
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
            Row(Modifier.fillMaxWidth().height(height).semantics { contentDescription = if (writing) "写作配件条" else "编辑导航栏" }, verticalAlignment = Alignment.CenterVertically) {
                @Composable fun UndoRedo() {
                    IconButton(onClick = onUndo, enabled = undo && !switching, modifier = Modifier.width(32.dp).height(height)) { Icon(painterResource(R.drawable.ic_undo), "撤销", Modifier.size(19.dp)) }
                IconButton(onClick = onRedo, enabled = redo && !switching, modifier = Modifier.width(32.dp).height(height)) { Icon(painterResource(R.drawable.ic_redo), "重做", Modifier.size(19.dp)) }
                }
                if (writing) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    UndoRedo()
                    LazyRow(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        val marks = if (punctuation) listOf(
                            Triple("「」", "「」", 1), Triple("“", "“", 0), Triple("”", "”", 0),
                            Triple("——", "——", 0), Triple("……", "……", 0), Triple("、", "、", 0), Triple("缩进", "　　", 0)
                        ) else emptyList()
                        items(marks + people.take(8).map { Triple(it.title, it.title, 0) }) { (label, insert, caretBack) ->
                            TextButton(enabled = !switching && session.editorState.composition == null, contentPadding = PaddingValues(horizontal = 7.dp), modifier = Modifier.height(height), onClick = {
                                session.editorState.let { state -> if (state.composition == null) state.edit {
                                    val at = selection.min; replace(at, selection.max, insert); selection = TextRange(at + insert.length - caretBack)
                                } }
                            }) { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    }
                    Column(Modifier.width(40.dp).fillMaxHeight().then(accessoryDrag)
                        .clickable(onClickLabel = "打开写作工具抽屉", onClick = onAccessories)
                        .semantics { contentDescription = "上拉或点按打开写作工具" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.width(18.dp).height(3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        EditorCoordinates(session, coordinateMode, today, onCycleCoordinates, Modifier.widthIn(max = 132.dp))
                    }
                } else {
                    UndoRedo()
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    listOf("上一章", "目录", "下一章").forEachIndexed { index, label ->
                        val enabled = when (index) { 0 -> previous; 2 -> next; else -> true }
                        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled) { when (index) { 0 -> onStep(-1); 1 -> onOutline(); else -> onStep(1) } }, contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .3f))
                        }
                    }
                }
                EditorCoordinates(session, coordinateMode, today, onCycleCoordinates, Modifier.widthIn(min = 100.dp, max = 132.dp))
                }
            }
        }
    }
}
