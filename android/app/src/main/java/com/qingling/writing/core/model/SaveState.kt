package com.qingling.writing.core.model

/**
 * 本地保存状态(contracts/WritingContracts.kt 的 SaveResult / LocalSaveState;M0 复核 F4 要求 M1 补齐建模)。
 * M2 的 SaveCoordinator 以此驱动「保存中 / 已保存到本机 / 保存失败」三态(docs/01 P05);
 * 失败时调用方保留正文、不得导航走(docs/01 §3.6)。
 */
sealed interface LocalSaveState {
    data object Idle : LocalSaveState
    data class Saving(val chapterId: String, val latestEditSeq: Long) : LocalSaveState
    data class Saved(val chapterId: String, val committedEditSeq: Long, val savedAt: String) : LocalSaveState
    data class Failed(val chapterId: String, val pendingEditSeq: Long, val reason: SaveFailure) : LocalSaveState
}

enum class SaveFailure { DISK_FULL, STORAGE_UNAVAILABLE, DELETED, STALE_SEQUENCE, UNKNOWN }

sealed interface SaveResult {
    data class Committed(val chapterId: String, val editSeq: Long, val bookLocalSeq: Long) : SaveResult
    data class Failed(val chapterId: String, val editSeq: Long, val reason: SaveFailure) : SaveResult
}
