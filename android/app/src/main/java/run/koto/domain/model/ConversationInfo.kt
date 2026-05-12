package run.koto.domain.model

data class ConversationInfo(
    val displayName : String,
    val peerId      : String,
    val online      : Boolean,
)
