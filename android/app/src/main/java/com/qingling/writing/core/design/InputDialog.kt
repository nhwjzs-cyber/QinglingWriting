package com.qingling.writing.core.design
import com.qingling.writing.core.design.AppTextField

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.qingling.writing.core.design.QuietTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qingling.writing.R
import com.qingling.writing.domain.validation.TitleError

/**
 * 标题校验错误 → 用户文案(组合期渲染,配置感知;lint LocalContextGetResourceValueCall 达标)。
 * [maxCodePoints] 是该校验的码点上限(书 80 / 卷章 120,见 TitleValidator)。
 */
@Composable
internal fun titleErrorText(error: TitleError, maxCodePoints: Int): String = when (error) {
    TitleError.EMPTY -> stringResource(R.string.title_error_empty)
    TitleError.CONTAINS_LINE_BREAK -> stringResource(R.string.title_error_line_break)
    TitleError.TOO_LONG -> stringResource(R.string.title_error_too_long_format, maxCodePoints)
}

/**
 * 单行标题输入对话框(书架重命名 / 目录新建与重命名共用)。
 * 校验由调用方完成:确认时回调 [onConfirm];若调用方传入 [error](TitleError)则内联展示、对话框不关闭。
 * 文案在组合期渲染;按钮满足 48dp 触控目标。
 */
@Composable
fun TitleInputDialog(
    title: String,
    label: String,
    initialValue: String,
    error: TitleError?,
    maxCodePoints: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            AppTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(text = label) },
                isError = error != null,
                supportingText = error?.let { current ->
                    {
                        Text(
                            text = titleErrorText(current, maxCodePoints),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ConfirmAction(onClick = { onConfirm(value) }) {
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

/** 通用确认对话框(删除范围提示等,docs/01 §4:先提示范围)。 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onConfirm,colors=androidx.compose.material3.ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error.copy(alpha=.12f),contentColor=MaterialTheme.colorScheme.error)) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            QuietTextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(end = Spacing.s4),
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
