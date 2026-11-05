package com.qingling.writing

import com.qingling.writing.core.model.*

object BackupFixture {
    const val TIME = "2026-11-01T00:00:00Z"
    fun sample(body: String = "　　原文😀\r\n\n末尾  ") = LibraryBackup(TIME,
        listOf(BackupBook("b", "原书", "作者", "简介\n保留", TIME, TIME, null, null, "c")),
        listOf(BackupVolume("v", "b", "第一卷", 0, TIME, TIME, null, null)),
        listOf(BackupChapter("c", "b", "v", "第一章", body, 0, TIME, TIME, null, null),
            BackupChapter("d", "b", "v", "被删章", "不能丢", 1, TIME, TIME, TIME, "batch")),
        listOf(BackupRevision("r", "c", "第一章", "更早正文😀", "MANUAL", TIME)),
        listOf(BackupPosition("c", 2, 5, 0)))
}
