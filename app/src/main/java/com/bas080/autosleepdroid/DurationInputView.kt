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
    private var maxHours = 24
    private var minuteStep = 1

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

        configure(0, 24, 1)
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
                val count = 60 / this.minuteStep
                val values = Array(count) { i -> (i * this.minuteStep).toString() }
                minutesPicker.minValue = 0
                minutesPicker.maxValue = count - 1
                minutesPicker.displayedValues = values
            } else {
                minutesPicker.minValue = 0
                minutesPicker.maxValue = 59
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
        picker ?: return ""
        val displayedValues = picker.displayedValues
        val valNum = picker.value
        if (displayedValues != null && valNum >= 0 && valNum < displayedValues.size) {
            return displayedValues[valNum]
        }
        return valNum.toString()
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
        val currentDisplayed = getCurrentlyDisplayedValue(picker)
        val currentValStr = picker.value.toString()

        val isSameAsCurrent = str == currentDisplayed || str == currentValStr
        val isEdited = str.isNotEmpty() && !isSameAsCurrent
        val isFocused = picker.hasFocus() || (editText != null && editText.hasFocus())

        if ((isEdited || isFocused) && str.isNotEmpty() && !isSameAsCurrent) {
            try {
                val valNum = str.toInt()
                if (picker === pickerHours) {
                    if (valNum in minHours..maxHours) {
                        picker.value = valNum
                    }
                } else if (picker === pickerMinutes) {
                    if (minuteStep > 1) {
                        val count = 60 / minuteStep
                        var stepIdx = Math.round(valNum.toFloat() / minuteStep)
                        if (stepIdx < 0) stepIdx = 0
                        if (stepIdx >= count) stepIdx = count - 1
                        picker.value = stepIdx
                    } else {
                        if (valNum in 0..59) {
                            picker.value = valNum
                        }
                    }
                }
                syncEditText(picker)
            } catch (ignored: NumberFormatException) {
            }
        }

        if (isFocused) {
            editText?.clearFocus()
            picker.clearFocus()
        }
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

    fun getTotalMinutes(): Int {
        val hoursPicker = pickerHours ?: return -1
        val minutesPicker = pickerMinutes ?: return -1

        commitPickerInput(hoursPicker)
        commitPickerInput(minutesPicker)

        val h = hoursPicker.value
        val m = if (minuteStep > 1) {
            minutesPicker.value * minuteStep
        } else {
            minutesPicker.value
        }
        val total = h * 60 + m
        if (total <= 0 || total > 1440) {
            return -1
        }
        return total
    }

    fun setTotalMinutes(totalMinutes: Int) {
        val hoursPicker = pickerHours ?: return
        val minutesPicker = pickerMinutes ?: return

        var mins = totalMinutes
        if (mins < 0) mins = 0
        if (mins > 1440) mins = 1440

        var h = mins / 60
        val remainingMins = mins % 60

        if (h < minHours) h = minHours
        if (h > maxHours) h = maxHours

        hoursPicker.value = h
        syncEditText(hoursPicker)

        if (minuteStep > 1) {
            val count = 60 / minuteStep
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
}
