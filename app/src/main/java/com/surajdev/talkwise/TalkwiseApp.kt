package com.surajdev.talkwise

import android.app.Application
import android.content.SharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.surajdev.talkwise.ai.TutorService
import com.surajdev.talkwise.data.UserRepository

class TalkwiseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(val app: Application) {
    val prefs: SharedPreferences = app.getSharedPreferences("talkwise", Application.MODE_PRIVATE)

    /** Null until google-services.json has been added to the project. */
    val backend: Backend? = if (FirebaseApp.getApps(app).isEmpty()) null else Backend()

    class Backend {
        init {
            AppCheckInstaller.install()
        }

        val users = UserRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
        val tutor = TutorService(FirebaseRemoteConfig.getInstance())
    }
}
