package com.qingling.writing;
import android.app.Activity;
import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.util.UUID;
import java.util.Base64;
import java.time.Instant;
/** Android-only fixture for the supplied R8 APK; touches only newly allocated test rows. */
public class Upgrade035Instrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try {
        String space;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("writing.db").getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE)) {
            try(android.database.Cursor c=db.rawQuery("PRAGMA user_version",null)){if(!c.moveToFirst()||c.getInt(0)!=8)throw new AssertionError("Expected original schema v8");}
            try(android.database.Cursor c=db.rawQuery("SELECT id FROM spaces WHERE kind='GUEST' LIMIT 1",null)){if(!c.moveToFirst())throw new AssertionError("Guest space missing");space=c.getString(0);}
            String book=UUID.randomUUID().toString(),empty=UUID.randomUUID().toString(),volume=UUID.randomUUID().toString(),chapter=UUID.randomUUID().toString(),note=UUID.randomUUID().toString(),stamp=Instant.now().toString();
            db.beginTransaction();try {
                for(String id:new String[]{book,empty})db.execSQL("INSERT INTO books(id,space_id,title,author,description,created_at,updated_at,deleted_at,deletion_batch_id,local_seq,synced_local_seq,remote_version,local_dirty,last_active_chapter_id) VALUES(?,?,?,?,?,?,?,NULL,NULL,3,0,NULL,1,?)",new Object[]{id,space,id.equals(book)?"增补升级验收·原稿":"增补升级验收·旧空书","作者","",stamp,stamp,id.equals(book)?chapter:null});
                db.execSQL("INSERT INTO volumes VALUES(?,?,?,0,?,?,NULL,NULL)",new Object[]{volume,book,"第一卷",stamp,stamp});
                db.execSQL("INSERT INTO chapters VALUES(?,?,?,?,?,0,7,25,?,?,NULL,NULL)",new Object[]{chapter,book,volume,"第一章","增补升级：中文与 emoji😀 保留。\n\n空行和行末空格  ",stamp,stamp});
                StringBuilder body=new StringBuilder("〔轻灵速记·1〕");String[] fields={chapter,"原稿 · 第一章","2","true","false","false"};for(int i=0;i<fields.length;i++){if(i>0)body.append('|');body.append(Base64.getUrlEncoder().withoutPadding().encodeToString(fields[i].getBytes(java.nio.charset.StandardCharsets.UTF_8)));}body.append("\n来自0.35的原始灵感😀");
                db.execSQL("INSERT INTO planning_entries VALUES(?,?,NULL,NULL,'INSPIRATION',?,?,?,1)",new Object[]{note,book,"原始灵感",body.toString(),stamp});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        getTargetContext().getSharedPreferences("design036-upgrade",0).edit().putBoolean("seeded035",true).commit();
        result.putString("stream","Seeded dedicated original-v8 manuscript, empty book and legacy note rows without modifying existing rows.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream",e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
