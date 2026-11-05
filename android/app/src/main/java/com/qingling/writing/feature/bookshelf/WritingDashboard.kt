package com.qingling.writing.feature.bookshelf
import com.qingling.writing.core.design.QuietTextButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.core.design.*
import com.qingling.writing.core.model.*
import kotlinx.coroutines.delay
import java.time.*
import java.util.Locale
import kotlin.math.abs

@Composable internal fun currentLocalDate(): State<LocalDate> = produceState(LocalDate.now()) {
    while(true) { value=LocalDate.now();delay(60_000) }
}
internal fun formatWords(value: Long) = String.format(Locale.CHINA,"%,d",value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WritingDashboard(onDismiss: () -> Unit,preferredBookId: String? = null) {
    val container=LocalAppContainer.current
    val model: DashboardViewModel = viewModel(key="writing-dashboard",factory=viewModelFactory {
        initializer { DashboardViewModel(container.writingRepository,container.writingProgress,container.settingsStore) }
    })
    val days by model.days.collectAsStateWithLifecycle();val bookDays by model.bookDays.collectAsStateWithLifecycle()
    val books by model.books.collectAsStateWithLifecycle();val goals by model.goals.collectAsStateWithLifecycle()
    val daily by model.dailyGoal.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle();val today by currentLocalDate()
    val inputDays by model.inputDays.collectAsStateWithLifecycle()
    val inputToday=inputDays.find {it.day==today.toString()}
    var period by rememberSaveable { mutableStateOf(ChartPeriod.DAY.name) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedBook by rememberSaveable { mutableStateOf(preferredBookId) }
    var chooseBook by remember { mutableStateOf(false) }
    var goalSheet by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var selectedBucket by rememberSaveable(period) { mutableIntStateOf(-1) }
    LaunchedEffect(books) { if(books.none {it.id==selectedBook}) selectedBook=books.firstOrNull()?.id }
    val book=books.find { it.id==selectedBook };val target=goals.find { it.bookId==selectedBook }?.targetWords ?: 0
    val stats=remember(days,today) { writingStats(days,today) }
    val month=YearMonth.parse(monthText)
    val calendar=remember(days,month) { calendarActivity(days,month) }
    val buckets=remember(days,today,period) { writingBuckets(days,today,ChartPeriod.valueOf(period)) }
    val forecast=remember(book?.wordCount,target,bookDays,selectedBook,today) {
        bookForecast(book?.wordCount ?: 0,target,bookDays.filter { it.bookId==selectedBook },today)
    }
    Dialog(onDismissRequest={ if(!busy) onDismiss() },properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
        BackHandler(enabled=busy) { }
        Scaffold(Modifier.fillMaxSize().imePadding(),topBar={ TopAppBar(title={Text("统计与目标",fontSize=19.sp)},
            navigationIcon={BackButton(onClick={if(!busy) onDismiss()})},actions={
                QuietTextButton(onClick={showInfo=true}) {Text("数据说明",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }) }) { padding ->
            LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.height(IntrinsicSize.Min)) {
                        StatCard(Modifier.weight(1f).fillMaxHeight()) {
                            StatCell(StatData("今日净增",formatWords(stats.todayNet),"字"),Modifier.fillMaxWidth())
                            Caption(if(daily>0) "目标 ${formatWords(daily.toLong())} · "+if(stats.todayNet>=daily) "已达成" else "还差 ${formatWords(daily-stats.todayNet)}" else "每日目标已关闭")
                            Caption("输入新增 ${formatWords(inputToday?.insertedWords ?: 0)} · 编辑 ${(inputToday?.activeMillis ?: 0)/60000} 分钟")
                        }
                        StatCard(Modifier.weight(1f).fillMaxHeight()) {
                            StatCell(StatData("连续写作",stats.streak.toString(),"天"),Modifier.fillMaxWidth())
                            WeekDots((0L..6L).map { offset-> days.any {it.day==today.minusDays(6-offset).toString() && it.edits>0} })
                            Caption("本周净增 ${formatWords(stats.weekNet)} 字")
                        }
                    }
                }
                item { StatCard {
                    Text("净增趋势",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    AppSegments(ChartPeriod.entries.map {it.label},ChartPeriod.valueOf(period).ordinal,{period=ChartPeriod.entries[it].name})
                    NetBars(buckets,selectedBucket,daily.toLong(),ChartPeriod.valueOf(period)) {selectedBucket=it}
                    buckets.getOrNull(selectedBucket)?.let { point->
                        val rows=inputDays.filter {it.day>=point.from.toString() && it.day<=point.through.toString()}
                        Caption("${point.from} 至 ${point.through} · 净增 ${formatWords(point.netWords)} 字\n输入新增 ${formatWords(rows.sumOf{it.insertedWords})} 字 · 编辑 ${rows.sumOf{it.activeMillis}/60000} 分钟")
                    }
                } }
                item { StatCard {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("活动日历",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                        QuietTextButton(onClick={monthText=month.minusMonths(1).toString();selectedDate=month.minusMonths(1).atDay(1).toString()},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(44.dp)) {Text("‹",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=23.sp)}
                        Text("${month.year} 年 ${month.monthValue} 月",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        QuietTextButton(enabled=month < YearMonth.from(today),onClick={monthText=month.plusMonths(1).toString();selectedDate=month.plusMonths(1).atDay(1).toString()},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(44.dp)) {Text("›",fontSize=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(month<YearMonth.from(today)) 1f else .3f))}
                    }
                    ActivityCalendar(calendar,month,today,selectedDate,daily.toLong()) { selectedDate=it.toString() }
                    HeatLegend()
                    val day=calendar.find { it.date.toString()==selectedDate }
                    if(day!=null) Caption("${day.date} · "+if(day.edits==0L) "尚无保存记录" else "净增 ${formatWords(day.netWords)} 字 · ${day.edits} 次保存")
                } }
                item { StatCard {
                    Row(Modifier.fillMaxWidth().clickable(enabled=books.isNotEmpty()) {chooseBook=true},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        book?.let {SeedBookCover(it.title,it.id,Modifier.size(34.dp,48.dp))}
                        Text((book?.title ?: "选择作品")+" · 整书目标",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
                        Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    book?.let { b ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Caption("${formatWords(b.wordCount)} / ${if(target>0) formatWords(target) else "未设置"} 字")
                            QuietTextButton(onClick={goalSheet=b.id}) {Text("调整目标 ›",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        QuietProgress(forecast.progress)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(if(target>0) "完成 ${String.format(Locale.CHINA,"%.1f",forecast.progress*100)}%" else "设置字数目标",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                            Caption(if(forecast.reached) "已达到目标" else if(forecast.observedDays<7) "写满 7 天后估算完稿日" else forecast.estimatedDate?.let {"预计 $it"} ?: "暂无正向速率")
                        }
                    }
                } }
                item { StatCard {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {goalSheet="daily"},verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text("每日目标",style=MaterialTheme.typography.titleMedium);Caption("按每日净增计算，改完即生效")}
                        Text("${formatWords(daily.toLong())} 字  ›",Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest,RoundedCornerShape(12.dp)).padding(12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
                    }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let {Caption(it)}
                } }
            }
        }
        if(chooseBook) ModalBottomSheet(onDismissRequest={chooseBook=false},containerColor=MaterialTheme.colorScheme.surfaceContainer) {
            Text("选择目标作品",Modifier.padding(horizontal=24.dp),style=MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.heightIn(max=440.dp).navigationBarsPadding()) {items(books.size) { i->
                Row(Modifier.fillMaxWidth().clickable {selectedBook=books[i].id;chooseBook=false}.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SeedBookCover(books[i].title,books[i].id,Modifier.size(32.dp,44.dp));Text(books[i].title)
                }
            }}
        }
        goalSheet?.let { id-> GoalSheet(if(id=="daily") "每日净增目标" else "整书字数目标",if(id=="daily") daily.toLong() else target,
            if(id=="daily") 1_000_000 else 10_000_000,onDismiss={goalSheet=null},message=message,busy=busy) { value->
                if(id=="daily") model.saveDaily(value) else model.saveGoal(id,value)
            } }
        if(showInfo) ModalBottomSheet(onDismissRequest={showInfo=false},containerColor=MaterialTheme.colorScheme.surfaceContainer) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {Text("数据说明",style=MaterialTheme.typography.titleLarge)}
                item {Text("净增以成功保存为准，删除、撤销可为负数。输入新增从 0.21 起记录观察到的已确认文字（含粘贴，不含撤销、重做、排版及未确认的输入法组合文字）。有效编辑时长累加前台连续两次文字变更之间不超过 30 秒的间隔，超时和后台不计；不是应用打开时长。导入、恢复和批量操作不增加输入量。旧记录不回填，活动统计不随备份复制。")}
                item {Text("速率按本书最近最多 14 个自然日的已保存净增计算，至少记录 7 个自然日后展示估算；周从周一开始。单书速率从 0.20 起记录，不把旧全局记录分摊给作品。没有正向速度或跨度过大时不估算；预计日期仅供参考。整书目标会随备份保留，活动统计不会随恢复副本重复导入。")}
                item {Caption("累计输入新增 ${formatWords(inputDays.sumOf{it.insertedWords})} 字 · 有效编辑 ${inputDays.sumOf{it.activeMillis}/60000} 分钟")}
            }
        }
    }
}

@Composable internal fun Caption(text: String) { Text(text,fontSize=12.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable internal fun StatCard(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable internal fun WeekDots(active: List<Boolean>) {
    val ink=MaterialTheme.colorScheme.primary;val dim=MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.width(104.dp).height(12.dp)) {active.forEachIndexed {i,on->drawCircle(if(on) ink else dim,3.dp.toPx(),Offset(size.width*(i+.5f)/active.size,size.height/2),style=if(on) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.dp.toPx()))}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalSheet(title: String,initial: Long,max: Long,onDismiss:()->Unit,message: String?,busy: Boolean,onChange:(String)->Unit) {
    var input by rememberSaveable {mutableStateOf(initial.toString())}
    val valid=input.toLongOrNull()?.let {it in 0..max}==true
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            AppTextField(input,{value->input=value.filter(Char::isDigit).take(9);if(input.toLongOrNull()?.let {it in 0..max}==true) onChange(input)},singleLine=true,
                label={Text("0 为关闭目标 · 上限 ${formatWords(max)} 字")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=!valid)
            Caption(if(!valid) "请输入 0 到 ${formatWords(max)} 的整数；当前输入尚未保存" else if(busy) "正在保存……" else message ?: "修改有效数字后自动保存")
        }
    }
}
@Composable private fun NetBars(points: List<WritingBucket>,selected: Int,dailyGoal:Long,period:ChartPeriod,onSelect:(Int)->Unit) {
    val positive=maxOf(0.0,points.maxOfOrNull {it.netWords.toDouble()} ?: 0.0)
    val negative=abs(minOf(0.0,points.minOfOrNull {it.netWords.toDouble()} ?: 0.0))
    val goals=points.map { bucketGoal(it,dailyGoal,period) }
    val upper=maxOf(positive,(goals.maxOrNull() ?: 0.0)*1.15)
    val scale=(upper+negative).coerceAtLeast(1.0)
    val green=MaterialTheme.colorScheme.primary;val red=MaterialTheme.colorScheme.error;val line=MaterialTheme.colorScheme.outlineVariant
    if(dailyGoal>0) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
        Text("目标 · " + when(period) { ChartPeriod.DAY -> "每日";ChartPeriod.WEEK -> "每日 × 7";ChartPeriod.MONTH -> "每日 × 已过天数" },fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        points.forEachIndexed {index,point->Column(Modifier.weight(1f).clickable(role=Role.Button) {onSelect(index)}.semantics {contentDescription="${point.from} 至 ${point.through}，净增 ${point.netWords} 字"},horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(abs(point.netWords.toDouble())>=10000) String.format(Locale.CHINA,"%.1f万",point.netWords/10000.0) else point.netWords.toString(),fontSize=10.sp,maxLines=1)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val baseline=if(upper+negative==0.0) size.height else (size.height*upper/scale).toFloat()
                drawLine(line,Offset(0f,baseline),Offset(size.width,baseline),1.dp.toPx())
                if(dailyGoal>0) {
                    val goalY=baseline-(size.height*goals[index]/scale).toFloat()
                    drawLine(green.copy(alpha=.55f),Offset(0f,goalY),Offset(size.width,goalY),1.dp.toPx(),
                        pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),6.dp.toPx())))
                }
                if(point.netWords!=0L) {
                    val h=(size.height*abs(point.netWords.toDouble())/scale).toFloat().coerceAtLeast(1f);val width=minOf(size.width*.7f,24.dp.toPx())
                    drawRoundRect((if(point.netWords>0) green else red).copy(alpha=if(index==selected || point.netWords.toDouble()==positive) 1f else .5f),Offset((size.width-width)/2,if(point.netWords>0) baseline-h else baseline),Size(width,h),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
            }
            Text(point.label,fontSize=10.sp,maxLines=1,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
    }
}
@Composable private fun heatColors(): List<Color> = if(MaterialTheme.colorScheme.background==DarkBackground)
    listOf(Color(0xFF232527),Color(0xFF3F4C46),Color(0xFF627A6C),Color(0xFF8BB199),Color(0xFFA7D6B7)) else
    listOf(MaterialTheme.colorScheme.surfaceContainerHighest,Color(0xFFDCE6DC),Color(0xFFBCD0BE),Color(0xFF8DB698),Color(0xFF5E8F73))
@Composable private fun HeatLegend() {
    val colors=heatColors()
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Caption("少");colors.forEach {Box(Modifier.size(12.dp,10.dp).background(it,RoundedCornerShape(3.dp)))};Caption("多")
        Spacer(Modifier.width(8.dp));Box(Modifier.size(12.dp,10.dp).background(MaterialTheme.colorScheme.error.copy(alpha=.25f),RoundedCornerShape(3.dp)));Caption("负净增")
    }
}
@Composable private fun ActivityCalendar(days: List<CalendarActivity>,month: YearMonth,today: LocalDate,selected: String,dailyGoal:Long,onSelect:(LocalDate)->Unit) {
    val first=month.atDay(1).dayOfWeek.value-1;val rows=(first+days.size+6)/7
    val colors=heatColors();val ink=MaterialTheme.colorScheme.primary;val danger=MaterialTheme.colorScheme.error
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row {listOf("一","二","三","四","五","六","日").forEach {Box(Modifier.weight(1f).height(24.dp),contentAlignment=Alignment.Center) {Caption(it)}}}
        repeat(rows) {week->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {repeat(7) {col->
            val day=days.getOrNull(week*7+col-first)
            if(day==null) Spacer(Modifier.weight(1f).height(44.dp)) else {
                val level=activityHeatLevel(day.netWords,day.edits,dailyGoal)
                val bg=if(day.netWords<0) (if(MaterialTheme.colorScheme.background==DarkBackground) Color(0xFF4C3A3C) else danger.copy(alpha=.22f)) else colors[level]
                Box(Modifier.weight(1f).height(44.dp).clickable(enabled=day.date<=today,role=Role.Button) {onSelect(day.date)}
                    .semantics {contentDescription="${day.date}，净增 ${day.netWords} 字，${day.edits} 次保存"},contentAlignment=Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(34.dp).background(bg,RoundedCornerShape(9.dp))
                        .then(if(day.date==today) Modifier.border(2.dp,ink,RoundedCornerShape(9.dp)) else if(day.date.toString()==selected) Modifier.border(1.dp,MaterialTheme.colorScheme.onSurfaceVariant,RoundedCornerShape(9.dp)) else Modifier),contentAlignment=Alignment.Center) {
                        Text(day.date.dayOfMonth.toString(),fontSize=12.sp,color=if(level>=3) Color(0xFF17301F) else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(day.netWords<0) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=3.dp).size(4.dp).background(danger,RoundedCornerShape(2.dp)))
                    }
                }
            }
        }}}
    }
}
