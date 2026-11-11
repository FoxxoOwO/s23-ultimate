package com.s23.enhancements.ui.oneui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;

/**
 * One UI style switch: pill track (52x32dp) with an animated white thumb.
 * Exposes the standard {@link CompoundButton.OnCheckedChangeListener} so it is a
 * drop-in replacement for {@code android.widget.Switch}.
 */
public class OneUiSwitch extends View {

    private static final long ANIM_DURATION = 160L;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTrack = new RectF();

    private final int mTrackWidth;
    private final int mTrackHeight;
    private final int mThumbRadius;
    private final int mShadowRadius;

    private boolean mChecked;
    private float mFraction;
    private ValueAnimator mAnimator;
    private CompoundButton.OnCheckedChangeListener mListener;

    public OneUiSwitch(Context context) {
        this(context, null);
    }

    public OneUiSwitch(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mTrackWidth = OneUiTheme.dp(context, 52);
        mTrackHeight = OneUiTheme.dp(context, 32);
        mThumbRadius = OneUiTheme.dp(context, 13);
        mShadowRadius = OneUiTheme.dp(context, 2);

        mThumbPaint.setColor(OneUiTheme.THUMB);
        mThumbPaint.setShadowLayer(mShadowRadius, 0, OneUiTheme.dp(context, 1), 0x40000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                mTrackWidth + getPaddingLeft() + getPaddingRight(),
                mTrackHeight + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = getPaddingLeft();
        float top = getPaddingTop();
        mTrack.set(left, top, left + mTrackWidth, top + mTrackHeight);

        int trackColor = blend(OneUiTheme.TRACK_OFF, OneUiTheme.ACCENT, mFraction);
        mTrackPaint.setColor(isEnabled() ? trackColor : withAlpha(trackColor, 0.45f));
        canvas.drawRoundRect(mTrack, mTrackHeight / 2f, mTrackHeight / 2f, mTrackPaint);

        float travel = mTrackWidth - mTrackHeight;
        float centerX = left + mTrackHeight / 2f + travel * mFraction;
        float centerY = top + mTrackHeight / 2f;
        mThumbPaint.setAlpha(isEnabled() ? 255 : 140);
        canvas.drawCircle(centerX, centerY, mThumbRadius, mThumbPaint);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(mChecked);
    }

    public boolean isChecked() {
        return mChecked;
    }

    public void toggle() {
        setChecked(!mChecked);
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        mListener = listener;
    }

    /** Changes the state and animates the thumb without notifying the listener. */
    public void setCheckedSilently(boolean checked) {
        applyChecked(checked, false);
    }

    public void setChecked(boolean checked) {
        applyChecked(checked, true);
    }

    private void applyChecked(boolean checked, boolean notify) {
        if (mChecked == checked) {
            return;
        }
        mChecked = checked;
        animateTo(checked ? 1f : 0f);
        if (notify && mListener != null) {
            mListener.onCheckedChanged(null, checked);
        }
    }

    private void animateTo(float target) {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (!isAttachedToWindow()) {
            mFraction = target;
            invalidate();
            return;
        }
        mAnimator = ValueAnimator.ofFloat(mFraction, target);
        mAnimator.setDuration(ANIM_DURATION);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(a -> {
            mFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    private static int blend(int from, int to, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }

    private static int withAlpha(int color, float alpha) {
        return Color.argb((int) (255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }
}
