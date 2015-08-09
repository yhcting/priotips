package free.yhc.priotips;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.AsyncTask;

import java.util.Iterator;
import java.util.LinkedList;

import free.yhc.priotips.model.DBHelper;

public class PrioTipsApp extends Application {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(Application.class);

    private static PrioTipsApp sInstance = null;

    // mLAppReady is set to null when app is ready.
    private LinkedList<OnAppReadyListener> mLAppReady = new LinkedList<>();

    private class PrepareAppAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer
        doInBackground(Integer... urls) {
            DBHelper.get().openDb();
            return 0;
        }

        @Override
        protected void
        onPostExecute(Integer result) {
            Utils.eAssert(Utils.isUiThread());
            Iterator<OnAppReadyListener> itr = mLAppReady.iterator();
            while (itr.hasNext()) {
                itr.next().onAppReadyListener();
            }
            mLAppReady = null;
        }
    }

    public interface OnAppReadyListener {
        public void onAppReadyListener();
    }

    public static PrioTipsApp
    get() {
        return sInstance;
    }

    // Run in background thread to prepare App.
    private void
    prepareAppBackground() {
        new PrepareAppAsyncTask().execute();
    }

    public boolean
    isAppReady() {
        Utils.eAssert(Utils.isUiThread());
        return (null == mLAppReady);
    }

    public void
    addAppReadyListener(final OnAppReadyListener l) {
        Utils.eAssert(Utils.isUiThread());
        if (!isAppReady())
            mLAppReady.add(l);
        else {
            Utils.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    l.onAppReadyListener();
                }
            });
        }
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void
    onCreate() {
        super.onCreate();
        sInstance = this;
        Context context = getApplicationContext();
        Utils.init(context);
        PrioTipsService.startService();
        // Run DB open in background - opening DB may take long~
        prepareAppBackground();
    }

    @Override
    public void
    onLowMemory() {
        super.onLowMemory();
    }

}
