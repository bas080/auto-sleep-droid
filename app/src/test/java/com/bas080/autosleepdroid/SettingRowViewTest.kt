package com.bas080.autosleepdroid

import android.content.Context
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingRowViewTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSettingRowViewProgrammaticInitializationAndEnablement() {
        val rowView = SettingRowView(context)
        assertNotNull(rowView)

        val titleView = rowView.findViewById<TextView>(R.id.setting_row_title)
        val descView = rowView.findViewById<TextView>(R.id.setting_row_description)
        val valueView = rowView.findViewById<TextView>(R.id.setting_row_value)

        assertNotNull(titleView)
        assertNotNull(descView)
        assertNotNull(valueView)

        // Test enablement and alpha states
        rowView.isEnabled = false
        assertFalse(rowView.isEnabled)
        assertEquals(0.38f, rowView.alpha, 0.01f)

        rowView.isEnabled = true
        assertTrue(rowView.isEnabled)
        assertEquals(1.0f, rowView.alpha, 0.01f)
    }

    @Test
    fun testSettingRowViewSwitchInteraction() {
        val rowView = SettingRowView(context)
        val switchView = rowView.findViewById<Switch>(R.id.setting_row_switch)
        assertNotNull(switchView)

        assertFalse(switchView.isChecked)
        switchView.toggle()
        assertTrue(switchView.isChecked)
    }

    @Test
    fun testProgrammaticIsCheckedChangeDoesNotTriggerOnCheckedChangeListener() {
        val rowView = SettingRowView(context)
        var callbackTriggered = false

        rowView.setOnCheckedChangeListener {
            callbackTriggered = true
        }

        assertFalse(rowView.isChecked)
        rowView.isChecked = true
        assertTrue(rowView.isChecked)
        assertFalse("Programmatic assignment to isChecked must NOT fire onCheckedChangeListener", callbackTriggered)

        rowView.isChecked = false
        assertFalse(rowView.isChecked)
        assertFalse("Programmatic assignment to isChecked must NOT fire onCheckedChangeListener", callbackTriggered)
    }

    @Test
    fun testUserRowClickTriggersOnCheckedChangeListener() {
        val rowView = SettingRowView(context)
        rowView.findViewById<Switch>(R.id.setting_row_switch).visibility = android.view.View.VISIBLE
        var lastCheckedState: Boolean? = null

        rowView.setOnCheckedChangeListener { isChecked ->
            lastCheckedState = isChecked
        }

        assertFalse(rowView.isChecked)
        rowView.performClick()
        assertTrue(rowView.isChecked)
        assertEquals(true, lastCheckedState)

        rowView.performClick()
        assertFalse(rowView.isChecked)
        assertEquals(false, lastCheckedState)
    }

    @Test
    fun testSettingRowViewConstructorsAndSetChildViewsEnabled() {
        val row1 = SettingRowView(context, null)
        assertNotNull(row1)

        val row2 = SettingRowView(context, null, 0)
        assertNotNull(row2)

        val row3 = SettingRowView(context, null, 0, 0)
        assertNotNull(row3)

        // Disable row when switch is present
        row3.findViewById<Switch>(R.id.setting_row_switch).visibility = android.view.View.VISIBLE
        row3.isEnabled = false
        assertFalse(row3.findViewById<Switch>(R.id.setting_row_switch).isEnabled)

        row3.isEnabled = true
        assertTrue(row3.findViewById<Switch>(R.id.setting_row_switch).isEnabled)
    }
}
