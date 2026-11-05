package com.qingling.writing.core.model

data class AutoBackupState(val enabled: Boolean = false, val folder: String = "", val hours: Int = 6,
    val lastSuccess: Long = 0, val lastAttempt: Long = 0, val error: String = "", val scheduleError: String = "")

object AutoBackupPolicy {
    const val HOUR = 3_600_000L
    fun due(state: AutoBackupState, now: Long): Boolean = state.enabled && state.folder.isNotEmpty() &&
        (state.lastSuccess == 0L || now - state.lastSuccess >= state.hours * HOUR) &&
        (state.lastAttempt == 0L || now - state.lastAttempt >= HOUR / 4)
    fun summary(state: AutoBackupState, now: Long): String = when {
        state.lastSuccess == 0L -> "自动备份尚未成功过"
        now - state.lastSuccess < HOUR -> "自动备份：最近 1 小时内成功"
        now - state.lastSuccess < 24 * HOUR -> "距上次自动备份 ${(now - state.lastSuccess) / HOUR} 小时"
        else -> "距上次自动备份 ${(now - state.lastSuccess) / (24 * HOUR)} 天"
    }
}
