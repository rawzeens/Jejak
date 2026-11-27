package com.r4xn.jejak;

import android.app.Application;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

/** Starts the app and keeps the settings and readings for every screen. */
public final class App extends Application {
    private static Store store;

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
        // Map pictures: say who is asking (OpenStreetMap's rule) and keep the saved pictures small, in the cache.
        IConfigurationProvider c = Configuration.getInstance();
        c.setUserAgentValue(getPackageName() + "/1.0");
        File base = new File(getCacheDir(), "osmdroid");
        c.setOsmdroidBasePath(base);
        c.setOsmdroidTileCache(new File(base, "tiles"));
        c.setTileFileSystemCacheMaxBytes(60L * 1024 * 1024);
        c.setTileFileSystemCacheTrimBytes(45L * 1024 * 1024);
    }

    public static Store store() {
        return store;
    }
}
