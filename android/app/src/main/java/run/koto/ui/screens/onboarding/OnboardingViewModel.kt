package run.koto.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.koto.crypto.KeyManager
import run.koto.data.repository.AuthRepository
import javax.inject.Inject

enum class OnboardingStep { Welcome, GeneratingKey, ShowAccountId }

data class OnboardingState(
    val step       : OnboardingStep = OnboardingStep.Welcome,
    val accountId  : String = "",
    val registered : Boolean = false,
    val error      : String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keyManager     : KeyManager,
    private val authRepository : AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun startKeyGen() {
        viewModelScope.launch {
            _state.update { it.copy(step = OnboardingStep.GeneratingKey) }

            // Generate Ed25519 keypair on-device
            val keys = keyManager.generateIdentityKey()
            delay(1200) // intentional short delay so user sees the step

            // Register with the server (no phone, no email)
            val result = authRepository.register(keys)
            result.onSuccess { accountId ->
                _state.update { it.copy(step = OnboardingStep.ShowAccountId, accountId = accountId) }
            }.onFailure { err ->
                _state.update { it.copy(step = OnboardingStep.Welcome, error = err.message) }
            }
        }
    }

    fun confirmAccountId() {
        _state.update { it.copy(registered = true) }
    }
}
