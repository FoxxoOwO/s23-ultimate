package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.s23.enhancements.R;

/**
 * One UI hero card: device model, firmware line and a status pill.
 */
public class OneUiDeviceCard extends LinearLayout {

    private final TextView mTitleView;
    private final TextView mSubtitleView;
    private final TextView mPillLabel;
    private final LinearLayout mPill;

    public OneUiDeviceCard(Context context) {
        this(context, null);
    }

    public OneUiDeviceCard(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.oneui_card_hero_bg);
        int padH = OneUiTheme.dp(context, 18);
        setPadding(padH, OneUiTheme.dp(context, 16), padH, OneUiTheme.dp(context, 16));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        textColumn.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mTitleView = new TextView(context);
        mTitleView.setTextColor(OneUiTheme.TEXT_PRIMARY);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        mTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        mSubtitleView = new TextView(context);
        mSubtitleView.setTextColor(OneUiTheme.TEXT_SECONDARY);
        mSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.SUMMARY_SP);
        mSubtitleView.setPadding(0, OneUiTheme.dp(context, 4), 0, 0);

        textColumn.addView(mTitleView);
        textColumn.addView(mSubtitleView);

        mPill = new LinearLayout(context);
        mPill.setOrientation(HORIZONTAL);
        mPill.setGravity(Gravity.CENTER_VERTICAL);
        mPill.setBackgroundResource(R.drawable.oneui_status_pill_bg);
        int pillPadH = OneUiTheme.dp(context, 10);
        int pillPadV = OneUiTheme.dp(context, 5);
        mPill.setPadding(pillPadH, pillPadV, pillPadH, pillPadV);
        LayoutParams pillParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        pillParams.setMarginStart(OneUiTheme.dp(context, 12));
        mPill.setLayoutParams(pillParams);

        View dot = new View(context);
        dot.setBackgroundResource(R.drawable.oneui_status_dot);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                OneUiTheme.dp(context, 7), OneUiTheme.dp(context, 7));
        dotParams.setMarginEnd(OneUiTheme.dp(context, 6));
        dot.setLayoutParams(dotParams);

        mPillLabel = new TextView(context);
        mPillLabel.setTextColor(0xFF3DDC84);
        mPillLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        mPillLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        mPill.addView(dot);
        mPill.addView(mPillLabel);

        addView(textColumn);
        addView(mPill);
    }

    public OneUiDeviceCard bind(String model, String details, String status) {
        mTitleView.setText(model);
        mSubtitleView.setText(details);
        mPillLabel.setText(status);
        return this;
    }

    /** Turns the status pill grey for the "module inactive" state. */
    public void setStatusActive(boolean active) {
        mPillLabel.setTextColor(active ? 0xFF3DDC84 : OneUiTheme.TEXT_SECONDARY);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(OneUiTheme.dp(getContext(), 20));
        bg.setColor(active ? 0x1F3DDC84 : 0x1F8E8E93);
        bg.setStroke(Math.max(1, OneUiTheme.dp(getContext(), 0.5f)),
                active ? 0x593DDC84 : 0x408E8E93);
        mPill.setBackground(bg);
    }
}
