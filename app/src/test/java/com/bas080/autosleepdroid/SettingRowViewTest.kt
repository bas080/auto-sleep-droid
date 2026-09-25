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
}
