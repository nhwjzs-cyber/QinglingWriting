package com.qingling.writing.feature.bookshelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.R
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

private data class ResumeExcerpt(val title: String, val text: String)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeDashboard(onOpenWorks: ()->Unit, onOpenBook: (String)->Unit, onOpenSettings: ()->Unit,
    modifier: Modifier=Modifier, onOpenBackup: ()->Unit=onOpenSettings,
    onOpenSource: ((String,Int,Int)->Unit)?=null, onShowInspiration: ()->Unit={}) {
    val container=LocalAppContainer.current
    val model: BookshelfViewModel=viewModel(factory=viewModelFactory { initializer { BookshelfViewModel(container.writingRepository,container.writingProgress) } })
    val books by model.books.collectAsStateWithLifecycle()
    val days by model.writingDays.collectAsStateWithLifecycle()
    val today by currentLocalDate()
    val dailyGoal by container.settingsStore.dailyGoal.collectAsStateWithLifecycle(initialValue=2000)
    val activeBooks=books.filterNot { it.archived }
    val recent=activeBooks.firstOrNull { it.continueChapterId!=null }
    val stats=remember(days,today) { writingStats(days,today) }
    val backup by container.autoBackupStore.state.collectAsStateWithLifecycle(initialValue=AutoBackupState())
    var showStats by rememberSaveable { mutableStateOf(false) }
    var createMode by rememberSaveable {mutableIntStateOf(1)}
    var create by remember { mutableStateOf(false) }
    val recovery by container.recoveryDrafts.entries.collectAsStateWithLifecycle()
    val recoveryProblem by container.recoveryDrafts.problem.collectAsStateWithLifecycle()
    var showRecovery by rememberSaveable {mutableStateOf(false)}
    val excerpt by produceState<ResumeExcerpt?>(null,recent?.id,recent?.continueChapterId,recent?.updatedAt) {
        value=null
        val id=recent?.continueChapterId
        if(id!=null) value=withContext(Dispatchers.IO) {
            try {
                val doc=container.writingRepository.readChapter(id)
                val position=container.writingRepository.readEditorPosition(id)?.selectionEnd ?: doc.body.length
                val at=position.coerceIn(0,doc.body.length)
                var start=(at-42).coerceAtLeast(0)
                if(start>0 && start<doc.body.length && Character.isLowSurrogate(doc.body[start])) start--
                val end=(start+90).coerceAtMost(doc.body.length)
                ResumeExcerpt(doc.title,doc.body.substring(start,end).replace('\n',' ').replace('\r',' ').trim())
            } catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(_:Exception) { ResumeExcerpt("最近编辑的章节","") }
        }
    }
    Scaffold(modifier.fillMaxSize(),topBar={
        TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_brand_mark),null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("轻灵写作",fontSize=19.sp,fontWeight=FontWeight.Bold)
        }},actions={
            ChromeAction(WritingIcons.Bulb,"灵感",onShowInspiration)
            ChromeAction(Icons.Filled.Search,"搜索作品",onOpenWorks)
            ChromeAction(Icons.Filled.Settings,"设置",onOpenSettings)
            Spacer(Modifier.width(12.dp))
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal=26.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            if(recovery.isNotEmpty() || recoveryProblem!=null) Card(onClick={showRecovery=true},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)){Text(if(recoveryProblem!=null) "草稿保护需要处理" else "有 "+recovery.size+" 份未保存草稿",style=MaterialTheme.typography.titleSmall);Text("点此查看、继续编辑或导出保护副本",style=MaterialTheme.typography.bodySmall)}}
            recent?.let { book ->
                Card(onClick={onOpenBook(book.id)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        SeedBookCover(book.title,book.id,Modifier.size(50.dp,82.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("继续写作",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            Text(book.title,fontFamily=FontFamily.Serif,fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(excerpt?.title ?: "正在读取上次章节…",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(excerpt?.text?.ifBlank { "还没有正文，写下第一句。" } ?: "",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.8f),maxLines=1,overflow=TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("上次编辑 · "+relativeEditTime(book.updatedAt),Modifier.weight(1f),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("继续写 ›",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clickable { showStats=true }) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
                Row(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("今日净增" to (formatWords(stats.todayNet)+" 字"),"本周净增" to (formatWords(stats.weekNet)+" 字"),"连续写作" to (stats.streak.toString()+" 天")).forEach { (label,value) ->
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                            Text(value,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                        }
                    }
                    val start=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val week=(0L..6L).map { day -> days.firstOrNull { it.day==start.plusDays(day).toString() }?.netWords?.coerceAtLeast(0) ?: 0L }
                    val actualPeak=week.maxOrNull() ?: 0L;val peak=maxOf(actualPeak,dailyGoal.toLong());val peakIndex=if(actualPeak>0)week.indexOf(actualPeak)else-1
                    val ink=MaterialTheme.colorScheme.primary;val quiet=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f)
                    Column(Modifier.width(58.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("本周",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                            week.forEachIndexed { i,value ->
                                val height=if(peak==0L)3.dp.toPx() else (size.height*value/peak).coerceAtLeast(3.dp.toPx())
                                drawRoundRect(if(i==peakIndex)ink else quiet,Offset(size.width*i/7,size.height-height),Size(size.width/10,height),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            }
                        }
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            }
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("最近作品",Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    TextButton(onClick=onOpenWorks,contentPadding=PaddingValues(horizontal=0.dp)) { Text("全部 "+books.size+" 部 ›",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if(activeBooks.isNotEmpty()) Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                    Column {
                        activeBooks.take(5).forEachIndexed { index,book ->
                            Row(Modifier.fillMaxWidth().heightIn(min=84.dp).clickable { onOpenBook(book.id) }.padding(horizontal=14.dp,vertical=10.dp),
                                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                SeedBookCover(book.title,book.id,Modifier.size(34.dp,54.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        Text(book.title,Modifier.weight(1f),fontFamily=FontFamily.Serif,fontSize=16.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        Text(relativeEditTime(book.updatedAt),Modifier.padding(start=8.dp),fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.75f))
                                    }
                                    Text("共 "+book.chapterCount+" 章 · "+formatWords(book.wordCount)+" 字 · 已完成 "+book.completedCount+" 章",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                                }
                            }
                            if(index<activeBooks.take(5).lastIndex) HorizontalDivider(Modifier.padding(horizontal=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))
                        }
                    }
                }
                else Text(if(books.isEmpty())"还没有作品，创建一本书开始写作。"else"作品均已归档，可从全部作品查看。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick={createMode=1;create=true},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(15.dp),
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurfaceVariant)) { Text("＋ 创建一本书",fontSize=14.sp) }
            }
            if(books.isEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick={createMode=2;create=true}){Text("导入 TXT")};TextButton(onClick={createMode=3;create=true}){Text("从备份恢复")}}
            if(backup.error.isNotEmpty() || backup.scheduleError.isNotEmpty()) TextButton(onClick=onOpenBackup) { Text("备份需关注 · 打开备份设置 ›",color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
        }
    }
    if(showRecovery) com.qingling.writing.feature.planning.RecoveryDraftsSheet{showRecovery=false}
    if(create) AddBookFlow(onDismiss={create=false},onCreated=onOpenBook,initialMode=createMode)
    if(showStats) WritingDashboard(onDismiss={showStats=false},preferredBookId=recent?.id)
}
