package free.yhc.priotips;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import free.yhc.priotips.model.DBHelper;

// For best performance, ArrayAdapter is used instead of CursorAdapter.
// (We can minimize Filesystem access.)
//
// Tips is data composed of integer and text.
// So, even if there are quite large number of items in database, loading all of them is still reasonable.
// sizeof(DBHelper.Tip) := 8 * 4 + text-len * 2
// Let's assume that average of text-len is 100 (large enough I think.)
// Then, each item occupies 32 + 200 = 232 bytes.
// Even if we have 10,000 items, it only requires, 2,320,000 := 2M
public class TipListAdapter extends ArrayAdapter<DBHelper.Tip> {
    @SuppressWarnings("unused")
    private static final boolean DBG = false;
    @SuppressWarnings("unused")
    private static final Utils.Logger P = new Utils.Logger(TipListAdapter.class);

    private static final int LAYOUT = R.layout.tiplist_adapter;

    private TipListActivity mActivity;
    private LayoutInflater mInflater;

    public TipListAdapter(TipListActivity activity) {
        super(activity, LAYOUT);
        mActivity = activity;
        mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private void
    updateTipPrioData(View rowv, DBHelper.Tip tip, DBHelper.TipPriority newPrio) {
        ImageView priov = (ImageView)rowv.findViewById(R.id.prio);
        priov.setImageResource(TipListActivity.sPrioUxInfos[newPrio.getNum()].drawable);
        tip.prio = newPrio;
    }
    private void
    updateTipTextData(View rowv, DBHelper.Tip tip, String newText) {
        TextView textv = (TextView)rowv.findViewById(R.id.text);
        TextView textbigv = (TextView)rowv.findViewById(R.id.text_big);
        textv.setText(newText);
        textbigv.setText(newText);
        tip.text = newText;
    }

    private void
    updateTipPrio(View rowv, DBHelper.Tip tip, DBHelper.TipPriority newPrio) {
        if (tip.prio.equals(newPrio))
            return; // nothign to do.
        DBHelper.Tip oldTip = new DBHelper.Tip(tip);
        updateTipPrioData(rowv, tip, newPrio);
        mActivity.onTipChanged(oldTip, tip, DBHelper.F_PRIO);
    }

    private void
    updateTipPrioAndText(View rowv, DBHelper.Tip tip, DBHelper.TipPriority newPrio, String newText) {
        int fieldOpt = 0;
        DBHelper.Tip oldTip = new DBHelper.Tip(tip);
        if (!tip.prio.equals(newPrio)) {
            fieldOpt |= DBHelper.F_PRIO;
            updateTipPrioData(rowv, tip, newPrio);
        }
        if (!tip.text.equals(newText)) {
            fieldOpt |= DBHelper.F_TEXT;
            updateTipTextData(rowv, tip, newText);
        }
        if (0 == fieldOpt)
            return; // nothing to notify.
        mActivity.onTipChanged(oldTip, tip, fieldOpt);
    }

    private boolean
    onPrioMenuItem(View rowv,
                   DBHelper.Tip tip,
                   MenuItem menuItem) {
        DBHelper.TipPriority prio = null;
        switch (menuItem.getItemId()) {
        case R.id.very_low:
            prio = DBHelper.TipPriority.VERY_LOW;
            break;
        case R.id.low:
            prio = DBHelper.TipPriority.LOW;
            break;
        case R.id.normal:
            prio = DBHelper.TipPriority.NORMAL;
            break;
        case R.id.high:
            prio = DBHelper.TipPriority.HIGH;
            break;
        case R.id.very_high:
            prio = DBHelper.TipPriority.VERY_HIGH;
            break;
        }

        updateTipPrio(rowv, tip, prio);
        return true;
    }

    private void
    bindPrioView(final View rowv,
                 final ImageView priov,
                 final DBHelper.Tip tip) {
        priov.setImageResource(TipListActivity.sPrioUxInfos[tip.prio.getNum()].drawable);
        priov.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                PopupMenu popup = new PopupMenu(mActivity, v);
                popup.getMenuInflater().inflate(R.menu.prio_select, popup.getMenu());
                popup.show();
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean
                    onMenuItemClick(MenuItem menuItem) {
                        return onPrioMenuItem(rowv, tip, menuItem);
                    }
                });
            }
        });
    }

    private void
    bindTextView(@SuppressWarnings("UnusedParameters") final View rowv,
                 final TextView textv,
                 final TextView textbigv,
                 final DBHelper.Tip tip) {
        textv.setText(tip.text);
        textv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                TextView tv = (TextView) v;
                if (2 > tv.getLineCount())
                    return; /* nothing to do */
                v.setVisibility(View.GONE);
                textbigv.setText(((TextView) v).getText());
                textbigv.setVisibility(View.VISIBLE);
            }
        });
        textbigv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                v.setVisibility(View.GONE);
                textv.setVisibility(View.VISIBLE);
            }
        });
    }

    private void
    onOptionMenuItemDelete(final DBHelper.Tip tip) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.delete);
        builder.setMessage(tip.text);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
                remove(tip);
                mActivity.onTipDeleted(tip);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void
            onClick(DialogInterface dialogInterface, int i) {
                // nothing to do.
            }
        });
        builder.create().show();
    }

    private void
    onOptionMenuItemEdit(final View rowv, final DBHelper.Tip tip) {
        AlertDialog diag = mActivity.buildUpdateTipDialog(tip, new TipListActivity.OnTipUpdateRequest() {
            @Override
            public void
            onTipUpdateRequest(DBHelper.TipPriority prio, String text) {
                updateTipPrioAndText(rowv, tip, prio, text);
            }
        });
        diag.show();
    }

    private boolean
    onOptionMenuItem(View rowv, DBHelper.Tip tip, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.delete:
            onOptionMenuItemDelete(tip);
            break;
        case R.id.edit:
            onOptionMenuItemEdit(rowv, tip);
            break;
        }
        return true;
    }

    private void
    bindOptionView(final View rowv,
                   ImageView optv,
                   final DBHelper.Tip tip) {
        optv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void
            onClick(View v) {
                PopupMenu popup = new PopupMenu(mActivity, v);
                popup.getMenuInflater().inflate(R.menu.tip_option, popup.getMenu());
                popup.show();
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean
                    onMenuItemClick(MenuItem menuItem) {
                        return onOptionMenuItem(rowv, tip, menuItem);
                    }
                });
            }
        });

    }

    @Override
    public View
    getView(int position, View convertView, ViewGroup parent) {
        View view = (null == convertView)? mInflater.inflate(LAYOUT, parent, false): convertView;
        DBHelper.Tip tip = getItem(position);

        ImageView priov = (ImageView)view.findViewById(R.id.prio);
        TextView textv = (TextView)view.findViewById(R.id.text);
        TextView textbigv = (TextView)view.findViewById(R.id.text_big);
        ImageView optv = (ImageView)view.findViewById(R.id.option);

        bindPrioView(view, priov, tip);
        bindTextView(view, textv, textbigv, tip);
        bindOptionView(view, optv, tip);
        return view;
    }
}
