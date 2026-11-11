package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.s23.enhancements.R;

/**
 * One UI list row that opens a picker: title, current value and a chevron.
 */
public class OneUiSelectRow extends LinearLayout {

    private final TextView mTitleView;
    private final TextView mValueView;
    private final TextView mChevronView;
    private final Paint mDividerPaint = new Paint();
    private final int mDividerInset;
    private boolean mShowDivider = true;

    public OneUiSelectRow(Context context) {
        this(context, null);
    }

    public OneUiSelectRow(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.oneui_row_ripple);
        int padH = OneUiTheme.dp(context, 18);
        setPadding(padH, OneUiTheme.dp(context, 15), padH, OneUiTheme.dp(context, 15));
        setMinimumHeight(OneUiTheme.dp(context, 64));
        setClickable(true);
        setWillNotDraw(false);

        mDividerPaint.setColor(OneUiTheme.DIVIDER);
        mDividerPaint.setStrokeWidth(Math.max(1f, OneUiTheme.dp(context, 0.5f)));
        mDividerInset = padH;

        mTitleView = new TextView(context);
        mTitleView.setTextColor(OneUiTheme.TEXT_PRIMARY);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.TITLE_SP);
        mTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitleView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mValueView = new TextView(context);
        mValueView.setTextColor(OneUiTheme.TEXT_SECONDARY);
        mValueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.SUMMARY_SP);
        mValueView.setGravity(Gravity.END);
        mValueView.setMaxLines(2);
        mValueView.setPadding(OneUiTheme.dp(context, 12), 0, 0, 0);

        mChevronView = new TextView(context);
        mChevronView.setText("\u203A");
        mChevronView.setTextColor(OneUiTheme.TEXT_SECONDARY);
        mChevronView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        mChevronView.setPadding(OneUiTheme.dp(context, 10), 0, 0, 0);

        addView(mTitleView);
        addView(mValueView);
        addView(mChevronView);
    }

    public OneUiSelectRow title(CharSequence title) {
        mTitleView.setText(title);
        return this;
    }

    public OneUiSelectRow value(CharSequence value) {
        mValueView.setText(TextUtils.isEmpty(value) ? "" : value);
        return this;
    }

    public void setValue(CharSequence value) {
        mValueView.setText(TextUtils.isEmpty(value) ? "" : value);
    }

    public OneUiSelectRow divider(boolean show) {
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
        setClickable(enabled);
        mChevronView.setVisibility(enabled ? VISIBLE : INVISIBLE);
    }
}
