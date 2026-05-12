package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.data.remote.dto.ProfileDto
import run.koto.desktop.domain.model.Profile
import run.koto.desktop.domain.model.UsernameAvailability
import run.koto.desktop.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val userApi: UserApi,
) : ProfileRepository {

    private val log = LoggerFactory.getLogger(ProfileRepositoryImpl::class.java)

    override suspend fun me(): Result<Profile> = runCatching {
        withContext(Dispatchers.IO) { userApi.me() }.toDomain()
    }.onFailure { log.warn("me failed", it) }

    override suspend fun get(accountId: String): Result<Profile> = runCatching {
        withContext(Dispatchers.IO) { userApi.getProfile(accountId) }.toDomain()
    }.onFailure { log.warn("get profile failed id={}", accountId, it) }

    override suspend fun batch(accountIds: List<String>): Result<List<Profile>> = runCatching {
        withContext(Dispatchers.IO) { userApi.batchProfiles(accountIds) }.map { it.toDomain() }
    }.onFailure { log.warn("batch profiles failed count={}", accountIds.size, it) }

    override suspend fun update(
        displayName : String?,
        avatarUrl   : String?,
        bannerUrl   : String?,
        bio         : String?,
    ): Result<Profile> = runCatching {
        withContext(Dispatchers.IO) { userApi.updateProfile(displayName, avatarUrl, bannerUrl, bio) }.toDomain()
    }.onFailure { log.warn("update profile failed", it) }

    override suspend fun checkUsername(candidate: String): Result<UsernameAvailability> = runCatching {
        val r = withContext(Dispatchers.IO) { userApi.checkUsername(candidate) }
        when {
            !r.valid     -> UsernameAvailability.Invalid(r.reason ?: "invalid")
            !r.available -> UsernameAvailability.Taken
            else         -> UsernameAvailability.Available
        }
    }

    override suspend fun setUsername(username: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) { userApi.setUsername(username) }.username
    }

    override suspend fun findByUsername(username: String): Result<Profile> = runCatching {
        withContext(Dispatchers.IO) { userApi.findByUsername(username) }.toDomain()
    }
}

private fun ProfileDto.toDomain() = Profile(
    accountId   = account_id,
    displayName = display_name,
    avatarUrl   = avatar_url.ifBlank { null },
    bannerUrl   = banner_url.ifBlank { null },
    bio         = bio.ifBlank { null },
    username    = username.ifBlank { null },
    updatedAt   = updated_at,
)
