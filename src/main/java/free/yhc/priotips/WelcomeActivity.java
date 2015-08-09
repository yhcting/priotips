package free.yhc.priotips;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class WelcomeActivity extends Activity {
    private static final boolean DBG = false;
    private static final Utils.Logger P = new Utils.Logger(WelcomeActivity.class);

    @Override
    protected void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        PrioTipsApp.get().addAppReadyListener(new PrioTipsApp.OnAppReadyListener() {
            @Override
            public void
            onAppReadyListener() {
                startActivity(new Intent(WelcomeActivity.this, TipListActivity.class));
                finish();
            }
        });
    }

    @Override
    public void
    onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Do nothing!
    }
}
