package com.xjie.app.feature.healthdata

import com.xjie.app.core.model.HealthDocument
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentListDataPolicyTest {
    @Test
    fun examRouteNeverLoadsRendersOrDeletesLegacyDocuments() {
        val legacyExam = HealthDocument(id = "legacy-exam", doc_type = "exam")

        assertFalse(DocumentListDataPolicy.usesLegacyDocuments("exam"))
        assertEquals(
            emptyList<HealthDocument>(),
            DocumentListDataPolicy.visibleLegacyDocuments("exam", listOf(legacyExam)),
        )
        assertFalse(DocumentListDataPolicy.canDeleteLegacyDocument("exam"))

        assertTrue(DocumentListDataPolicy.usesLegacyDocuments("record"))
        assertEquals(
            listOf(legacyExam),
            DocumentListDataPolicy.visibleLegacyDocuments("record", listOf(legacyExam)),
        )
        assertTrue(DocumentListDataPolicy.canDeleteLegacyDocument("record"))
    }

    @Test
    fun activeExamRouteUsesHealthReportTitle() {
        val candidates = listOf(
            Path.of("src/main/java/com/xjie/app/navigation/MainScaffold.kt"),
            Path.of("Android/app/src/main/java/com/xjie/app/navigation/MainScaffold.kt"),
        )
        val sourcePath = candidates.first(Files::exists)
        val source = String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8)
        val examRoute = Regex(
            "docType\\s*=\\s*\"exam\"[\\s\\S]{0,160}title\\s*=\\s*\"([^\"]+)\"",
        ).find(source)

        assertEquals("健康报告", examRoute?.groupValues?.get(1))
    }
}
