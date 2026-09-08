package com.bas080.autosleepdroid

interface PreferenceGetter {
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getString(key: String, defValue: String?): String?
    fun contains(key: String): Boolean
}
