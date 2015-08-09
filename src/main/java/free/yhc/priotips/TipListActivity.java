package free.yhc.priotips;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import free.yhc.priotips.model.DBHelper;

public class TipListActivity extends Activity {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(TipListActivity.class);

    private static final String SDCARD_DIRECTORY =
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/priotips";
    private static final String EXPORTED_DB_PATH = SDCARD_DIRECTORY + "/" + DBHelper.getDbName();


    private TipListAdapter mAdapter = null;

    // Array index is integer value stored at DB
    public static final PrioUxInfo[] sPrioUxInfos = new PrioUxInfo[] {
            new PrioUxInfo(R.drawable.prio_very_low,
                           R.string.very_low),
            new PrioUxInfo(R.drawable.prio_low,
                           R.string.low),
            new PrioUxInfo(R.drawable.prio_normal,
                           R.string.normal),
            new PrioUxInfo(R.drawable.prio_high,
                           R.string.high),
            new PrioUxInfo(R.drawable.prio_very_high,
                           R.string.very_high),
    };

    private static class PrioSpinnerAdapter extends ArrayAdapter<PrioUxInfo> {
        private static final int ADPTER_RESOURCE = R.layout.prio_spinner_adapter;
        private boolean mSimpleMode = false;
        PrioSpinnerAdapter(Context context, boolean simpleMode) {
            super(context, ADPTER_RESOURCE);
            for (PrioUxInfo i : sPrioUxInfos)
                add(i);
            mSimpleMode = simpleMode;
        }

        @Override
        public View
        getView(int position, View convertView, ViewGroup parent) {
            View view;
            LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (null == convertView)
                view = inflater.inflate(ADPTER_RESOURCE, parent, false);
            else
                view = convertView;
            TextView tv = (TextView)view.findViewById(R.id.text);
            if (mSimpleMode)
                tv.setVisibility(View.GONE);
            else
                tv.setText(getItem(position).string);
            ((ImageView)view.findViewById(R.id.icon)).setImageResource(getItem(position).drawable);
            return view;
        }

        @Override
        public View
        getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent);
        }
    }

    private abstract class AsyncHelper<T> extends AsyncTask<Integer, Integer, T> {
        @Override
        protected void
        onPreExecute() {
            setToLoadingView();
        }

        protected abstract T doInBackground(Integer... vs);

        @Override
        protected void
        onCancelled (T result) {
            if (!TipListActivity.this.isFinishing())
                setToMainView();
        }

        @Override
        protected void
        onPostExecute(T result) {
            setToMainView();
        }
    }

    private class ListCursorLoader extends AsyncHelper<Vector<DBHelper.Tip>> {
        @Override
        protected Vector<DBHelper.Tip>
        doInBackground(Integer... vs) {
            Cursor cur = DBHelper.get().queryTips();
            Utils.eAssert(null != cur);

            Vector<DBHelper.Tip> tips = new Vector<DBHelper.Tip>();
            if (cur.moveToFirst()) {
                int i = 0;
                do {
                    tips.add(new DBHelper.Tip(cur));
                } while (cur.moveToNext());
            }
            cur.close();
            return tips;
        }

        @Override
        protected void
        onPostExecute(Vector<DBHelper.Tip> result) {
            super.onPostExecute(result);
            if (null == mAdapter)
                return;
            mAdapter.clear();
            mAdapter.addAll(result);
        }
    }

    private class DbExporter extends AsyncHelper<Boolean> {
        @Override
        protected Boolean
        doInBackground(Integer... vs) {
            boolean ret;
            DBHelper.get().closeDb();
            File exporteddb = new File(EXPORTED_DB_PATH);
            exporteddb.getParentFile().mkdirs(); // don't care return
            ret = Utils.copyFileSafe(getDatabasePath(DBHelper.getDbName()), exporteddb);
            DBHelper.get().openDb();
            return ret;
        }

        @Override
        protected void
        onPostExecute(Boolean result) {
            super.onPostExecute(result);
            // 'Import' option menu can be enabled at this moment.
            invalidateOptionsMenu();
            reloadAdapter();
        }
    }

    private class DbImporter extends AsyncHelper<Boolean> {
        @Override
        protected Boolean
        doInBackground(Integer... vs) {
            boolean ret;
            DBHelper.get().closeDb();
            ret = Utils.copyFileSafe(new File(EXPORTED_DB_PATH),
                    getDatabasePath(DBHelper.getDbName()));
            DBHelper.get().openDb();
            return ret;
        }

        @Override
        protected void
        onPostExecute(Boolean result) {
            super.onPostExecute(result);
            reloadAdapter();
        }
    }

    private class AsyncInsert extends AsyncHelper<DBHelper.Tip> {
        private final DBHelper.TipPriority mPrio;
        private final String mText;
        AsyncInsert(DBHelper.TipPriority prio, String text) {
            mPrio = prio;
            mText = text;
        }

        @Override
        protected DBHelper.Tip
        doInBackground(Integer... vs) {
            long id = DBHelper.get().insertTip(mPrio, mText);
            return DBHelper.get().queryTip(id);
        }

        @Override
        protected void
        onPostExecute(DBHelper.Tip tip) {
            super.onPostExecute(tip);
            if (null == mAdapter)
                return;
            mAdapter.add(tip);
        }
    }

    public static class PrioUxInfo {
        public final int drawable;
        public final int string;
        PrioUxInfo(int aDrawable, int aString) {
            drawable = aDrawable;
            string = aString;
        }
    }

    public interface OnTipUpdateRequest {
        public void onTipUpdateRequest(DBHelper.TipPriority prio, String text);
    }

    private void
    setToLoadingView() {
        findViewById(R.id.loading).setVisibility(View.VISIBLE);
        findViewById(R.id.mainview).setVisibility(View.GONE);
    }

    private void
    setToMainView() {
        findViewById(R.id.loading).setVisibility(View.GONE);
        findViewById(R.id.mainview).setVisibility(View.VISIBLE);
    }

    private void
    reloadAdapter() {
        ListCursorLoader loader = new ListCursorLoader();
        loader.execute();
    }


    private void
    onClickBtnAdd(View view) {
        AlertDialog diag = buildUpdateTipDialog(null, new OnTipUpdateRequest() {
            @Override
            public void
            onTipUpdateRequest(final DBHelper.TipPriority prio, final String text) {
                new AsyncInsert(prio, text).execute();
            }
        });
        diag.show();
    }

    private void
    doExportDb() {
        new DbExporter().execute();
    }

    private void
    onMenuExportDb() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.exportdb);
        String msg = Utils.getResString(R.string.exportdb_desc) + "\n => " + EXPORTED_DB_PATH;
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
                doExportDb();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
            }
        });
        builder.create().show();
    }

    private void
    doImportDb() {
        new DbImporter().execute();
    }

    private void
    onMenuImportDb() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.importdb);
        String msg = Utils.getResString(R.string.importdb_desc) + "\n => " + EXPORTED_DB_PATH;
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
                doImportDb();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
            }
        });
        builder.create().show();
    }

    public AlertDialog
    buildUpdateTipDialog(DBHelper.Tip tip, final OnTipUpdateRequest callback) {
        final View diagView = getLayoutInflater().inflate(R.layout.tiplist_update_tip_dialog, null);
        Spinner spin = (Spinner)diagView.findViewById(R.id.prio_spinner);
        EditText edtext = (EditText)diagView.findViewById(R.id.tip_edit);
        spin.setAdapter(new PrioSpinnerAdapter(this, false));
        if (null != tip) {
            spin.setSelection(tip.prio.getNum());
            edtext.setText(tip.text);
            edtext.setSelection(edtext.getText().length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(diagView);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
                Spinner spin = (Spinner)diagView.findViewById(R.id.prio_spinner);
                EditText edtext = (EditText)diagView.findViewById(R.id.tip_edit);
                callback.onTipUpdateRequest(DBHelper.TipPriority.convert(spin.getSelectedItemPosition()),
                        edtext.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
            }
        });
        AlertDialog diag = builder.create();
        diag.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return diag;
    }

    public void
    onTipChanged(DBHelper.Tip oldTip, DBHelper.Tip newTip, int fieldOpt) {
        DBHelper.get().updateTipAsync(newTip, fieldOpt, null);
    }

    public void
    onTipDeleted(DBHelper.Tip tip) {
        DBHelper.get().deleteTipAsync(tip.id, null);
    }

    @Override
    protected void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiplist);

        mAdapter = new TipListAdapter(this);
        ListView lv = (ListView)findViewById(R.id.list);
        lv.setAdapter(mAdapter);
        lv.setEmptyView(findViewById(R.id.emptylist));

        // Register onClick callbacks
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View view) {
                onClickBtnAdd(view);
            }
        });

        reloadAdapter();
    }

    @Override
    public boolean
    onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tiplist_option, menu);
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            menu.findItem(R.id.exportdb).setEnabled(false);
        else
            menu.findItem(R.id.exportdb).setEnabled(true);
        if (!new File(EXPORTED_DB_PATH).exists())
            menu.findItem(R.id.importdb).setEnabled(false);
        else
            menu.findItem(R.id.importdb).setEnabled(true);
        return true;
    }

    @Override
    public boolean
    onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.exportdb:
            onMenuExportDb();
            return true;
        case R.id.importdb:
            onMenuImportDb();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void
    onDestroy() {
        super.onDestroy();
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }

    @Override
    public void
    onBackPressed() {
        super.onBackPressed();
    }
}
