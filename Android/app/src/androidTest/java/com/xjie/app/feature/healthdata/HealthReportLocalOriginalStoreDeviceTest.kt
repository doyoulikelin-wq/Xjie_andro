package com.xjie.app.feature.healthdata

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthReportLocalOriginalStoreDeviceTest {
    @Test
    fun productionStorePersistsExactBytesOnlyBelowNoBackupRootOnApi35() = runBlocking {
        assertEquals(35, Build.VERSION.SDK_INT)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xfe.toByte(), 0xff.toByte())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        val accountScope = "account-${"a".repeat(64)}"
        val requestId = "android-api35-local-original-v1"
        val workflowId = 9_350_001L
        val subjectId = 7_350_001L

        val first = HealthReportLocalOriginalStore.production(context)
        first.persistUpload(
            inputs = listOf(HealthReportUploadAssetInput(bytes, "设备体检原件.png")),
            clientRequestId = requestId,
            accountScope = accountScope,
            subjectUserId = subjectId,
        )
        first.bindWorkflow(workflowId, requestId, accountScope, subjectId)

        val afterRecreation = HealthReportLocalOriginalStore.production(context)
        assertArrayEquals(
            bytes,
            afterRecreation.loadAsset(workflowId, 1, accountScope, subjectId).data,
        )
        assertEquals(
            digest,
            afterRecreation.bindingProof(workflowId, accountScope, subjectId).aggregateSha256,
        )

        val noBackupRoot = context.noBackupFilesDir.toPath().toAbsolutePath().normalize()
        val blobName = "$digest.original"
        val matchingBlobs = mutableListOf<Path>()
        Files.walk(noBackupRoot).use { paths ->
            paths.filter { it.fileName?.toString() == blobName }
                .forEach(matchingBlobs::add)
        }
        assertEquals(1, matchingBlobs.size)
        val blob = matchingBlobs.single().toAbsolutePath().normalize()
        assertTrue(blob.startsWith(noBackupRoot))
        assertFalse(blob.startsWith(context.cacheDir.toPath().toAbsolutePath().normalize()))
        context.getExternalFilesDir(null)?.toPath()?.toAbsolutePath()?.normalize()?.let { external ->
            assertFalse(blob.startsWith(external))
        }
        assertArrayEquals(bytes, Files.readAllBytes(blob))
    }
}
