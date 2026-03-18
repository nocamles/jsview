package com.ganha.test.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MainViewModel: ViewModel() {
    private val _messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val messageFlow = _messageFlow.asSharedFlow()

    fun sendJsMessage(message: String) {
        _messageFlow.tryEmit(message)
    }
}