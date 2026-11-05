package com.qingling.writing.core.database.entities
import androidx.room.*
@Entity(tableName="shelf_flags",foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class ShelfFlagsEntity(@PrimaryKey @ColumnInfo(name="book_id") val bookId: String,val pinned: Boolean,val archived: Boolean)
