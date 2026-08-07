package com.xjie.app.feature.chat

import com.xjie.app.core.model.ChatInteractionRoute
import com.xjie.app.core.model.ChatResponse
import com.xjie.app.core.model.Citation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCitationReplayParityTest {
    @Test
    fun explicitMarkersOnlyPreserveBackendPositionsWithoutTopicGuessingOrRenumbering() {
        val message = ChatMessageItem(
            id = "server-18",
            role = "assistant",
            content = "结论明确引用第二条证据[2]。",
            analysis = "补充分析还引用第三条[3]。",
            citations = listOf(
                citation(1, "同主题但未引用"),
                citation(2, "第二条"),
                citation(3, "第三条"),
            ),
        )

        assertEquals(listOf(2, 3), message.relevantCitationReferences.map { it.number })
        assertEquals(listOf(2, 3), message.relevantCitationReferences.map { it.citation.claim_id })

        val noMarker = message.copy(content = "血糖主题与证据重合，但正文没有编号。", analysis = null)
        assertTrue(noMarker.relevantCitationReferences.isEmpty())
    }

    @Test
    fun deepAndIncompleteSummariesUseCompleteStructuredAnswer() {
        val complete = "**直接结论**\n鼻炎可影响睡眠，但是否缺氧需要客观检查确认。"
        val deepRoute = route(depth = "deep")

        assertEquals(
            complete,
            ChatPresentationPolicy.selectContent(
                ChatResponse(
                    summary = "可能有关，但",
                    analysis = complete,
                    answer_markdown = complete,
                    interaction_route = deepRoute,
                ),
            ),
        )
        assertEquals(
            complete,
            ChatPresentationPolicy.selectContent(
                ChatResponse(summary = "鼻炎可能影响睡眠，但[1]。", answer_markdown = complete),
            ),
        )
        assertTrue(ChatPresentationPolicy.looksIncomplete("**未闭合结论"))
        assertTrue(ChatPresentationPolicy.looksIncomplete("当前证据包括[1]。"))
        assertTrue(ChatPresentationPolicy.looksIncomplete("变化可能归因于：[2]"))
        assertFalse(ChatPresentationPolicy.looksIncomplete("**结论一****结论二**"))
        assertFalse(ChatPresentationPolicy.looksIncomplete("结论完整。[1]"))
    }

    @Test
    fun duplicateAnalysisIsSuppressedAndStudyContextIsLocalized() {
        val response = ChatResponse(
            summary = "完整回答",
            analysis = "## 详细分析：完整回答",
            answer_markdown = "完整回答",
        )
        val content = ChatPresentationPolicy.selectContent(response)

        assertNull(ChatPresentationPolicy.distinctAnalysis(response, content))
        assertNull(
            ChatPresentationPolicy.distinctAnalysis(
                response.copy(analysis = "**完整回答**"),
                content,
            ),
        )
        assertEquals(
            "随机对照试验",
            ChatPresentationPolicy.studyDesignDisplayText("randomized-controlled trial"),
        )
        assertEquals("前瞻性队列", ChatPresentationPolicy.studyDesignDisplayText("前瞻性队列"))
    }

    private fun citation(id: Int, claim: String) = Citation(
        claim_id = id,
        literature_id = id,
        claim_text = claim,
        evidence_level = "L1",
        short_ref = "Fixture $id",
        confidence = "high",
    )

    private fun route(depth: String) = ChatInteractionRoute(
        version = "2026-07-10",
        route_id = "llm.health.$depth",
        strategy = "llm",
        primary_intent = "causal_assessment",
        depth = depth,
        safety_level = "medium",
        subject_type = "self",
        needs_literature = true,
        max_followups = 1,
    )
}
