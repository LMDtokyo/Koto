package run.koto.desktop.domain.repository

/**
 * Read-only access to the user's BIP39 recovery phrase. Writes happen as a
 * side-effect of [AuthRepository.register] / [AuthRepository.restore] — no
 * caller has any reason to set the phrase directly.
 */
interface SeedRepository {
    /** Returns the stored phrase, or null when the user has none locally. */
    suspend fun read(): List<String>?
}
