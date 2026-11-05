package com.qingling.writing.feature.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingling.writing.core.files.BackupCodec
import com.qingling.writing.core.files.BackupFileStore
import com.qingling.writing.core.files.EncryptedBackup
import com.qingling.writing.core.model.LibraryBackup
import com.qingling.writing.domain.repository.BackupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

enum class BackupNotice { SAVED, CANCELLED, FAILED, INVALID, EMPTY, RESTORED, MISSING, PASSWORD_REQUIRED, PASSWORD_ERROR }
data class BackupUi(val busy: Boolean = false, val awaitingSave: Boolean = false,
    val preview: LibraryBackup? = null, val notice: BackupNotice? = null,
    val restoredBooks: Int = 0, val restoredChapters: Int = 0,val restoredPlanning:Int=0)

class BackupViewModel(private val repository: BackupRepository, private val files: BackupFileStore) : ViewModel() {
    private val _ui = MutableStateFlow(BackupUi())
    val ui = _ui.asStateFlow()
    private var pending: BackupFileStore.Staged? = null
    private var pendingInput: (() -> InputStream?)? = null

    suspend fun beginSave(bookId: String? = null, password: CharArray? = null): String? {
        if (_ui.value.busy || _ui.value.awaitingSave) { password?.fill('\u0000'); return null }
        _ui.value = _ui.value.copy(busy = true, notice = null)
        return try {
            val prepared = withContext(Dispatchers.IO) {
                val snapshot = if(bookId == null) repository.capture() else repository.captureBook(bookId)
                if (snapshot.books.isEmpty() && snapshot.planning.isEmpty()) null else files.stage(snapshot,singleBook = bookId != null,password = password)
            }
            pending = prepared
            _ui.value = _ui.value.copy(busy = false, awaitingSave = prepared != null,
                notice = if (prepared == null) BackupNotice.EMPTY else null)
            prepared?.name
        } catch (failure: CancellationException) {
            _ui.value = _ui.value.copy(busy = false, awaitingSave = false)
            throw failure
        } catch (_: Exception) { _ui.value = _ui.value.copy(busy = false, notice = BackupNotice.FAILED); null }
        finally { password?.fill('\u0000') }
    }

    fun pickerFailed() {
        pending?.file?.delete(); pending = null
        _ui.value = _ui.value.copy(busy = false, awaitingSave = false, notice = BackupNotice.FAILED)
    }

    fun finishSave(openOutput: (() -> OutputStream?)?) {
        if (_ui.value.busy) return
        val staged = pending
        pending = null
        if (openOutput == null) {
            _ui.value = _ui.value.copy(awaitingSave = false, notice = BackupNotice.CANCELLED)
            staged?.file?.delete()
            return
        }
        if (staged == null) {
            _ui.value = _ui.value.copy(awaitingSave = false, notice = BackupNotice.MISSING)
            return
        }
        _ui.value = _ui.value.copy(busy = true, awaitingSave = false)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { files.copyTo(staged, openOutput) }
                _ui.value = _ui.value.copy(notice = BackupNotice.SAVED)
            } catch (failure: CancellationException) { throw failure
            } catch (_: Exception) { _ui.value = _ui.value.copy(notice = BackupNotice.FAILED)
            } finally { staged.file.delete(); _ui.value = _ui.value.copy(busy = false) }
        }
    }

    fun inspect(password: CharArray? = null, openInput: () -> InputStream?) {
        if (_ui.value.busy || _ui.value.awaitingSave) { password?.fill('\u0000'); return }
        _ui.value = _ui.value.copy(busy = true, notice = null, preview = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    PushbackInputStream(requireNotNull(openInput()) { "BACKUP_UNREADABLE" }, 4).use {
                        if (EncryptedBackup.isEncrypted(it)) {
                            if (password == null) throw PasswordNeeded()
                            EncryptedBackup.read(it, password)
                        } else { require(password == null) { "BACKUP_CHANGED" }; BackupCodec().read(it) }
                    }
                }
                pendingInput = null
                _ui.value = _ui.value.copy(preview = snapshot)
            } catch (failure: CancellationException) { throw failure
            } catch (_: PasswordNeeded) { pendingInput = openInput; _ui.value = _ui.value.copy(notice = BackupNotice.PASSWORD_REQUIRED)
            } catch (_: Exception) {
                if (password != null) { pendingInput = openInput; _ui.value = _ui.value.copy(notice = BackupNotice.PASSWORD_ERROR) }
                else { pendingInput = null; _ui.value = _ui.value.copy(notice = BackupNotice.INVALID) }
            } finally { password?.fill('\u0000'); _ui.value = _ui.value.copy(busy = false) }
        }
    }

    fun retryPassword(password: CharArray) { val input = pendingInput; if (input == null) { password.fill('\u0000'); return }; inspect(password, input) }
    fun cancelPassword() { pendingInput = null; _ui.value = _ui.value.copy(notice = null) }
    private class PasswordNeeded : Exception()

    fun restore() {
        if (_ui.value.busy || _ui.value.awaitingSave) return
        val snapshot = _ui.value.preview ?: return
        _ui.value = _ui.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.restoreCopies(snapshot) }
                _ui.value = _ui.value.copy(preview = null, notice = BackupNotice.RESTORED,
                    restoredBooks = result.books, restoredChapters = result.chapters,restoredPlanning=snapshot.planning.size)
            } catch (failure: CancellationException) { throw failure
            } catch (_: Exception) { _ui.value = _ui.value.copy(notice = BackupNotice.FAILED)
            } finally { _ui.value = _ui.value.copy(busy = false) }
        }
    }
}
