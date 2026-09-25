package com.wisp.todo.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object WispCipher {
    private const val ALIAS = "wisp_todo_backup_key"
    private const val MAGIC = "ATODO1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return MAGIC.toByteArray() + byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(data)
    }
    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 20 && data.copyOfRange(0, MAGIC.length).decodeToString() == MAGIC) { "Not an Wisp To Do archive" }
        val size = data[MAGIC.length].toInt(); val start = MAGIC.length + 1
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(start, start + size))) }
        return cipher.doFinal(data.copyOfRange(start + size, data.size))
    }
}
