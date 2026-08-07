package com.xjie.app.feature.xage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XAgeQuickActionRegistryTest {
    @Test
    fun activeQuickActionsMatchTheCurrentIosFiveWithoutHiddenDuplicates() {
        val actions = XAgeQuickActionRegistry.activeActions

        assertEquals(listOf("饮食", "体重", "报告", "用药", "就医助手"), actions.map { it.title })
        assertEquals(listOf("meals", "weight", "reports", "medications", "medical"), actions.map { it.id })
        assertEquals(actions.size, actions.map { it.id }.distinct().size)
        assertEquals(actions.size, actions.map { it.destination }.distinct().size)
        assertTrue(actions.all { !it.destination.isNullOrBlank() })
        assertFalse(actions.any { it.id in setOf("mood", "health-plan", "data-manager") })
    }
}
