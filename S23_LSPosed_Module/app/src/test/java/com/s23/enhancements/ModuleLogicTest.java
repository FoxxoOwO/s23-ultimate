package com.s23.enhancements;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModuleLogicTest {

    @Test
    public void testIsNewerVersion() {
        assertTrue(AppUpdater.isNewerVersion("v2.8", "2.7"));
        assertTrue(AppUpdater.isNewerVersion("v3.0", "2.9.9"));
        assertTrue(AppUpdater.isNewerVersion("2.7.1", "2.7"));
        assertFalse(AppUpdater.isNewerVersion("v2.7", "2.7"));
        assertFalse(AppUpdater.isNewerVersion("v2.6", "2.7"));
        assertFalse(AppUpdater.isNewerVersion("v2.7-beta", "2.7"));
        assertTrue(AppUpdater.isNewerVersion("v2.8-rc1", "2.7"));
    }

    @Test
    public void testIsAllowedDownloadUrl() {
        assertTrue(AppUpdater.isAllowedDownloadUrl("https://github.com/FoxxoOwO/s23-ultimate/releases/download/v2.7/S23.apk"));
        assertTrue(AppUpdater.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/foo.apk"));
        assertTrue(AppUpdater.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/foo.apk"));
        assertTrue(AppUpdater.isAllowedDownloadUrl("https://api.github.com/repos/FoxxoOwO/s23-ultimate/releases/latest"));

        assertFalse(AppUpdater.isAllowedDownloadUrl("http://github.com/FoxxoOwO/s23-ultimate/foo.apk"));
        assertFalse(AppUpdater.isAllowedDownloadUrl("https://evil.example.com/S23.apk"));
        assertFalse(AppUpdater.isAllowedDownloadUrl("file:///data/local/tmp/foo.apk"));
        assertFalse(AppUpdater.isAllowedDownloadUrl(""));
        assertFalse(AppUpdater.isAllowedDownloadUrl(null));
    }

    @Test
    public void testFormatSpeed() {
        assertEquals("0 B/s", SystemUiHooks.formatSpeed(0L, false));
        assertEquals("512 B/s", SystemUiHooks.formatSpeed(512L, false));
        assertEquals("512B", SystemUiHooks.formatSpeed(512L, true));
        assertEquals("2.5 KB/s", SystemUiHooks.formatSpeed(2560L, false));
        assertEquals("2.5K", SystemUiHooks.formatSpeed(2560L, true));
        assertEquals("250 KB/s", SystemUiHooks.formatSpeed(250L * 1024L, false));
        assertEquals("2.4 MB/s", SystemUiHooks.formatSpeed((long) (2.4 * 1024 * 1024), false));
        assertEquals("2.4M", SystemUiHooks.formatSpeed((long) (2.4 * 1024 * 1024), true));
    }

    @Test
    public void testIsValidPackageName() {
        assertTrue(RecentsMenuHooks.isValidPackageName("com.whatsapp"));
        assertTrue(RecentsMenuHooks.isValidPackageName("org.telegram.messenger"));

        assertFalse(RecentsMenuHooks.isValidPackageName(null));
        assertFalse(RecentsMenuHooks.isValidPackageName(""));
        assertFalse(RecentsMenuHooks.isValidPackageName("com.foo..bar"));
        assertFalse(RecentsMenuHooks.isValidPackageName("com.foo'; rm -rf /"));
        assertFalse(RecentsMenuHooks.isValidPackageName("android"));
        assertFalse(RecentsMenuHooks.isValidPackageName("com.android.systemui"));
        assertFalse(RecentsMenuHooks.isValidPackageName("com.sec.android.app.launcher"));
        assertFalse(RecentsMenuHooks.isValidPackageName("com.s23.enhancements"));
    }
}
