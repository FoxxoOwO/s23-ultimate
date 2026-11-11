package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.s23.enhancements.R;

/**
 * Rounded One UI card that stacks {@link OneUiPreferenceRow}s and draws a divider
 * between them (never after the last one).
 */
public class OneUiPreferenceGroup extends LinearLayout {

    public OneUiPreferenceGroup(Context context) {
        this(context, null);
    }

    public OneUiPreferenceGroup(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.oneui_card_bg);
        setClipToOutline(true);
    }

    @Override
    public void addView(View child) {
        super.addView(child);
        refreshDividers();
    }

    private void refreshDividers() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            boolean divider = i < count - 1;
            if (child instanceof OneUiPreferenceRow) {
                ((OneUiPreferenceRow) child).divider(divider);
            } else if (child instanceof OneUiSelectRow) {
                ((OneUiSelectRow) child).divider(divider);
            } else if (child instanceof OneUiInfoRow) {
                ((OneUiInfoRow) child).divider(divider);
            }
        }
    }
}
