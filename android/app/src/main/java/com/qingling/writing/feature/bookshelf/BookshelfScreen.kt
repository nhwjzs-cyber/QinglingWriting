package com.qingling.writing.feature.bookshelf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import com.qingling.writing.core.design.MenuRule
import com.qingling.writing.core.design.ConfirmAction
import com.qingling.writing.core.design.SearchField
import com.qingling.writing.core.design.EmptyState
import com.qingling.writing.core.design.AppTextField
import com.qingling.writing.core.design.AppChoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import com.qingling.writing.core.design.ChromeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import com.qingling.writing.core.design.AppMenu
import com.qingling.writing.core.design.AppMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.BackButton
import com.qingling.writing.core.design.ConfirmDialog
import com.qingling.writing.core.design.Dimens
import com.qingling.writing.core.design.Radii
import com.qingling.writing.core.design.Spacing
import com.qingling.writing.core.design.TitleInputDialog
import com.qingling.writing.core.design.TouchTarget
import com.qingling.writing.core.design.formatTimestamp
import com.qingling.writing.core.design.titleErrorText
import com.qingling.writing.core.model.BookSummary
import com.qingling.writing.domain.validation.TitleError
import com.qingling.writing.domain.validation.TitleValidator
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.qingling.writing.core.model.*

/**
 * 书架(起始页,P01):数据驱动(Room 最近编辑倒序),创建/重命名/软删除/继续写作/回收站入口。
 * 「演示·」前缀的书是 debug 种子数据(角标明示);release 书架只会出现用户自建的书。
 * 系统返回不拦截(此页为栈底)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onOpenBook: (String) -> Unit,
    onContinueWriting: (String) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val viewModel: BookshelfViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BookshelfViewModel(container.writingRepository) }
        },
    )
    val books by viewModel.books.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var createMode by rememberSaveable {mutableIntStateOf(1)}
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BookSummary?>(null) }
    var renameError by remember { mutableStateOf<TitleError?>(null) }
    var deleteTarget by remember { mutableStateOf<BookSummary?>(null) }
    var menuTargetId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable {mutableStateOf("")}
    var scopeName by rememberSaveable {mutableStateOf(ShelfScope.ACTIVE.name)}
    val order by container.settingsStore.shelfSort.collectAsStateWithLifecycle(initialValue=ShelfSort.UPDATED)
    var orderMenu by remember {mutableStateOf(false)}
    var archiveTarget by remember {mutableStateOf<BookSummary?>(null)}
    var actionError by remember {mutableStateOf<String?>(null)}
    val visible=remember(books,query,scopeName,order) {queryShelf(books,query,ShelfScope.valueOf(scopeName),order)}
    fun shelfAction(block: suspend ()->Unit) {scope.launch {
        try {block();actionError=null} catch(e: kotlinx.coroutines.CancellationException){throw e}
        catch(_: Exception){actionError="操作未保存，请稍后重试；原稿不受影响"}
    }}

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.works_title)) },
                navigationIcon = { onBack?.let { BackButton(onClick = it) } },
                actions = {
                    ChromeAction(Icons.Filled.Delete, stringResource(R.string.action_recycle_bin), onOpenRecycleBin)
                    ChromeAction(Icons.Filled.Settings, stringResource(R.string.action_settings), onOpenSettings)

                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createMode=1;showCreateDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.bookshelf_create_book),
                )
            }
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(Spacing.s24),
                verticalArrangement = Arrangement.spacedBy(Spacing.s8, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bookshelf_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.bookshelf_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { createMode=1;showCreateDialog = true },
                    modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min),
                ) {
                    Text(text = stringResource(R.string.bookshelf_create_book))
                }
                Row {TextButton(onClick={createMode=2;showCreateDialog=true}){Text("导入 TXT")};TextButton(onClick={createMode=3;showCreateDialog=true}){Text("从备份恢复")}}
            }
        } else {
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                SearchField(query,{query=it.take(200)},placeholder="搜索书名或作者",
                    modifier=Modifier.fillMaxWidth().padding(horizontal=Spacing.s24,vertical=8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal=Spacing.s24).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ShelfScope.entries.forEach { item->AppChoice(scopeName==item.name,{scopeName=item.name},label={Text(item.label)}) }
                    Box {
                        QuietTextButton(onClick={orderMenu=true}) {Text(order.label+" ▾")}
                        AppMenu(orderMenu,{orderMenu=false}) {ShelfSort.entries.forEach {sort->AppMenuItem(text={Text(sort.label)},icon=if(order==sort) Icons.Filled.Check else Icons.Filled.List,onClick={orderMenu=false;shelfAction {container.settingsStore.setShelfSort(sort)}})}}
                    }
                }
                Text("${visible.size} 部作品 · 置顶作品优先",Modifier.padding(horizontal=Spacing.s24),style=MaterialTheme.typography.bodySmall)
                actionError?.let {Text(it,Modifier.padding(Spacing.s24),color=MaterialTheme.colorScheme.error)}
                if(visible.isEmpty()) EmptyState(if(query.isBlank()) "这个栏目暂无作品" else "没有匹配的作品",subtitle=if(query.isBlank()) "切换栏目查看其他作品" else "试试其他书名或作者")
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.s48),
            ) {
                if(books.any {it.isDemo}) item(key = "demo-notice") { DemoNotice() }
                items(visible, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        menuOpen = menuTargetId == book.id,
                        onMenuOpen = { open -> menuTargetId = if (open) book.id else null },
                        onOpen = { onOpenBook(book.id) },
                        onContinue = { book.continueChapterId?.let(onContinueWriting) },
                        onRename = {
                            menuTargetId = null
                            renameError = null
                            renameTarget = book
                        },
                        onDelete = {
                            menuTargetId = null
                            deleteTarget = book
                        },
                        onPin = {menuTargetId=null;shelfAction {viewModel.pinBook(book.id,!book.pinned)}},
                        onArchive = {menuTargetId=null;if(book.archived) shelfAction {viewModel.archiveBook(book.id,false)} else archiveTarget=book},
                    )

                }
            }
            }
        }
    }

    if(showCreateDialog) AddBookFlow(onDismiss={showCreateDialog=false},onCreated=onOpenBook,initialMode=createMode)
    archiveTarget?.let {book->ConfirmDialog(title="归档作品",body="《${book.title}》将移入已归档栏目，并从首页和自动续写中隐藏。正文、历史和备份仍保留，随时可以移回。",confirmText="归档",
        onConfirm={archiveTarget=null;shelfAction {viewModel.archiveBook(book.id,true)}},onDismiss={archiveTarget=null})}

    renameTarget?.let { target ->
        TitleInputDialog(
            title = stringResource(R.string.rename_book_title),
            label = stringResource(R.string.field_book_title),
            initialValue = target.title,
            error = renameError,
            maxCodePoints = TitleValidator.BOOK_MAX_CODE_POINTS,
            onConfirm = { raw ->
                scope.launch {
                    val error = viewModel.renameBook(target.id, raw)
                    if (error == null) {
                        renameTarget = null
                        renameError = null
                    } else {
                        renameError = error
                    }
                }
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let {target -> DeleteBookDialog(target,onDismiss={deleteTarget=null},onDeleted={deleteTarget=null})}

}

/** 演示数据声明横幅:仅当列表中出现「演示·」前缀书籍时显示(debug 种子)。 */
@Composable
private fun DemoNotice(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radii.panel),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s24, vertical = Spacing.s16),
    ) {
        Text(
            text = stringResource(R.string.bookshelf_demo_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.s16),
        )
    }
}

@Composable
private fun BookCard(
    book: BookSummary,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(Radii.bookCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s24, vertical = Spacing.s8),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.listRowMinHeight)
                .clickable(onClick = onOpen)
                .padding(start = Spacing.s20, top = Spacing.s12, bottom = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.qingling.writing.core.design.SeedBookCover(book.title, book.id, Modifier.padding(end = 16.dp).then(Modifier.sizeIn(minWidth = 46.dp, maxWidth = 46.dp, minHeight = 68.dp, maxHeight = 68.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.title,
                        maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (book.isDemo) {
                        Surface(
                            shape = RoundedCornerShape(Radii.button),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = Spacing.s8),
                        ) {
                            Text(
                                text = stringResource(R.string.bookshelf_demo_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.s8, vertical = Spacing.s4),
                            )
                        }
                    }
                }
                if(book.pinned || book.archived) Text(listOfNotNull(if(book.pinned) "置顶" else null,if(book.archived) "已归档" else null).joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(
                        R.string.bookshelf_meta_format,
                        book.wordCount,
                        formatTimestamp(book.updatedAt),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s4),
                )
            }
            QuietTextButton(
                onClick = onContinue,
                enabled = book.continueChapterId != null,
                modifier = Modifier.sizeIn(minWidth = TouchTarget.min, minHeight = TouchTarget.min),
            ) {
                Text(text = stringResource(R.string.action_continue_writing))
            }
            Box {
                IconButton(onClick = { onMenuOpen(!menuOpen) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                AppMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }, context="${book.title} · ${book.wordCount} 字") {
                    AppMenuItem(text={Text(if(book.pinned) "取消置顶" else "置顶作品")},onClick=onPin,icon=Icons.Filled.Star)
                    AppMenuItem(text={Text(if(book.archived) "移回写作中" else "归档作品")},onClick=onArchive,icon=Icons.Filled.Check)
                    AppMenuItem(
                        text = { Text(text = stringResource(R.string.action_rename)) },
                        onClick = onRename, icon=Icons.Filled.Edit,
                    )
                    MenuRule()
                    AppMenuItem(
                        text = { Text(text = stringResource(R.string.action_move_to_recycle_bin)) },
                        onClick = onDelete, icon=Icons.Filled.Delete, danger=true,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CreateBookDialog(
    onConfirm: (title: String, author: String, description: String, onValidated: (TitleError?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorState by remember { mutableStateOf<TitleError?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.create_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorState = null
                    },
                    label = { Text(text = stringResource(R.string.field_book_title)) },
                    isError = errorState != null,
                    supportingText = errorState?.let { current ->
                        {
                            Text(
                                text = titleErrorText(current, TitleValidator.BOOK_MAX_CODE_POINTS),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(text = stringResource(R.string.field_book_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = stringResource(R.string.field_book_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            ConfirmAction(
                onClick = {
                    // 先本地校验(与数据层同一实现);通过后交给 ViewModel 落库。
                    val checked = TitleValidator.checkBookTitle(title)
                    if (checked.error != null) {
                        errorState = checked.error
                    } else {
                        onConfirm(checked.value, author, description) { dialogError ->
                            errorState = dialogError
                        }
                    }
                },
            ) {
                Text(text = stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            QuietTextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
