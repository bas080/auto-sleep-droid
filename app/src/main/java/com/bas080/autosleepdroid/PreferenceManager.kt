package com.bas080.autosleepdroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PreferenceManager(val sharedPreferences: SharedPreferences) : SharedPreferences.OnSharedPreferenceChangeListener, PreferenceGetter {

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChanged(key: String)
    }

    fun interface ComputedValue<T> {
        fun compute(getter: PreferenceGetter): T
    }

    fun interface PreferenceEffect {
        fun run(getter: PreferenceGetter)
    }

    fun interface EffectHandle {
        fun dispose()
    }

    private class TrackingPreferenceGetter(private val preferenceManager: PreferenceManager) : PreferenceGetter {
        val accessedValues: MutableMap<String, Any> = ConcurrentHashMap()

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            val value = preferenceManager.getBoolean(key, defValue)
            accessedValues[key] = value
            return value
        }

        override fun getInt(key: String, defValue: Int): Int {
            val value = preferenceManager.getInt(key, defValue)
            accessedValues[key] = value
            return value
        }

        override fun getLong(key: String, defValue: Long): Long {
            val value = preferenceManager.getLong(key, defValue)
            accessedValues[key] = value
            return value
        }

        override fun getString(key: String, defValue: String?): String? {
            val value = preferenceManager.getString(key, defValue)
            if (value != null) {
                accessedValues[key] = value
            } else {
                accessedValues[key] = NULL_SENTINEL
            }
            return value
        }

        override fun contains(key: String): Boolean {
            val value = preferenceManager.contains(key)
            accessedValues["contains:$key"] = value
            return value
        }
    }

    private class CachedComputation(
        val value: Any?,
        val trackedValues: Map<String, Any>?
    ) {
        fun isStale(preferenceManager: PreferenceManager): Boolean {
            if (trackedValues == null || trackedValues.isEmpty()) {
                return false
            }
            for ((key, trackedValue) in trackedValues) {
                if (key.startsWith("contains:")) {
                    val actualKey = key.substring("contains:".length)
                    val currentContains = preferenceManager.contains(actualKey)
                    if (trackedValue != currentContains) {
                        return true
                    }
                } else {
                    if (trackedValue is Boolean) {
                        if (trackedValue != preferenceManager.getBoolean(key, !trackedValue)) {
                            return true
                        }
                    } else if (trackedValue is Int) {
                        if (trackedValue != preferenceManager.getInt(key, trackedValue + 1)) {
                            return true
                        }
                    } else if (trackedValue is Long) {
                        if (trackedValue != preferenceManager.getLong(key, trackedValue + 1L)) {
                            return true
                        }
                    } else if (trackedValue === NULL_SENTINEL) {
                        if (preferenceManager.contains(key)) {
                            return true
                        }
                    } else if (trackedValue is String) {
                        val current = preferenceManager.getString(key, null)
                        if (trackedValue != current) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    private class WatchEffectRegistration(
        val preferenceManager: PreferenceManager,
        val effect: PreferenceEffect,
        val isMainThread: Boolean
    ) : EffectHandle {
        val trackedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
        @Volatile
        var isDisposed = false

        fun runEffect() {
            if (isDisposed) return
            val runnable = Runnable {
                if (isDisposed) return@Runnable
                val getter = TrackingPreferenceGetter(preferenceManager)
                effect.run(getter)
                trackedKeys.clear()
                for (key in getter.accessedValues.keys) {
                    if (key.startsWith("contains:")) {
                        trackedKeys.add(key.substring("contains:".length))
                    } else {
                        trackedKeys.add(key)
                    }
                }
            }
            dispatch(runnable)
        }

        fun dispatch(runnable: Runnable) {
            if (isMainThread) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runnable.run()
                } else {
                    preferenceManager.mainHandler.post(runnable)
                }
            } else {
                preferenceManager.asyncExecutor.execute(runnable)
            }
        }

        override fun dispose() {
            isDisposed = true
            preferenceManager.activeEffects.remove(this)
        }
    }

    private val listenersMap: MutableMap<String, MutableSet<OnPreferenceChangeListener>> = ConcurrentHashMap()
    private val computedCache: MutableMap<Any, CachedComputation> = ConcurrentHashMap()
    private val activeEffects: MutableSet<WatchEffectRegistration> = CopyOnWriteArraySet()
    private val taggedEffects: MutableMap<Any, MutableSet<EffectHandle>> = ConcurrentHashMap()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val asyncExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun registerListener(key: String?, listener: OnPreferenceChangeListener?) {
        if (key == null || listener == null) return
        listenersMap.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(listener)
    }

    fun unregisterListener(listener: OnPreferenceChangeListener?) {
        if (listener == null) return
        for (listeners in listenersMap.values) {
            listeners.remove(listener)
        }
    }

    fun unregisterListener(key: String?, listener: OnPreferenceChangeListener?) {
        if (key == null || listener == null) return
        val listeners = listenersMap[key]
        listeners?.remove(listener)
    }

    fun watchEffect(effect: PreferenceEffect?): EffectHandle {
        if (effect == null) return EffectHandle { }
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val reg = WatchEffectRegistration(this, effect, isMainThread)
        activeEffects.add(reg)
        reg.runEffect()
        return reg
    }

    fun watchEffect(tag: Any?, effect: PreferenceEffect?): EffectHandle {
        val handle = watchEffect(effect)
        if (tag != null) {
            taggedEffects.computeIfAbsent(tag) { CopyOnWriteArraySet() }.add(handle)
        }
        return handle
    }

    fun watchEffects(vararg effects: PreferenceEffect?): EffectHandle {
        if (effects.isEmpty()) return EffectHandle { }
        val handles = ArrayList<EffectHandle>()
        for (effect in effects) {
            if (effect != null) {
                handles.add(watchEffect(effect))
            }
        }
        return EffectHandle {
            for (handle in handles) {
                handle.dispose()
            }
            handles.clear()
        }
    }

    fun disposeEffects(tag: Any?) {
        tag ?: return
        val handles = taggedEffects.remove(tag)
        if (handles != null) {
            for (handle in handles) {
                handle.dispose()
            }
            handles.clear()
        }
    }

    fun <T> getComputed(computer: ComputedValue<T>?): T? {
        return getComputed(computer as Any?, computer)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getComputed(cacheKey: Any?, computer: ComputedValue<T>?): T? {
        if (cacheKey == null || computer == null) return null
        val cached = computedCache[cacheKey]
        if (cached != null && !cached.isStale(this)) {
            return cached.value as T?
        }
        val getter = TrackingPreferenceGetter(this)
        val result = computer.compute(getter)
        computedCache[cacheKey] = CachedComputation(result, getter.accessedValues)
        return result
    }

    fun invalidateComputed(cacheKey: Any?) {
        if (cacheKey != null) {
            computedCache.remove(cacheKey)
        }
    }

    fun invalidateAllComputed() {
        computedCache.clear()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return

        if (computedCache.isNotEmpty()) {
            for ((cacheKey, cached) in computedCache) {
                if (cached.trackedValues != null && (cached.trackedValues.containsKey(key) || cached.trackedValues.containsKey("contains:$key"))) {
                    computedCache.remove(cacheKey)
                }
            }
        }

        if (activeEffects.isNotEmpty()) {
            for (reg in activeEffects) {
                if (!reg.isDisposed && reg.trackedKeys.contains(key)) {
                    reg.runEffect()
                }
            }
        }

        val listeners = listenersMap[key]
        if (!listeners.isNullOrEmpty()) {
            for (listener in listeners) {
                listener.onPreferenceChanged(key)
            }
        }
    }

    fun edit(): SharedPreferences.Editor {
        return sharedPreferences.edit()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defValue)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return sharedPreferences.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return sharedPreferences.getLong(key, defValue)
    }

    override fun getString(key: String, defValue: String?): String? {
        return sharedPreferences.getString(key, defValue)
    }

    override fun contains(key: String): Boolean {
        return sharedPreferences.contains(key)
    }

    fun shutdown() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        listenersMap.clear()
        computedCache.clear()
        for (reg in activeEffects) {
            reg.dispose()
        }
        activeEffects.clear()
        asyncExecutor.shutdown()
    }

    companion object {
        private val NULL_SENTINEL = Any()
    }
}
