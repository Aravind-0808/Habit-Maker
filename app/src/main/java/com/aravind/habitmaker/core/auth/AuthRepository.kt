package com.aravind.habitmaker.core.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

data class AuthState(
    val signedIn: Boolean = false,
    val guest: Boolean = false,
    val name: String = "",
    val email: String = "",
) {
    val inside get() = signedIn || guest
    val initials get() = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "G" }
}

class AuthException(message: String) : Exception(message)

private val Context.authStore by preferencesDataStore("habitmaker.auth")

/**
 * One account kept on this phone (password stored as a salted SHA-256 hash).
 *
 * This is the swap point for a real backend: replace the bodies of
 * [register], [signIn] and [resetAccount] with calls to Firebase Auth or the
 * Redmind API and keep the same [state] flow — no screen has to change.
 */
class AuthRepository(private val context: Context) {
    private val ds get() = context.authStore

    val state: Flow<AuthState> = ds.data.map { p ->
        AuthState(
            signedIn = p[SIGNED_IN] ?: false,
            guest = p[GUEST] ?: false,
            name = p[NAME] ?: "",
            email = p[EMAIL] ?: "",
        )
    }

    suspend fun hasAccount(): Boolean = ds.data.first()[HASH] != null

    suspend fun register(name: String, email: String, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        ds.edit {
            it[NAME] = name.trim()
            it[EMAIL] = email.trim().lowercase()
            it[SALT] = salt
            it[HASH] = hash(salt, password)
            it[SIGNED_IN] = true
            it[GUEST] = false
        }
    }

    suspend fun signIn(email: String, password: String) {
        val p = ds.data.first()
        val salt = p[SALT]
        if (salt == null || p[EMAIL] != email.trim().lowercase() || p[HASH] != hash(salt, password)) {
            throw AuthException("Email or password is incorrect.")
        }
        ds.edit { it[SIGNED_IN] = true; it[GUEST] = false }
    }

    suspend fun continueAsGuest() = ds.edit { it[GUEST] = true; it[SIGNED_IN] = false }

    suspend fun updateName(name: String) = ds.edit { it[NAME] = name.trim() }

    suspend fun signOut() = ds.edit { it[SIGNED_IN] = false; it[GUEST] = false }

    /** Forgets the account on this phone (used by "forgot password" and "delete account"). */
    suspend fun resetAccount() = ds.edit { it.clear() }

    private fun hash(salt: String, password: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        val SIGNED_IN = booleanPreferencesKey("signed_in")
        val GUEST = booleanPreferencesKey("guest")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val SALT = stringPreferencesKey("salt")
        val HASH = stringPreferencesKey("hash")
    }
}
