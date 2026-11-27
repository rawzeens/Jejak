package com.r4xn.jejak.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.r4xn.jejak.App;
import com.r4xn.jejak.Store;

import java.util.ArrayList;
import java.util.List;

/** A page with a title bar and scrolling content. */
public abstract class Screen extends AppCompatActivity {
    protected MaterialToolbar toolbar;
    protected LinearLayout body;
    protected FrameLayout frame;
    protected ScrollView scroll;
    private final List<Object[]> menuItems = new ArrayList<>();
    private final List<MapCard> maps = new ArrayList<>();

    /** What to do on each menu item: [title, icon (or null for the overflow menu), Runnable]. */
    protected void menu(String title, String icon, Runnable action) {
        menuItems.add(new Object[]{title, icon, action});
        invalidateOptionsMenu();
    }

    protected Store store() {
        return App.store();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.PAGE);
        toolbar = new MaterialToolbar(this);
        toolbar.setNavigationIcon(Icons.of(Icons.BACK, Ui.INK, Ui.dp(this, 24)));
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setBackgroundColor(Ui.PAGE);
        setSupportActionBar(toolbar);
        root.addView(toolbar, Ui.match());
        frame = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = Ui.vbox(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(body);
        frame.addView(scroll);
        root.addView(frame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Ui.fitSystemBars(root);
    }

    protected void title(String title, String subtitle) {
        toolbar.setTitle(title);
        toolbar.setSubtitle(subtitle);
    }

    protected void add(View v) {
        body.addView(v);
    }

    /** A map on this page; it is paused and closed along with the page. */
    protected MapCard map(int heightDp) {
        MapCard m = new MapCard(this, heightDp);
        maps.add(m);
        return m;
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (MapCard m : maps) m.resume();
    }

    @Override
    protected void onPause() {
        for (MapCard m : maps) m.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (MapCard m : maps) m.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        for (int i = 0; i < menuItems.size(); i++) {
            Object[] it = menuItems.get(i);
            MenuItem m = menu.add(Menu.NONE, i, i, (String) it[0]);
            if (it[1] != null) {
                m.setIcon(Icons.of((String) it[1], Ui.INK, Ui.dp(this, 24)));
                m.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            } else {
                m.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i >= 0 && i < menuItems.size()) {
            ((Runnable) menuItems.get(i)[2]).run();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
