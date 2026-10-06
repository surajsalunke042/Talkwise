package com.surajdev.talkwise.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** The learner's profile, progress and saved words, stored under users/{uid} in Firestore. */
class UserRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _words = MutableStateFlow<List<PocketWord>>(emptyList())
    val words: StateFlow<List<PocketWord>> = _words

    private fun userDoc(): DocumentReference =
        db.collection("users").document(checkNotNull(auth.currentUser?.uid) { "Not signed in" })

    /** Signs in anonymously if needed, then keeps [profile] and [words] in sync until cancelled. */
    suspend fun connect() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        val doc = userDoc()
        coroutineScope {
            launch { doc.snapshots().collect { _profile.value = it.toProfile() } }
            launch {
                doc.collection("words").orderBy("addedAt", Query.Direction.DESCENDING).limit(200)
                    .snapshots { docs -> docs.map { it.toWord() } }
                    .collect { _words.value = it }
            }
        }
    }

    fun completeOnboarding(goalMinutes: Int) {
        userDoc().set(mapOf("onboarded" to true, "goalMinutes" to goalMinutes), SetOptions.merge())
    }

    /**
     * Records one completed conversation turn: updates the level estimate, the streak and today's counters.
     * Returns the profile as it stands after the turn.
     */
    fun recordTurn(sample: Skills?, today: LocalDate = LocalDate.now()): UserProfile? {
        val current = _profile.value ?: return null
        val day = today.toString()
        val level = LevelState(current.skills, current.band, current.assessedTurns)
            .let { if (sample != null) LevelEngine.update(it, sample) else it }
        val streak = StreakEngine.onActive(
            StreakState(current.streak, current.bestStreak, current.freezes, current.lastActiveDay?.let(LocalDate::parse)),
            today,
        )
        val next = current.copy(
            skills = level.skills,
            band = level.band,
            assessedTurns = level.assessedTurns,
            streak = streak.streak,
            bestStreak = streak.best,
            freezes = streak.freezes,
            lastActiveDay = day,
            today = day,
            todaySeconds = current.secondsOn(day),
            todayTurns = current.turnsOn(day) + 1,
        )
        _profile.value = next
        val doc = userDoc()
        doc.set(next.toMap(), SetOptions.merge())
        // One snapshot per day gives a history of the level over time.
        doc.collection("days").document(day).set(
            mapOf(
                "vocabulary" to next.skills.vocabulary,
                "grammar" to next.skills.grammar,
                "fluency" to next.skills.fluency,
                "band" to next.band.name,
                "turns" to next.todayTurns,
            ),
            SetOptions.merge(),
        )
        return next
    }

    fun addPracticeSeconds(seconds: Int, today: LocalDate = LocalDate.now()) {
        if (seconds <= 0) return
        val current = _profile.value ?: return
        val day = today.toString()
        val next = current.copy(
            today = day,
            todaySeconds = current.secondsOn(day) + seconds,
            todayTurns = current.turnsOn(day),
        )
        _profile.value = next
        val doc = userDoc()
        doc.set(
            mapOf("today" to day, "todaySeconds" to next.todaySeconds, "todayTurns" to next.todayTurns),
            SetOptions.merge(),
        )
        doc.collection("days").document(day).set(mapOf("seconds" to next.todaySeconds), SetOptions.merge())
    }

    fun saveWord(word: PocketWord) {
        userDoc().collection("words").document(word.word.lowercase().replace('/', ' ').take(60)).set(
            mapOf("word" to word.word, "meaning" to word.meaning, "addedAt" to System.currentTimeMillis()),
        )
    }

    fun removeWord(id: String) {
        userDoc().collection("words").document(id).delete()
    }

    fun saveSession(scenarioId: String, seconds: Int, turns: Int, recap: Recap) {
        userDoc().collection("sessions").add(
            mapOf(
                "scenario" to scenarioId,
                "seconds" to seconds,
                "turns" to turns,
                "wins" to recap.wins,
                "focus" to recap.focus,
                "endedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }
}

private fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

private fun <T> Query.snapshots(map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(map(snapshot.documents))
    }
    awaitClose { registration.remove() }
}

private fun DocumentSnapshot.toProfile(): UserProfile {
    val defaults = UserProfile()
    if (!exists()) return defaults
    fun score(key: String, fallback: Float) = getDouble(key)?.toFloat() ?: fallback
    fun int(key: String, fallback: Int) = getLong(key)?.toInt() ?: fallback
    return UserProfile(
        onboarded = getBoolean("onboarded") ?: false,
        skills = Skills(
            vocabulary = score("vocabulary", defaults.skills.vocabulary),
            grammar = score("grammar", defaults.skills.grammar),
            fluency = score("fluency", defaults.skills.fluency),
        ),
        band = getString("band")?.let { name -> Band.entries.firstOrNull { it.name == name } } ?: defaults.band,
        assessedTurns = int("assessedTurns", 0),
        streak = int("streak", 0),
        bestStreak = int("bestStreak", 0),
        freezes = int("freezes", defaults.freezes),
        lastActiveDay = getString("lastActiveDay"),
        goalMinutes = int("goalMinutes", defaults.goalMinutes),
        today = getString("today"),
        todaySeconds = int("todaySeconds", 0),
        todayTurns = int("todayTurns", 0),
    )
}

private fun UserProfile.toMap(): Map<String, Any?> = mapOf(
    "vocabulary" to skills.vocabulary,
    "grammar" to skills.grammar,
    "fluency" to skills.fluency,
    "band" to band.name,
    "assessedTurns" to assessedTurns,
    "streak" to streak,
    "bestStreak" to bestStreak,
    "freezes" to freezes,
    "lastActiveDay" to lastActiveDay,
    "today" to today,
    "todaySeconds" to todaySeconds,
    "todayTurns" to todayTurns,
)

private fun DocumentSnapshot.toWord() = PocketWord(
    id = id,
    word = getString("word").orEmpty(),
    meaning = getString("meaning").orEmpty(),
    addedAt = getLong("addedAt") ?: 0L,
)
