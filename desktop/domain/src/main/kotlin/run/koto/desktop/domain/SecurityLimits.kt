package run.koto.desktop.domain

/**
 * Hard caps enforced at the client boundary to keep a malicious or buggy peer
 * from DoS-ing us and to keep conversation payloads within server expectations.
 *
 * These are defence-in-depth numbers, NOT the canonical protocol limits. The
 * server enforces its own; these stop problems earlier and give clearer errors.
 */
object SecurityLimits {
    /** Max UTF-8 bytes for a single text message. Matches Signal's conservative default. */
    const val MAX_PLAINTEXT_BYTES       = 64  * 1024          // 64 KiB

    /** Max encrypted ciphertext blob size (plaintext + Double Ratchet overhead + padding). */
    const val MAX_CIPHERTEXT_BYTES      = 128 * 1024          // 128 KiB

    /** Max uploaded file size; media service also enforces this. */
    const val MAX_MEDIA_FILE_BYTES      = 100L * 1024 * 1024  // 100 MiB

    /** Max display name length. */
    const val MAX_DISPLAY_NAME_LENGTH   = 64

    /** Max bio length — server enforces this too. */
    const val MAX_BIO_LENGTH            = 300

    /** Max nickname length for contacts. */
    const val MAX_NICKNAME_LENGTH       = 64
}
