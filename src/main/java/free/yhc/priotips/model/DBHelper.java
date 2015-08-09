package free.yhc.priotips.model;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import free.yhc.priotips.Utils;

public class DBHelper {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(DBHelper.class);

    private static final DBHelper sInstance = new DBHelper(); /* singleton instance */

    public static final int F_ID_OFF    = 0;
    public static final int F_CTIME_OFF = 1;
    public static final int F_ATIME_OFF = 2;
    public static final int F_PRIO_OFF  = 3;
    public static final int F_TEXT_OFF  = 4;

    public static final int F_ID        = 1 << F_ID_OFF;
    public static final int F_CTIME     = 1 << F_CTIME_OFF;
    public static final int F_ATIME     = 1 << F_ATIME_OFF;
    public static final int F_PRIO      = 1 << F_PRIO_OFF;
    public static final int F_TEXT      = 1 << F_TEXT_OFF;
    public static final int F_ALL       = 0xffffffff;

    private final DB mDb = DB.get();
    private final HandlerThread mHThread = new HandlerThread("DBHelper");

    private Handler mHandler = null;

    // This is for future use...
    public interface OnContentChanged {
        public void onTipChanged(long id);
    }

    public interface OnAsyncCursor {
        public void onAsyncCursor(Cursor cursor);
    }

    public interface OnAsyncBoolean {
        public void onAsyncBoolean(Boolean value);
    }

    public interface OnAsyncLong {
        public void onAsyncLong(long value);
    }

    public enum TipPriority {
        VERY_LOW(0),
        LOW(1),
        NORMAL(2),
        HIGH(3),
        VERY_HIGH(4);

        private final int _mN;

        private TipPriority(int n) {
            _mN = n;
        }

        public int getNum() { return _mN; }

        // Convert from number to priority enum.
        public static TipPriority
        convert(int n) {
            for (TipPriority p : TipPriority.values()) {
                if (p.getNum() == n)
                    return p;
            }
            Utils.eAssert(false);
            return null;
        }
    }

    public static class Tip {
        public long id;
        public long ctime; /* seconds */
        public long atime; /* seconds */
        public TipPriority prio;
        public String text;

        public Tip() { }

        public Tip(Tip tip) {
            set(tip);
        }

        public Tip(Cursor c) {
            set(c);
        }

        public void
        set(Tip tip) {
            id = tip.id;
            ctime = tip.ctime;
            atime = tip.atime;
            prio = tip.prio;
            text = tip.text;
        }

        public void
        set(Cursor c) {
            id = c.getLong(c.getColumnIndex(DB.ColTip.ID.getName()));
            ctime = c.getLong(c.getColumnIndex(DB.ColTip.CTIME.getName()));
            atime = c.getLong(c.getColumnIndex(DB.ColTip.ATIME.getName()));
            int n = c.getInt(c.getColumnIndex(DB.ColTip.PRIORITY.getName()));
            prio = TipPriority.convert(n);
            text = c.getString(c.getColumnIndex(DB.ColTip.TEXT.getName()));
        }

        ContentValues
        toContentValues(int fieldOpt) {
            // ID is auto generated and incremented.
            ContentValues cvs = new ContentValues();
            if (0 != (fieldOpt & F_CTIME))
                cvs.put(DB.ColTip.CTIME.getName(), ctime);
            if (0 != (fieldOpt & F_ATIME))
                cvs.put(DB.ColTip.ATIME.getName(), atime);
            if (0 != (fieldOpt & F_PRIO))
                cvs.put(DB.ColTip.PRIORITY.getName(), prio.getNum());
            if (0 != (fieldOpt & F_TEXT))
                cvs.put(DB.ColTip.TEXT.getName(), text);
            return cvs;
        }
    }

    private DBHelper() {
        mHThread.start();
        mHandler = new Handler(mHThread.getLooper());
    }

    public static DBHelper
    get() {
        return sInstance;
    }

    public static String
    getDbName() {
        return DB.getName();
    }

    public void
    openDb() {
        // Not run in UI thread context - DB is opened in background
        Utils.eAssert(!Utils.isUiThread());
        mDb.open();
    }

    public void
    closeDb() {
        // Not run in UI thread context - DB is opened in background
        Utils.eAssert(!Utils.isUiThread());
        mDb.close();
    }

    public Cursor
    queryTips() {
        return mDb.queryTips();
    }

    public Tip
    queryTip(long id) {
        Cursor c = mDb.queryTip(id);
        Tip tip = null;
        if (c.moveToFirst())
            tip = new Tip(c);
        c.close();
        return tip;
    }

    public Cursor
    queryTips(TipPriority prio) {
        return mDb.queryTips(prio.getNum());
    }

    public long
    getCurrentAtime() {
        return System.currentTimeMillis() / 1000;
    }

    public long
    insertTip(TipPriority prio, String text) {
        Tip tip = new Tip();
        tip.ctime = System.currentTimeMillis() / 1000;
        tip.atime = tip.ctime;
        tip.prio = prio;
        tip.text = text;
        return mDb.insertTip(tip.toContentValues(F_ALL));
    }

    // This is sample implementation for async call.
    public boolean
    insertTipAsync(final TipPriority prio, final String text, final OnAsyncLong callback) {
        return mHandler.post(new Runnable() {
            @Override
            public void
            run() {
                long id = insertTip(prio, text);
                if (null != callback)
                    callback.onAsyncLong(id);
            }
        });
    }

    public boolean
    updateTip(Tip tip, int opt) {
        return 1 == mDb.updateTip(tip.id, tip.toContentValues(opt));
    }

    public boolean
    updateTipAsync(final Tip tip, final int opt, final OnAsyncBoolean callback) {
        return mHandler.post(new Runnable() {
            @Override
            public void
            run() {
                Boolean result = updateTip(tip, opt);
                if (null != callback)
                    callback.onAsyncBoolean(result);
            }
        });
    }

    public boolean
    deleteTip(long id) {
        return 1 == mDb.deleteTip(id);
    }

    public boolean
    deleteTipAsync(final long id, final OnAsyncBoolean callback) {
        return mHandler.post(new Runnable() {
            @Override
            public void
            run() {
                Boolean result = deleteTip(id);
                if (null != callback)
                    callback.onAsyncBoolean(result);
            }
        });
    }
}
