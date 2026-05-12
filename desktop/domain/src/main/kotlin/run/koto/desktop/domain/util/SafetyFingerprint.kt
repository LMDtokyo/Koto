package run.koto.desktop.domain.util

import java.security.MessageDigest

/**
 * Signal-style 60-digit safety number for a pair of identity keys.
 *
 * Both peers MUST derive the same string regardless of who is "self" — that's
 * the whole point of a comparable fingerprint. We sort the two raw byte arrays
 * lexicographically before hashing so input order does not matter.
 */
object SafetyFingerprint {

    private const val GROUPS = 12
    private const val DIGITS_PER_GROUP = 5
    private const val BYTES_PER_GROUP = 5

    fun forPair(selfId: String, peerId: String): String {
        val a = decodeId(selfId)
        val b = decodeId(peerId)
        val (lo, hi) = if (compareUnsigned(a, b) <= 0) a to b else b to a

        val md = MessageDigest.getInstance("SHA-512")
        md.update(lo)
        md.update(hi)
        val digest = md.digest()

        val sb = StringBuilder(GROUPS * (DIGITS_PER_GROUP + 1))
        for (i in 0 until GROUPS) {
            val off = i * BYTES_PER_GROUP
            var v = 0L
            for (j in 0 until BYTES_PER_GROUP) {
                v = (v shl 8) or (digest[off + j].toLong() and 0xFF)
            }
            val chunk = (v % 100_000L).toString().padStart(DIGITS_PER_GROUP, '0')
            if (i > 0) sb.append(' ')
            sb.append(chunk)
        }
        return sb.toString()
    }

    /** Strip any prefix and best-effort hex-decode; fall back to raw UTF-8 bytes. */
    private fun decodeId(id: String): ByteArray {
        val clean = id.trim().removePrefix("0x")
        return if (clean.length % 2 == 0 && clean.all { it.isHexChar() }) {
            ByteArray(clean.length / 2) { i ->
                ((clean[i * 2].digitToInt(16) shl 4) or clean[i * 2 + 1].digitToInt(16)).toByte()
            }
        } else {
            id.toByteArray(Charsets.UTF_8)
        }
    }

    private fun Char.isHexChar(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            if (ai != bi) return ai - bi
        }
        return a.size - b.size
    }
}
