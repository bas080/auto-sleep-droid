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

    @Test
    fun testConstructorsAndChildIds() {
        val viewAttr = DurationInputView(context, null)
        assertNotNull(viewAttr.getHoursPicker())
        assertNotNull(viewAttr.getMinutesPicker())

        val viewStyle = DurationInputView(context, null, 0)
        assertNotNull(viewStyle.getHoursPicker())

        viewStyle.setChildInputIds(101, 102)
        assertEquals(101, viewStyle.getHoursPicker()?.id)
        assertEquals(102, viewStyle.getMinutesPicker()?.id)
    }

    @Test
    fun testSetEnabledDisablesChildPickers() {
        durationInputView.setEnabled(false)
        assertEquals(false, durationInputView.getHoursPicker()?.isEnabled)
        assertEquals(false, durationInputView.getMinutesPicker()?.isEnabled)

        durationInputView.setEnabled(true)
        assertEquals(true, durationInputView.getHoursPicker()?.isEnabled)
        assertEquals(true, durationInputView.getMinutesPicker()?.isEnabled)
    }

    @Test
    fun testMinuteStepOneConfigurationAndSetTotalMinutes() {
        durationInputView.configure(0, 12, 1)

        val minutesPicker = durationInputView.getMinutesPicker()
        org.junit.Assert.assertNull(minutesPicker?.displayedValues)
        assertEquals(0, minutesPicker?.minValue)
        assertEquals(59, minutesPicker?.maxValue)

        durationInputView.setTotalMinutes(125)
        assertEquals(2, durationInputView.getHoursPicker()?.value)
        assertEquals(5, durationInputView.getMinutesPicker()?.value)
        assertEquals(125, durationInputView.getTotalMinutes())

        // Negative or excessive setTotalMinutes
        durationInputView.setTotalMinutes(-10)
        assertEquals(-1, durationInputView.getTotalMinutes())

        durationInputView.setTotalMinutes(2000)
        assertEquals(12, durationInputView.getHoursPicker()?.value) // max hours 12
    }

    @Test
    fun testOnDurationChangeListenerCallbacks() {
        var changedMinutes = 0
        var invalidFired = false

        durationInputView.configure(0, 12, 5)
        durationInputView.setOnDurationChangeListener(object : DurationInputView.FullOnDurationChangeListener {
            override fun onDurationChanged(totalMinutes: Int) {
                changedMinutes = totalMinutes
            }

            override fun onInvalidDuration() {
                invalidFired = true
            }
        })

        // Setting value triggers listener when total > 0
        durationInputView.setTotalMinutes(0) // total 0
        durationInputView.getHoursPicker()?.value = 0
        durationInputView.getMinutesPicker()?.value = 0

        // Trigger value change programmatically
        durationInputView.getHoursPicker()?.value = 1
        durationInputView.getMinutesPicker()?.value = 2 // 2 * 5 = 10m -> 70m

        val handleMethod = DurationInputView::class.java.getDeclaredMethod("handleDurationChange")
        handleMethod.isAccessible = true
        handleMethod.invoke(durationInputView)

        assertEquals(70, changedMinutes)

        // Set 0 hours & 0 minutes to test invalid duration callback
        durationInputView.getHoursPicker()?.value = 0
        durationInputView.getMinutesPicker()?.value = 0
        handleMethod.invoke(durationInputView)

        assertEquals(true, invalidFired)
    }

    @Test
    fun testApplyParsedValuesWithInvalidOrOutOfRangeTypedText() {
        durationInputView.configure(0, 12, 5)
        durationInputView.setTotalMinutes(60)

        val hoursEditText = findEditTextInPicker(durationInputView.getHoursPicker())
        val minutesEditText = findEditTextInPicker(durationInputView.getMinutesPicker())

        // Non-numeric text
        hoursEditText?.setText("abc")
        minutesEditText?.setText("xyz")
        assertEquals(60, durationInputView.getTotalMinutes())

        // Out of range hours
        hoursEditText?.setText("99")
        assertEquals(60, durationInputView.getTotalMinutes())
    }

    @Test
    fun testCommitPickerInputFocusedAndStepVariations() {
        val view = DurationInputView(context)

        // Focus branches in commitPickerInput
        view.configure(0, 12, 5)
        val hoursEditText = findEditTextInPicker(view.getHoursPicker())
        hoursEditText?.requestFocus()
        hoursEditText?.setText("3")
        assertEquals(180, view.getTotalMinutes())

        // Minute step == 1 typed text
        val view2 = DurationInputView(context)
        view2.configure(0, 12, 1)
        val minEditTextStep1 = findEditTextInPicker(view2.getMinutesPicker())
        minEditTextStep1?.setText("25")
        assertEquals(25, view2.getTotalMinutes())

        // Minute step > 1 valid step typed text
        val view3 = DurationInputView(context)
        view3.configure(0, 12, 5)
        view3.setTotalMinutes(60)
        view3.getMinutesPicker()?.value = 3
        assertEquals(75, view3.getTotalMinutes())
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
