package com.bas080.autosleepdroid;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

public class DurationInputView extends LinearLayout {
    private NumberPicker pickerHours;
    private NumberPicker pickerMinutes;
    private OnDurationChangeListener durationChangeListener;

    private int minHours = 0;
    private int maxHours = 24;
    private int minuteStep = 1;

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

        NumberPicker.OnValueChangeListener valueChangeListener = (picker, oldVal, newVal) -> handleDurationChange();

        if (pickerHours != null) pickerHours.setOnValueChangedListener(valueChangeListener);
        if (pickerMinutes != null) pickerMinutes.setOnValueChangedListener(valueChangeListener);

        configure(0, 24, 1);
    }

    public void configure(int minHours, int maxHours, int minuteStep) {
        this.minHours = Math.max(0, minHours);
        this.maxHours = Math.max(this.minHours, maxHours);
        this.minuteStep = Math.max(1, minuteStep);

        if (pickerHours != null) {
            pickerHours.setMinValue(this.minHours);
            pickerHours.setMaxValue(this.maxHours);
            pickerHours.setWrapSelectorWheel(false);
            setNumericKeyboard(pickerHours);
        }

        if (pickerMinutes != null) {
            pickerMinutes.setDisplayedValues(null);
            if (this.minuteStep > 1) {
                int count = 60 / this.minuteStep;
                String[] values = new String[count];
                for (int i = 0; i < count; i++) {
                    values[i] = String.valueOf(i * this.minuteStep);
                }
                pickerMinutes.setMinValue(0);
                pickerMinutes.setMaxValue(count - 1);
                pickerMinutes.setDisplayedValues(values);
            } else {
                pickerMinutes.setMinValue(0);
                pickerMinutes.setMaxValue(59);
                pickerMinutes.setFormatter(String::valueOf);
            }
            pickerMinutes.setWrapSelectorWheel(true);
            setNumericKeyboard(pickerMinutes);
        }
    }

    private void setNumericKeyboard(NumberPicker picker) {
        if (picker == null) return;
        int inputId = android.content.res.Resources.getSystem().getIdentifier("numberpicker_input", "id", "android");
        EditText editText = null;
        if (inputId != 0) {
            editText = picker.findViewById(inputId);
        }
        if (editText == null) {
            for (int i = 0; i < picker.getChildCount(); i++) {
                View child = picker.getChildAt(i);
                if (child instanceof EditText) {
                    editText = (EditText) child;
                    break;
                }
            }
        }
        if (editText != null) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
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
        int m;
        if (minuteStep > 1) {
            m = pickerMinutes.getValue() * minuteStep;
        } else {
            m = pickerMinutes.getValue();
        }
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
        int remainingMins = totalMinutes % 60;

        if (h < minHours) h = minHours;
        if (h > maxHours) h = maxHours;

        pickerHours.setValue(h);

        if (minuteStep > 1) {
            int count = 60 / minuteStep;
            int stepIdx = Math.round((float) remainingMins / minuteStep);
            if (stepIdx >= count) stepIdx = count - 1;
            pickerMinutes.setValue(stepIdx);
        } else {
            pickerMinutes.setValue(remainingMins);
        }
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
