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
 * One UI list row: title, optional summary and a trailing switch.
 * Tapping anywhere on the row toggles the switch.
 */
public class OneUiPreferenceRow extends LinearLayout {

    public interface OnToggleListener {
        void onToggled(String key, boolean checked);
    }

    private final TextView mTitleView;
    private final TextView mSummaryView;
    private final OneUiSwitch mSwitch;
    private final Paint mDividerPaint = new Paint();
    private final int mDividerInset;

    private String mKey;
    private OnToggleListener mListener;
    private boolean mShowDivider = true;

    public OneUiPreferenceRow(Context context) {
        this(context, null);
    }

    public OneUiPreferenceRow(Context context, android.util.AttributeSet attrs) {
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

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(VERTICAL);
        textColumn.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        mTitleView = new TextView(context);
        mTitleView.setTextColor(OneUiTheme.TEXT_PRIMARY);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.TITLE_SP);
        mTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitleView.setLineSpacing(0f, 1.12f);

        mSummaryView = new TextView(context);
        mSummaryView.setTextColor(OneUiTheme.TEXT_SECONDARY);
        mSummaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, OneUiTheme.SUMMARY_SP);
        mSummaryView.setLineSpacing(OneUiTheme.dp(context, 1), 1.18f);
        mSummaryView.setPadding(0, OneUiTheme.dp(context, 4), 0, 0);
        mSummaryView.setVisibility(GONE);

        textColumn.addView(mTitleView);
        textColumn.addView(mSummaryView);

        mSwitch = new OneUiSwitch(context);
        LayoutParams switchParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        switchParams.setMarginStart(OneUiTheme.dp(context, 14));
        mSwitch.setLayoutParams(switchParams);
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mListener != null) {
                mListener.onToggled(mKey, checked);
            }
        });

        addView(textColumn);
        addView(mSwitch);

        setOnClickListener(v -> mSwitch.toggle());
    }

    public OneUiPreferenceRow key(String key) {
        mKey = key;
        return this;
    }

    public String getKey() {
        return mKey;
    }

    public OneUiPreferenceRow title(CharSequence title) {
        mTitleView.setText(title);
        return this;
    }

    public OneUiPreferenceRow summary(CharSequence summary) {
        if (TextUtils.isEmpty(summary)) {
            mSummaryView.setVisibility(GONE);
        } else {
            mSummaryView.setText(summary);
            mSummaryView.setVisibility(VISIBLE);
        }
        return this;
    }

    public OneUiPreferenceRow checked(boolean checked) {
        mSwitch.setCheckedSilently(checked);
        return this;
    }

    public OneUiPreferenceRow onToggle(OnToggleListener listener) {
        mListener = listener;
        return this;
    }

    public OneUiPreferenceRow divider(boolean show) {
        mShowDivider = show;
        invalidate();
        return this;
    }

    public boolean isChecked() {
        return mSwitch.isChecked();
    }

    public void setCheckedSilently(boolean checked) {
        mSwitch.setCheckedSilently(checked);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
        setClickable(enabled);
        mSwitch.setEnabled(enabled);
        mTitleView.setEnabled(enabled);
        mSummaryView.setEnabled(enabled);
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
