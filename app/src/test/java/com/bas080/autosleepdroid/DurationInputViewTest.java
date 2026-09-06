package com.bas080.autosleepdroid;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class DurationInputViewTest {

    private Context context;
    private DurationInputView durationInputView;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        durationInputView = new DurationInputView(context);
    }

    @Test
    public void testMinuteStepGreaterThanOneDisplaysPlainNumbersWithoutLeadingZeros() {
        durationInputView.configure(0, 12, 5);

        NumberPicker minutesPicker = durationInputView.getMinutesPicker();
        assertNotNull(minutesPicker);

        String[] displayedValues = minutesPicker.getDisplayedValues();
        assertNotNull(displayedValues);
        assertEquals("0", displayedValues[0]);
        assertEquals("5", displayedValues[1]);
        assertEquals("10", displayedValues[2]);
    }

    @Test
    public void testPickersConfigureNumericKeyboardInputType() {
        durationInputView.configure(0, 12, 5);

        EditText hoursEditText = findEditTextInPicker(durationInputView.getHoursPicker());
        EditText minutesEditText = findEditTextInPicker(durationInputView.getMinutesPicker());

        if (hoursEditText != null) {
            assertEquals(InputType.TYPE_CLASS_NUMBER, hoursEditText.getInputType());
        }
        if (minutesEditText != null) {
            assertEquals(InputType.TYPE_CLASS_NUMBER, minutesEditText.getInputType());
        }
    }

    @Test
    public void testGetTotalMinutesCommitsTypedTextWithoutLosingFocus() {
        durationInputView.configure(0, 12, 1);
        durationInputView.setTotalMinutes(30);

        EditText minutesEditText = findEditTextInPicker(durationInputView.getMinutesPicker());
        assertNotNull(minutesEditText);

        minutesEditText.setText("45");

        assertEquals(45, durationInputView.getTotalMinutes());
    }

    @Test
    public void testGetTotalMinutesCommitsTypedHoursWithoutLosingFocus() {
        durationInputView.configure(0, 12, 5);
        durationInputView.setTotalMinutes(30);

        EditText hoursEditText = findEditTextInPicker(durationInputView.getHoursPicker());
        assertNotNull(hoursEditText);

        hoursEditText.setText("2");

        assertEquals(150, durationInputView.getTotalMinutes());
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
}
