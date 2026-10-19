package com.todi.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todi.app.backup.BackupAuthClient
import com.todi.app.backup.BackupException
import com.todi.app.backup.BackupFailure
import com.todi.app.backup.BackupServer
import com.todi.app.backup.toBackupFailure
import com.todi.app.domain.model.BackupAccount
import com.todi.app.domain.repository.BackupAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInState(
    val busy: Boolean = false,
    val awaitingCodeFor: String? = null,
    val error: BackupFailure? = null,
)

@HiltViewModel
class BackupAccountViewModel
    @Inject
    constructor(
        private val backupAuth: BackupAuthClient,
        private val backupAccounts: BackupAccountRepository,
    ) : ViewModel() {
        val account: StateFlow<BackupAccount?> =
            backupAccounts.account.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(ACCOUNT_SUBSCRIBE_MILLIS),
                initialValue = null,
            )

        private val _signIn = MutableStateFlow(SignInState())

        val signIn: StateFlow<SignInState> = _signIn.asStateFlow()

        fun requestCode(email: String) {
            viewModelScope.launch {
                _signIn.update { it.copy(busy = true, error = null) }
                try {
                    backupAuth.requestCode(BackupServer.url, email)
                    _signIn.update { it.copy(busy = false, awaitingCodeFor = email) }
                } catch (e: BackupException) {
                    _signIn.update { it.copy(busy = false, awaitingCodeFor = null, error = e.toBackupFailure()) }
                }
            }
        }

        fun verifyCode(
            email: String,
            code: String,
        ) {
            viewModelScope.launch {
                _signIn.update { it.copy(busy = true, error = null) }
                try {
                    backupAccounts.signIn(email, backupAuth.verifyCode(BackupServer.url, email, code), expiresAt = null)
                    _signIn.update { SignInState() }
                } catch (e: BackupException) {
                    _signIn.update { it.copy(busy = false, error = e.toBackupFailure()) }
                }
            }
        }

        fun cancelCode() {
            _signIn.update { it.copy(awaitingCodeFor = null, error = null) }
        }

        fun consumeError() {
            _signIn.update { it.copy(error = null) }
        }

        private companion object {
            const val ACCOUNT_SUBSCRIBE_MILLIS = 5_000L
        }
    }
