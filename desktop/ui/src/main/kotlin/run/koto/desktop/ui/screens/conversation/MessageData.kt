package run.koto.desktop.ui.screens.conversation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import run.koto.desktop.ui.components.atoms.MessageStatus

/**
 * Single chat message for the conversation UI. Mirrors the mockup's
 * `KOTO_MESSAGES` entries from `data.jsx` — intentionally loose (optional fields
 * for typing / ephemeral / author / reactions) so the same type covers every
 * bubble variant the design calls for.
 */
@Immutable
data class Message(
    val id           : String,
    val self         : Boolean,
    val text         : String           = "",
    val time         : String           = "",
    val status       : MessageStatus?   = null,
    val typing       : Boolean          = false,
    val author       : String?          = null,
    val authorColor  : Color?           = null,
    val ephemeral    : Boolean          = false,
    val ephemeralPct : Float            = 0.5f,
    val reactions    : ImmutableList<Reaction> = persistentListOf(),
    /** Quoted-parent preview rendered above this bubble's content. */
    val replyTo      : ReplyPreview?    = null,
    /** When non-null, the bubble shows an "(изменено)" hint next to the time. */
    val edited       : Boolean          = false,
    /** When non-null, the bubble shows a "Переслано от <label>" header above
     *  the text. The label is resolved from the original author's profile. */
    val forwardedFromLabel : String?    = null,
    /** True when this message is currently pinned in the conversation. */
    val pinned             : Boolean    = false,
)

/** A compact view of a message being quoted. The `targetId` is the full id of
 *  the parent — the bubble uses it to scroll to the parent on click. */
@Immutable
data class ReplyPreview(
    val targetId : String,
    val author   : String,
    val text     : String,
)

@Immutable
data class Reaction(
    val emoji : String,
    val count : Int,
    val mine  : Boolean,
)

/**
 * Bubble grouping metadata computed on the fly from consecutive messages.
 * `startGroup` — first message in a run by the same author.
 * `endGroup`   — last message in the run (controls tail rendering).
 */
@Immutable
data class GroupedMessage(
    val message    : Message,
    val startGroup : Boolean,
    val endGroup   : Boolean,
)

fun List<Message>.withGrouping(): List<GroupedMessage> {
    val out = ArrayList<GroupedMessage>(size)
    for (i in indices) {
        val m    = this[i]
        val prev = getOrNull(i - 1)
        val next = getOrNull(i + 1)
        val startGroup = prev == null || prev.self != m.self || prev.author != m.author
        val endGroup   = next == null || next.self != m.self || next.author != m.author
        out.add(GroupedMessage(m, startGroup, endGroup))
    }
    return out
}

/**
 * Mock seed data — byte-for-byte port of `KOTO_MESSAGES` from the React mockup.
 * Swapped for the real ChatRepository once the UI layer wires into the data
 * module (Phase 4).
 */
object MockMessages {

    private val teamOla    = Color(0xFFF0B400)
    private val teamMaksim = Color(0xFF7C5CFF)

    val byChatId: Map<String, List<Message>> = mapOf(
        "alina" to listOf(
            Message("m1", self = false, text = "привет! как день?", time = "16:38"),
            Message("m2", self = true,  text = "всё ок, заканчиваю дизайн Koto. скинуть?", time = "16:39", status = MessageStatus.READ),
            Message("m3", self = false, text = "скидывай конечно", time = "16:40",
                    reactions = persistentListOf(Reaction("🔥", 1, mine = false))),
            Message("m4", self = true,  text = "смотри, пузыри, таймеры, всё как хотели", time = "16:41", status = MessageStatus.READ),
            Message("m5", self = false, text = "ок, тогда встречаемся у Koto 🐈", time = "16:42"),
            Message("m6", self = false, text = "я буду в 19", time = "16:42"),
        ),
        "maks" to listOf(
            Message("mm1", self = false, text = "ревью завтра?", time = "15:50"),
            Message("mm2", self = true,  text = "да, в 11 по мск", time = "15:52", status = MessageStatus.READ),
            Message("mm3", self = true,  text = "отправил проект на ревью", time = "16:08", status = MessageStatus.READ),
        ),
        "team" to listOf(
            Message("t1", self = false, author = "Оля",    authorColor = teamOla,    text = "когда релиз?", time = "15:40"),
            Message("t2", self = false, author = "Максим", authorColor = teamMaksim, text = "релиз сдвигается на четверг", time = "15:47"),
        ),
        "olya" to listOf(
            Message("o1", self = true,  text = "купила билеты", time = "14:18", status = MessageStatus.DELIVERED),
            Message("o2", self = false, typing = true, time = "14:20"),
        ),
        "mama" to listOf(
            Message("ma1", self = false, text = "как там?",            time = "12:50"),
            Message("ma2", self = true,  text = "позвоню вечером",      time = "13:02", status = MessageStatus.DELIVERED),
        ),
        "nikita" to listOf(
            Message("nk1", self = true,  text = "спасибо, отправил",    time = "вчера", status = MessageStatus.READ),
            Message("nk2", self = false, text = "спс, получил",         time = "вчера"),
        ),
        "lera" to listOf(
            Message("lr1", self = true,  text = "скинула фото",         time = "вчера", status = MessageStatus.READ),
        ),
        "pavel" to listOf(
            Message("p1", self = false, text = "секретные данные", time = "10:04",
                    ephemeral = true, ephemeralPct = 0.35f),
            Message("p2", self = true,  text = "принял, удаляю",   time = "10:05", status = MessageStatus.READ,
                    ephemeral = true, ephemeralPct = 0.7f),
        ),
        "doma" to listOf(
            Message("d1", self = false, author = "Оля", authorColor = teamOla, text = "забрала ключи", time = "вс"),
        ),
    )

    val default = listOf(
        Message("d1", self = false, text = "привет", time = "12:00"),
    )

    fun for_(chatId: String): List<Message> = byChatId[chatId] ?: default
}

/**
 * Deterministic canned reply for mock conversations — the React mockup's
 * `pickReply` ported verbatim. Used so typing a message gets a response
 * before the real data layer is wired.
 */
fun mockReply(draft: String): String {
    val lower = draft.lowercase()
    return when {
        '?' in lower          -> "да, конечно"
        lower.length < 8      -> "ок 👌"
        else                  -> "принял, посмотрю и вернусь"
    }
}
