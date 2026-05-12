package run.koto.desktop.ui.screens.chatlist

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Straight port of the React mockup's `data.jsx` — `KOTO_CONTACTS` / `KOTO_CHATS` /
 * `KOTO_MESSAGES`. These exist so the UI can be developed and reviewed without the
 * real backend data flowing yet. Swapped for [run.koto.desktop.domain.repository.ChatRepository]
 * -backed data once the UI wires into the data layer.
 *
 * Russian strings preserved byte-for-byte so text metrics, wrap behaviour and localised
 * layout match the reference mockup exactly.
 */
@Immutable
data class MockContact(
    val id       : String,
    val name     : String,
    val initials : String,
    val color    : Color,
    val kotoId   : String,
    val meta     : String,
    val status   : String,
    val isGroup  : Boolean = false,
    val isBot    : Boolean = false,
    val verified : Boolean = false,
)

@Immutable
data class MockChat(
    val id         : String,
    val contactId  : String,
    val time       : String,
    val unread     : Int,
    val pinned     : Boolean,
    val muted      : Boolean,
    val preview    : String,
    val typing     : Boolean,
    val lastSelf   : Boolean,
    val status     : String? = null,         // "sent" / "delivered" / "read" when lastSelf
    val ephemeral  : Boolean = false,
    val isBot      : Boolean = false,
)

object MockData {

    val contacts = listOf(
        MockContact("alina",  "Алина Ким",       "АК", Color(0xFFFF6B35), "05a9f2c4e7b1d038f5c26e9a48103b5d7c91e2d64a58fc03bd917e4a62c85b71f0", "Koto ID",                    "была в сети недавно"),
        MockContact("maks",   "Максим Орлов",    "МО", Color(0xFF7C5CFF), "05b74e8c09d1a2f36b59ac8f14d728503fc6e1b095a3d48f27eb906c1f8d542a37", "Koto ID",                    "в сети"),
        MockContact("team",   "Команда Koto",    "КК", Color(0xFF00A676), "05c18f3d72b49e5ac014826df5913a8b2e7c05149d36f8b2a07ec4d51f83627ab9", "группа · 8 участников",       "3 онлайн",  isGroup = true),
        MockContact("olya",   "Оля Громова",     "ОГ", Color(0xFFF0B400), "05d4e8f29a0b315cf86147b2d50e93ca74812b936ef0a5d7c2148f36b9a0e5c28d", "Koto ID",                    "была вчера"),
        MockContact("mama",   "Мама",            "М",  Color(0xFFE74C6F), "05e0a3f87b62c41d5e98037af1b62d04e85c91f23a6bd8043e7c90f58a16d2c7b4", "Koto ID",                    "в сети"),
        MockContact("nikita", "Никита Вельт",    "НВ", Color(0xFF3276FF), "05f23b58a17d6e09c4e2f80b15a63d9e72c48b10f96ac3d74e58b20af19d63c8e04", "Koto ID",                    "был в 15:24"),
        MockContact("lera",   "Лера Станкевич",  "ЛС", Color(0xFF13B3A5), "0516acf3d872b04e15f9c38a67b2d051fc9e3a8b274d06f51e29ac38b475e16f2d9", "Koto ID",                    "была в 12:10"),
        MockContact("pavel",  "Павел Зотов",     "ПЗ", Color(0xFFFF7A9A), "0527d19fe3b48a5c62e901f7a3b8d46105ce2f98a6b3d4017e28f5c10b749e3d6a", "Koto ID",                    "был вчера"),
        MockContact("doma",   "Дом · чат 4",     "ДЧ", Color(0xFF5B6EF5), "0538e2ca71f9b0643d58e71ac2f930b41e72d6f09a3cb148f57e01c92a7b503db2", "группа · 4 участника",        "",          isGroup = true),
    )

    val chats = listOf(
        MockChat("alina",  "alina",  "16:42", 2,  pinned = true,  muted = false, preview = "ок, тогда встречаемся у Koto 🐈", typing = false, lastSelf = false),
        MockChat("maks",   "maks",   "16:08", 0,  pinned = true,  muted = false, preview = "Вы: отправил проект на ревью",    typing = false, lastSelf = true,  status = "read"),
        MockChat("team",   "team",   "15:47", 11, pinned = false, muted = true,  preview = "Максим: релиз сдвигается на четверг", typing = false, lastSelf = false),
        MockChat("olya",   "olya",   "14:20", 0,  pinned = false, muted = false, preview = "печатает…",                         typing = true,  lastSelf = false),
        MockChat("mama",   "mama",   "13:02", 0,  pinned = false, muted = false, preview = "Вы: позвоню вечером",             typing = false, lastSelf = true,  status = "delivered"),
        MockChat("nikita", "nikita", "вчера", 0,  pinned = false, muted = false, preview = "спс, получил",                     typing = false, lastSelf = false),
        MockChat("lera",   "lera",   "вчера", 0,  pinned = false, muted = false, preview = "Вы: 📷 Фото",            typing = false, lastSelf = true,  status = "read"),
        MockChat("pavel",  "pavel",  "пн",    0,  pinned = false, muted = true,  preview = "Исчезающее сообщение",             typing = false, lastSelf = false, ephemeral = true),
        MockChat("doma",   "doma",   "вс",    0,  pinned = false, muted = false, preview = "Оля: забрала ключи",               typing = false, lastSelf = false),
    )

    fun contact(id: String): MockContact = contacts.firstOrNull { it.id == id } ?: contacts.first()
}
