package com.qingling.writing.feature.bookshelf
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qingling.writing.core.design.LocalMotionEnabled
import com.qingling.writing.feature.planning.InspirationPage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(onOpenWorks:()->Unit,onOpenBook:(String)->Unit,onOpenSettings:()->Unit,modifier:Modifier=Modifier,
    onOpenBackup:()->Unit=onOpenSettings,onOpenSource:((String,Int,Int)->Unit)?=null,initialPage:Int=0) {
    val pager=rememberPagerState(initialPage=initialPage,pageCount={2})
    val scope=rememberCoroutineScope();val motion=LocalMotionEnabled.current
    fun go(page:Int){scope.launch{if(motion)pager.animateScrollToPage(page,animationSpec=com.qingling.writing.core.design.AppMotion.panelSpring())else pager.scrollToPage(page)}}
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(pager,modifier=Modifier.weight(1f).fillMaxWidth(),beyondViewportPageCount=1) {page ->
            val offset=((pager.currentPage-page)+pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f,1f)
            Box(Modifier.fillMaxSize().graphicsLayer{if(motion){alpha=1f-.45f*offset;translationX=if(page==0)offset*size.width*.08f else 0f}}) {
                if(page==0){
                    HomeDashboard(onOpenWorks,onOpenBook,onOpenSettings,onOpenBackup=onOpenBackup,onOpenSource=onOpenSource,onShowInspiration={go(1)})
                }else InspirationPage(onOpenSource={id,at,end->onOpenSource?.invoke(id,at,end)},onOpenSettings=onOpenSettings)
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(40.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            listOf("主页","灵感").forEachIndexed{i,label->Box(Modifier.size(48.dp,40.dp).semantics{contentDescription=if(i==0)"主页"else"灵感页"}.clickable{go(i)},contentAlignment=Alignment.Center){Box(Modifier.size(if(pager.currentPage==i)22.dp else 8.dp,8.dp).background(if(pager.currentPage==i)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f),RoundedCornerShape(8.dp)))}}
        }
    }
}
