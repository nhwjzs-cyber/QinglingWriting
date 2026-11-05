package com.qingling.writing.domain.repository

import com.qingling.writing.core.model.ExportSelection
import com.qingling.writing.core.model.ExportSnapshot

/**
 * 导出快照(contracts/WritingContracts.kt 的 ExportRepository)。
 * capture 只读不写:按目录顺序收集选中章(过滤软删除),不合并或删除数据库中的章。
 * Android URI/OutputStream 适配在 UI 层(ExportScreen),不在 domain。
 */
interface ExportRepository {

    /** 按目录序(卷 position → 章 position)收集选中存活章;不含软删除项。 */
    suspend fun capture(selection: ExportSelection): ExportSnapshot
}
