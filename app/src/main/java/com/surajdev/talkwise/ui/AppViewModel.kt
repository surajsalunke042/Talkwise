package com.surajdev.talkwise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.surajdev.talkwise.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /** Erases the learner's data and anonymous account, then starts again with a fresh one. */
    fun deleteData() {
        val users = backend?.users ?: return
        // Stop the live listeners first, so they neither fail on the vanished account nor bring the profile back.
        job?.cancel()
        _boot.value = Boot.Connecting
        job = viewModelScope.launch {
            try {
                // Firestore writes never finish while offline, so give up rather than wait forever.
                if (withTimeoutOrNull(DELETE_TIMEOUT_MS) { users.deleteEverything() } == null) {
                    _boot.value = Boot.Failed("Couldn't finish deleting", "Check your internet, then try deleting again.")
                    return@launch
                }
                users.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _boot.value = e.toBootFailure()
            }
        }
    }

    // The details are for the developer and go to Logcat; the learner only needs to know what to do next.
    private fun Exception.toBootFailure(): Boot.Failed {
        Log.w("Talkwise", "Could not connect", this)
        return when {
            this is FirebaseNetworkException ||
                (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE) ->
                Boot.Failed("You're offline", "Talkwise needs a connection to talk. Check your internet and try again.")

            else -> Boot.Failed("Something went wrong", "Talkwise couldn't get ready. Please try again in a moment.")
        }
    }

    private companion object {
        const val DELETE_TIMEOUT_MS = 20_000L
    }
}
