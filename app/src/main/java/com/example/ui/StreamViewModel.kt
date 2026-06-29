package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.StreamDao
import com.example.data.model.Stream
import com.example.data.repository.StreamRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class StreamViewModel(
    private val repository: StreamRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _channelsList = MutableStateFlow<List<Stream>>(emptyList())
    val channelsList: StateFlow<List<Stream>> = _channelsList.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val count = repository.getStreamsCount()
            if (count == 0) {
                syncRemoteStreams()
            } else {
                loadStreams()
            }
        }

        // Keep observing search query with debounce
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            searchQuery.debounce(300).collect {
                loadStreams()
            }
        }
    }

    fun syncRemoteStreams() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val result = repository.refreshStreams()
            _isSyncing.value = false
            if (result.isSuccess) {
                loadStreams()
            } else {
                _syncError.value = result.exceptionOrNull()?.localizedMessage ?: "Unknown network error"
                // Load local database data anyway if available
                loadStreams()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadStreams() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoadingMore.value = true
            val results = repository.loadStreams(query = _searchQuery.value)
            _channelsList.value = results
            _isLoadingMore.value = false
        }
    }

    fun setSpeechRecognizerListening(listening: Boolean) {
        _isListening.value = listening
    }

    class Factory(private val repository: StreamRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StreamViewModel::class::class.java) || modelClass.isAssignableFrom(StreamViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StreamViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
