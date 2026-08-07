package com.xjie.app.feature.healthdata

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission
import java.util.Comparator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthReportLocalOriginalStoreTest {
    @Test
    fun exactBytesSurviveCrossInstanceReadsWithAccountAndSubjectIsolation() = runTest {
        withStoreRoot { root ->
            val durability = RecordingDurabilityPolicy()
            val original = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xfe.toByte(), 0xff.toByte())
            val mutableInput = original.copyOf()
            val first = HealthReportLocalOriginalStore(root, durabilityPolicy = durability)

            first.persistUpload(
                inputs = listOf(
                    HealthReportUploadAssetInput(mutableInput, "../../体检报告原件.png"),
                ),
                clientRequestId = "request-exact-bytes",
                accountScope = "account-a",
                subjectUserId = 7,
            )
            mutableInput.fill(42)
            first.bindWorkflow(
                workflowId = 41,
                clientRequestId = "request-exact-bytes",
                accountScope = "account-a",
                subjectUserId = 7,
            )

            val afterRestart = HealthReportLocalOriginalStore(root)
            val metadata = afterRestart.listAssets(41, "account-a", 7).single()
            assertEquals(1, metadata.assetIndex)
            assertEquals("体检报告原件.png", metadata.fileName)
            assertEquals("image/png", metadata.mimeType)
            assertEquals(original.size.toLong(), metadata.byteSize)
            assertArrayEquals(original, afterRestart.loadAsset(41, 1, "account-a", 7).data)
            assertArrayEquals(original, afterRestart.loadAssets(41, "account-a", 7).single().data)

            expectStoreError(HealthReportLocalOriginalStoreError.ReportNotFound) {
                afterRestart.loadAssets(41, "account-b", 7)
            }
            expectStoreError(HealthReportLocalOriginalStoreError.ReportNotFound) {
                afterRestart.loadAssets(41, "account-a", 8)
            }

            val allPaths = walk(root)
            assertFalse(allPaths.any { "account-a" in it.toString() })
            val accountPartition = allPaths.single {
                it.parent?.fileName?.toString() == "accounts" && Files.isDirectory(it)
            }
            assertTrue(Regex("[0-9a-f]{64}").matches(accountPartition.fileName.toString()))
            assertProtectedTree(root)
            assertFalse(allPaths.any { it.fileName.toString().endsWith(".tmp") })
            assertTrue(durability.forcedFiles.any { it.fileName.toString().endsWith(".original") })
            assertTrue(durability.forcedFiles.any { it.fileName.toString().endsWith(".json") })
            assertTrue(durability.forcedDirectories.isNotEmpty())
        }
    }

    @Test
    fun metadataListingDoesNotReadBodyButPageAndBindingProofRejectSameLengthTamper() = runTest {
        withStoreRoot { root ->
            val store = HealthReportLocalOriginalStore(root)
            store.persistUpload(
                listOf(HealthReportUploadAssetInput("original-body".encodeToByteArray(), "report.pdf")),
                "request-tamper",
                "account-a",
                9,
            )
            store.bindWorkflow(42, "request-tamper", "account-a", 9)
            val originalMetadata = store.listAssets(42, "account-a", 9).single()
            val blob = walk(root).single { it.fileName.toString().endsWith(".original") }
            Files.write(blob, "tampered-body".encodeToByteArray(), WRITE, TRUNCATE_EXISTING)

            assertEquals(originalMetadata, store.listAssets(42, "account-a", 9).single())
            expectStoreError(HealthReportLocalOriginalStoreError.IntegrityMismatch(1)) {
                store.loadAsset(42, 1, "account-a", 9)
            }
            expectStoreError(HealthReportLocalOriginalStoreError.IntegrityMismatch(1)) {
                store.bindingProof(42, "account-a", 9)
            }
        }
    }

    @Test
    fun workflowBindingJournalRecoversEveryCommittedBoundary() = runTest {
        for (checkpoint in HealthReportLocalOriginalBindingCheckpoint.entries) {
            withStoreRoot { root ->
                val interrupted = HealthReportLocalOriginalStore(
                    rootDirectory = root,
                    bindingCheckpoint = { reached ->
                        if (reached == checkpoint) throw SimulatedCrash(checkpoint)
                    },
                )
                val bytes = "journal-${checkpoint.name}".encodeToByteArray()
                interrupted.persistUpload(
                    listOf(HealthReportUploadAssetInput(bytes, "report.png")),
                    "request-${checkpoint.name}",
                    "account-journal",
                    11,
                )
                val crash = runCatching {
                    interrupted.bindWorkflow(
                        43,
                        "request-${checkpoint.name}",
                        "account-journal",
                        11,
                    )
                }.exceptionOrNull()
                assertTrue("checkpoint $checkpoint must interrupt", crash is SimulatedCrash)

                val recovered = HealthReportLocalOriginalStore(root)
                assertArrayEquals(bytes, recovered.loadAsset(43, 1, "account-journal", 11).data)
                assertEquals(
                    "request-${checkpoint.name}",
                    recovered.bindingProof(43, "account-journal", 11).clientRequestId,
                )
                assertFalse(walk(root).any { it.parent?.fileName?.toString() == "binding-journals" })
            }
        }
    }

    @Test
    fun bindingProofMatchesBackendAggregateAndReplacementRevalidatesExactBytes() = runTest {
        withStoreRoot { root ->
            val store = HealthReportLocalOriginalStore(root)
            store.persistUpload(
                listOf(
                    HealthReportUploadAssetInput("first".encodeToByteArray(), "first.png"),
                    HealthReportUploadAssetInput("second-page".encodeToByteArray(), "second.pdf"),
                ),
                "request-aggregate",
                "account-aggregate",
                12,
            )
            store.bindWorkflow(44, "request-aggregate", "account-aggregate", 12)

            val firstProof = store.bindingProof(44, "account-aggregate", 12)
            assertEquals(1, firstProof.contractVersion)
            assertEquals(2, firstProof.assetCount)
            assertEquals(
                "d0d7f28ecfd8daf68b9b410ba7350f6cf5efd5eac5693da06c1422d25f7da05c",
                firstProof.aggregateSha256,
            )

            val replacement = byteArrayOf(9, 8, 7, 6, 5)
            store.persistReplacement(
                HealthReportUploadAssetInput(replacement, "replacement.heic"),
                2,
                "request-aggregate",
                "account-aggregate",
                12,
            )
            assertArrayEquals(replacement, store.loadAsset(44, 2, "account-aggregate", 12).data)
            assertEquals("image/heic", store.listAssets(44, "account-aggregate", 12)[1].mimeType)
            assertNotEquals(firstProof.aggregateSha256, store.bindingProof(44, "account-aggregate", 12).aggregateSha256)
        }
    }

    @Test
    fun exactDuplicateCanRecoverAndRebindRepeatedlyButDifferentBytesCannotReuseWorkflow() = runTest {
        withStoreRoot { root ->
            val exactBytes = "same-report-original".encodeToByteArray()
            val initial = HealthReportLocalOriginalStore(root)
            initial.persistUpload(
                listOf(HealthReportUploadAssetInput(exactBytes, "initial.pdf")),
                "request-initial",
                "account-rebind",
                13,
            )
            initial.bindWorkflow(45, "request-initial", "account-rebind", 13)

            val duplicateRequest = "request-duplicate"
            val interrupted = HealthReportLocalOriginalStore(
                rootDirectory = root,
                bindingCheckpoint = { reached ->
                    if (reached == HealthReportLocalOriginalBindingCheckpoint.ManifestPersisted) {
                        throw SimulatedCrash(reached)
                    }
                },
            )
            interrupted.persistUpload(
                listOf(HealthReportUploadAssetInput(exactBytes, "renamed.pdf")),
                duplicateRequest,
                "account-rebind",
                13,
            )
            assertTrue(
                runCatching {
                    interrupted.bindWorkflow(45, duplicateRequest, "account-rebind", 13)
                }.exceptionOrNull() is SimulatedCrash,
            )

            val recovered = HealthReportLocalOriginalStore(root)
            assertEquals(
                duplicateRequest,
                recovered.bindingProof(45, "account-rebind", 13).clientRequestId,
            )
            recovered.bindWorkflow(45, duplicateRequest, "account-rebind", 13)
            recovered.bindWorkflow(45, "request-initial", "account-rebind", 13)
            recovered.bindWorkflow(45, duplicateRequest, "account-rebind", 13)

            val different = ByteArray(exactBytes.size) { index -> (index + 1).toByte() }
            recovered.persistUpload(
                listOf(HealthReportUploadAssetInput(different, "different.pdf")),
                "request-different",
                "account-rebind",
                13,
            )
            expectStoreError(HealthReportLocalOriginalStoreError.CorruptManifest) {
                recovered.bindWorkflow(45, "request-different", "account-rebind", 13)
            }
            assertEquals(
                duplicateRequest,
                recovered.bindingProof(45, "account-rebind", 13).clientRequestId,
            )
        }
    }

    @Test
    fun protectionFailureFailsClosedAndCommitsNoManifest() = runTest {
        withStoreRoot { root ->
            val rejectedProtection = HealthReportLocalFileProtectionPolicy { path, kind ->
                StrictHealthReportLocalFileProtection.applyAndVerify(path, kind)
                if (kind == HealthReportLocalPathKind.File) {
                    throw IOException("simulated protection verification failure")
                }
            }
            val store = HealthReportLocalOriginalStore(root, protectionPolicy = rejectedProtection)

            expectStoreError(HealthReportLocalOriginalStoreError.WriteFailed) {
                store.persistUpload(
                    listOf(HealthReportUploadAssetInput("sensitive".encodeToByteArray(), "report.png")),
                    "request-protection-failure",
                    "account-protection",
                    14,
                )
            }

            assertFalse(walk(root).any { it.fileName.toString().endsWith(".json") })
            assertFalse(walk(root).any { it.fileName.toString().endsWith(".original") })
        }
    }

    private suspend fun expectStoreError(
        expected: HealthReportLocalOriginalStoreError,
        block: suspend () -> Unit,
    ) {
        val error = runCatching { block() }.exceptionOrNull()
        val storeError = error as? HealthReportLocalOriginalStoreException
            ?: throw AssertionError("expected $expected, got $error")
        assertEquals(expected, storeError.error)
    }

    private suspend fun withStoreRoot(block: suspend (Path) -> Unit) {
        val temporary = Files.createTempDirectory("xjie-report-original-store-")
        val root = temporary.resolve("store")
        try {
            block(root)
        } finally {
            deleteTree(temporary)
        }
    }

    private fun walk(root: Path): List<Path> =
        if (!Files.exists(root)) emptyList() else Files.walk(root).use { it.toList() }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun assertProtectedTree(root: Path) {
        val directoryPermissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
        val filePermissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )
        walk(root).forEach { path ->
            val expected = if (Files.isDirectory(path)) directoryPermissions else filePermissions
            assertEquals("permissions for $path", expected, Files.getPosixFilePermissions(path))
        }
    }

    private class RecordingDurabilityPolicy : HealthReportLocalDurabilityPolicy {
        val forcedFiles = mutableListOf<Path>()
        val forcedDirectories = mutableListOf<Path>()

        override fun forceFile(path: Path) {
            FsyncHealthReportLocalDurability.forceFile(path)
            forcedFiles.add(path)
        }

        override fun forceDirectory(path: Path) {
            FsyncHealthReportLocalDurability.forceDirectory(path)
            forcedDirectories.add(path)
        }
    }

    private class SimulatedCrash(
        val checkpoint: HealthReportLocalOriginalBindingCheckpoint,
    ) : RuntimeException(checkpoint.name)
}
