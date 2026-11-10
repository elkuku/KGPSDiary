package org.elkuku.kgpsdiary.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.elkuku.kgpsdiary.R
import org.elkuku.kgpsdiary.UiText
import org.elkuku.kgpsdiary.auth.AuthException
import org.elkuku.kgpsdiary.auth.SessionManager
import org.elkuku.kgpsdiary.data.ApiResult
import org.elkuku.kgpsdiary.data.DiaryRepository
import org.elkuku.kgpsdiary.data.KgpsApi
import org.elkuku.kgpsdiary.data.Me
import org.elkuku.kgpsdiary.uiText

data class MainUiState(
    /** False until the silent sign-in at the start finished. */
    val started: Boolean = false,
    val busy: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    /** The user on the server, once `/api/me` answered. */
    val me: Me? = null,
    val message: UiText? = null,
) {
    val signedIn: Boolean get() = email != null
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KgpsApi,
    private val diary: DiaryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        // A session that ends elsewhere (a token the server keeps rejecting) signs out here too
        viewModelScope.launch {
            sessionManager.session.collect { session ->
                if (session == null && _state.value.started && !_state.value.busy) {
                    diary.clear()
                    _state.update { it.copy(email = null, displayName = null, me = null) }
                }
            }
        }
    }

    /** Restores the session of an account that already signed in, once. */
    fun start(context: Context) {
        if (_state.value.started || _state.value.busy) return

        run {
            if (sessionManager.restore(context) != null) {
                loadMe(context)
            }
        }
    }

    fun signIn(context: Context) = run {
        try {
            sessionManager.signIn(context)
            loadMe(context)
        } catch (e: AuthException) {
            if (!e.cancelled) {
                _state.update { it.copy(message = e.text) }
            }
        }
    }

    fun refresh(context: Context) = run { loadMe(context) }

    fun signOut(context: Context) = run {
        sessionManager.signOut(context)
        diary.clear()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private suspend fun loadMe(context: Context) {
        when (val result = sessionManager.authorized(context, api::me)) {
            is ApiResult.Success -> _state.update { it.copy(me = result.value, message = null) }
            is ApiResult.Unauthorized -> _state.update {
                it.copy(message = result.message ?: uiText(R.string.error_unauthorized))
            }
            is ApiResult.Failure -> _state.update { it.copy(message = result.message) }
        }
    }

    /** [block] while busy; the session's account is shown afterwards. */
    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                val session = sessionManager.session.value
                _state.update {
                    it.copy(
                        started = true,
                        busy = false,
                        email = session?.email,
                        displayName = session?.displayName,
                        me = if (session == null) null else it.me,
                    )
                }
            }
        }
    }
}
