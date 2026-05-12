package run.koto.desktop.domain.repository

import run.koto.desktop.domain.model.Profile
import run.koto.desktop.domain.model.UsernameAvailability

interface ProfileRepository {
    suspend fun me(): Result<Profile>
    suspend fun get(accountId: String): Result<Profile>
    suspend fun batch(accountIds: List<String>): Result<List<Profile>>
    suspend fun update(
        displayName : String? = null,
        avatarUrl   : String? = null,
        bannerUrl   : String? = null,
        bio         : String? = null,
    ): Result<Profile>

    /** Probe whether [candidate] is a valid, free handle. */
    suspend fun checkUsername(candidate: String): Result<UsernameAvailability>

    /** Claim [username] for the current account. Surfaces collision as failure. */
    suspend fun setUsername(username: String): Result<String>

    /** Resolve a public @handle to a profile. Failure on 404 / network. */
    suspend fun findByUsername(username: String): Result<Profile>
}
