package com.qingling.writing.feature.editor

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SidePanelStateTest {
    private fun TestScope.panels(enabled:Boolean=true): SidePanelState {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(testScheduler.currentTime * 1_000_000)
            }
        }
        return SidePanelState(CoroutineScope(coroutineContext + clock)){enabled}
    }

    @Test fun openingStartsWithoutAnOppositePanelDelay() = runTest {
        val panel = panels()
        panel.open(SidePanelSide.LEFT)
        advanceTimeBy(100); runCurrent()
        assertTrue(panel.progress > 0f)
        advanceUntilIdle()
        assertEquals(1f, panel.progress, 0.001f)
    }

    @Test fun closingKeepsContentUntilAnimationFinishes() = runTest {
        val panel = panels()
        panel.open(SidePanelSide.LEFT); advanceUntilIdle()
        panel.close(); advanceTimeBy(80); runCurrent()
        assertEquals(SidePanelSide.LEFT, panel.side)
        assertTrue(panel.progress in 0f..0.999f)
        advanceUntilIdle()
        assertNull(panel.side)
    }

    @Test fun reopeningInterruptsCloseWithoutLateRemoval() = runTest {
        val panel = panels()
        panel.open(SidePanelSide.LEFT); advanceUntilIdle()
        panel.close(); advanceTimeBy(80); runCurrent()
        panel.open(SidePanelSide.RIGHT); advanceUntilIdle()
        assertEquals(SidePanelSide.RIGHT, panel.side)
        assertEquals(1f, panel.progress, 0.001f)
    }

    @Test fun draggingUsesActualWidthAndCancelsAnimation() = runTest {
        val panel = panels()
        panel.dragBy(60f, 240f, 200f)
        assertEquals(0.25f, panel.progress, 0.001f)
        panel.open(SidePanelSide.LEFT); advanceTimeBy(64); runCurrent()
        panel.beginDrag()
        panel.dragBy(-24f, 240f, 200f)
        val held = panel.progress
        advanceTimeBy(1000); runCurrent()
        assertEquals(held, panel.progress, 0.001f)
        panel.settle(0f); advanceUntilIdle()
    }

    @Test fun rightPanelFlingUsesReversedDirectionAndCancelledDragSettles() = runTest {
        val panel = panels()
        panel.dragBy(-20f, 240f, 200f)
        panel.settle(-1000f); advanceUntilIdle()
        assertEquals(SidePanelSide.RIGHT, panel.side)
        assertEquals(1f, panel.progress, 0.001f)
        panel.dragBy(180f, 240f, 200f)
        panel.settle(0f); advanceUntilIdle()
        assertNull(panel.side)
    }
    @Test fun reducedMotionUsesShortFadeBeforeCommitting() = runTest {
        val panel = panels(false)
        panel.open(SidePanelSide.LEFT)
        advanceTimeBy(64);runCurrent();assertTrue(panel.progress>0f&&panel.progress<1f)
        advanceUntilIdle()
        assertEquals(1f, panel.progress, 0f)
        assertEquals(SidePanelSide.LEFT, panel.side)
        assertTrue(panel.dismiss())
        assertNull(panel.side)
    }

    @Test fun oppositePanelWaitsForVisiblePanelToClose() = runTest {
        val panel = panels()
        panel.open(SidePanelSide.LEFT); advanceUntilIdle()
        panel.open(SidePanelSide.RIGHT)
        advanceTimeBy(80); runCurrent()
        assertEquals(SidePanelSide.LEFT, panel.side)
        assertTrue(panel.progress < 1f)
        advanceUntilIdle()
        assertEquals(SidePanelSide.RIGHT, panel.side)
        assertEquals(1f, panel.progress, 0.001f)
    }

    @Test fun finishingMotionCancelsOldCompletionAndKeepsLatestDestination() = runTest {
        val panel = panels()
        panel.open(SidePanelSide.LEFT); advanceTimeBy(60); runCurrent()
        panel.open(SidePanelSide.RIGHT)
        panel.finishMotion()
        advanceUntilIdle()
        assertEquals(SidePanelSide.RIGHT, panel.side)
        assertEquals(1f, panel.progress, 0f)
    }

}
