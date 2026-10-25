package io.github.asagnc.aurora.fridafixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Separate owned target for explicit final-device spawn/early ART-hook acceptance. */
public final class MainActivity extends Activity {
    public static String marker() { return "unhooked"; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("Aurora Frida fixture: " + marker());
        setContentView(view);
    }
}
