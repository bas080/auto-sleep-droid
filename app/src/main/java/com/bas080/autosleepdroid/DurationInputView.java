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
            syncEditText(pickerHours);
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
            syncEditText(pickerMinutes);
        }
    }

    private void setNumericKeyboard(NumberPicker picker) {
        if (picker == null) return;
        EditText editText = findEditTextInPicker(picker);
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

    private String getCurrentlyDisplayedValue(NumberPicker picker) {
        if (picker == null) return "";
        String[] displayedValues = picker.getDisplayedValues();
        int val = picker.getValue();
        if (displayedValues != null && val >= 0 && val < displayedValues.length) {
            return displayedValues[val];
        }
        return String.valueOf(val);
    }

    private void syncEditText(NumberPicker picker) {
        if (picker == null) return;
        EditText editText = findEditTextInPicker(picker);
        if (editText != null) {
            editText.setText(getCurrentlyDisplayedValue(picker));
        }
    }

    private void commitPickerInput(NumberPicker picker) {
        if (picker == null) return;

        EditText editText = findEditTextInPicker(picker);
        String str = (editText != null && editText.getText() != null) ? editText.getText().toString().trim() : "";
        String currentDisplayed = getCurrentlyDisplayedValue(picker);
        String currentValStr = String.valueOf(picker.getValue());

        boolean isSameAsCurrent = str.equals(currentDisplayed) || str.equals(currentValStr);
        boolean isEdited = !str.isEmpty() && !isSameAsCurrent;
        boolean isFocused = picker.hasFocus() || (editText != null && editText.hasFocus());

        if ((isEdited || isFocused) && !str.isEmpty() && !isSameAsCurrent) {
            try {
                int val = Integer.parseInt(str);
                if (picker == pickerHours) {
                    if (val >= minHours && val <= maxHours) {
                        picker.setValue(val);
                    }
                } else if (picker == pickerMinutes) {
                    if (minuteStep > 1) {
                        int count = 60 / minuteStep;
                        int stepIdx = Math.round((float) val / minuteStep);
                        if (stepIdx < 0) stepIdx = 0;
                        if (stepIdx >= count) stepIdx = count - 1;
                        picker.setValue(stepIdx);
                    } else {
                        if (val >= 0 && val <= 59) {
                            picker.setValue(val);
                        }
                    }
                }
                syncEditText(picker);
            } catch (NumberFormatException ignored) {
            }
        }

        if (isFocused) {
            if (editText != null) {
                editText.clearFocus();
            }
            picker.clearFocus();
        }
    }

    private EditText findEditTextInPicker(NumberPicker picker) {
        if (picker == null) return null;
        int inputId = android.content.res.Resources.getSystem().getIdentifier("numberpicker_input", "id", "android");
        if (inputId != 0) {
            View v = picker.findViewById(inputId);
            if (v instanceof EditText) return (EditText) v;
        }
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                return (EditText) child;
            }
        }
        return null;
    }

    public int getTotalMinutes() {
        if (pickerHours == null || pickerMinutes == null) return -1;

        commitPickerInput(pickerHours);
        commitPickerInput(pickerMinutes);

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
        syncEditText(pickerHours);

        if (minuteStep > 1) {
            int count = 60 / minuteStep;
            int stepIdx = Math.round((float) remainingMins / minuteStep);
            if (stepIdx >= count) stepIdx = count - 1;
            pickerMinutes.setValue(stepIdx);
            syncEditText(pickerMinutes);
        } else {
            pickerMinutes.setValue(remainingMins);
            syncEditText(pickerMinutes);
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
