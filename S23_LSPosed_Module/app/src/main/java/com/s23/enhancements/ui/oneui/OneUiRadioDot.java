package com.s23.enhancements.ui.oneui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** One UI radio indicator: accent ring with a filled centre when selected. */
public class OneUiRadioDot extends View {

    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mRadius;
    private final float mRingWidth;
    private boolean mSelected;

    public OneUiRadioDot(Context context) {
        this(context, null);
    }

    public OneUiRadioDot(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        mRadius = OneUiTheme.dp(context, 10);
        mRingWidth = Math.max(1.5f, OneUiTheme.dp(context, 1.5f));
        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeWidth(mRingWidth);
        mDotPaint.setStyle(Paint.Style.FILL);
    }

    public void setSelected(boolean selected) {
        mSelected = selected;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = mRadius * 2 + (int) mRingWidth * 2;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        mRingPaint.setColor(mSelected ? OneUiTheme.ACCENT : OneUiTheme.TEXT_SECONDARY);
        canvas.drawCircle(cx, cy, mRadius - mRingWidth / 2f, mRingPaint);
        if (mSelected) {
            mDotPaint.setColor(OneUiTheme.ACCENT);
            canvas.drawCircle(cx, cy, mRadius / 2.2f, mDotPaint);
        }
    }
}
