package com.ganha.test.utils

import androidx.lifecycle.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 描述 ：通过Flow和协程作为事件总线，感性生命周期，不需要手动取消绑定
 * 绑定事件：FlowEventBus.with<String>("key").register(this){}
 * 发送事件：FlowEventBus.with<String>("key").post("value")
 */
object FlowEventBus {
    private val busMap = mutableMapOf<String, EventBus<*>>()

    /**
     * 绑定事件
     * @param key String
     * @return EventBus<T>
     */
    @Synchronized
    fun <T> with(key: String): EventBus<T> {
        var eventBus = busMap[key]
        if (eventBus == null) {
            eventBus = EventBus<T>(key)
            busMap[key] = eventBus
        }
        return eventBus as EventBus<T>
    }

    class EventBus<T>(private val key: String) : LifecycleObserver {

        private val _events = MutableSharedFlow<T>()
        private val events = _events.asSharedFlow()

        /**
         * 注册事件
         * @param lifecycleOwner LifecycleOwner
         * @param action Function1<[@kotlin.ParameterName] T, Unit>
         */
        fun register(lifecycleOwner: LifecycleOwner, action: (t: T) -> Unit) {
            lifecycleOwner.lifecycle.addObserver(this)
            lifecycleOwner.lifecycleScope.launch {
                events.collect {
                    try {
                        action(it)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        /**
         * 发送事件
         * @param event T
         */
        fun post(value: T) {
            GlobalScope.launch {
                _events.emit(value)
            }

        }

        /**
         * 页面销毁，自动移除事件
         */
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() {
            val subscriptCount = _events.subscriptionCount.value
            if (subscriptCount <= 0) busMap.remove(key)

        }

        /**
         * 手动取消注册
         * @param key String
         */
        fun clear(key: String) {
            if (busMap.containsKey(key)) busMap.remove(key)
        }
    }
}