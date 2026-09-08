package com.bas080.autosleepdroid

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DurationInputViewTest {

    private lateinit var context: Context
    private lateinit var durationInputView: DurationInputView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        durationInputView = DurationInputView(context)
    }

    @Test
    fun testMinuteStepGreaterThanOneDisplaysPlainNumbersWithoutLeadingZeros() {
        durationInputView.configure(0, 12, 5)

        val minutesPicker = durationInputView.getMinutesPicker()
        assertNotNull(minutesPicker)

        val displayedValues = minutesPicker?.displayedValues
        assertNotNull(displayedValues)
        assertEquals("0", displayedValues!![0])
        assertEquals("5", displayedValues[1])
        assertEquals("10", displayedValues[2])
    }

    @Test
    fun testPickersConfigureNumericKeyboardInputType() {
        durationInputView.configure(0, 12, 5)

        val hoursEditText = findEditTextInPicker(durationInputView.getHoursPicker())
        val minutesEditText = findEditTextInPicker(durationInputView.getMinutesPicker())

        if (hoursEditText != null) {
            assertEquals(InputType.TYPE_CLASS_NUMBER, hoursEditText.inputType)
        }
        if (minutesEditText != null) {
            assertEquals(InputType.TYPE_CLASS_NUMBER, minutesEditText.inputType)
        }
    }

    @Test
    fun testGetTotalMinutesCommitsTypedTextWithoutLosingFocus() {
        durationInputView.configure(0, 12, 1)
        durationInputView.setTotalMinutes(30)

        val minutesEditText = findEditTextInPicker(durationInputView.getMinutesPicker())
        assertNotNull(minutesEditText)

        minutesEditText?.setText("45")

        assertEquals(45, durationInputView.getTotalMinutes())
    }

    @Test
    fun testGetTotalMinutesCommitsTypedHoursWithoutLosingFocus() {
        durationInputView.configure(0, 12, 5)
        durationInputView.setTotalMinutes(30)

        val hoursEditText = findEditTextInPicker(durationInputView.getHoursPicker())
        assertNotNull(hoursEditText)

        hoursEditText?.setText("2")

        assertEquals(150, durationInputView.getTotalMinutes())
    }

    private fun findEditTextInPicker(picker: NumberPicker?): EditText? {
        picker ?: return null
        val inputId = android.content.res.Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
        if (inputId != 0) {
            val v = picker.findViewById<View>(inputId)
            if (v is EditText) return v
        }
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is EditText) {
                return child
            }
        }
        return null
    }
}
