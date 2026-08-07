package com.xjie.app.feature.healthdata

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class HealthReportLocalPathKind { Directory, File }

internal fun interface HealthReportLocalFileProtectionPolicy {
    fun applyAndVerify(path: Path, kind: HealthReportLocalPathKind)
}

internal interface HealthReportLocalDurabilityPolicy {
    fun forceFile(path: Path)
    fun forceDirectory(path: Path)
}

internal object StrictHealthReportLocalFileProtection : HealthReportLocalFileProtectionPolicy {
    private val directoryPermissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
    )
    private val filePermissions = setOf(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
    )

    override fun applyAndVerify(path: Path, kind: HealthReportLocalPathKind) {
        val expected = when (kind) {
            HealthReportLocalPathKind.Directory -> directoryPermissions
            HealthReportLocalPathKind.File -> filePermissions
        }
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        val correctType = when (kind) {
            HealthReportLocalPathKind.Directory -> attributes.isDirectory
            HealthReportLocalPathKind.File -> attributes.isRegularFile
        }
        require(correctType && !attributes.isSymbolicLink) { "unexpected local-original path type" }
        Files.setPosixFilePermissions(path, expected)
        require(Files.getPosixFilePermissions(path, NOFOLLOW_LINKS) == expected) {
            "local-original permissions were not applied exactly"
        }
    }
}

internal object FsyncHealthReportLocalDurability : HealthReportLocalDurabilityPolicy {
    override fun forceFile(path: Path) {
        FileChannel.open(path, WRITE).use { channel -> channel.force(true) }
    }

    override fun forceDirectory(path: Path) {
        FileChannel.open(path, READ).use { channel -> channel.force(true) }
    }
}

/**
 * Exact-byte local report-original store. Production construction is rooted only below
 * [Context.getNoBackupFilesDir]; tests inject an isolated root and deterministic fault hooks.
 */
class HealthReportLocalOriginalStore internal constructor(
    rootDirectory: Path,
    private val protectionPolicy: HealthReportLocalFileProtectionPolicy =
        StrictHealthReportLocalFileProtection,
    private val durabilityPolicy: HealthReportLocalDurabilityPolicy =
        FsyncHealthReportLocalDurability,
    private val bindingCheckpoint: (HealthReportLocalOriginalBindingCheckpoint) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HealthReportLocalOriginalStoreContract {
    @Serializable
    private data class StoredAsset(
        val assetIndex: Int,
        val fileName: String,
        val mimeType: String,
        val byteSize: Long,
        val sha256: String,
        val blobName: String,
    )

    @Serializable
    private data class Manifest(
        val schemaVersion: Int,
        val clientRequestId: String,
        val subjectUserId: Long,
        val workflowId: Long? = null,
        val assets: List<StoredAsset>,
    )

    @Serializable
    private data class WorkflowBinding(
        val schemaVersion: Int,
        val workflowId: Long,
        val clientRequestId: String,
        val subjectUserId: Long,
    )

    @Serializable
    private data class WorkflowBindingJournal(
        val schemaVersion: Int,
        val workflowId: Long,
        val clientRequestId: String,
        val subjectUserId: Long,
    )

    private data class Identity(
        val clientRequestId: String,
        val normalizedAccountScope: String,
        val requestKey: String,
    )

    private data class SubjectDirectory(
        val creationOrder: List<Path>,
        val blobs: Path,
        val manifests: Path,
        val workflows: Path,
        val journals: Path,
    )

    private data class ResolvedManifest(
        val manifest: Manifest,
        val directory: SubjectDirectory,
    )

    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()
    private val processLock = locks.computeIfAbsent(this.rootDirectory.toString()) { ReentrantLock() }
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    override suspend fun persistUpload(
        inputs: List<HealthReportUploadAssetInput>,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    ) = execute {
        val identity = validateIdentity(clientRequestId, accountScope, subjectUserId)
        if (inputs.isEmpty()) fail(HealthReportLocalOriginalStoreError.InvalidAsset(1))
        val directory = prepareSubjectDirectory(identity.normalizedAccountScope, subjectUserId)
        val candidateAssets = inputs.mapIndexed { offset, input ->
            persistBlob(input, offset + 1, directory.blobs)
        }
        val manifestPath = directory.manifests.resolve("${identity.requestKey}.json")
        val existing = if (exists(manifestPath)) {
            loadManifest(manifestPath, identity.clientRequestId, subjectUserId)
        } else {
            null
        }
        if (existing != null && !sameAssetContract(existing.assets, candidateAssets)) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        writeJsonAtomically(
            Manifest(
                schemaVersion = SCHEMA_VERSION,
                clientRequestId = identity.clientRequestId,
                subjectUserId = subjectUserId,
                workflowId = existing?.workflowId,
                assets = candidateAssets,
            ),
            manifestPath,
        )
    }

    override suspend fun persistReplacement(
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    ) = execute {
        if (assetIndex <= 0) fail(HealthReportLocalOriginalStoreError.InvalidAsset(assetIndex))
        val identity = validateIdentity(clientRequestId, accountScope, subjectUserId)
        val directory = prepareSubjectDirectory(identity.normalizedAccountScope, subjectUserId)
        val manifestPath = directory.manifests.resolve("${identity.requestKey}.json")
        val manifest = loadManifest(manifestPath, identity.clientRequestId, subjectUserId)
        val replacement = persistBlob(input, assetIndex, directory.blobs)
        val assets = (manifest.assets.filterNot { it.assetIndex == assetIndex } + replacement)
            .sortedBy(StoredAsset::assetIndex)
        writeJsonAtomically(manifest.copy(assets = assets), manifestPath)
    }

    override suspend fun bindWorkflow(
        workflowId: Long,
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    ) = execute {
        if (workflowId <= 0L) fail(HealthReportLocalOriginalStoreError.InvalidIdentity)
        val identity = validateIdentity(clientRequestId, accountScope, subjectUserId)
        val directory = prepareSubjectDirectory(identity.normalizedAccountScope, subjectUserId)
        recoverBindingIfNeeded(workflowId, directory, subjectUserId)
        val manifestPath = directory.manifests.resolve("${identity.requestKey}.json")
        var manifest = loadManifest(manifestPath, identity.clientRequestId, subjectUserId)
        validateAssets(manifest.assets, directory.blobs)
        if (manifest.workflowId != null && manifest.workflowId != workflowId) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        validateExistingBinding(
            workflowId,
            identity.clientRequestId,
            subjectUserId,
            manifest.assets,
            directory,
        )

        val journalPath = directory.journals.resolve("$workflowId.json")
        writeJsonAtomically(
            WorkflowBindingJournal(
                schemaVersion = SCHEMA_VERSION,
                workflowId = workflowId,
                clientRequestId = identity.clientRequestId,
                subjectUserId = subjectUserId,
            ),
            journalPath,
        )
        bindingCheckpoint(HealthReportLocalOriginalBindingCheckpoint.JournalPersisted)

        manifest = manifest.copy(workflowId = workflowId)
        writeJsonAtomically(manifest, manifestPath)
        bindingCheckpoint(HealthReportLocalOriginalBindingCheckpoint.ManifestPersisted)

        writeJsonAtomically(
            WorkflowBinding(
                schemaVersion = SCHEMA_VERSION,
                workflowId = workflowId,
                clientRequestId = identity.clientRequestId,
                subjectUserId = subjectUserId,
            ),
            directory.workflows.resolve("$workflowId.json"),
        )
        bindingCheckpoint(HealthReportLocalOriginalBindingCheckpoint.BindingPersisted)
        removeDurably(journalPath)
    }

    override suspend fun loadAssets(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): List<HealthReportLocalOriginalAsset> = execute {
        val resolved = resolveManifest(workflowId, accountScope, subjectUserId)
        resolved.manifest.assets.sortedBy(StoredAsset::assetIndex).map { asset ->
            makeLoadedAsset(asset, resolved.directory.blobs)
        }
    }

    override suspend fun listAssets(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): List<HealthReportLocalOriginalMetadata> = execute {
        val resolved = resolveManifest(workflowId, accountScope, subjectUserId)
        resolved.manifest.assets.sortedBy(StoredAsset::assetIndex).map { asset ->
            makeMetadata(asset, resolved.directory.blobs)
        }
    }

    override suspend fun loadAsset(
        workflowId: Long,
        assetIndex: Int,
        accountScope: String,
        subjectUserId: Long,
    ): HealthReportLocalOriginalAsset = execute {
        if (assetIndex <= 0) fail(HealthReportLocalOriginalStoreError.InvalidAsset(assetIndex))
        val resolved = resolveManifest(workflowId, accountScope, subjectUserId)
        val stored = resolved.manifest.assets.firstOrNull { it.assetIndex == assetIndex }
            ?: fail(HealthReportLocalOriginalStoreError.ReportNotFound)
        makeLoadedAsset(stored, resolved.directory.blobs)
    }

    override suspend fun bindingProof(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): HealthReportLocalOriginalBindingProof = execute {
        val resolved = resolveManifest(workflowId, accountScope, subjectUserId)
        val assets = resolved.manifest.assets.sortedBy(StoredAsset::assetIndex).onEach { asset ->
            loadValidatedBlob(asset, resolved.directory.blobs)
        }
        HealthReportLocalOriginalBindingProof(
            contractVersion = SCHEMA_VERSION,
            clientRequestId = resolved.manifest.clientRequestId,
            assetCount = assets.size,
            aggregateSha256 = aggregateDigest(assets),
        )
    }

    private fun resolveManifest(
        workflowId: Long,
        accountScope: String,
        subjectUserId: Long,
    ): ResolvedManifest {
        val scope = validateOwner(workflowId, accountScope, subjectUserId)
        val directory = requireSubjectDirectory(scope, subjectUserId)
        recoverBindingIfNeeded(workflowId, directory, subjectUserId)
        val bindingPath = directory.workflows.resolve("$workflowId.json")
        if (!exists(bindingPath)) fail(HealthReportLocalOriginalStoreError.ReportNotFound)
        val binding: WorkflowBinding = readJson(bindingPath)
        if (
            binding.schemaVersion != SCHEMA_VERSION ||
            binding.workflowId != workflowId ||
            binding.subjectUserId != subjectUserId ||
            binding.clientRequestId.isBlank()
        ) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        val requestKey = digest(binding.clientRequestId.toByteArray(StandardCharsets.UTF_8))
        val manifest = loadManifest(
            directory.manifests.resolve("$requestKey.json"),
            binding.clientRequestId,
            subjectUserId,
        )
        if (manifest.workflowId != workflowId) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        return ResolvedManifest(manifest, directory)
    }

    private fun recoverBindingIfNeeded(
        workflowId: Long,
        directory: SubjectDirectory,
        subjectUserId: Long,
    ) {
        val journalPath = directory.journals.resolve("$workflowId.json")
        if (!exists(journalPath)) return
        val journal: WorkflowBindingJournal = readJson(journalPath)
        if (
            journal.schemaVersion != SCHEMA_VERSION ||
            journal.workflowId != workflowId ||
            journal.subjectUserId != subjectUserId ||
            journal.clientRequestId.isBlank()
        ) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        val requestKey = digest(journal.clientRequestId.toByteArray(StandardCharsets.UTF_8))
        val manifestPath = directory.manifests.resolve("$requestKey.json")
        var manifest = loadManifest(manifestPath, journal.clientRequestId, subjectUserId)
        if (manifest.workflowId != null && manifest.workflowId != workflowId) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        // The journal is written only after bindWorkflow has verified every candidate byte.
        // Recovery intentionally replays metadata only so listAssets never loads report bodies;
        // bindingProof still re-reads every byte before an acknowledgement can be issued.
        if (manifest.workflowId == null) {
            manifest = manifest.copy(workflowId = workflowId)
            writeJsonAtomically(manifest, manifestPath)
        }
        validateExistingBinding(
            workflowId,
            journal.clientRequestId,
            subjectUserId,
            manifest.assets,
            directory,
        )
        writeJsonAtomically(
            WorkflowBinding(
                schemaVersion = SCHEMA_VERSION,
                workflowId = workflowId,
                clientRequestId = journal.clientRequestId,
                subjectUserId = subjectUserId,
            ),
            directory.workflows.resolve("$workflowId.json"),
        )
        removeDurably(journalPath)
    }

    private fun validateExistingBinding(
        workflowId: Long,
        clientRequestId: String,
        subjectUserId: Long,
        candidateAssets: List<StoredAsset>,
        directory: SubjectDirectory,
    ) {
        val bindingPath = directory.workflows.resolve("$workflowId.json")
        if (!exists(bindingPath)) return
        val binding: WorkflowBinding = readJson(bindingPath)
        if (
            binding.schemaVersion != SCHEMA_VERSION ||
            binding.workflowId != workflowId ||
            binding.subjectUserId != subjectUserId
        ) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
        if (binding.clientRequestId == clientRequestId) return
        val existingKey = digest(binding.clientRequestId.toByteArray(StandardCharsets.UTF_8))
        val existingManifest = loadManifest(
            directory.manifests.resolve("$existingKey.json"),
            binding.clientRequestId,
            subjectUserId,
        )
        if (
            existingManifest.workflowId != workflowId ||
            !sameAssetContract(existingManifest.assets, candidateAssets)
        ) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        }
    }

    private fun validateIdentity(
        clientRequestId: String,
        accountScope: String,
        subjectUserId: Long,
    ): Identity {
        val requestId = clientRequestId.trim()
        val scope = accountScope.trim()
        if (requestId.isEmpty() || scope.isEmpty() || subjectUserId <= 0L) {
            fail(HealthReportLocalOriginalStoreError.InvalidIdentity)
        }
        return Identity(
            clientRequestId = requestId,
            normalizedAccountScope = scope,
            requestKey = digest(requestId.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    private fun validateOwner(workflowId: Long, accountScope: String, subjectUserId: Long): String {
        val scope = accountScope.trim()
        if (workflowId <= 0L || subjectUserId <= 0L || scope.isEmpty()) {
            fail(HealthReportLocalOriginalStoreError.InvalidIdentity)
        }
        return scope
    }

    private fun prepareSubjectDirectory(accountScope: String, subjectUserId: Long): SubjectDirectory {
        val directory = subjectDirectory(accountScope, subjectUserId)
        directory.creationOrder.forEach(::ensureDirectory)
        return directory
    }

    private fun requireSubjectDirectory(accountScope: String, subjectUserId: Long): SubjectDirectory {
        val directory = subjectDirectory(accountScope, subjectUserId)
        directory.creationOrder.forEach { path ->
            if (!exists(path)) fail(HealthReportLocalOriginalStoreError.ReportNotFound)
            protect(path, HealthReportLocalPathKind.Directory)
        }
        return directory
    }

    private fun subjectDirectory(accountScope: String, subjectUserId: Long): SubjectDirectory {
        val accountKey = digest(accountScope.toByteArray(StandardCharsets.UTF_8))
        val accounts = rootDirectory.resolve("accounts")
        val account = accounts.resolve(accountKey)
        val subjects = account.resolve("subjects")
        val subject = subjects.resolve(subjectUserId.toString())
        val blobs = subject.resolve("blobs")
        val manifests = subject.resolve("manifests")
        val workflows = subject.resolve("workflows")
        val journals = subject.resolve("binding-journals")
        return SubjectDirectory(
            creationOrder = listOf(
                rootDirectory,
                accounts,
                account,
                subjects,
                subject,
                blobs,
                manifests,
                workflows,
                journals,
            ),
            blobs = blobs,
            manifests = manifests,
            workflows = workflows,
            journals = journals,
        )
    }

    private fun ensureDirectory(path: Path) {
        try {
            if (!exists(path)) {
                val parent = path.parent
                if (parent == null || !Files.isDirectory(parent, NOFOLLOW_LINKS)) {
                    throw java.io.IOException("local-original parent is missing")
                }
                Files.createDirectory(path)
                protect(path, HealthReportLocalPathKind.Directory)
                durabilityPolicy.forceDirectory(path)
                durabilityPolicy.forceDirectory(parent)
            } else {
                protect(path, HealthReportLocalPathKind.Directory)
            }
        } catch (error: HealthReportLocalOriginalStoreException) {
            throw error
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
        }
    }

    private fun persistBlob(
        input: HealthReportUploadAssetInput,
        assetIndex: Int,
        blobsDirectory: Path,
    ): StoredAsset {
        val bytes = input.data
        if (assetIndex <= 0 || bytes.isEmpty()) {
            fail(HealthReportLocalOriginalStoreError.InvalidAsset(assetIndex))
        }
        val sha256 = digest(bytes)
        val blobName = "$sha256.original"
        val blobPath = blobsDirectory.resolve(blobName)
        if (exists(blobPath)) {
            val existing = loadRawFile(blobPath)
            if (existing.size != bytes.size || digest(existing) != sha256) {
                fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(assetIndex))
            }
        } else {
            writeAtomically(blobPath, bytes)
            val written = loadRawFile(blobPath)
            if (written.size != bytes.size || digest(written) != sha256) {
                fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(assetIndex))
            }
        }
        val displayName = safeDisplayName(input.fileName, assetIndex)
        return StoredAsset(
            assetIndex = assetIndex,
            fileName = displayName,
            mimeType = healthReportUploadMimeType(input.fileName),
            byteSize = bytes.size.toLong(),
            sha256 = sha256,
            blobName = blobName,
        )
    }

    private fun loadManifest(path: Path, clientRequestId: String, subjectUserId: Long): Manifest {
        if (!exists(path)) fail(HealthReportLocalOriginalStoreError.ReportNotFound)
        val manifest: Manifest = readJson(path)
        val indices = manifest.assets.map(StoredAsset::assetIndex)
        val valid =
            manifest.schemaVersion == SCHEMA_VERSION &&
                manifest.clientRequestId == clientRequestId &&
                manifest.subjectUserId == subjectUserId &&
                (manifest.workflowId == null || manifest.workflowId > 0L) &&
                manifest.assets.isNotEmpty() &&
                indices.toSet().size == indices.size &&
                manifest.assets.all(::isValidStoredAsset)
        if (!valid) fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        return manifest
    }

    private fun isValidStoredAsset(asset: StoredAsset): Boolean =
        asset.assetIndex > 0 &&
            asset.byteSize > 0L &&
            SHA256_PATTERN.matches(asset.sha256) &&
            asset.blobName == "${asset.sha256}.original" &&
            asset.fileName.isNotBlank() &&
            asset.fileName.length <= MAX_FILE_NAME_LENGTH &&
            '/' !in asset.fileName &&
            '\\' !in asset.fileName &&
            asset.mimeType.isNotBlank()

    private fun validateAssets(assets: List<StoredAsset>, blobsDirectory: Path) {
        assets.forEach { asset -> loadValidatedBlob(asset, blobsDirectory) }
    }

    private fun loadValidatedBlob(asset: StoredAsset, blobsDirectory: Path): ByteArray {
        if (!isValidStoredAsset(asset)) {
            fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(asset.assetIndex))
        }
        val path = blobsDirectory.resolve(asset.blobName)
        if (!exists(path)) fail(HealthReportLocalOriginalStoreError.ReportNotFound)
        val data = try {
            loadRawFile(path)
        } catch (error: HealthReportLocalOriginalStoreException) {
            throw error
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.ReportNotFound, error)
        }
        if (data.size.toLong() != asset.byteSize || digest(data) != asset.sha256) {
            fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(asset.assetIndex))
        }
        return data
    }

    private fun makeMetadata(asset: StoredAsset, blobsDirectory: Path): HealthReportLocalOriginalMetadata {
        if (!isValidStoredAsset(asset)) {
            fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(asset.assetIndex))
        }
        val path = blobsDirectory.resolve(asset.blobName)
        if (!exists(path)) fail(HealthReportLocalOriginalStoreError.ReportNotFound)
        protect(path, HealthReportLocalPathKind.File)
        val actualSize = try {
            Files.size(path)
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.ReportNotFound, error)
        }
        if (actualSize != asset.byteSize) {
            fail(HealthReportLocalOriginalStoreError.IntegrityMismatch(asset.assetIndex))
        }
        return HealthReportLocalOriginalMetadata(
            assetIndex = asset.assetIndex,
            fileName = asset.fileName,
            mimeType = asset.mimeType,
            byteSize = asset.byteSize,
            sha256 = asset.sha256,
        )
    }

    private fun makeLoadedAsset(asset: StoredAsset, blobsDirectory: Path) =
        HealthReportLocalOriginalAsset(
            assetIndex = asset.assetIndex,
            fileName = asset.fileName,
            mimeType = asset.mimeType,
            byteSize = asset.byteSize,
            sha256 = asset.sha256,
            data = loadValidatedBlob(asset, blobsDirectory),
        )

    private inline fun <reified T> writeJsonAtomically(value: T, path: Path) {
        val bytes = try {
            json.encodeToString(value).toByteArray(StandardCharsets.UTF_8)
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
        }
        writeAtomically(path, bytes)
    }

    private inline fun <reified T> readJson(path: Path): T {
        val bytes = loadRawFile(path)
        return try {
            json.decodeFromString(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.CorruptManifest, error)
        }
    }

    private fun writeAtomically(path: Path, bytes: ByteArray) {
        val parent = path.parent ?: fail(HealthReportLocalOriginalStoreError.WriteFailed)
        var temporary: Path? = null
        try {
            temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
            protect(temporary, HealthReportLocalPathKind.File)
            FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            protect(temporary, HealthReportLocalPathKind.File)
            try {
                Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
            }
            temporary = null
            protect(path, HealthReportLocalPathKind.File)
            durabilityPolicy.forceFile(path)
            durabilityPolicy.forceDirectory(parent)
        } catch (error: HealthReportLocalOriginalStoreException) {
            throw error
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun removeDurably(path: Path) {
        if (!exists(path)) return
        try {
            Files.delete(path)
            durabilityPolicy.forceDirectory(path.parent)
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
        }
    }

    private fun loadRawFile(path: Path): ByteArray {
        protect(path, HealthReportLocalPathKind.File)
        return try {
            Files.readAllBytes(path)
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.ReportNotFound, error)
        }
    }

    private fun protect(path: Path, kind: HealthReportLocalPathKind) {
        try {
            protectionPolicy.applyAndVerify(path, kind)
        } catch (error: HealthReportLocalOriginalStoreException) {
            throw error
        } catch (error: Exception) {
            fail(HealthReportLocalOriginalStoreError.WriteFailed, error)
        }
    }

    private fun sameAssetContract(first: List<StoredAsset>, second: List<StoredAsset>): Boolean =
        first.size == second.size && aggregateDigest(first) == aggregateDigest(second)

    private fun aggregateDigest(assets: List<StoredAsset>): String {
        if (assets.isEmpty()) fail(HealthReportLocalOriginalStoreError.CorruptManifest)
        val ordered = assets.sortedBy(StoredAsset::assetIndex)
        if (ordered.size == 1) {
            if (hexBytes(ordered.single().sha256)?.size != SHA256_BYTES) {
                fail(HealthReportLocalOriginalStoreError.CorruptManifest)
            }
            return ordered.single().sha256
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(AGGREGATE_PREFIX)
        ordered.forEach { asset ->
            val shaBytes = hexBytes(asset.sha256)
                ?: fail(HealthReportLocalOriginalStoreError.CorruptManifest)
            val mimeBytes = asset.mimeType.toByteArray(StandardCharsets.UTF_8)
            if (
                asset.assetIndex <= 0 ||
                asset.byteSize <= 0L ||
                shaBytes.size != SHA256_BYTES ||
                mimeBytes.size > UShort.MAX_VALUE.toInt()
            ) {
                fail(HealthReportLocalOriginalStoreError.CorruptManifest)
            }
            val frame = ByteBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES + Short.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(asset.assetIndex)
                .putLong(asset.byteSize)
                .putShort(mimeBytes.size.toShort())
                .array()
            digest.update(frame)
            digest.update(mimeBytes)
            digest.update(shaBytes)
        }
        return digest.digest().toHex()
    }

    private fun safeDisplayName(raw: String, assetIndex: Int): String {
        val normalized = raw.replace('\\', '/')
            .substringAfterLast('/')
            .filterNot(Char::isISOControl)
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
        return normalized.takeUnless { it.isBlank() || it == "." || it == ".." }
            ?: "报告原件-$assetIndex"
    }

    private suspend fun <T> execute(block: () -> T): T = withContext(ioDispatcher) {
        processLock.withLock(block)
    }

    private fun exists(path: Path): Boolean = Files.exists(path, NOFOLLOW_LINKS)

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun hexBytes(value: String): ByteArray? {
        if (value.length != SHA256_BYTES * 2) return null
        return runCatching {
            ByteArray(SHA256_BYTES) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun fail(error: HealthReportLocalOriginalStoreError, cause: Throwable? = null): Nothing {
        throw HealthReportLocalOriginalStoreException(error, cause)
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_NAME_LENGTH = 180
        private const val SHA256_BYTES = 32
        private const val ROOT_DIRECTORY_NAME = "health-report-originals"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val AGGREGATE_PREFIX = "xjie-report-asset-set-v1\u0000"
            .toByteArray(StandardCharsets.UTF_8)
        private val locks = ConcurrentHashMap<String, ReentrantLock>()

        /** Production files never fall back to cache/external storage or a backup-eligible root. */
        fun production(context: Context): HealthReportLocalOriginalStore =
            HealthReportLocalOriginalStore(
                context.applicationContext.noBackupFilesDir.toPath().resolve(ROOT_DIRECTORY_NAME),
            )
    }
}
