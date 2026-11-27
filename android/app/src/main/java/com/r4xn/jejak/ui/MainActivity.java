package com.r4xn.jejak.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;
import com.r4xn.jejak.rec.Live;
import com.r4xn.jejak.rec.RecordService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Home: Timeline, Live, Places and Settings along the bottom. */
public final class MainActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";
    public static final int TIMELINE = 0, LIVE = 1, PLACES = 2, SETTINGS = 3;
    private final String[] names = {"Timeline", "Live", "Places", "Settings"};
    private final String[] icons = {Icons.TIMELINE, Icons.GPS, Icons.PIN, Icons.SETTINGS};
    private final Tab[] tabs = {new TimelineTab(), new LiveTab(), new PlacesTab(), new SettingsTab()};
    private FrameLayout content;
    private BottomNavigationView nav;
    private int tab;
    private final List<MapCard> maps = new ArrayList<>();
    /** The day the Timeline shows. */
    LocalDate date;

    /** One of the four main pages. */
    public interface Tab {
        View build(MainActivity a);

        /** The page is going away: stop listening and ticking. */
        default void closed() {
        }
    }

    private final ActivityResultLauncher<String[]> askLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> r) -> {
                if (RecordService.allowed(this)) {
                    begin();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Location is needed")
                            .setMessage("Jejak can't record where you go without your location. You can turn it on in this app's settings: Permissions → Location.")
                            .setNegativeButton("Not now", null)
                            .setPositiveButton("Open settings", (d, w) -> openAppSettings())
                            .show();
                }
            });
    private final ActivityResultLauncher<String> askBackground = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> show(tab));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        date = store().today();
        debugExtras(getIntent());
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.PAGE);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        nav = new BottomNavigationView(this);
        nav.setBackgroundColor(android.graphics.Color.WHITE);
        nav.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);
        nav.setItemIconTintList(null);
        Menu m = nav.getMenu();
        for (int i = 0; i < names.length; i++) {
            MenuItem item = m.add(Menu.NONE, i, i, names[i]);
            item.setIcon(tabIcon(i));
        }
        nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        root.addView(nav, Ui.match());
        setContentView(root);
        Ui.fitSystemBars(root);
        tab = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_TAB, 0) : getIntent().getIntExtra(EXTRA_TAB, 0);
        if (savedInstanceState != null && savedInstanceState.containsKey("date")) date = LocalDate.ofEpochDay(savedInstanceState.getLong("date"));
        nav.setSelectedItemId(tab);
    }

    Store store() {
        return App.store();
    }

    private android.graphics.drawable.StateListDrawable tabIcon(int i) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_checked}, Icons.of(icons[i], Ui.BRAND, Ui.dp(this, 24)));
        s.addState(new int[]{}, Icons.of(icons[i], Ui.MUTED, Ui.dp(this, 24)));
        return s;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recording was on but Android stopped it (or the phone restarted without "Allow all the time"): carry on.
        if (store().recording() && !Live.running && RecordService.allowed(this)) {
            try {
                RecordService.start(this);
            } catch (RuntimeException ignored) {
                // tried again next time
            }
        }
        show(tab);
        for (MapCard m : maps) m.resume();
    }

    @Override
    protected void onPause() {
        for (MapCard m : maps) m.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        tabs[tab].closed();
        for (MapCard m : maps) m.close();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        debugExtras(intent);
        if (intent.hasExtra(EXTRA_TAB)) nav.setSelectedItemId(intent.getIntExtra(EXTRA_TAB, 0));
        else if (getIntent().hasExtra(EXTRA_TAB)) nav.setSelectedItemId(getIntent().getIntExtra(EXTRA_TAB, 0));
        else refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_TAB, tab);
        out.putLong("date", date.toEpochDay());
    }

    /** Goes up each time a page is built, so late answers for an old page are dropped. */
    private int page;

    int page() {
        return page;
    }

    public void show(int i) {
        page++;
        tabs[tab].closed();
        for (MapCard m : maps) m.close();
        maps.clear();
        tab = i;
        content.removeAllViews();
        content.addView(tabs[i].build(this));
    }

    public void refresh() {
        show(tab);
    }

    public void goTo(int i) {
        nav.setSelectedItemId(i);
    }

    /** A map on the current page; it is paused and closed with the page. */
    MapCard map(int heightDp) {
        MapCard m = new MapCard(this, heightDp);
        maps.add(m);
        return m;
    }

    // ------------------------------------------------------------------ recording

    public void startRecording() {
        if (RecordService.allowed(this)) {
            begin();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow your location")
                .setMessage("Jejak records where you go so it can show your day on a map. Your readings stay on this phone.\n\n"
                        + "On the next screen choose \"While using the app\" and keep \"Precise\" on. "
                        + "Recording carries on with the screen off while the Recording notice shows.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) -> askLocation.launch(locationPermissions()))
                .show();
    }

    private static String[] locationPermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        return p.toArray(new String[0]);
    }

    private void begin() {
        store().setRecording(true);
        try {
            RecordService.start(this);
            Ui.toast(this, "Recording started");
        } catch (RuntimeException e) {
            Ui.toast(this, "Android didn't let Jejak start recording. Please try again.");
        }
        content.postDelayed(this::refresh, 400);
    }

    public void stopRecording() {
        store().setRecording(false);
        RecordService.stop(this);
        Ui.toast(this, "Recording stopped");
        content.postDelayed(this::refresh, 300);
    }

    // ------------------------------------------------------------------ things that stop recording

    static boolean precise(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean always(Context c) {
        return Build.VERSION.SDK_INT < 29
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean locationOn(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(LOCATION_SERVICE);
        return lm != null && LocationManagerCompat.isLocationEnabled(lm);
    }

    static boolean batteryFree(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(POWER_SERVICE);
        return pm == null || pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    void askAlways() {
        if (Build.VERSION.SDK_INT < 29) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow all the time")
                .setMessage("So Jejak can start recording again by itself after the phone restarts, choose \"Allow all the time\" on the next screen.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) -> askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                .show();
    }

    void askBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            openAppSettings();
        }
    }

    void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (RuntimeException e) {
            Ui.toast(this, "Turn on Location in the phone's quick settings.");
        }
    }

    void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    // ------------------------------------------------------------------ screenshots and testing

    /**
     * Debug builds only: {@code --ez sample true --es sampleTime 21:30 --ei day -1 --es open timeline|live|places|settings|points|item:N}.
     */
    private void debugExtras(Intent i) {
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debuggable || i == null) return;
        if (i.hasExtra("sample")) store().setSample(i.getBooleanExtra("sample", false));
        if (i.hasExtra("sampleTime")) store().setSampleTime(LocalTime.parse(i.getStringExtra("sampleTime")));
        date = store().today().plusDays(i.getIntExtra("day", 0));
        String open = i.getStringExtra("open");
        if (open == null) return;
        i.removeExtra("open");
        String what = open.contains(":") ? open.substring(0, open.indexOf(':')) : open;
        String arg = open.contains(":") ? open.substring(open.indexOf(':') + 1) : "";
        switch (what) {
            case "timeline" -> getIntent().putExtra(EXTRA_TAB, TIMELINE);
            case "live" -> getIntent().putExtra(EXTRA_TAB, LIVE);
            case "places" -> getIntent().putExtra(EXTRA_TAB, PLACES);
            case "settings" -> getIntent().putExtra(EXTRA_TAB, SETTINGS);
            case "points" -> startActivity(PointsActivity.open(this, date, 0, 0));
            case "item" -> startActivity(ItemActivity.open(this, date, Integer.parseInt(arg)));
            default -> {
            }
        }
    }
}
