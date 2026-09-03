package com.bas080.autosleepdroid;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;

public class DurationInputView extends LinearLayout {
    private EditText inputHours;
    private EditText inputMinutes;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnDurationChangeListener durationChangeListener;

    public interface OnDurationChangeListener {
        void onDurationChanged(int totalMinutes);
        void onInvalidDuration();
    }

    public DurationInputView(Context context) {
        super(context);
        init(context);
    }

    public DurationInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DurationInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_duration_input, this, true);

        inputHours = findViewById(R.id.input_hours);
        inputMinutes = findViewById(R.id.input_minutes);

        OnFocusChangeListener focusListener = (v, hasFocus) -> {
            mainHandler.post(() -> {
                boolean hoursFocused = inputHours != null && inputHours.hasFocus();
                boolean minutesFocused = inputMinutes != null && inputMinutes.hasFocus();
                if (!hoursFocused && !minutesFocused) {
                    handleFocusLoss();
                }
            });
        };

        if (inputHours != null) inputHours.setOnFocusChangeListener(focusListener);
        if (inputMinutes != null) inputMinutes.setOnFocusChangeListener(focusListener);
    }

    public void setChildInputIds(int hoursId, int minutesId) {
        if (inputHours != null) inputHours.setId(hoursId);
        if (inputMinutes != null) inputMinutes.setId(minutesId);
    }

    public EditText getHoursInput() {
        return inputHours;
    }

    public EditText getMinutesInput() {
        return inputMinutes;
    }

    public void setOnDurationChangeListener(OnDurationChangeListener listener) {
        this.durationChangeListener = listener;
    }

    public int getTotalMinutes() {
        if (inputHours == null || inputMinutes == null) return -1;
        String hStr = inputHours.getText().toString().trim();
        String mStr = inputMinutes.getText().toString().trim();
        if (hStr.isEmpty() && mStr.isEmpty()) return -1;
        int h = 0;
        int m = 0;
        try {
            if (!hStr.isEmpty()) h = Integer.parseInt(hStr);
            if (!mStr.isEmpty()) m = Integer.parseInt(mStr);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (h < 0 || m < 0) return -1;
        long total = h * 60L + m;
        if (total <= 0 || total > 1440) {
            return -1;
        }
        return (int) total;
    }

    public void setTotalMinutes(int totalMinutes) {
        if (inputHours == null || inputMinutes == null) return;
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        inputHours.setText(h > 0 ? String.valueOf(h) : "");
        inputMinutes.setText(m > 0 || h == 0 ? String.valueOf(m) : "");
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (inputHours != null) inputHours.setEnabled(enabled);
        if (inputMinutes != null) inputMinutes.setEnabled(enabled);
    }

    public boolean hasInputFocus() {
        return (inputHours != null && inputHours.hasFocus()) || (inputMinutes != null && inputMinutes.hasFocus());
    }

    private void handleFocusLoss() {
        int totalMinutes = getTotalMinutes();
        if (durationChangeListener != null) {
            if (totalMinutes > 0) {
                durationChangeListener.onDurationChanged(totalMinutes);
            } else {
                durationChangeListener.onInvalidDuration();
            }
        }
    }
}