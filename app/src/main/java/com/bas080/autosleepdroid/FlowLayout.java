package com.bas080.autosleepdroid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class FlowLayout extends ViewGroup {

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

        int height = getPaddingTop() + getPaddingBottom();
        int currentLineWidth = 0;
        int currentLineHeight = 0;
        int maxLineWidth = 0;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (currentLineWidth + childWidth > widthSize && currentLineWidth > 0) {
                maxLineWidth = Math.max(maxLineWidth, currentLineWidth);
                height += currentLineHeight;
                currentLineWidth = childWidth;
                currentLineHeight = childHeight;
            } else {
                currentLineWidth += childWidth;
                currentLineHeight = Math.max(currentLineHeight, childHeight);
            }
        }

        height += currentLineHeight;
        maxLineWidth = Math.max(maxLineWidth, currentLineWidth);

        int totalWidth = (widthMode == MeasureSpec.EXACTLY) ? MeasureSpec.getSize(widthMeasureSpec) : maxLineWidth + getPaddingLeft() + getPaddingRight();
        int totalHeight = resolveSize(height, heightMeasureSpec);

        setMeasuredDimension(totalWidth, totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int widthSize = r - l - getPaddingLeft() - getPaddingRight();
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int currentLineHeight = 0;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            if (left + childWidth > getPaddingLeft() + widthSize && left > getPaddingLeft()) {
                left = getPaddingLeft();
                top += currentLineHeight;
                currentLineHeight = childHeight;
            } else {
                currentLineHeight = Math.max(currentLineHeight, childHeight);
            }

            child.layout(left, top, left + childWidth, top + childHeight);
            left += childWidth;
        }
    }
}
