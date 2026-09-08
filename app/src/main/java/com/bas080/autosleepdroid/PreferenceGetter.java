package com.bas080.autosleepdroid;

public interface PreferenceGetter {
    boolean getBoolean(String key, boolean defValue);
    int getInt(String key, int defValue);
    long getLong(String key, long defValue);
    String getString(String key, String defValue);
    boolean contains(String key);
}
