package com.bas080.autosleepdroid;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingRowView extends LinearLayout {

    private TextView titleTextView;
    private TextView descriptionTextView;
    private TextView valueTextView;
    private Switch switchView;

    public SettingRowView(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public SettingRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public SettingRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SettingRowView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());
        setMinimumHeight(minHeightPx);

        int paddingHorizPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        int paddingVertPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        setPaddingRelative(paddingHorizPx, paddingVertPx, paddingHorizPx, paddingVertPx);

        setClickable(true);
        setFocusable(true);

        if (getBackground() == null) {
            TypedValue outValue = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                setBackgroundResource(outValue.resourceId);
            }
        }

        LayoutInflater.from(context).inflate(R.layout.view_setting_row, this, true);

        titleTextView = findViewById(R.id.setting_row_title);
        descriptionTextView = findViewById(R.id.setting_row_description);
        valueTextView = findViewById(R.id.setting_row_value);
        switchView = findViewById(R.id.setting_row_switch);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingRowView, defStyleAttr, defStyleRes);
            try {
                CharSequence title = a.getText(R.styleable.SettingRowView_rowTitle);
                if (title != null) {
                    titleTextView.setText(title);
                }

                CharSequence description = a.getText(R.styleable.SettingRowView_rowDescription);
                if (description != null) {
                    descriptionTextView.setText(description);
                }

                CharSequence value = a.getText(R.styleable.SettingRowView_rowValue);
                if (value != null) {
                    valueTextView.setText(value);
                }

                int valueId = a.getResourceId(R.styleable.SettingRowView_valueId, View.NO_ID);
                if (valueId != View.NO_ID) {
                    valueTextView.setId(valueId);
                }

                int switchId = a.getResourceId(R.styleable.SettingRowView_switchId, View.NO_ID);
                if (switchId != View.NO_ID) {
                    switchView.setId(switchId);
                }

                int rowType = a.getInt(R.styleable.SettingRowView_rowType, 0);
                if (rowType == 2 || switchId != View.NO_ID) {
                    switchView.setVisibility(View.VISIBLE);
                    valueTextView.setVisibility(View.GONE);
                } else if (rowType == 1 || valueId != View.NO_ID || value != null) {
                    valueTextView.setVisibility(View.VISIBLE);
                    switchView.setVisibility(View.GONE);
                } else {
                    valueTextView.setVisibility(View.GONE);
                    switchView.setVisibility(View.GONE);
                }
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setClickable(enabled);
        setFocusable(enabled);
        setAlpha(enabled ? 1.0f : 0.38f);
        for (int i = 0; i < getChildCount(); i++) {
            setChildViewsEnabled(getChildAt(i), enabled);
        }
    }

    private void setChildViewsEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof Switch) {
            view.setClickable(enabled);
            view.setFocusable(enabled);
        } else {
            view.setClickable(false);
            view.setFocusable(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setChildViewsEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    public void setTitleText(CharSequence title) {
        if (titleTextView != null) {
            titleTextView.setText(title);
        }
    }

    public void setTitleText(int resId) {
        if (titleTextView != null) {
            titleTextView.setText(resId);
        }
    }

    public void setDescriptionText(CharSequence description) {
        if (descriptionTextView != null) {
            descriptionTextView.setText(description);
        }
    }

    public void setDescriptionText(int resId) {
        if (descriptionTextView != null) {
            descriptionTextView.setText(resId);
        }
    }

    public void setValueText(CharSequence value) {
        if (valueTextView != null) {
            valueTextView.setText(value);
            valueTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setValueText(int resId) {
        if (valueTextView != null) {
            valueTextView.setText(resId);
            valueTextView.setVisibility(View.VISIBLE);
        }
    }

    public CharSequence getValueText() {
        return valueTextView != null ? valueTextView.getText() : "";
    }

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public TextView getDescriptionTextView() {
        return descriptionTextView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public Switch getSwitchView() {
        return switchView;
    }

    public void toggleSwitch() {
        if (switchView != null && switchView.getVisibility() == View.VISIBLE) {
            switchView.setPressed(true);
            switchView.toggle();
            switchView.setPressed(false);
        }
    }
}
