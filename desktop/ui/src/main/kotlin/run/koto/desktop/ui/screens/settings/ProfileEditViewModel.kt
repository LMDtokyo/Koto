package run.koto.desktop.ui.screens.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import run.koto.desktop.domain.model.Profile
import run.koto.desktop.domain.repository.MediaRepository
import run.koto.desktop.domain.repository.ProfileRepository

/**
 * Drives the Profile sub-screen. Holds two parallel snapshots:
 *
 *   - [original]: what the server returned
 *   - [draft]:    the in-flight edits the user is making
 *
 * The "Сохранить" bar appears whenever any draft field diverges from the
 * original. Saving sends only the changed fields (server treats null as
 * "leave alone"), then re-fetches and resets [draft] to match.
 */
class ProfileEditViewModel(
    private val profileRepo : ProfileRepository,
    private val mediaRepo   : MediaRepository,
) {
    private val log    = LoggerFactory.getLogger(ProfileEditViewModel::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    sealed interface SaveStatus {
        data object Idle    : SaveStatus
        data object Saving  : SaveStatus
        data object Saved   : SaveStatus
        data class  Failed(val message: String) : SaveStatus
    }

    sealed interface AvatarStatus {
        data object Idle       : AvatarStatus
        data object Uploading  : AvatarStatus
        data class  Failed(val message: String) : AvatarStatus
    }

    sealed interface BannerStatus {
        data object Idle       : BannerStatus
        data object Uploading  : BannerStatus
        data class  Failed(val message: String) : BannerStatus
    }

    data class State(
        val loading       : Boolean       = true,
        val accountId     : String        = "",
        val originalName  : String        = "",
        val originalBio   : String        = "",
        val originalAvatar: String?       = null,
        val originalBanner: String?       = null,
        val draftName     : String        = "",
        val draftBio      : String        = "",
        val draftAvatar   : String?       = null,
        val draftBanner   : String?       = null,
        // The image bytes the UI renders. Populated either by a fresh upload
        // (cleaned JPEG from ImageHardener) or by [resolveImagesAsync] which
        // pulls the server-stored file via the media service.
        val draftAvatarBytes : ByteArray? = null,
        val draftBannerBytes : ByteArray? = null,
        val username      : String?       = null,
        val updatedAt     : Long          = 0L,
        val save          : SaveStatus    = SaveStatus.Idle,
        val avatar        : AvatarStatus  = AvatarStatus.Idle,
        val banner        : BannerStatus  = BannerStatus.Idle,
    ) {
        val dirty: Boolean = !loading && (
            draftName   != originalName ||
            draftBio    != originalBio  ||
            draftAvatar != originalAvatar ||
            draftBanner != originalBanner
        )
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = _state.value.copy(loading = true)
            profileRepo.me().fold(
                onSuccess = {
                    applyServer(it)
                    // Resolve the server-stored avatar / banner ids into real
                    // download URLs so the UI can render them. Done after
                    // applyServer so we don't fight a State reset race.
                    resolveImagesAsync(it.avatarUrl, it.bannerUrl)
                },
                onFailure = {
                    log.warn("load profile failed", it)
                    _state.value = _state.value.copy(loading = false)
                },
            )
        }
    }

    private fun resolveImagesAsync(avatarFileId: String?, bannerFileId: String?) {
        if (!avatarFileId.isNullOrBlank() && _state.value.draftAvatarBytes == null) {
            scope.launch {
                mediaRepo.downloadBytes(avatarFileId).onSuccess { bytes ->
                    // Skip the write if the user has uploaded a fresher one
                    // in the meantime, or already navigated away.
                    if (_state.value.draftAvatar == avatarFileId && _state.value.draftAvatarBytes == null) {
                        _state.value = _state.value.copy(draftAvatarBytes = bytes)
                    }
                }
            }
        }
        if (!bannerFileId.isNullOrBlank() && _state.value.draftBannerBytes == null) {
            scope.launch {
                mediaRepo.downloadBytes(bannerFileId).onSuccess { bytes ->
                    if (_state.value.draftBanner == bannerFileId && _state.value.draftBannerBytes == null) {
                        _state.value = _state.value.copy(draftBannerBytes = bytes)
                    }
                }
            }
        }
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(draftName = value.take(NAME_MAX), save = SaveStatus.Idle)
    }

    fun setBio(value: String) {
        _state.value = _state.value.copy(draftBio = value.take(BIO_MAX), save = SaveStatus.Idle)
    }

    fun removeAvatar() {
        _state.value = _state.value.copy(
            draftAvatar      = null,
            draftAvatarBytes = null,
            save             = SaveStatus.Idle,
        )
    }

    fun removeBanner() {
        _state.value = _state.value.copy(
            draftBanner      = null,
            draftBannerBytes = null,
            save             = SaveStatus.Idle,
        )
    }

    /** Take raw user-provided bytes, run them through [ImageHardener] (magic
     *  byte verification, decompression-bomb protection, EXIF strip via
     *  re-encode), then upload the cleaned blob. */
    fun uploadAvatar(rawBytes: ByteArray) {
        if (_state.value.avatar is AvatarStatus.Uploading) return
        scope.launch {
            _state.value = _state.value.copy(avatar = AvatarStatus.Uploading)
            when (val processed = ImageHardener.process(rawBytes, ImageHardener.Kind.Avatar)) {
                is ImageHardener.Result.Rejected -> {
                    _state.value = _state.value.copy(avatar = AvatarStatus.Failed(processed.reason))
                }
                is ImageHardener.Result.Ok -> {
                    // Show the user their freshly cropped image immediately —
                    // upload happens in the background, but the visual switch
                    // from initials to photo is what they care about.
                    _state.value = _state.value.copy(draftAvatarBytes = processed.bytes)
                    mediaRepo.upload(processed.contentType, processed.bytes, isPublic = true).fold(
                        onSuccess = { uploaded ->
                            _state.value = _state.value.copy(
                                draftAvatar = uploaded.fileId,
                                avatar      = AvatarStatus.Idle,
                                save        = SaveStatus.Idle,
                            )
                        },
                        onFailure = {
                            log.warn("avatar upload failed", it)
                            _state.value = _state.value.copy(
                                avatar = AvatarStatus.Failed(it.message ?: "upload failed"),
                            )
                        },
                    )
                }
            }
        }
    }

    fun uploadBanner(rawBytes: ByteArray) {
        if (_state.value.banner is BannerStatus.Uploading) return
        scope.launch {
            _state.value = _state.value.copy(banner = BannerStatus.Uploading)
            when (val processed = ImageHardener.process(rawBytes, ImageHardener.Kind.Banner)) {
                is ImageHardener.Result.Rejected -> {
                    _state.value = _state.value.copy(banner = BannerStatus.Failed(processed.reason))
                }
                is ImageHardener.Result.Ok -> {
                    _state.value = _state.value.copy(draftBannerBytes = processed.bytes)
                    mediaRepo.upload(processed.contentType, processed.bytes, isPublic = true).fold(
                        onSuccess = { uploaded ->
                            _state.value = _state.value.copy(
                                draftBanner = uploaded.fileId,
                                banner      = BannerStatus.Idle,
                                save        = SaveStatus.Idle,
                            )
                        },
                        onFailure = {
                            log.warn("banner upload failed", it)
                            _state.value = _state.value.copy(
                                banner = BannerStatus.Failed(it.message ?: "upload failed"),
                            )
                        },
                    )
                }
            }
        }
    }

    fun discard() {
        val cur = _state.value
        _state.value = cur.copy(
            draftName        = cur.originalName,
            draftBio         = cur.originalBio,
            draftAvatar      = cur.originalAvatar,
            draftBanner      = cur.originalBanner,
            draftAvatarBytes = null,
            draftBannerBytes = null,
            save             = SaveStatus.Idle,
        )
    }

    fun save() {
        val cur = _state.value
        if (!cur.dirty || cur.save is SaveStatus.Saving) return
        scope.launch {
            _state.value = cur.copy(save = SaveStatus.Saving)
            // Always send all four fields explicitly — the server's PUT /me
            // upserts whatever it receives, so a partial body would silently
            // wipe the unchanged columns. Null on the wire = "absent", which
            // the server reads as "" → wipe; sending the original value when
            // unchanged sidesteps that.
            val displayName = cur.draftName.trim()
            val bio         = cur.draftBio
            val avatar      = cur.draftAvatar.orEmpty()
            val banner      = cur.draftBanner.orEmpty()
            profileRepo.update(
                displayName = displayName,
                avatarUrl   = avatar,
                bannerUrl   = banner,
                bio         = bio,
            ).fold(
                onSuccess = { saved ->
                    // Promote draft to original to clear the "dirty" flag,
                    // BUT keep the in-memory image bytes alive so the user
                    // doesn't see a flash from photo to initials. Bytes
                    // are still the most accurate render until either the
                    // user navigates away or the resolved URL arrives.
                    val cur = _state.value
                    _state.value = cur.copy(
                        originalName   = saved.displayName,
                        originalBio    = saved.bio.orEmpty(),
                        originalAvatar = saved.avatarUrl,
                        originalBanner = saved.bannerUrl,
                        draftName      = saved.displayName,
                        draftBio       = saved.bio.orEmpty(),
                        draftAvatar    = saved.avatarUrl,
                        draftBanner    = saved.bannerUrl,
                        username       = saved.username,
                        updatedAt      = saved.updatedAt,
                        save           = SaveStatus.Saved,
                        avatar         = AvatarStatus.Idle,
                        banner         = BannerStatus.Idle,
                    )
                    resolveImagesAsync(saved.avatarUrl, saved.bannerUrl)
                },
                onFailure = {
                    log.warn("save profile failed", it)
                    _state.value = _state.value.copy(save = SaveStatus.Failed(it.message ?: "ошибка"))
                },
            )
        }
    }

    private fun applyServer(p: Profile) {
        _state.value = State(
            loading        = false,
            accountId      = p.accountId,
            originalName   = p.displayName,
            originalBio    = p.bio.orEmpty(),
            originalAvatar = p.avatarUrl,
            originalBanner = p.bannerUrl,
            draftName      = p.displayName,
            draftBio       = p.bio.orEmpty(),
            draftAvatar    = p.avatarUrl,
            draftBanner    = p.bannerUrl,
            username       = p.username,
            updatedAt      = p.updatedAt,
            save           = SaveStatus.Idle,
            avatar         = AvatarStatus.Idle,
            banner         = BannerStatus.Idle,
        )
    }

    fun close() {
        loadJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val NAME_MAX = 32
        const val BIO_MAX  = 200
    }
}
