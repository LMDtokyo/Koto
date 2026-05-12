package run.koto.desktop.domain.model

/** A user-defined chat-list folder ("Работа", "Семья", "Архив"). Pure
 *  client-side concept — server has no idea folders exist. */
data class Folder(
    val id        : Long,
    val name      : String,
    val sortOrder : Int,
)
