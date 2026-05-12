package run.koto.desktop.domain

sealed class DomainError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(message: String = "unauthorized")         : DomainError(message)
    class NotFound(message: String = "not found")                : DomainError(message)
    class AlreadyExists(message: String = "already exists")      : DomainError(message)
    class Network(message: String, cause: Throwable? = null)     : DomainError(message, cause)
    class EncryptionUnavailable(peerAccountId: String)
        : DomainError("no signal session with peer $peerAccountId")
    class InvalidInput(message: String)                          : DomainError(message)
    class Internal(message: String, cause: Throwable? = null)    : DomainError(message, cause)
}
