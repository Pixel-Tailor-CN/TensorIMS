package app.mystery0.ims.tensor.privilege

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

/** 损坏记录没有可信的开机信息，另存检测时的开机计数；不解析或删除原记录。 */
class CorruptJournalRecovery(private val directory: File) {
    private val checkpoint = File(directory, "corrupt-recovery.properties")
    private val temporary = File(directory, "corrupt-recovery.properties.new")
    private data class Detection(val boot: Int, val fingerprint: String, val verifying: Boolean = false)

    @Synchronized fun recordDetection(boot: Int) {
        val fingerprint = fingerprint()
        val previous = detection()
        if (previous == null || previous.fingerprint != fingerprint || (previous.boot < 0 && boot >= 0)) {
            save(Detection(boot, fingerprint))
        }
    }

    @Synchronized fun mayReconnect(currentBoot: Int): Boolean {
        val saved = detection() ?: return false
        return saved.boot >= 0 && currentBoot >= 0 && saved.boot != currentBoot && saved.fingerprint == fingerprint()
    }

    /** 先记录本次核对的开机计数；核对中断或结果未知时，必须再次重启才能重试。 */
    @Synchronized fun beginVerification(currentBoot: Int): String {
        check(mayReconnect(currentBoot)) { "A confirmed reboot is required before journal recovery" }
        val fingerprint = fingerprint()
        save(Detection(currentBoot, fingerprint, verifying = true))
        return fingerprint
    }

    /** 仅供运行时在用户确认、重启及只读核对完成后调用；整目录归档，保留所有证据。 */
    @Synchronized fun archiveVerified(expectedFingerprint: String): File {
        val saved = detection()
        check(saved?.verifying == true && saved.fingerprint == expectedFingerprint && fingerprint() == expectedFingerprint) {
            "Operation journal changed during recovery"
        }
        val parent = requireNotNull(directory.parentFile)
        val archive = File(parent, "${directory.name}_archive_${UUID.randomUUID()}")
        Files.move(directory.toPath(), archive.toPath(), StandardCopyOption.ATOMIC_MOVE)
        sync(parent)
        return archive
    }

    private fun detection(): Detection? = runCatching {
        check(!temporary.exists())
        val values = Properties().apply { checkpoint.inputStream().use(::load) }
        check(values.getProperty("schema") == "1")
        Detection(requireNotNull(values.getProperty("boot")).toInt(), requireNotNull(values.getProperty("fingerprint")),
            requireNotNull(values.getProperty("verifying")).toBooleanStrict())
            .also { check(it.boot >= -1 && it.fingerprint.matches(Regex("[a-f0-9]{64}"))) }
    }.getOrNull()

    private fun save(detection: Detection) {
        check(directory.exists() || directory.mkdirs())
        val values = Properties().apply {
            setProperty("schema", "1")
            setProperty("boot", detection.boot.toString())
            setProperty("fingerprint", detection.fingerprint)
            setProperty("verifying", detection.verifying.toString())
        }
        FileOutputStream(temporary).use { output -> values.store(output, null); output.fd.sync() }
        Files.move(temporary.toPath(), checkpoint.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        sync(directory)
    }

    private fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf("operation.properties", "operation.properties.new").forEach { name ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            val file = File(directory, name)
            digest.update(if (file.exists()) 1.toByte() else 0.toByte())
            if (file.exists()) file.inputStream().use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    digest.update(buffer, 0, size)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sync(directory: File) {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }
}
