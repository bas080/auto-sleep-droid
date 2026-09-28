@file:Suppress("TooManyFunctions")

package com.bas080.autosleepdroid

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker

class DurationInputView : LinearLayout {

    private var pickerHours: NumberPicker? = null
    private var pickerMinutes: NumberPicker? = null
    private var durationChangeListener: OnDurationChangeListener? = null

    private var minHours = 0
    private var maxHours = DEFAULT_MAX_HOURS
    private var minuteStep = DEFAULT_MINUTE_STEP

    fun interface OnDurationChangeListener {
        fun onDurationChanged(totalMinutes: Int)
    }

    interface FullOnDurationChangeListener : OnDurationChangeListener {
        fun onInvalidDuration()
    }

    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView(context)
    }

    private fun initView(context: Context) {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        LayoutInflater.from(context).inflate(R.layout.view_duration_input, this, true)

        pickerHours = findViewById(R.id.picker_hours)
        pickerMinutes = findViewById(R.id.picker_minutes)

        val valueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ -> handleDurationChange() }

        pickerHours?.setOnValueChangedListener(valueChangeListener)
        pickerMinutes?.setOnValueChangedListener(valueChangeListener)

        configure(0, DEFAULT_MAX_HOURS, DEFAULT_MINUTE_STEP)
    }

    fun configure(minHours: Int, maxHours: Int, minuteStep: Int) {
        this.minHours = Math.max(0, minHours)
        this.maxHours = Math.max(this.minHours, maxHours)
        this.minuteStep = Math.max(1, minuteStep)

        pickerHours?.let { hoursPicker ->
            hoursPicker.minValue = this.minHours
            hoursPicker.maxValue = this.maxHours
            hoursPicker.wrapSelectorWheel = false
            setNumericKeyboard(hoursPicker)
            syncEditText(hoursPicker)
        }

        pickerMinutes?.let { minutesPicker ->
            minutesPicker.displayedValues = null
            if (this.minuteStep > 1) {
                val count = MINUTES_IN_HOUR / this.minuteStep
                val values = Array(count) { i -> (i * this.minuteStep).toString() }
                minutesPicker.minValue = 0
                minutesPicker.maxValue = count - 1
                minutesPicker.displayedValues = values
            } else {
                minutesPicker.minValue = 0
                minutesPicker.maxValue = MAX_MINUTES
                minutesPicker.setFormatter { valNum -> valNum.toString() }
            }
            minutesPicker.wrapSelectorWheel = true
            setNumericKeyboard(minutesPicker)
            syncEditText(minutesPicker)
        }
    }

    private fun setNumericKeyboard(picker: NumberPicker?) {
        picker ?: return
        val editText = findEditTextInPicker(picker)
        editText?.inputType = InputType.TYPE_CLASS_NUMBER
        val label = if (picker === pickerHours) {
            context.getString(R.string.label_hours)
        } else {
            context.getString(R.string.label_minutes)
        }
        picker.contentDescription = label
        editText?.contentDescription = label
    }

    fun setChildInputIds(hoursId: Int, minutesId: Int) {
        pickerHours?.id = hoursId
        pickerMinutes?.id = minutesId
    }

    fun getHoursPicker(): NumberPicker? = pickerHours

    fun getMinutesPicker(): NumberPicker? = pickerMinutes

    fun setOnDurationChangeListener(listener: OnDurationChangeListener?) {
        this.durationChangeListener = listener
    }

    private fun getCurrentlyDisplayedValue(picker: NumberPicker?): String {
        if (picker == null) return ""
        val displayedValues = picker.displayedValues
        val valNum = picker.value
        val hasDisplayed = displayedValues != null && valNum in 0 until displayedValues.size
        return if (hasDisplayed) displayedValues!![valNum] else valNum.toString()
    }

    private fun syncEditText(picker: NumberPicker?) {
        picker ?: return
        val editText = findEditTextInPicker(picker)
        editText?.setText(getCurrentlyDisplayedValue(picker))
    }

    private fun commitPickerInput(picker: NumberPicker?) {
        picker ?: return

        val editText = findEditTextInPicker(picker)
        val str = editText?.text?.toString()?.trim() ?: ""
        val isFocused = picker.hasFocus() || (editText != null && editText.hasFocus())

        if (shouldApplyPickerValue(picker, str, isFocused)) {
            applyParsedPickerValue(picker, str)
        }

        if (isFocused) {
            editText?.clearFocus()
            picker.clearFocus()
        }
    }

    private fun shouldApplyPickerValue(picker: NumberPicker, str: String, isFocused: Boolean): Boolean {
        if (str.isEmpty()) return false
        val currentDisplayed = getCurrentlyDisplayedValue(picker)
        val currentValStr = picker.value.toString()
        val isSameAsCurrent = str == currentDisplayed || str == currentValStr
        return !isSameAsCurrent && (isFocused || str.isNotEmpty())
    }

    private fun applyParsedPickerValue(picker: NumberPicker, str: String) {
        try {
            val valNum = str.toInt()
            if (picker === pickerHours) {
                if (valNum in minHours..maxHours) {
                    picker.value = valNum
                }
            } else if (picker === pickerMinutes) {
                applyParsedMinutesValue(picker, valNum)
            }
            syncEditText(picker)
        } catch (ignored: NumberFormatException) {
        }
    }

    private fun applyParsedMinutesValue(picker: NumberPicker, valNum: Int) {
        if (minuteStep > 1) {
            val count = MINUTES_IN_HOUR / minuteStep
            var stepIdx = Math.round(valNum.toFloat() / minuteStep)
            if (stepIdx < 0) stepIdx = 0
            if (stepIdx >= count) stepIdx = count - 1
            picker.value = stepIdx
        } else if (valNum in 0..MAX_MINUTES) {
            picker.value = valNum
        }
    }

    private fun findEditTextInPicker(picker: NumberPicker?): EditText? {
        if (picker == null) return null
        val inputId = android.content.res.Resources.getSystem().getIdentifier("numberpicker_input", "id", "android")
        val viewById = if (inputId != 0) picker.findViewById<View>(inputId) else null

        return (viewById as? EditText) ?: (0 until picker.childCount)
            .map { picker.getChildAt(it) }
            .filterIsInstance<EditText>()
            .firstOrNull()
    }

    fun getTotalMinutes(): Int {
        val hp = pickerHours
        val mp = pickerMinutes
        if (hp == null || mp == null) return -1

        commitPickerInput(hp)
        commitPickerInput(mp)

        val h = hp.value
        val m = if (minuteStep > 1) mp.value * minuteStep else mp.value
        val total = h * MINUTES_IN_HOUR + m
        return if (total in 1..MAX_TOTAL_MINUTES) total else -1
    }

    fun setTotalMinutes(totalMinutes: Int) {
        val hoursPicker = pickerHours ?: return
        val minutesPicker = pickerMinutes ?: return

        var mins = totalMinutes
        if (mins < 0) mins = 0
        if (mins > MAX_TOTAL_MINUTES) mins = MAX_TOTAL_MINUTES

        var h = mins / MINUTES_IN_HOUR
        val remainingMins = mins % MINUTES_IN_HOUR

        if (h < minHours) h = minHours
        if (h > maxHours) h = maxHours

        hoursPicker.value = h
        syncEditText(hoursPicker)

        if (minuteStep > 1) {
            val count = MINUTES_IN_HOUR / minuteStep
            var stepIdx = Math.round(remainingMins.toFloat() / minuteStep)
            if (stepIdx >= count) stepIdx = count - 1
            minutesPicker.value = stepIdx
            syncEditText(minutesPicker)
        } else {
            minutesPicker.value = remainingMins
            syncEditText(minutesPicker)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        pickerHours?.isEnabled = enabled
        pickerMinutes?.isEnabled = enabled
    }

    private fun handleDurationChange() {
        val totalMinutes = getTotalMinutes()
        val listener = durationChangeListener ?: return
        if (totalMinutes > 0) {
            listener.onDurationChanged(totalMinutes)
        } else if (listener is FullOnDurationChangeListener) {
            listener.onInvalidDuration()
        }
    }

    companion object {
        private const val DEFAULT_MAX_HOURS = 24
        private const val DEFAULT_MINUTE_STEP = 1
        private const val MINUTES_IN_HOUR = 60
        private const val MAX_MINUTES = 59
        private const val MAX_TOTAL_MINUTES = 1440
    }
}
