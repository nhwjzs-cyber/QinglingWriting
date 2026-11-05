package com.qingling.writing.feature.outline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qingling.writing.R
import com.qingling.writing.core.design.BackButton

/**
 * 目录页(outline 目的地,书详情入口):与编辑器左滑面板共用 [OutlineContent]。
 * 系统返回不拦截;返回即回书架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSource:(String,Int,Int)->Unit={id,_,_->onOpenChapter(id)},
    onExport:((String,String)->Unit)?=null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.outline_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        OutlineContent(
            bookId = bookId,
            currentChapterId = null,
            onOpenChapter = onOpenChapter,onOpenSource=onOpenSource,onExport=onExport,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }
}
