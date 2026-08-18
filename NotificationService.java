package com.ghazou.wpp;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * Tracks which package names currently have
 * an active notification, so MainActivity
 * can draw a small red dot on that app's
 * icon in the sidebar and drawer.
 *
 * This only works once the user manually
 * grants "Notification access" for WPP in
 * system Settings -- Android requires that
 * to be a manual step, it can't be granted
 * from code. MainActivity's WPP Settings ->
 * Notification Access menu item opens that
 * screen directly.
 *
 * Also requires a <service> declaration in
 * AndroidManifest.xml -- see the note given
 * alongside this file.
 */
public class NotificationService
        extends NotificationListenerService {

    static final Set<String> active =
            Collections.synchronizedSet(
                    new HashSet<String>());

    @Override
    public void onListenerConnected() {

        super.onListenerConnected();

        refresh();
    }

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn) {

        if (sbn != null &&
                sbn.getPackageName() != null) {

            active.add(
                    sbn.getPackageName());
        }
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn) {

        /*
         * Don't just remove this one package --
         * the same app may have other active
         * notifications. Re-scan instead.
         */

        refresh();
    }

    void refresh() {

        try {

            StatusBarNotification[] list =
                    getActiveNotifications();

            Set<String> fresh =
                    new HashSet<String>();

            if (list != null) {

                for (int i = 0;
                        i < list.length;
                        i++) {

                    if (list[i] != null &&
                            list[i].getPackageName() != null) {

                        fresh.add(
                                list[i].getPackageName());
                    }
                }
            }

            active.clear();
            active.addAll(fresh);

        } catch (Exception e) {
        }
    }

    static boolean has(String pkg) {

        if (pkg == null || pkg.length() == 0)
            return false;

        return active.contains(pkg);
    }
}
