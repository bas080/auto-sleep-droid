package com.bas080.autosleepdroid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {
    private int horizontalSpacing = 0;
    private int verticalSpacing = 0;

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        int heightSize = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int width = 0;
        int height = 0;

        int lineWidth = 0;
        int lineHeight = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (lineWidth + childWidth > widthSize && lineWidth > 0) {
                width = Math.max(width, lineWidth);
                height += lineHeight + verticalSpacing;
                lineWidth = childWidth;
                lineHeight = childHeight;
            } else {
                lineWidth += childWidth + (lineWidth > 0 ? horizontalSpacing : 0);
                lineHeight = Math.max(lineHeight, childHeight);
            }
        }

        width = Math.max(width, lineWidth);
        height += lineHeight;

        width += getPaddingLeft() + getPaddingRight();
        height += getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(
                widthMode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthMeasureSpec) : width,
                heightMode == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec) : height
        );
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int widthSize = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (x + childWidth > getPaddingLeft() + widthSize && x > getPaddingLeft()) {
                x = getPaddingLeft();
                y += lineHeight + verticalSpacing;
                lineHeight = childHeight;
            } else {
                lineHeight = Math.max(lineHeight, childHeight);
            }

            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + horizontalSpacing;
        }
    }
}
