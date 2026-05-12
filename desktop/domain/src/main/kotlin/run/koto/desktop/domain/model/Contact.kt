package run.koto.desktop.domain.model

data class Contact(
    val contactId : String,
    val nickname  : String,
    val addedAt   : Long,
    val blocked   : Boolean,
)

data class Profile(
    val accountId   : String,
    val displayName : String,
    val avatarUrl   : String?,
    val bannerUrl   : String?,
    val bio         : String?,
    val username    : String?,
    val updatedAt   : Long,
)

/**
 * Result of a [ProfileRepository.checkUsername] probe — three states the UI
 * cares about: invalid format, taken by someone else, available to claim.
 */
sealed interface UsernameAvailability {
    data object Available           : UsernameAvailability
    data object Taken               : UsernameAvailability
    data class  Invalid(val reason: String) : UsernameAvailability
}

data class UploadedFile(
    val fileId      : String,
    val contentType : String,
    val sizeBytes   : Long,
    val isPublic    : Boolean,
)
