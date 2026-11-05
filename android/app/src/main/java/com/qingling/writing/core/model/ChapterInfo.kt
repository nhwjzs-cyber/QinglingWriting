package com.qingling.writing.core.model

/** Persisted chapter metadata; completion remains an explicit user decision. */
data class ChapterInfo(val createdAt:String,val updatedAt:String,val completed:Boolean)
