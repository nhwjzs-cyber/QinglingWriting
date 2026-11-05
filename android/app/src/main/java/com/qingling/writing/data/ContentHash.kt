package com.qingling.writing.data

import java.security.MessageDigest

/** 正文内容 SHA-256(十六进制小写);历史与备份共用同一口径(docs/03 §4)。 */
internal object ContentHash {
    fun of(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
