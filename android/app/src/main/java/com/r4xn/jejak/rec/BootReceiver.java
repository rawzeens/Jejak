package com.r4xn.jejak.rec;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.r4xn.jejak.App;

/** Starts recording again after the phone restarts (or the app is updated), if it was on. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (!App.store().recording() || !App.store().restart() || !RecordService.allowed(c)) return;
        try {
            RecordService.start(c);
        } catch (RuntimeException e) {
            // Android didn't allow it from the background; it starts again when the app is opened
        }
    }
}
