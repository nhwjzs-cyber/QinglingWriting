package com.qingling.writing.feature.settings
import com.qingling.writing.core.design.QuietTextButton
import com.qingling.writing.core.design.AppSecondaryButton
import com.qingling.writing.core.design.AppTextField

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine

@Composable
fun WritingAppearanceScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val saved by produceState<Pair<EditorAppearance,String>?>(null) { value = combine(container.settingsStore.appearance,container.settingsStore.customFontId) {a,id->a to id}.first() }
    val initial = saved
    if (initial == null) CenterMessage(stringResource(R.string.editor_loading))
    else AppearanceForm(initial.first,initial.second, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceForm(initial: EditorAppearance,initialFont: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var draft by remember(initial) { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var draftFont by remember(initialFont) {mutableStateOf(initialFont)}
    var fontMessage by remember {mutableStateOf<String?>(null)}
    val fonts by container.importedFonts.fonts.collectAsStateWithLifecycle()
    val previewFamily=rememberFontFamily(draft.font,draftFont)
    val importFont=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) scope.launch {
        busy=true;fontMessage=null
        try {draftFont=container.importedFonts.import(uri);fontMessage="字体已导入，请检查预览后点右上角保存"}
        catch(e: CancellationException){throw e}
        catch(e: Exception){fontMessage=e.message ?: "字体导入失败，原设置保留"}
        finally {busy=false}
    }}
    val importImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = false
            try {
                val path = container.backgroundImages.importImage(uri)
                if (draft.imagePath != initial.imagePath) container.backgroundImages.remove(draft.imagePath)
                draft = draft.copy(imagePath = path)
            } catch (failure: CancellationException) { throw failure
            } catch (_: Exception) { error = true
            } finally { busy = false }
        }
    }
    val valid = draft.canvas != EditorCanvas.CUSTOM ||
        (normalizeHex(draft.backgroundHex) != null && normalizeHex(draft.textHex) != null)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.appearance_title)) },
            navigationIcon = { BackButton(onBack) },
            actions = {
                Button(enabled = !busy && valid, onClick = {
                    scope.launch {
                        busy = true
                        error = false
                        try {
                            container.settingsStore.setAppearance(draft,draftFont)
                            if (initial.imagePath != draft.imagePath) container.backgroundImages.remove(initial.imagePath)
                            onBack()
                        } catch (failure: CancellationException) { throw failure
                        } catch (_: Exception) { error = true
                        } finally { busy = false }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.appearance_scope), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card {
                WritingCanvas(draft.normalized(), Modifier.fillMaxWidth().height(174.dp)) {
                    Text(stringResource(R.string.appearance_sample), modifier = Modifier.padding(draft.gutter.dp),
                        color = draft.normalized().textColor(), fontSize = draft.fontSize.sp,
                        lineHeight = (draft.fontSize * draft.lineHeightPercent / 100f).sp,
                        fontFamily = previewFamily, maxLines = 4)
                }
            }
            AppearanceSlider(stringResource(R.string.appearance_font_size, draft.fontSize), draft.fontSize.toFloat(), 14f..32f, 17) {
                draft = draft.copy(fontSize = it.roundToInt())
            }
            AppearanceSlider(stringResource(R.string.appearance_line_height, draft.lineHeightPercent), draft.lineHeightPercent.toFloat(), 120f..220f, 19) {
                draft = draft.copy(lineHeightPercent = it.roundToInt())
            }
            AppearanceSlider(stringResource(R.string.appearance_gutter, draft.gutter), draft.gutter.toFloat(), 12f..40f, 13) {
                draft = draft.copy(gutter = it.roundToInt())
            }
            Text(stringResource(R.string.appearance_font), style = MaterialTheme.typography.titleMedium)
            EditorFont.entries.forEach { font ->
                ChoiceRow(stringResource(when (font) {
                    EditorFont.SYSTEM -> R.string.appearance_font_system
                    EditorFont.SERIF -> R.string.appearance_font_serif
                    EditorFont.MONOSPACE -> R.string.appearance_font_mono
                }), draftFont.isEmpty() && draft.font == font) { draftFont="";draft = draft.copy(font = font) }
            }
            fonts.forEach {font->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {ChoiceRow(font.name,draftFont==font.id) {if(!busy) draftFont=font.id};Text("${font.bytes/1024} KB",style=MaterialTheme.typography.bodySmall)}
                    QuietTextButton(enabled=!busy,onClick={scope.launch {
                        busy=true
                        try {container.settingsStore.clearFontIfSelected(font.id);container.importedFonts.remove(font.id);if(draftFont==font.id)draftFont="";fontMessage="字体已移除，使用内置字体回退"}
                        catch(e: CancellationException){throw e}
                        catch(_: Exception){fontMessage="字体移除失败，请重试"}
                        finally {busy=false}
                    }}) {Text("移除")}
                }
            }
            AppSecondaryButton(enabled=!busy,onClick={importFont.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) {Text("导入 TTF / OTF 字体")}
            Text("最多 10 款、每款 20 MB；只改变本机编辑显示。字体不进入作品备份或 DOCX，文件缺失时回退内置字体。请确认字体的使用授权。",style=MaterialTheme.typography.bodySmall)
            fontMessage?.let {Text(it,style=MaterialTheme.typography.bodyMedium)}
            HorizontalDivider()
            Text(stringResource(R.string.appearance_background), style = MaterialTheme.typography.titleMedium)
            EditorCanvas.entries.forEach { canvas ->
                ChoiceRow(stringResource(when (canvas) {
                    EditorCanvas.THEME -> R.string.appearance_theme
                    EditorCanvas.INK -> R.string.appearance_ink
                    EditorCanvas.PAPER -> R.string.appearance_paper
                    EditorCanvas.FOREST -> R.string.appearance_forest
                    EditorCanvas.CUSTOM -> R.string.appearance_custom
                }), draft.canvas == canvas) { draft = draft.copy(canvas = canvas) }
            }
            if (draft.canvas == EditorCanvas.CUSTOM) {
                AppTextField(value = draft.backgroundHex, onValueChange = { draft = draft.copy(backgroundHex = it.take(7)) },
                    label = { Text(stringResource(R.string.appearance_background_hex)) }, singleLine = true,
                    isError = normalizeHex(draft.backgroundHex) == null, modifier = Modifier.fillMaxWidth())
                AppTextField(value = draft.textHex, onValueChange = { draft = draft.copy(textHex = it.take(7)) },
                    label = { Text(stringResource(R.string.appearance_text_hex)) }, singleLine = true,
                    isError = normalizeHex(draft.textHex) == null, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.appearance_hex_hint), style = MaterialTheme.typography.bodySmall)
                if (valid) {
                    val bg = draft.normalized().backgroundColor().luminance()
                    val fg = draft.normalized().textColor().luminance()
                    val contrast = (maxOf(bg, fg) + 0.05f) / (minOf(bg, fg) + 0.05f)
                    if (contrast < 4.5f) Text(stringResource(R.string.appearance_low_contrast),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            AppSecondaryButton(onClick = { importImage.launch(arrayOf("image/*")) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.appearance_choose_image)) }
            if (draft.imagePath.isNotEmpty()) {
                Text(stringResource(R.string.appearance_image_local), style = MaterialTheme.typography.bodySmall)
                AppearanceSlider(stringResource(R.string.appearance_image_dim, draft.imageDimPercent),
                    draft.imageDimPercent.toFloat(), 0f..90f, 17) { draft = draft.copy(imageDimPercent = it.roundToInt()) }
                QuietTextButton(onClick = { draft = draft.copy(imagePath = "") }, enabled = !busy) {
                    Text(stringResource(R.string.appearance_remove_image))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error) Text(stringResource(R.string.appearance_error), color = MaterialTheme.colorScheme.error)
            QuietTextButton(onClick = { draftFont="";draft = EditorAppearance() }, enabled = !busy) { Text(stringResource(R.string.appearance_reset)) }
        }
    }
}

@Composable
private fun AppearanceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
