package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Test

class XAgeShellStateTest {
    @Test
    fun capsuleAndSwipeResolveThroughTheSamePageIndex() {
        val capsuleSelection = XAgeShellState().selecting(XAgeSection.Chat)
        val swipeSelection = XAgeShellState(page = 1)

        assertEquals(capsuleSelection, swipeSelection)
        assertEquals(XAgeSection.Chat, capsuleSelection.selectedSection)
        assertEquals(3, XAgeShellState.pageCount)
    }

    @Test
    fun returningFromChildRestoresTheOriginPageAndInvalidPagesFailClosed() {
        val origin = XAgeShellState().selecting(XAgeSection.XAge)

        assertEquals(origin, origin.returningFromChild())
        assertEquals(XAgeSection.XAge, origin.returningFromChild().selectedSection)
        assertEquals(XAgeSection.Data, XAgeShellState(page = -9).selectedSection)
        assertEquals(XAgeSection.XAge, XAgeShellState(page = 99).selectedSection)
    }
}
