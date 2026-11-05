package com.qingling.writing.core.design

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RFC3339 时间字符串 → 本地时区的短展示格式(书架「最近编辑」/ 回收站「删除于」)。
 * 时间只用于展示与提示(docs/05 §3),不参与任何排序或冲突判断;解析失败回退原文。
 * java.time 自 minSdk 26 起可用,无需 desugaring。
 */
fun formatTimestamp(iso: String): String = try {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    formatter.format(Instant.parse(iso))
} catch (e: Exception) {
    iso
}
