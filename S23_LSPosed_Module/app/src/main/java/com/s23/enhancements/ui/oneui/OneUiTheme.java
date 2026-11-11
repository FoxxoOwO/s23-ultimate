package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.util.TypedValue;

/**
 * Design tokens of the in-app One UI component layer.
 * Single source of truth for colours, spacing and type sizes.
 */
public final class OneUiTheme {

    public static final int BG = 0xFF000000;
    public static final int CARD = 0xFF1C1C1E;
    public static final int DIVIDER = 0xFF27292E;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF8E8E93;
    public static final int ACCENT = 0xFF2388FF;
    public static final int TRACK_OFF = 0xFF3A3A3C;
    public static final int THUMB = 0xFFFFFFFF;

    public static final float TITLE_SP = 17f;
    public static final float SUMMARY_SP = 12f;
    public static final float SECTION_SP = 13f;

    private OneUiTheme() {
    }

    public static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    public static int sp(Context context, float sp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics()));
    }
}
