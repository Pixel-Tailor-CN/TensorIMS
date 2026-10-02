package app.mystery0.ims.tensor.privilege

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

enum class OperationPhase { PREPARED, UNKNOWN, TERMINAL }
data class OperationRecord(
    val operationId: String,
    val mode: BackendMode,
    val operation: String,
    val epoch: Long,
    val bootCount: Int,
    val subId: Int,
    val identityDigest: String? = null,
    val recoveryReference: String? = null,
    val phase: OperationPhase = OperationPhase.PREPARED,
    val cleanupConfirmed: Boolean = false,
    val targetIdentities: Map<Int, String> = emptyMap(),
    val dispatched: Boolean = false,
)

/** 每次只允许一笔未完成事务；损坏日志必须按需要恢复处理。 */
class OperationJournal(private val directory: File) {
    private val file = File(directory, "operation.properties")
    private val pending = File(directory, "operation.properties.new")

    @Synchronized fun read(): OperationRecord? {
        if (!file.exists()) {
            check(!pending.exists()) { "Interrupted operation journal write" }
            return null
        }
        return try {
            check(file.length() in 1..16_384) { "Invalid journal size" }
            val values = Properties().apply { file.inputStream().use(::load) }
            fun value(key: String) = checkNotNull(values.getProperty(key)) { "Missing journal field" }
            check(value("schema") == "1") { "Unsupported journal schema" }
            OperationRecord(
                operationId = value("id"), mode = BackendMode.valueOf(value("mode")),
                operation = value("operation"), epoch = value("epoch").toLong(),
                bootCount = value("boot").toInt(), subId = value("subId").toInt(),
                identityDigest = values.getProperty("identity"),
                recoveryReference = values.getProperty("recovery"),
                phase = OperationPhase.valueOf(value("phase")),
                cleanupConfirmed = value("cleanup").toBooleanStrict(),
                dispatched = values.getProperty("dispatched", "true").toBooleanStrict(),
                targetIdentities = values.stringPropertyNames().filter { it.startsWith("sim.") }
                    .associate { it.removePrefix("sim.").toInt() to value(it) },
            ).also(::validate)
        } catch (failure: Exception) {
            throw IllegalStateException("Operation journal requires recovery", failure)
        }
    }

    @Synchronized fun write(record: OperationRecord) {
        validate(record)
        check(directory.exists() || directory.mkdirs()) { "Cannot create operation journal directory" }
        val values = Properties().apply {
            setProperty("schema", "1")
            setProperty("id", record.operationId)
            setProperty("mode", record.mode.name)
            setProperty("operation", record.operation)
            setProperty("epoch", record.epoch.toString())
            setProperty("boot", record.bootCount.toString())
            setProperty("subId", record.subId.toString())
            setProperty("phase", record.phase.name)
            setProperty("cleanup", record.cleanupConfirmed.toString())
            setProperty("dispatched", record.dispatched.toString())
            record.identityDigest?.let { setProperty("identity", it) }
            record.recoveryReference?.let { setProperty("recovery", it) }
            record.targetIdentities.forEach { (id, digest) -> setProperty("sim.$id", digest) }
        }
        // 先同步文件，再原子替换并同步目录；任何失败都禁止开始设备端写入。
        FileOutputStream(pending).use { output ->
            values.store(output, null)
            output.fd.sync()
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory()
    }

    @Synchronized fun clear() {
        check(!file.exists() || file.delete()) { "Cannot complete operation journal" }
        check(!pending.exists() || pending.delete()) { "Cannot remove interrupted operation journal" }
        if (directory.exists()) syncDirectory()
    }

    private fun syncDirectory() {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }

    private fun validate(record: OperationRecord) {
        check(record.operationId.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid operation id" }
        check(record.targetIdentities.all { (id, digest) -> id >= 0 && digest.matches(Regex("[a-fA-F0-9]{64}")) })
        check(record.mode != BackendMode.UNSET && record.epoch >= 0 && record.bootCount >= -1 && record.subId >= -1)
        check(record.operation.matches(Regex("[A-Z_]{1,40}")))
        check(record.identityDigest == null || record.identityDigest.matches(Regex("[a-fA-F0-9]{64}")))
        check(record.recoveryReference == null || record.recoveryReference.matches(Regex("persistent_volte_[0-9]+\\.json")))
    }
}
