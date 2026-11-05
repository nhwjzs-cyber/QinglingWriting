package com.qingling.writing.data

import com.qingling.writing.core.files.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class IdleBackupState(val files: List<File> = emptyList(), val busy: Boolean = false, val error: String? = null)

/** Separate from SQLite and periodic external backups. A failed new generation never prunes old ones. */
class IdleBackupController(private val root: File, private val writer: AutoBackupWriter,
    private val syncDirectory:(File)->Unit=DurableFiles::syncDirectory,
    private val external: suspend () -> Unit) {
    private val _state = MutableStateFlow(IdleBackupState())
    val state = _state.asStateFlow()
    private fun copies() = root.listFiles()?.filter { it.isFile && it.name.endsWith(".qlbackup") }
        ?.sortedByDescending { it.name }.orEmpty()
    @OptIn(FlowPreview::class)
    suspend fun monitor(versions: Flow<List<String>>) {
        try {
            if(!root.isDirectory){
                check(root.mkdirs() || root.isDirectory)
                syncDirectory(root.parentFile ?: root)
            }
        }catch(_:Exception){
            _state.value=IdleBackupState(copies(),error="停笔备份目录暂不可写，请检查存储。已有副本保留。")
            return
        }
        root.listFiles()?.filter { it.isFile && it.name.endsWith(".partial") }?.forEach { it.delete() }
        var generation = copies().firstOrNull()?.name?.substringBefore('-')?.toLongOrNull() ?: 0L
        _state.value = IdleBackupState(copies())
        versions.distinctUntilChanged().debounce(5_000).catch { failure ->
            if (failure is CancellationException) throw failure
            _state.value = IdleBackupState(copies(), error = "停笔备份监听中断，请重启应用并检查本机存储。")
        }.collect { rows ->
            if (rows.isEmpty()) return@collect
            val hash = MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val checkpoint = File(root, "checkpoint")
            val saved = runCatching { checkpoint.readText().split('|', limit = 2) }.getOrNull()
            if (saved != null && saved.firstOrNull() == hash && copies().any { it.name == saved.getOrNull(1) }) return@collect
            _state.value = _state.value.copy(busy = true, error = null)
            generation = maxOf(System.currentTimeMillis(), generation + 1)
            val pending = File(root, "%019d-%s.partial".format(generation, UUID.randomUUID()))
            try {
                check(root.usableSpace >= 128L * 1024 * 1024) { "Keep free space for manuscript saves" }
                writer.write {
                    object : AutoBackupTarget {
                        override fun output() = object : FileOutputStream(pending) {
                            override fun close() { try { fd.sync() } finally { super.close() } }
                        }
                        override fun input() = pending.inputStream()
                        override fun delete() { pending.delete() }
                    }
                }
                val published = File(root, pending.name.removeSuffix(".partial") + ".qlbackup")
                check(pending.renameTo(published)) { "Cannot publish verified backup" }
                // Publish the directory entry durably before pruning any older backup.
                syncDirectory(root)
                // A missing checkpoint only causes another backup; never a missed generation.
                runCatching { checkpoint.writeText("$hash|${published.name}") }
                copies().drop(20).forEach { it.delete() }
                syncDirectory(root)
                _state.value = IdleBackupState(copies())
            } catch (e: CancellationException) { pending.delete(); throw e
            } catch (_: Exception) {
                pending.delete()
                _state.value = IdleBackupState(copies(), error = "本机停笔备份未成功，请检查空间并手动备份。旧副本保留。")
            }
            // Local and external failures are independent. External controller reports its own result.
            try { external() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
}
