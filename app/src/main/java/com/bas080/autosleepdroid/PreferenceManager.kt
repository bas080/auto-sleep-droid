package com.bas080.autosleepdroid

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class PreferenceManager(
    val sharedPreferences: SharedPreferences
) : SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceGetter by DefaultPreferenceGetter(sharedPreferences) {

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

    internal val keyListeners = ConcurrentHashMap<String, MutableSet<OnPreferenceChangeListener>>()
    internal val activeEffects = CopyOnWriteArraySet<WatchEffectRegistration>()
    internal val taggedEffects = ConcurrentHashMap<Any, MutableSet<EffectHandle>>()
    internal val computedCache = ConcurrentHashMap<Any, CachedComputation>()

    init {
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun registerListener(key: String?, listener: OnPreferenceChangeListener?) {
        if (key == null || listener == null) return
        keyListeners.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(listener)
    }

    fun unregisterListener(key: String?, listener: OnPreferenceChangeListener?) {
        if (key == null) {
            if (listener != null) {
                for (set in keyListeners.values) set.remove(listener)
            }
            return
        }
        if (listener == null) return
        keyListeners[key]?.remove(listener)
    }

    fun watchEffect(tag: Any?, effect: PreferenceEffect?): EffectHandle {
        if (effect == null) return EffectHandle { }
        val reg = WatchEffectRegistration(this, effect)
        activeEffects.add(reg)
        if (tag != null) {
            taggedEffects.computeIfAbsent(tag) { CopyOnWriteArraySet() }.add(reg)
        }
        reg.runEffect()
        return reg
    }

    fun watchEffects(vararg effects: PreferenceEffect?): EffectHandle {
        if (effects.isEmpty()) return EffectHandle { }
        val handles = ArrayList<EffectHandle>()
        for (effect in effects) {
            if (effect != null) {
                handles.add(watchEffect(null, effect))
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

    fun <T> getComputed(cacheKey: Any?, computer: ComputedValue<T>?): T? {
        if (cacheKey == null || computer == null) return null
        val cached = computedCache[cacheKey]
        val value = if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            (cached.value as T?)
        } else {
            val tracker = TrackingPreferenceGetter(this)
            val result = computer.compute(tracker)
            computedCache[cacheKey] = CachedComputation(result, tracker.accessedKeys)
            result
        }
        return value
    }

    fun invalidateComputed(cacheKey: Any?) {
        if (cacheKey == null) {
            computedCache.clear()
        } else {
            computedCache.remove(cacheKey)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        PreferenceManagerNotifier.handlePreferenceChange(this, key)
    }

    fun edit(): SharedPreferences.Editor {
        return sharedPreferences.edit()
    }

    fun shutdown() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        keyListeners.clear()
        computedCache.clear()
        for (reg in activeEffects) {
            reg.dispose()
        }
        activeEffects.clear()
        taggedEffects.clear()
    }

    companion object {
        internal val NULL_SENTINEL = Any()
    }

    internal class CachedComputation(
        val value: Any?,
        val trackedKeys: Set<String>
    )

    internal inner class WatchEffectRegistration(
        val preferenceManager: PreferenceManager,
        val effect: PreferenceEffect
    ) : EffectHandle {
        val trackedKeys = ConcurrentHashMap.newKeySet<String>()
        @Volatile
        var isDisposed = false

        fun runEffect() {
            if (isDisposed) return
            val tracker = TrackingPreferenceGetter(preferenceManager)
            effect.run(tracker)
            trackedKeys.clear()
            trackedKeys.addAll(tracker.accessedKeys)
        }

        override fun dispose() {
            isDisposed = true
            activeEffects.remove(this)
        }
    }
}

internal object PreferenceManagerNotifier {
    fun handlePreferenceChange(manager: PreferenceManager, key: String?) {
        if (key == null) {
            manager.computedCache.clear()
            for (set in manager.keyListeners.values) {
                for (listener in set) listener.onPreferenceChanged("")
            }
            for (reg in manager.activeEffects) reg.runEffect()
            return
        }

        if (manager.computedCache.isNotEmpty()) {
            for ((cacheKey, cached) in manager.computedCache) {
                if (cached.trackedKeys.contains(key)) manager.computedCache.remove(cacheKey)
            }
        }

        val listeners = manager.keyListeners[key]
        if (!listeners.isNullOrEmpty()) {
            for (listener in listeners) listener.onPreferenceChanged(key)
        }

        if (manager.activeEffects.isNotEmpty()) {
            for (reg in manager.activeEffects) {
                if (!reg.isDisposed && reg.trackedKeys.contains(key)) reg.runEffect()
            }
        }
    }
}

fun PreferenceManager.unregisterListener(listener: PreferenceManager.OnPreferenceChangeListener?) {
    unregisterListener(null, listener)
}

fun PreferenceManager.watchEffect(effect: PreferenceManager.PreferenceEffect?): PreferenceManager.EffectHandle {
    return watchEffect(null, effect)
}

fun <T> PreferenceManager.getComputed(computer: PreferenceManager.ComputedValue<T>?): T? {
    return getComputed(computer as Any?, computer)
}

fun PreferenceManager.invalidateAllComputed() {
    invalidateComputed(null)
}

internal class DefaultPreferenceGetter(
    private val sharedPreferences: SharedPreferences
) : PreferenceGetter {
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
}

internal class TrackingPreferenceGetter(private val preferenceGetter: PreferenceGetter) : PreferenceGetter {
    val accessedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        accessedKeys.add(key)
        return preferenceGetter.getBoolean(key, defValue)
    }

    override fun getInt(key: String, defValue: Int): Int {
        accessedKeys.add(key)
        return preferenceGetter.getInt(key, defValue)
    }

    override fun getLong(key: String, defValue: Long): Long {
        accessedKeys.add(key)
        return preferenceGetter.getLong(key, defValue)
    }

    override fun getString(key: String, defValue: String?): String? {
        accessedKeys.add(key)
        return preferenceGetter.getString(key, defValue)
    }

    override fun contains(key: String): Boolean {
        accessedKeys.add(key)
        return preferenceGetter.contains(key)
    }
}
