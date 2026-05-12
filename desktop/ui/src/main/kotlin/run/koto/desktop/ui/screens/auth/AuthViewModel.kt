package run.koto.desktop.ui.screens.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import run.koto.desktop.domain.model.Account
import run.koto.desktop.domain.model.Session
import run.koto.desktop.domain.repository.AuthRepository

class AuthViewModel(
    private val auth: AuthRepository,
) {
    sealed interface State {
        data object Idle              : State
        data object Loading           : State
        data class  Success(val a: Account) : State
        data class  Error  (val msg: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val session: Flow<Session?> = auth.session

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun register(displayName: String, seedPhrase: List<String>) {
        if (_state.value is State.Loading) return
        _state.value = State.Loading
        scope.launch {
            val res = auth.register(displayName, seedPhrase)
            _state.value = res.fold(
                onSuccess = { State.Success(it) },
                onFailure = { State.Error(it.message ?: "Не удалось зарегистрироваться") },
            )
        }
    }

    fun restore(seedPhrase: List<String>) {
        if (_state.value is State.Loading) return
        _state.value = State.Loading
        scope.launch {
            val res = auth.restore(seedPhrase)
            _state.value = res.fold(
                onSuccess = { State.Success(it) },
                onFailure = { State.Error(it.message ?: "Не удалось восстановить аккаунт") },
            )
        }
    }

    suspend fun previewKotoId(seedPhrase: List<String>): String? =
        auth.previewKotoId(seedPhrase).getOrNull()

    fun resetState() { _state.value = State.Idle }

    fun signOut() {
        scope.launch {
            auth.signOut()
            _state.value = State.Idle
        }
    }

    fun close() { scope.cancel() }
}
