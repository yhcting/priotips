package free.yhc.priotips;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class Utils {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Logger P = new Logger(Utils.class);

    // Even if these two variables are not 'final', those should be handled like 'final'
    //   because those are set only at init() function, and SHOULD NOT be changed.
    private static Context sAppContext  = null;
    private static Handler sUiHandler   = null;

    private enum LogLV{
        V ("[V]", 6),
        D ("[D]", 5),
        I ("[I]", 4),
        W ("[W]", 3),
        E ("[E]", 2),
        F ("[F]", 1);

        private String pref; // prefix string
        private int    pri;  // priority
        LogLV(String aPref, int aPri) {
            pref = aPref;
            pri = aPri;
        }

        @SuppressWarnings("unused")
        String pref() {
            return pref;
        }

        @SuppressWarnings("unused")
        int pri() {
            return pri;
        }
    }

    private static void
    log(Class<?> cls, LogLV lv, String msg) {
        if (null == msg)
            return;

        StackTraceElement ste = Thread.currentThread().getStackTrace()[5];
        msg = ste.getClassName() + "/" + ste.getMethodName() + "(" + ste.getLineNumber() + ") : " + msg;

        switch(lv) {
            case V: Log.v(cls.getSimpleName(), msg); break;
            case D: Log.d(cls.getSimpleName(), msg); break;
            case I: Log.i(cls.getSimpleName(), msg); break;
            case W: Log.w(cls.getSimpleName(), msg); break;
            case E: Log.e(cls.getSimpleName(), msg); break;
            case F: Log.wtf(cls.getSimpleName(), msg); break;
        }
    }

    @SuppressWarnings("unused")
    public static class Logger {
        private final Class<?> _mCls;
        public Logger(Class<?> cls) {
            _mCls = cls;
        }
        // For logging
        public void v(String msg) { log(_mCls, LogLV.V, msg); }
        public void d(String msg) { log(_mCls, LogLV.D, msg); }
        public void i(String msg) { log(_mCls, LogLV.I, msg); }
        public void w(String msg) { log(_mCls, LogLV.W, msg); }
        public void e(String msg) { log(_mCls, LogLV.E, msg); }
        public void f(String msg) { log(_mCls, LogLV.F, msg); }
    }

    public static void
    init(Context appContext) {
        sAppContext = appContext;
        sUiHandler = new Handler();
    }

    // Assert
    public static void
    eAssert(boolean cond) {
        if (!cond)
            throw new AssertionError();
    }

    public static Context
    getAppContext() {
        return sAppContext;
    }

    public static Resources
    getResources() {
        return getAppContext().getResources();
    }

    public static String
    getResString(int id) {
        return getResources().getString(id);
    }

    public static Handler
    getUiHandler() {
        return sUiHandler;
    }

    public static boolean
    isUiThread(Thread thread) {
        return thread == sUiHandler.getLooper().getThread();
    }

    public static boolean
    isUiThread() {
        return isUiThread(Thread.currentThread());
    }

    public static void
    copyFile(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    public static boolean
    copyFileSafe(File src, File dst) {
        if (null == src || null == dst)
            return false;

        File backup = new File(dst.getAbsoluteFile() + ".backup__");
        try {
            if (dst.exists() && !dst.renameTo(backup))
                return false;
        } catch (Exception e) {
            return false;
        }

        try {
            copyFile(src, dst);
        } catch (Exception e) {
            try {
                // We tried our best. Failures on delete and rename are ignored.
                //noinspection ResultOfMethodCallIgnored
                dst.delete();
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(dst);
            } catch (Exception ignored) { }
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        backup.delete();
        return true;
    }
}
