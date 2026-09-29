package com.bas080.autosleepdroid

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    private val listenerManager = PreferenceListenerManager()
    private val computedCache = PreferenceComputedCache()
    private val effectManager = PreferenceEffectManager(this)

    init {
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun registerListener(key: String?, listener: OnPreferenceChangeListener?) {
        listenerManager.registerListener(key, listener)
    }

    fun unregisterListener(key: String?, listener: OnPreferenceChangeListener?) {
        listenerManager.unregisterListener(key, listener)
    }

    fun watchEffect(tag: Any?, effect: PreferenceEffect?): EffectHandle {
        return effectManager.watchEffect(tag, effect)
    }

    fun watchEffects(vararg effects: PreferenceEffect?): EffectHandle {
        return effectManager.watchEffects(*effects)
    }

    fun disposeEffects(tag: Any?) {
        effectManager.disposeEffects(tag)
    }

    fun <T> getComputed(cacheKey: Any?, computer: ComputedValue<T>?): T? {
        return computedCache.getComputed(this, cacheKey, computer)
    }

    fun invalidateComputed(cacheKey: Any?) {
        if (cacheKey == null) {
            computedCache.invalidateAllComputed()
        } else {
            computedCache.invalidateComputed(cacheKey)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return

        computedCache.invalidateOnKeyChange(key)
        effectManager.notifyEffectsOnKeyChange(key)
        listenerManager.notifyListenersOnKeyChange(key)
    }

    fun edit(): SharedPreferences.Editor {
        return sharedPreferences.edit()
    }

    fun shutdown() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        listenerManager.clear()
        computedCache.invalidateAllComputed()
        effectManager.shutdown()
    }

    companion object {
        internal val NULL_SENTINEL = Any()
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

internal class PreferenceListenerManager {
    private val listenersMap: MutableMap<String, MutableSet<PreferenceManager.OnPreferenceChangeListener>> =
        ConcurrentHashMap()

    fun registerListener(key: String?, listener: PreferenceManager.OnPreferenceChangeListener?) {
        if (key == null || listener == null) return
        listenersMap.computeIfAbsent(key) { CopyOnWriteArraySet() }.add(listener)
    }

    fun unregisterListener(listener: PreferenceManager.OnPreferenceChangeListener?) {
        if (listener == null) return
        for (listeners in listenersMap.values) {
            listeners.remove(listener)
        }
    }

    fun unregisterListener(key: String?, listener: PreferenceManager.OnPreferenceChangeListener?) {
        if (key == null) {
            unregisterListener(listener)
            return
        }
        if (listener == null) return
        listenersMap[key]?.remove(listener)
    }

    fun notifyListenersOnKeyChange(key: String) {
        val listeners = listenersMap[key]
        if (!listeners.isNullOrEmpty()) {
            for (listener in listeners) {
                listener.onPreferenceChanged(key)
            }
        }
    }

    fun clear() {
        listenersMap.clear()
    }
}

internal class PreferenceComputedCache {
    private val computedCache: MutableMap<Any, CachedComputation> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    fun <T> getComputed(
        preferenceManager: PreferenceManager,
        cacheKey: Any?,
        computer: PreferenceManager.ComputedValue<T>?
    ): T? {
        if (cacheKey == null || computer == null) return null
        val cached = computedCache[cacheKey]
        return if (cached != null && !cached.isStale(preferenceManager)) {
            cached.value as T?
        } else {
            val getter = TrackingPreferenceGetter(preferenceManager)
            val result = computer.compute(getter)
            computedCache[cacheKey] = CachedComputation(result, getter.accessedValues)
            result
        }
    }

    fun invalidateComputed(cacheKey: Any?) {
        if (cacheKey != null) {
            computedCache.remove(cacheKey)
        }
    }

    fun invalidateAllComputed() {
        computedCache.clear()
    }

    fun invalidateOnKeyChange(key: String) {
        if (computedCache.isEmpty()) return
        for ((cacheKey, cached) in computedCache) {
            val tracked = cached.trackedValues
            if (tracked != null && (tracked.containsKey(key) || tracked.containsKey("contains:$key"))) {
                computedCache.remove(cacheKey)
            }
        }
    }

    private class CachedComputation(
        val value: Any?,
        val trackedValues: Map<String, Any>?
    ) {
        fun isStale(preferenceManager: PreferenceManager): Boolean {
            val tracked = trackedValues
            return if (tracked.isNullOrEmpty()) {
                false
            } else {
                tracked.entries.any { (key, trackedValue) ->
                    isKeyStale(preferenceManager, key, trackedValue)
                }
            }
        }

        private fun isKeyStale(
            preferenceManager: PreferenceManager,
            key: String,
            trackedValue: Any
        ): Boolean {
            if (key.startsWith("contains:")) {
                val actualKey = key.substring("contains:".length)
                return trackedValue != preferenceManager.contains(actualKey)
            }
            return isValueStale(preferenceManager, key, trackedValue)
        }

        private fun isValueStale(
            preferenceManager: PreferenceManager,
            key: String,
            trackedValue: Any
        ): Boolean {
            return when (trackedValue) {
                is Boolean -> trackedValue != preferenceManager.getBoolean(key, !trackedValue)
                is Int -> trackedValue != preferenceManager.getInt(key, trackedValue + 1)
                is Long -> trackedValue != preferenceManager.getLong(key, trackedValue + 1L)
                PreferenceManager.NULL_SENTINEL -> preferenceManager.contains(key)
                is String -> trackedValue != preferenceManager.getString(key, null)
                else -> false
            }
        }
    }
}

internal class PreferenceEffectManager(private val preferenceManager: PreferenceManager) {
    private val activeEffects: MutableSet<WatchEffectRegistration> = CopyOnWriteArraySet()
    private val taggedEffects: MutableMap<Any, MutableSet<PreferenceManager.EffectHandle>> = ConcurrentHashMap()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val asyncExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun watchEffect(tag: Any?, effect: PreferenceManager.PreferenceEffect?): PreferenceManager.EffectHandle {
        if (effect == null) return PreferenceManager.EffectHandle { }
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val reg = WatchEffectRegistration(preferenceManager, effect, isMainThread, this)
        activeEffects.add(reg)
        if (tag != null) {
            taggedEffects.computeIfAbsent(tag) { CopyOnWriteArraySet() }.add(reg)
        }
        reg.runEffect()
        return reg
    }

    fun watchEffects(vararg effects: PreferenceManager.PreferenceEffect?): PreferenceManager.EffectHandle {
        if (effects.isEmpty()) return PreferenceManager.EffectHandle { }
        val handles = ArrayList<PreferenceManager.EffectHandle>()
        for (effect in effects) {
            if (effect != null) {
                handles.add(watchEffect(null, effect))
            }
        }
        return PreferenceManager.EffectHandle {
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

    fun notifyEffectsOnKeyChange(key: String) {
        if (activeEffects.isEmpty()) return
        for (reg in activeEffects) {
            if (!reg.isDisposed && reg.trackedKeys.contains(key)) {
                reg.runEffect()
            }
        }
    }

    fun shutdown() {
        for (reg in activeEffects) {
            reg.dispose()
        }
        activeEffects.clear()
        taggedEffects.clear()
        asyncExecutor.shutdown()
    }

    private class WatchEffectRegistration(
        val preferenceManager: PreferenceManager,
        val effect: PreferenceManager.PreferenceEffect,
        val isMainThread: Boolean,
        val effectManager: PreferenceEffectManager
    ) : PreferenceManager.EffectHandle {
        val trackedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
        @Volatile
        var isDisposed = false

        fun runEffect() {
            if (isDisposed) return
            val runnable = Runnable { executeEffect() }
            dispatch(runnable)
        }

        private fun executeEffect() {
            if (isDisposed) return
            val getter = TrackingPreferenceGetter(preferenceManager)
            effect.run(getter)
            trackedKeys.clear()
            for (key in getter.accessedValues.keys) {
                val cleanKey = if (key.startsWith("contains:")) key.substring("contains:".length) else key
                trackedKeys.add(cleanKey)
            }
        }

        fun dispatch(runnable: Runnable) {
            if (isMainThread) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runnable.run()
                } else {
                    effectManager.mainHandler.post(runnable)
                }
            } else {
                effectManager.asyncExecutor.execute(runnable)
            }
        }

        override fun dispose() {
            isDisposed = true
            effectManager.activeEffects.remove(this)
        }
    }
}

internal class TrackingPreferenceGetter(private val preferenceManager: PreferenceManager) : PreferenceGetter {
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
            accessedValues[key] = PreferenceManager.NULL_SENTINEL
        }
        return value
    }

    override fun contains(key: String): Boolean {
        val value = preferenceManager.contains(key)
        accessedValues["contains:$key"] = value
        return value
    }
}
