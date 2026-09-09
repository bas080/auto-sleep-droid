package com.bas080.autosleepdroid

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class SettingRowView : LinearLayout {

    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var valueTextView: TextView
    private lateinit var switchView: Switch

    constructor(context: Context) : super(context) {
        initView(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initView(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView(context, attrs, defStyleAttr, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initView(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun initView(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val minHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64f, resources.displayMetrics).toInt()
        minimumHeight = minHeightPx

        val paddingHorizPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val paddingVertPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
        setPaddingRelative(paddingHorizPx, paddingVertPx, paddingHorizPx, paddingVertPx)

        isClickable = true
        isFocusable = true

        if (background == null) {
            val outValue = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                setBackgroundResource(outValue.resourceId)
            }
        }

        LayoutInflater.from(context).inflate(R.layout.view_setting_row, this, true)

        titleTextView = findViewById(R.id.setting_row_title)
        descriptionTextView = findViewById(R.id.setting_row_description)
        valueTextView = findViewById(R.id.setting_row_value)
        switchView = findViewById(R.id.setting_row_switch)

        if (attrs != null) {
            val a: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SettingRowView, defStyleAttr, defStyleRes)
            try {
                val title = a.getText(R.styleable.SettingRowView_rowTitle)
                if (title != null) {
                    titleTextView.text = title
                }

                val description = a.getText(R.styleable.SettingRowView_rowDescription)
                if (description != null) {
                    descriptionTextView.text = description
                }

                val value = a.getText(R.styleable.SettingRowView_rowValue)
                if (value != null) {
                    valueTextView.text = value
                }

                val valueId = a.getResourceId(R.styleable.SettingRowView_valueId, View.NO_ID)
                if (valueId != View.NO_ID) {
                    valueTextView.id = valueId
                }

                val switchId = a.getResourceId(R.styleable.SettingRowView_switchId, View.NO_ID)
                if (switchId != View.NO_ID) {
                    switchView.id = switchId
                }

                val rowType = a.getInt(R.styleable.SettingRowView_rowType, 0)
                if (rowType == 2 || switchId != View.NO_ID) {
                    switchView.visibility = View.VISIBLE
                    valueTextView.visibility = View.GONE
                } else if (rowType == 1 || valueId != View.NO_ID || value != null) {
                    valueTextView.visibility = View.VISIBLE
                    switchView.visibility = View.GONE
                } else {
                    valueTextView.visibility = View.GONE
                    switchView.visibility = View.GONE
                }
            } finally {
                a.recycle()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isClickable = enabled
        isFocusable = enabled
        alpha = if (enabled) 1.0f else 0.38f
        for (i in 0 until childCount) {
            setChildViewsEnabled(getChildAt(i), enabled)
        }
    }

    private fun setChildViewsEnabled(view: View?, enabled: Boolean) {
        if (view == null) return
        view.isEnabled = enabled
        if (view is Switch) {
            view.isClickable = enabled
            view.isFocusable = enabled
        } else {
            view.isClickable = false
            view.isFocusable = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setChildViewsEnabled(view.getChildAt(i), enabled)
            }
        }
    }
}
