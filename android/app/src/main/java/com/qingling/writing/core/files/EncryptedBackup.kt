package com.qingling.writing.core.files

import com.qingling.writing.core.model.LibraryBackup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** QLE1 + salt + IV are authenticated with the encrypted, validated backup ZIP. */
object EncryptedBackup {
    private val magic = byteArrayOf(0x51, 0x4c, 0x45, 0x31)
    private const val HEADER = 4 + 16 + 12
    private const val MAX_BYTES = 36 * 1024 * 1024
    private const val ITERATIONS = 210_000

    fun isEncrypted(input: PushbackInputStream): Boolean {
        val prefix = ByteArray(magic.size)
        var n = 0
        while (n < prefix.size) { val r = input.read(prefix, n, prefix.size - n); if (r < 0) break; n += r }
        input.unread(prefix, 0, n)
        return n == magic.size && prefix.contentEquals(magic)
    }

    fun write(backup: LibraryBackup, output: OutputStream, password: CharArray) {
        val zip = ByteArrayOutputStream()
        BackupCodec().write(backup, zip)
        val plain = zip.toByteArray()
        require(plain.size <= MAX_BYTES)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + iv
        try {
            val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv)
            cipher.updateAAD(header)
            output.use { it.write(header); it.write(cipher.doFinal(plain)); it.flush() }
        } finally { plain.fill(0) }
    }

    fun read(input: InputStream, password: CharArray): LibraryBackup {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var count = 0
        while (true) {
            val n = input.read(buf); if (n < 0) break
            count += n; require(count <= MAX_BYTES + HEADER + 16) { "BACKUP_LIMIT" }
            out.write(buf, 0, n)
        }
        val bytes = out.toByteArray()
        require(bytes.size >= HEADER + 16 && bytes.copyOfRange(0, 4).contentEquals(magic)) { "BACKUP_FORMAT" }
        val header = bytes.copyOfRange(0, HEADER)
        val cipher = cipher(Cipher.DECRYPT_MODE, password, header.copyOfRange(4, 20), header.copyOfRange(20, HEADER))
        cipher.updateAAD(header)
        val plain = cipher.doFinal(bytes, HEADER, bytes.size - HEADER) // Authenticate before parsing.
        try { return BackupCodec().read(ByteArrayInputStream(plain)) } finally { plain.fill(0) }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        require(password.size in 8..128) { "BACKUP_PASSWORD_LENGTH" }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        try { return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) } }
        finally { key.fill(0) }
    }
}
