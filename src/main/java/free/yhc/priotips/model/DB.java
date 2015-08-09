package free.yhc.priotips.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import free.yhc.priotips.Utils;

import static free.yhc.priotips.Utils.eAssert;

public class DB {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(DB.class);

    private static final String NAME = "priotips.db";
    private static final int    VERSION = 1;
    private static final String TABLE_TIPS = "tips";
    private static final String TABLE_HISTORY = "history";

    private static final DB sInstance = new DB(); /* singleton instance */

    private final DBOpenHelper mDbOpenHelper = new DBOpenHelper();
    private SQLiteDatabase mDb = null;

    private interface Col {
        String getName();
        String getType();
        String getDefault();
        String getConstraint();
    }

    private class DBOpenHelper extends SQLiteOpenHelper {
        DBOpenHelper() {
            super(Utils.getAppContext(), NAME, null, getVersion());
        }

        @Override
        public void
        onCreate(SQLiteDatabase db) {
            // Not run in UI thread context - DB is opened in background
            db.execSQL(buildTableSQL(getTipTableName(), ColTip.values()));
            db.execSQL(buildTableSQL(getHistoryTableName(), ColHistory.values()));
        }

        @Override
        public void
        onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Not run in UI thread context - DB is opened in background
            // Not Implemented yet.
            eAssert(false);
        }

        @Override
        public void
        close() {
            super.close();
        }

        @Override
        public void
        onOpen(SQLiteDatabase db) {
            super.onOpen(db);
        }
    }

    public enum ColTip implements Col {
        PRIORITY    ("priority",        "integer",  null,   "not null"),
        CTIME       ("ctime",           "integer",  null,   "not null"),
        ATIME       ("atime",           "integer",  null,   "not null"),
        TEXT        ("text_",           "text",     null,   "not null"),

        ID          (BaseColumns._ID,   "integer",  null,   "primary key autoincrement");
        private ColTip(String name, String type, String def, String constraint) {
            _mName = name;
            _mType = type;
            _mDefault = def;
            _mConstraint = constraint;
        }

        private final String _mName;
        private final String _mType;
        private final String _mDefault;
        private final String _mConstraint;

        public String getName() { return _mName; }
        public String getType() { return _mType; }
        public String getConstraint() { return _mConstraint; }
        public String getDefault() { return _mDefault; }
    }

    public enum ColHistory implements Col {
        TIPID       ("tipid",           "integer",  null,   "not null"),
        TYPE        ("type",            "integer",  null,   "not null"),
        TIME        ("time",            "integer",  null,   "not null"),
        FROM        ("from_",           "text",     null,   "not null"), // 'from' is sqlite reserved word
        TO          ("to_",             "text",     null,   "not null"), // 'to' is sqlite reserved word
        ID          (BaseColumns._ID,   "integer",  null,   "primary key autoincrement, "
                + "FOREIGN KEY(tipid) REFERENCES " + DB.getTipTableName() + "(" + ColTip.ID.getName() + ")");

        private final String _mName;
        private final String _mType;
        private final String _mDefault;
        private final String _mConstraint;

        private ColHistory(String name, String type, String def, String constraint) {
            _mName = name;
            _mType = type;
            _mDefault = def;
            _mConstraint = constraint;
        }

        public String getName() { return _mName; }
        public String getType() { return _mType; }
        public String getConstraint() { return _mConstraint; }
        public String getDefault() { return _mDefault; }
    }

    private static int
    getVersion() {
        return VERSION;
    }

    private static String
    getTipTableName() {
        return TABLE_TIPS;
    }

    private static String
    getHistoryTableName() {
        return TABLE_HISTORY;
    }

    /**
     * Convert Col[] to string[] of column's name
     * @param cols
     * @return
     */
    private static String[]
    getColNames(Col[] cols) {
        String[] strs = new String[cols.length];
        for (int i = 0; i < cols.length; i++)
            strs[i] = cols[i].getName();
        return strs;
    }

    // ========================================================================
    //
    //
    //
    // ========================================================================
    private static String
    buildColumnDef(DB.Col col) {
        String defaultv = col.getDefault();
        if (null == defaultv)
            defaultv = "";
        else
            defaultv = " DEFAULT " + defaultv;

        String constraint = col.getConstraint();
        if (null == constraint)
            constraint = "";
        return col.getName() + " "
                + col.getType() + " "
                + defaultv + " "
                + constraint;
    }

    /**
     * Get SQL statement for creating table
     * @param table
     *   name of table
     * @param cols
     *   columns of table.
     * @return
     */
    private static String
    buildTableSQL(String table, DB.Col[] cols) {
        String sql = "CREATE TABLE " + table + " (";
        for (Col col : cols)
            sql += buildColumnDef(col) + ", ";
        sql += ");";
        sql = sql.replace(", );", ");");
        return sql;
    }

    // ======================================================================
    //
    // Creation / Upgrade
    //
    // ======================================================================
    private DB() {
    }

    static String
    getName() {
        return NAME;
    }

    static DB
    get() {
        return sInstance;
    }

    void
    open() {
        mDb = mDbOpenHelper.getWritableDatabase();
    }

    void
    close() {
        mDb = null;
        mDbOpenHelper.close();
    }

    Cursor
    queryTips() {
        if (DBG) P.d("Enter");
        String sql = "SELECT * FROM " + getTipTableName()
                    + " ORDER BY " + ColTip.PRIORITY.getName() + " DESC";
        return mDb.rawQuery(sql, null);
    }

    Cursor
    queryTips(int prio) {
        if (DBG) P.d("Enter");
        String sql = "SELECT * FROM " + getTipTableName()
                + " WHERE " + ColTip.PRIORITY.getName() + " = " + prio;
        return mDb.rawQuery(sql, null);
    }

    Cursor
    queryTip(long id) {
        String sql = "SELECT * FROM "  + getTipTableName()
                    + " WHERE " + ColTip.ID.getName() + " = " + id;
        return mDb.rawQuery(sql, null);
    }

    int
    updateTip(long id, ContentValues cvs) {
        if (DBG) P.d("Enter : " + id);
        return mDb.update(getTipTableName(),
                          cvs,
                          ColTip.ID.getName() + " = " + id,
                          null);
    }

    long
    insertTip(ContentValues cvs) {
        if (DBG) P.d("Enter");
        return mDb.insert(getTipTableName(), null, cvs);
    }

    int
    deleteTip(long id) {
        if (DBG) P.d("Enter : " + id);
        return mDb.delete(getTipTableName(),
                          ColTip.ID.getName() + " = " + id,
                          null);
    }
}
