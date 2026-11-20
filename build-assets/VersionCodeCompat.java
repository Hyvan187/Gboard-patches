/*
 * Copyright 2026 OpenAI adaptation for Samsung Email 6.2.16.5.
 * Based on the algorithm in Morphe's Disable Play Store updates patch:
 * https://github.com/MorpheApp/morphe-patches/pull/2470
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This code does not alter the installed package's real store origin. It
 * preserves the expected Samsung Email version in in-app PackageInfo reads
 * when the Android manifest version is raised to prevent Play Store updates.
 */
package org.ungoogled.samsungemail;

import android.content.pm.PackageInfo;

/** Original manifest versionCode: Samsung Email 6.2.16.5 (build 621600005). */
@SuppressWarnings({"unused", "deprecation"})
public final class VersionCodeCompat {
    private static final int ORIGINAL_VERSION_CODE = 621600005;

    private VersionCodeCompat() {}

    public static int getVersionCode(PackageInfo info) {
        int versionCode = info.versionCode;
        return versionCode == Integer.MAX_VALUE ? ORIGINAL_VERSION_CODE : versionCode;
    }

    public static long getLongVersionCode(PackageInfo info) {
        long versionCode = info.getLongVersionCode();
        long lower = versionCode & 0xFFFFFFFFL;
        return lower == Integer.MAX_VALUE
                ? (versionCode & 0xFFFFFFFF00000000L) | (ORIGINAL_VERSION_CODE & 0xFFFFFFFFL)
                : versionCode;
    }
}
