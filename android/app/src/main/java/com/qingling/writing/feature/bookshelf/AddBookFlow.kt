package com.qingling.writing.feature.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.focus.focusRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookFlow(onDismiss:()->Unit,onCreated:(String)->Unit,initialMode:Int=1) {
    val container=LocalAppContainer.current
    val scope=rememberCoroutineScope()
    var step by rememberSaveable {mutableStateOf(initialMode.coerceIn(1,3))}
    var title by rememberSaveable {mutableStateOf("")}
    var author by rememberSaveable {mutableStateOf("")}
    var description by rememberSaveable {mutableStateOf("")}
    var color by rememberSaveable {mutableIntStateOf(0)}
    var motif by rememberSaveable {mutableIntStateOf(0)}
    var descriptionOpen by rememberSaveable {mutableStateOf(false)}
    var defaultsReady by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(Unit){if(!defaultsReady){author=container.settingsStore.lastAuthor.first();val cover=container.settingsStore.lastCoverStyle.first();color=cover.color;motif=cover.motif;defaultsReady=true}}
    var createdId by rememberSaveable {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    fun close(){if(!busy) {if(step==1 && title.isNotBlank() && createdId==null)discard=true else onDismiss()}}
    if(step==1 && defaultsReady) WritingSheet("新建书",scopeLabel="打开就能写",onDismiss=::close,canDismiss=!busy,footer={
        Button(enabled=!busy&&title.isNotBlank(),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),onClick={busy=true;error=null;scope.launch {
            try {
                val id=createdId ?: container.writingRepository.createBook(GuestSpace.ID,title,author,description).bookId
                createdId=id
                container.settingsStore.setCoverStyle(id,CoverStyle(color,motif))
                container.settingsStore.setLastBookDefaults(author,CoverStyle(color,motif))
                onDismiss();onCreated(id)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:com.qingling.writing.domain.TitleValidationException){error="书名须为 1～80 字，不能换行"}
            catch(e:Exception){error=if(createdId!=null) "作品已创建，偏好未保存。可重试，不会重复建书。" else "创建未完成，请检查存储空间后重试"}
            finally{busy=false}
        }},modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(busy)"正在创建…"else"创建并打开第一章")}
    }) {
        val focus=remember{androidx.compose.ui.focus.FocusRequester()}
        val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit){kotlinx.coroutines.delay(180);focus.requestFocus();keyboard?.show()}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                SeedBookCover(title.ifBlank{"新作品"},"preview",Modifier.requiredSize(84.dp,120.dp).semantics{contentDescription="新书封面预览"},CoverStyle(color,motif))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("封面颜色",style=MaterialTheme.typography.labelSmall)
                    Row {listOf(0xFF546E5CL,0xFF617282L,0xFF806854L,0xFF6E6E5AL,0xFF6E625EL).forEachIndexed{i,c->
                        Box(Modifier.weight(1f).height(44.dp).semantics{contentDescription="封面颜色 "+(i+1)}.clickable(enabled=!busy&&createdId==null){color=i},contentAlignment=Alignment.Center) {
                            Box(Modifier.size(27.dp).background(if(i==color)MaterialTheme.colorScheme.primary else Color.Transparent,CircleShape).padding(3.dp).background(Color(c),CircleShape),contentAlignment=Alignment.Center){if(i==color)Text("✓",style=MaterialTheme.typography.labelSmall,color=Color.White)}
                        }
                    }}
                    AppSegments(listOf("枝叶","圆月","横线"),motif,{if(!busy&&createdId==null)motif=it})
                }
            }
            AppTextField(title,{title=it;error=null},enabled=!busy&&createdId==null,label={Text("书名")},supportingText={Text(title.codePointCount(0,title.length).toString()+" / 80")},singleLine=true,modifier=Modifier.fillMaxWidth().focusRequester(focus))
            AppTextField(author,{author=it},enabled=!busy&&createdId==null,label={Text("作者")},singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={descriptionOpen=!descriptionOpen}){Text(if(descriptionOpen)"收起简介"else"简介 · 点开再写 — 不填也能开写",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(descriptionOpen)AppTextField(description,{description=it},enabled=!busy&&createdId==null,label={Text("简介（可选）")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
            Text("✓ 创建时自动生成 第一卷 · 第一章，打开就能写",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            TextButton(enabled=!busy&&createdId==null,onClick={step=2}){Text("导入 TXT 稿件 ›")}
            TextButton(enabled=!busy&&createdId==null,onClick={step=3}){Text("从备份恢复 ›")}
            Spacer(Modifier.height(12.dp))
        }
    }
    if(step==2) com.qingling.writing.feature.recovery.TxtImportDialog(onDismiss={if(initialMode==2)onDismiss()else step=1},onOpenBook={id->onDismiss();onCreated(id)})
    if(step==3) Dialog(onDismissRequest={if(initialMode==3)onDismiss()else step=1},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
        com.qingling.writing.feature.recovery.BackupScreen(onBack={if(initialMode==3)onDismiss()else step=1})
    }
    if(discard) ConfirmDialog("关闭新书表单？","书名和简介还没有保存，关闭后需要重新填写。","关闭",onConfirm=onDismiss,onDismiss={discard=false})
}
