package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/** Accent-coloured section label that separates One UI preference cards. */
public final class OneUiSectionHeader extends TextView {

    public OneUiSectionHeader(Context context, CharSequence text) {
        this(context, null, text);
    }

    public OneUiSectionHeader(Context context, android.util.AttributeSet attrs) {
        this(context, attrs, null);
    }

    private OneUiSectionHeader(Context context, android.util.AttributeSet attrs, CharSequence text) {
        super(context, attrs);
        setText(text);
        setTextColor(OneUiTheme.ACCENT);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.SECTION_SP);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setPadding(OneUiTheme.dp(context, 4), OneUiTheme.dp(context, 22),
                OneUiTheme.dp(context, 4), OneUiTheme.dp(context, 8));
    }
}
