package mit.alibaba.nuidemo.Util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "memo_db5";

    private static final String TABLE_Memo = "Memo";
    private static final String KEY_ID = "_id";

    private static final String KEY_README = "readme";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_USERS_TABLE = "CREATE TABLE " + "IF NOT EXISTS " + TABLE_Memo + "("
                + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_README + " TEXT" + ")";
        db.execSQL(CREATE_USERS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    //增
    public void addMemo(String memo) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_README, memo);

        db.insert(TABLE_Memo, null, values);
//        System.out.println("成功添加记忆：" + memo);
        db.close();
    }

    //查
    public List<String> getAllMemo() {
        List<String> memoList = new ArrayList<>();

        String selectQuery = "SELECT * FROM " + TABLE_Memo;

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);//查询出的表的游标

        if (cursor.moveToFirst()) {
            do {
                int ID = cursor.getInt(0);
                String README = cursor.getString(1);
                memoList.add(README);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
//        System.out.println("成功查询所有记忆：" + memoList);
        return memoList;
    }
}