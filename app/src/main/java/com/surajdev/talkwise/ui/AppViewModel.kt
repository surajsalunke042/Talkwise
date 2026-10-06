package com.surajdev.talkwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.surajdev.talkwise.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Boot {
    data object Connecting : Boot
    data object SetupMissing : Boot
    data class Failed(val title: String, val detail: String) : Boot
}

/** Owns the connection to the backend: sign-in and the live profile. */
class AppViewModel(private val backend: AppContainer.Backend?) : ViewModel() {
    private val _boot = MutableStateFlow<Boot>(if (backend == null) Boot.SetupMissing else Boot.Connecting)
    val boot: StateFlow<Boot> = _boot

    private var job: Job? = null

    init {
        connect()
    }

    fun connect() {
        val users = backend?.users ?: return
        job?.cancel()
        _boot.value = Boot.Connecting
        job = viewModelScope.launch {
            try {
                users.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _boot.value = e.toBootFailure()
            }
        }
    }

    private fun Exception.toBootFailure(): Boot.Failed = when {
        this is FirebaseNetworkException ||
            (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE) ->
            Boot.Failed("You're offline", "Talkwise needs a connection to talk. Check your internet and try again.")

        this is FirebaseAuthException ->
            Boot.Failed("Sign-in isn't ready", "Enable Anonymous sign-in in the Firebase console, then try again.")

        this is FirebaseFirestoreException ->
            Boot.Failed("The database isn't ready", "Create the Firestore database and publish the rules from firestore.rules.")

        else -> Boot.Failed("Something went wrong", message ?: "Please try again in a moment.")
    }
}
