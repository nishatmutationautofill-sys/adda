package com.adda.chat.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.security.spec.MGF1ParameterSpec
import java.security.SecureRandom

/**
 * Android-side E2EE helper. Private RSA key stays in Android Keystore.
 * Server only receives AES-GCM ciphertext and RSA-wrapped AES keys.
 */
object E2ee {
    private const val ALIAS = "adda_e2ee_rsa_v1"
    private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

    data class EncryptedFile(val bytes: ByteArray, val envelope: String)

    @Synchronized private fun keyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return KeyPair(existing.certificate.publicKey, existing.privateKey)
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .build()
        )
        return gen.generateKeyPair()
    }

    fun publicKeyBase64(): String = Base64.encodeToString(keyPair().public.encoded, Base64.NO_WRAP)

    private fun decodePublic(encoded: String): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.DEFAULT)))

    private fun wrapKey(key: SecretKey, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaep)
        return Base64.encodeToString(cipher.doFinal(key.encoded), Base64.NO_WRAP)
    }

    private fun unwrapKey(wrapped: String): SecretKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyPair().private, oaep)
        val raw = cipher.doFinal(Base64.decode(wrapped, Base64.DEFAULT))
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    private fun aesEncrypt(key: SecretKey, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(plain) to iv
    }

    private fun aesDecrypt(key: SecretKey, cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun wrappedKeys(senderUid: String, senderPublicKey: String, recipientUid: String, recipientPublicKey: String, key: SecretKey): JSONObject {
        val keys = JSONObject()
        keys.put(senderUid, wrapKey(key, decodePublic(senderPublicKey)))
        keys.put(recipientUid, wrapKey(key, decodePublic(recipientPublicKey)))
        return keys
    }

    fun encryptText(plain: String, senderUid: String, recipientUid: String, recipientPublicKey: String): String {
        val pair = keyPair()
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        val (cipherText, iv) = aesEncrypt(key, plain.toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("_e2ee", 1)
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("cipher", Base64.encodeToString(cipherText, Base64.NO_WRAP))
            .put("keys", wrappedKeys(senderUid, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP), recipientUid, recipientPublicKey, key))
        return envelope.toString()
    }

    fun decryptText(envelopeText: String, myUid: String): String {
        val envelope = JSONObject(envelopeText)
        require(envelope.optInt("_e2ee") == 1) { "এটি E2EE মেসেজ নয়" }
        val wrapped = envelope.getJSONObject("keys").optString(myUid)
        require(wrapped.isNotBlank()) { "এই ডিভাইসের জন্য এনক্রিপশন কী নেই" }
        val key = unwrapKey(wrapped)
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val ciphertext = Base64.decode(envelope.getString("cipher"), Base64.DEFAULT)
        return aesDecrypt(key, ciphertext, iv).toString(Charsets.UTF_8)
    }

    fun encryptFile(
        plain: ByteArray,
        senderUid: String,
        recipientUid: String,
        recipientPublicKey: String,
        fileName: String,
        mimeType: String
    ): EncryptedFile {
        val pair = keyPair()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val (cipherBytes, fileIv) = aesEncrypt(key, plain)
        val metadata = JSONObject().put("fileName", fileName).put("mimeType", mimeType)
        val (metaCipher, metaIv) = aesEncrypt(key, metadata.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("_e2ee", 1)
            .put("fileIv", Base64.encodeToString(fileIv, Base64.NO_WRAP))
            .put("metaIv", Base64.encodeToString(metaIv, Base64.NO_WRAP))
            .put("metaCipher", Base64.encodeToString(metaCipher, Base64.NO_WRAP))
            .put("keys", wrappedKeys(senderUid, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP), recipientUid, recipientPublicKey, key))
            .toString()
        return EncryptedFile(cipherBytes, envelope)
    }

    data class DecryptedFile(val bytes: ByteArray, val fileName: String, val mimeType: String)

    fun decryptFile(cipherBytes: ByteArray, envelopeText: String, myUid: String): DecryptedFile {
        val envelope = JSONObject(envelopeText)
        require(envelope.optInt("_e2ee") == 1) { "এনক্রিপ্টেড ফাইলের তথ্য পাওয়া যায়নি" }
        val wrapped = envelope.getJSONObject("keys").optString(myUid)
        require(wrapped.isNotBlank()) { "এই ডিভাইসের জন্য এনক্রিপশন কী নেই" }
        val key = unwrapKey(wrapped)
        val file = aesDecrypt(key, cipherBytes, Base64.decode(envelope.getString("fileIv"), Base64.DEFAULT))
        val metaBytes = aesDecrypt(key, Base64.decode(envelope.getString("metaCipher"), Base64.DEFAULT), Base64.decode(envelope.getString("metaIv"), Base64.DEFAULT))
        val meta = JSONObject(metaBytes.toString(Charsets.UTF_8))
        return DecryptedFile(file, meta.optString("fileName", "adda_file"), meta.optString("mimeType", "application/octet-stream"))
    }
}
