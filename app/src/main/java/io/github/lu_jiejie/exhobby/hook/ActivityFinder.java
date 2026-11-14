package io.github.lu_jiejie.exhobby.hook;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

/** Resolves the Activity behind an arbitrary host context. */
public final class ActivityFinder {

    private ActivityFinder() {
    }

    public static Activity of(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) {
                return (Activity) c;
            }
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
