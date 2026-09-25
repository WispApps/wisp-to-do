package com.wisp.todo.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class RecoveryKeyManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "wisp_recovery",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPhrase() = !prefs.getString("phrase", null).isNullOrBlank()
    fun phrase(): String = prefs.getString("phrase", null) ?: error("Recovery phrase is not configured")
    @Synchronized fun create(): String = if (hasPhrase()) phrase() else generatePhrase().also { save(it) }
    fun isConfirmed() = prefs.getBoolean("confirmed", false)
    fun confirmSaved() { check(hasPhrase()); check(prefs.edit().putBoolean("confirmed", true).commit()) }
    fun save(value: String) { prefs.edit().putString("phrase", normalize(value)).apply() }
    fun verify(value: String) = runCatching { normalize(value) == phrase() }.getOrDefault(false)

    private fun generatePhrase(): String {
        val left = arrayOf("amber","april","arctic","autumn","bright","calm","cedar","clear","cobalt","coral","crystal","dawn","gentle","golden","green","lunar")
        val right = arrayOf("anchor","bird","bridge","cloud","field","forest","harbor","island","lake","leaf","meadow","moon","river","stone","sun","willow")
        val random = SecureRandom()
        return List(24) { val value = random.nextInt(256); "${left[value ushr 4]}-${right[value and 15]}" }.joinToString(" ")
    }

    companion object {
        private val magic = "ATODO2".toByteArray()
        fun normalize(value: String) = value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
        fun encrypt(plain: ByteArray, phrase: String): ByteArray {
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val key = derive(phrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            return magic + salt + cipher.iv + cipher.doFinal(plain)
        }
        fun decrypt(payload: ByteArray, phrase: String): ByteArray {
            require(payload.size > 50 && payload.copyOfRange(0, magic.size).contentEquals(magic)) { "Invalid Wisp backup" }
            val saltStart = magic.size; val ivStart = saltStart + 16; val bodyStart = ivStart + 12
            val key = derive(phrase, payload.copyOfRange(saltStart, ivStart))
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(ivStart, bodyStart)))
                doFinal(payload.copyOfRange(bodyStart, payload.size))
            }
        }
        private fun derive(phrase: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(normalize(phrase).toCharArray(), salt, 310_000, 256)
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        }
    }
}
