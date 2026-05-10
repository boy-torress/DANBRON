package com.danbron.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.danbron.app.data.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "danbron_prefs")

class UserPreferences(private val context: Context) {
    private val gson = Gson()

    companion object {
        // API key configured by the developer — never exposed to users
        const val DEFAULT_API_KEY = "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"

        val USER_JSON = stringPreferencesKey("user_json")
        val API_KEY = stringPreferencesKey("api_key")
        val TASKS_JSON = stringPreferencesKey("tasks_json")
        val HABITS_JSON = stringPreferencesKey("habits_json")
        val HABIT_LOGS_JSON = stringPreferencesKey("habit_logs_json")
        val NOTES_JSON = stringPreferencesKey("notes_json")
        val CHAT_MESSAGES_JSON = stringPreferencesKey("chat_messages_json")
        val ONBOARDING_MESSAGES_JSON = stringPreferencesKey("onboarding_messages_json")
        val USAGE_MINUTES = intPreferencesKey("usage_minutes")
        val LAST_BRON_MSG = stringPreferencesKey("last_bron_msg")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val REFERRAL_CODE = stringPreferencesKey("referral_code")
        val REFERRAL_COUNT = intPreferencesKey("referral_count")
        val IS_PRO = booleanPreferencesKey("is_pro")
        val SYNC_CODE = stringPreferencesKey("sync_code")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val user: Flow<User?> = context.dataStore.data.map { prefs ->
        prefs[USER_JSON]?.let { gson.fromJson(it, User::class.java) }
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_KEY] ?: DEFAULT_API_KEY
    }

    val tasks: Flow<List<Task>> = context.dataStore.data.map { prefs ->
        prefs[TASKS_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<Task>>() {}.type)
        } ?: emptyList()
    }

    val habits: Flow<List<Habit>> = context.dataStore.data.map { prefs ->
        prefs[HABITS_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<Habit>>() {}.type)
        } ?: emptyList()
    }

    val habitLogs: Flow<List<HabitLog>> = context.dataStore.data.map { prefs ->
        prefs[HABIT_LOGS_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<HabitLog>>() {}.type)
        } ?: emptyList()
    }

    val notes: Flow<List<Note>> = context.dataStore.data.map { prefs ->
        prefs[NOTES_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<Note>>() {}.type)
        } ?: emptyList()
    }

    val chatMessages: Flow<List<ChatMessage>> = context.dataStore.data.map { prefs ->
        prefs[CHAT_MESSAGES_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<ChatMessage>>() {}.type)
        } ?: emptyList()
    }

    val onboardingMessages: Flow<List<ChatMessage>> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_MESSAGES_JSON]?.let {
            gson.fromJson(it, object : TypeToken<List<ChatMessage>>() {}.type)
        } ?: emptyList()
    }

    val usageMinutes: Flow<Int> = context.dataStore.data.map { it[USAGE_MINUTES] ?: 0 }
    val lastBronMsg: Flow<String> = context.dataStore.data.map { it[LAST_BRON_MSG] ?: "" }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDED] ?: false }
    val referralCode: Flow<String> = context.dataStore.data.map { it[REFERRAL_CODE] ?: "" }
    val referralCount: Flow<Int> = context.dataStore.data.map { it[REFERRAL_COUNT] ?: 0 }
    val isPro: Flow<Boolean> = context.dataStore.data.map { it[IS_PRO] ?: false }
    val syncCode: Flow<String> = context.dataStore.data.map { it[SYNC_CODE] ?: "" }
    val userId: Flow<String> = context.dataStore.data.map { it[USER_ID] ?: "" }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { it[USER_JSON] = gson.toJson(user) }
    }
    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }
    suspend fun saveTasks(tasks: List<Task>) {
        context.dataStore.edit { it[TASKS_JSON] = gson.toJson(tasks) }
    }
    suspend fun saveHabits(habits: List<Habit>) {
        context.dataStore.edit { it[HABITS_JSON] = gson.toJson(habits) }
    }
    suspend fun saveHabitLogs(logs: List<HabitLog>) {
        context.dataStore.edit { it[HABIT_LOGS_JSON] = gson.toJson(logs) }
    }
    suspend fun saveNotes(notes: List<Note>) {
        context.dataStore.edit { it[NOTES_JSON] = gson.toJson(notes) }
    }
    suspend fun saveChatMessages(messages: List<ChatMessage>) {
        context.dataStore.edit { it[CHAT_MESSAGES_JSON] = gson.toJson(messages.takeLast(50)) }
    }
    suspend fun saveOnboardingMessages(messages: List<ChatMessage>) {
        context.dataStore.edit { it[ONBOARDING_MESSAGES_JSON] = gson.toJson(messages.takeLast(30)) }
    }
    suspend fun saveUsage(minutes: Int) {
        context.dataStore.edit { it[USAGE_MINUTES] = minutes }
    }
    suspend fun saveLastBronMsg(msg: String) {
        context.dataStore.edit { it[LAST_BRON_MSG] = msg }
    }
    suspend fun setOnboarded() {
        context.dataStore.edit { it[ONBOARDED] = true }
    }
    suspend fun saveReferralCode(code: String) {
        context.dataStore.edit { it[REFERRAL_CODE] = code }
    }
    suspend fun saveReferralCount(count: Int) {
        context.dataStore.edit { it[REFERRAL_COUNT] = count }
    }
    suspend fun setIsPro(pro: Boolean) {
        context.dataStore.edit { it[IS_PRO] = pro }
    }
    suspend fun saveSyncCode(code: String) {
        context.dataStore.edit { it[SYNC_CODE] = code }
    }
    suspend fun saveUserId(id: String) {
        context.dataStore.edit { it[USER_ID] = id }
    }
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
