package free.yhc.priotips;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Random;

import free.yhc.priotips.model.DB;
import free.yhc.priotips.model.DBHelper;

public class PrioTipsService extends Service {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(PrioTipsService.class);

    private static final String ACTION_REMIND_TIP = "free.yhc.priotips.intent.action.REMIND_TIP";
    private static final String ACTION_REVIVE = "free.yhc.priotips.intent.action.REVIVE";
    private static long[] sPrioCoeffs = new long[DBHelper.TipPriority.values().length];

    static {
        for (int i = 0; i < sPrioCoeffs.length; i++)
            sPrioCoeffs[i] = 1 << i; // 1, 2, 4, 8 ...
    }

    static private BroadcastReceiver sReceiver = new BroadcastReceiver() {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            Utils.getUiHandler().post(new Runnable() {
                @Override
                public void
                run() {
                    if (PrioTipsApp.get().isAppReady()) {
                        Intent i = new Intent(Utils.getAppContext(), PrioTipsService.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        i.setAction(ACTION_REMIND_TIP);
                        Utils.getAppContext().startService(i);
                    } else
                        Utils.getUiHandler().postDelayed(this, 500); // retry after 500 ms.
                }
            });
        }
    };

    // This receiver doens't do anything except for start servcie.
    // And 'service' also doesn't do anything for action 'ACTION_REVIVE'.
    // The reason why this receiver should exists, is that sometimes service is killed in force.
    // And in this case, even if service is 'STICKY' it is NOT restarted.
    // To win over this limitation, this service get TIME_TICK broadcast to revive itself.
    public static class ReviveReceiver extends BroadcastReceiver {
        @Override
        public void
        onReceive(Context context, Intent intent) {
            Intent i = new Intent(Utils.getAppContext(), PrioTipsService.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setAction(ACTION_REVIVE);
            Utils.getAppContext().startService(i);
        }
    }

    private static class SelectTipTask extends AsyncTask<Integer, Integer, String> {

        // return -1 for error.
        private int
        selectPriority(Random rand, Cursor c) {
            long[] portions = new long[DBHelper.TipPriority.values().length];
            if (!c.moveToFirst())
                return -1;

            // Count # of items for each priority.
            int index = c.getColumnIndex(DB.ColTip.PRIORITY.getName());
            do {
                ++portions[c.getInt(index)];
            } while (c.moveToNext());

            long total = 0;
            for (int i = 0; i < portions.length; i++) {
                portions[i] *= sPrioCoeffs[i];
                total += portions[i];
            }

            if (0 == total)
                return -1; // There is no item. Just ignore this request.

            // calculate probability slots.
            double[] probabiltyRange = new double[portions.length];
            long count = 0;
            for (int i = 0; i < portions.length; i++) {
                count += portions[i];
                // To avoid side-effect cased by error during floating point operation.
                // To be sure that priority is selected
                probabiltyRange[i] = (count == total)? 1: (double)count / (double)total;
            }

            // select priority.
            double randprob = rand.nextDouble();
            int prionum = 0;
            for (prionum = 0; prionum < portions.length; prionum++) {
                if (probabiltyRange[prionum] > randprob)
                    break;
            }
            // Function design should guarantee priority is chosen at this moment.
            Utils.eAssert(prionum < portions.length);
            return prionum;
        }

        // return false for exceptional case.
        // minmax[0] = min
        // minmax[1] = max
        private boolean
        getAtimeMinMax(long[] minmax, Random rand, Cursor c) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            if (!c.moveToFirst())
                return false;
            int index = c.getColumnIndex(DB.ColTip.ATIME.getName());
            do {
                long atime = c.getLong(index);
                if (atime < min)
                    min = atime;
                if (atime > max)
                    max = atime;
            } while (c.moveToNext());
            minmax[0] = min;
            minmax[1] = max;
            return true;
        }

        @Override
        protected String
        doInBackground(Integer ... dummy) {
            DBHelper dbh = DBHelper.get();

            // Selection Algorithm
            // ===================
            // 1. Select Priority
            //   - each priory slot has portion amount of [priority coefficient] * [# of items]
            //   - priority coefficient is : (very low) 1, 2, 4, 8, 16 (very high) => 31 in total.
            //   - Priority has probability being selected based on portion of each priority.
            //
            // 2. Select atime range.
            //   - Select min / max of atime.
            //   - Choose random float[0.0, 1.0) <-> [min, max)
            //   - Filter tips whose atime is smaller than randomly selected - based on random float -pivot
            //
            // 3. Select one randomly among tips chosen at step (2)
            Random rand = new Random();
            Cursor c = dbh.queryTips();
            Utils.eAssert(null != c);

            // Step 1
            // ------
            int prionum = selectPriority(rand, c);
            c.close();
            if (prionum < 0)
                return null; // exceptional case.

            // Step 2
            // ------
            c = dbh.queryTips(DBHelper.TipPriority.convert(prionum));
            Utils.eAssert(null != c);
            long[] minmax = new long[2];
            if (!getAtimeMinMax(minmax, rand, c)) {
                c.close();
                return null;
            }

            // Step 3
            // ------
            long atimeMin = minmax[0];
            long atimeMax = minmax[1];
            long atimePivot = atimeMin + (long)(rand.nextDouble() * (atimeMax - atimeMin));

            int atimei = c.getColumnIndex(DB.ColTip.ATIME.getName());
            int idi = c.getColumnIndex(DB.ColTip.ID.getName());
            ArrayList<Long> posArr = new ArrayList<>();
            c.moveToFirst(); // This should not fails.
            do {
                if (atimePivot >= c.getLong(atimei))
                    posArr.add(c.getLong(idi));
            } while (c.moveToNext());
            c.close();

            // Now tip is selected!
            DBHelper.Tip tip = dbh.queryTip(posArr.get(rand.nextInt(posArr.size())));

            // Update atime
            tip.atime = dbh.getCurrentAtime();
            dbh.updateTip(tip, DBHelper.F_ATIME);

            return tip.text;
        }

        @Override
        protected void
        onPostExecute(String result) {
            if (null == result
                || result.isEmpty())
                return; // nothing to do.
            Toast.makeText(Utils.getAppContext(), result, Toast.LENGTH_LONG).show();
        }
    }

    public static void
    startService() {
        Intent i = new Intent(Utils.getAppContext(), PrioTipsService.class);
        Utils.getAppContext().startService(i);
    }

    private void
    remindTip() {
        new SelectTipTask().execute();
    }

    @Override
    public IBinder
    onBind(Intent intent) {
        return null;
    }


    @Override
    public void
    onCreate() {
        super.onCreate();
        registerReceiver(sReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int
    onStartCommand(Intent intent, int flags, int startId) {
        if (null == intent
            || null == intent.getAction())
            return START_STICKY; // nothing to do

        switch (intent.getAction()) {
        case ACTION_REMIND_TIP:
            remindTip();
            break;
        case ACTION_REVIVE:
            break;
        }
        // Unknown intent. Just ignore it.
        return START_STICKY;
    }

    @Override
    public void
    onDestroy() {
        super.onDestroy();
    }

}
