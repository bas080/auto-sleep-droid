package com.bas080.autosleepdroid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

public class DurationInputView extends LinearLayout {
    private NumberPicker pickerHours;
    private NumberPicker pickerMinutes;
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
        setGravity(Gravity.CENTER);
        LayoutInflater.from(context).inflate(R.layout.view_duration_input, this, true);

        pickerHours = findViewById(R.id.picker_hours);
        pickerMinutes = findViewById(R.id.picker_minutes);

        if (pickerHours != null) {
            pickerHours.setMinValue(0);
            pickerHours.setMaxValue(24);
            pickerHours.setWrapSelectorWheel(false);
        }

        if (pickerMinutes != null) {
            pickerMinutes.setMinValue(0);
            pickerMinutes.setMaxValue(59);
            pickerMinutes.setFormatter(val -> String.format(java.util.Locale.US, "%02d", val));
            pickerMinutes.setWrapSelectorWheel(true);
        }

        NumberPicker.OnValueChangeListener valueChangeListener = (picker, oldVal, newVal) -> handleDurationChange();

        if (pickerHours != null) pickerHours.setOnValueChangedListener(valueChangeListener);
        if (pickerMinutes != null) pickerMinutes.setOnValueChangedListener(valueChangeListener);
    }

    public void setChildInputIds(int hoursId, int minutesId) {
        if (pickerHours != null) pickerHours.setId(hoursId);
        if (pickerMinutes != null) pickerMinutes.setId(minutesId);
    }

    public NumberPicker getHoursPicker() {
        return pickerHours;
    }

    public NumberPicker getMinutesPicker() {
        return pickerMinutes;
    }

    public void setOnDurationChangeListener(OnDurationChangeListener listener) {
        this.durationChangeListener = listener;
    }

    public int getTotalMinutes() {
        if (pickerHours == null || pickerMinutes == null) return -1;
        int h = pickerHours.getValue();
        int m = pickerMinutes.getValue();
        int total = h * 60 + m;
        if (total <= 0 || total > 1440) {
            return -1;
        }
        return total;
    }

    public void setTotalMinutes(int totalMinutes) {
        if (pickerHours == null || pickerMinutes == null) return;
        if (totalMinutes < 0) totalMinutes = 0;
        if (totalMinutes > 1440) totalMinutes = 1440;
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        pickerHours.setValue(h);
        pickerMinutes.setValue(m);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (pickerHours != null) pickerHours.setEnabled(enabled);
        if (pickerMinutes != null) pickerMinutes.setEnabled(enabled);
    }

    private void handleDurationChange() {
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
