package com.qingling.writing.core.model

/**
 * 游客空间(local-schema.sql 的 spaces 表,kind=GUEST、account_id 为空)。
 * M1–M4 只有这一个空间;账号空间(kind=ACCOUNT)随 M5 引入,UUID 为身份。
 * 常量 id:空间本身无用户语义,固定值便于 ensureGuest 幂等(INSERT OR IGNORE)。
 */
object GuestSpace {
    const val ID = "e5a70000-0000-4000-8000-c0ffee000001"
    const val KIND_GUEST = "GUEST"
}
