package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Read-only One UI preference row for detail cards.
 *
 * Mirrors Samsung Settings: the label is the main title and the value sits on the second
 * line as a smaller secondary text (same pattern as "Stav baterie / Nabíjí se (USB)" in
 * Settings &gt; Baterie &gt; Informace o baterii). No ripple and no chevron.
 */
public class OneUiInfoRow extends LinearLayout {

    private final TextView mTitleView;
    private final TextView mValueView;
    private final Paint mDividerPaint = new Paint();
    private final int mDividerInset;
    private boolean mShowDivider = true;

    public OneUiInfoRow(Context context) {
        this(context, null);
    }

    public OneUiInfoRow(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        int padH = OneUiTheme.dp(context, 18);
        setPadding(padH, OneUiTheme.dp(context, 12), padH, OneUiTheme.dp(context, 12));
        setWillNotDraw(false);

        mDividerPaint.setColor(OneUiTheme.DIVIDER);
        mDividerPaint.setStrokeWidth(Math.max(1f, OneUiTheme.dp(context, 0.5f)));
        mDividerInset = padH;

        mTitleView = new TextView(context);
        mTitleView.setTextColor(OneUiTheme.TEXT_PRIMARY);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.TITLE_SP);
        mTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitleView.setLineSpacing(0f, 1.12f);

        mValueView = new TextView(context);
        mValueView.setTextColor(OneUiTheme.TEXT_SECONDARY);
        mValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.SUMMARY_SP);
        mValueView.setLineSpacing(OneUiTheme.dp(context, 1), 1.18f);
        mValueView.setPadding(0, OneUiTheme.dp(context, 2), 0, 0);

        addView(mTitleView);
        addView(mValueView);
    }

    public OneUiInfoRow title(CharSequence title) {
        mTitleView.setText(title);
        return this;
    }

    public OneUiInfoRow value(CharSequence value) {
        setValue(value);
        return this;
    }

    /** Updates the second line; an empty value renders as an em dash. */
    public void setValue(CharSequence value) {
        mValueView.setText(TextUtils.isEmpty(value) ? "\u2014" : value);
    }

    /** Paints the value in the accent colour (used for the charging state). */
    public void setValueAccent(boolean accent) {
        mValueView.setTextColor(accent ? OneUiTheme.ACCENT : OneUiTheme.TEXT_SECONDARY);
    }

    public OneUiInfoRow divider(boolean show) {
        mShowDivider = show;
        invalidate();
        return this;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mShowDivider) {
            return;
        }
        float y = getHeight() - mDividerPaint.getStrokeWidth() / 2f;
        canvas.drawLine(mDividerInset, y, getWidth() - mDividerInset, y, mDividerPaint);
    }
}
