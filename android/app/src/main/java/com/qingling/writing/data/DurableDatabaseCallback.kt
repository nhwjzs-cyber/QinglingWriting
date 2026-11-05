package com.qingling.writing.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** A successful WAL commit must survive an Android system crash, not only an app crash. */
object DurableDatabaseCallback:RoomDatabase.Callback(){
    override fun onOpen(db:SupportSQLiteDatabase){
        super.onOpen(db)
        db.execSQL("PRAGMA synchronous=FULL")
    }
}
