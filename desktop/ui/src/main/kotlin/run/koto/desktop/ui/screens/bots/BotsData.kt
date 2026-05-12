package run.koto.desktop.ui.screens.bots

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Bot catalog entry — mirrors `KOTO_BOTS` from `data-bots.jsx`. Concise because
 * the desktop catalog shows a distilled card view; full bot detail sheets come
 * from the real registry (Phase 4).
 */
@Immutable
data class KotoBot(
    val id        : String,
    val name      : String,
    val handle    : String,
    val initials  : String,
    val color     : Color,
    val tagline   : String,
    val category  : String,
    val users     : String,
    val verified  : Boolean,
    val featured  : Boolean,
    val commands  : List<BotCommand>,
)

@Immutable
data class BotCommand(val cmd: String, val desc: String)

object BotCatalog {
    val all = listOf(
        KotoBot(
            id       = "pawket",
            name     = "Pawket",
            handle   = "@pawket",
            initials = "🐾",
            color    = Color(0xFFFF6B35),
            tagline  = "Кот-ассистент с ИИ. Заметки, погода, валюта, напоминания.",
            category = "AI · ассистенты",
            users    = "128k",
            verified = true,
            featured = true,
            commands = listOf(
                BotCommand("/weather", "прогноз погоды"),
                BotCommand("/rates",   "курсы валют"),
                BotCommand("/note",    "сохранить заметку"),
                BotCommand("/remind",  "напомнить о деле"),
                BotCommand("/ask",     "спросить ИИ"),
            ),
        ),
        KotoBot(
            id       = "kassa",
            name     = "Касса",
            handle   = "@kassa",
            initials = "КС",
            color    = Color(0xFF7C5CFF),
            tagline  = "Билеты на концерты, кино, спорт. Оплата за 2 тапа.",
            category = "Покупки",
            users    = "54k",
            verified = true,
            featured = true,
            commands = listOf(
                BotCommand("/concerts", "концерты"),
                BotCommand("/movies",   "кино"),
                BotCommand("/sport",    "спорт"),
            ),
        ),
        KotoBot(
            id       = "meow-quiz",
            name     = "Meow Quiz",
            handle   = "@meowquiz",
            initials = "🧩",
            color    = Color(0xFF00A676),
            tagline  = "Викторина в чате с друзьями. 5-минутные раунды.",
            category = "Игры",
            users    = "22k",
            verified = true,
            featured = false,
            commands = listOf(
                BotCommand("/start", "начать раунд"),
                BotCommand("/top",   "рейтинг"),
            ),
        ),
        KotoBot(
            id       = "rates",
            name     = "Rates",
            handle   = "@rates",
            initials = "💱",
            color    = Color(0xFFF0B400),
            tagline  = "Курсы валют и криптовалют. Алерты на пороги.",
            category = "Финансы",
            users    = "18k",
            verified = true,
            featured = false,
            commands = listOf(
                BotCommand("/rates", "курсы"),
                BotCommand("/alert", "алерт"),
            ),
        ),
        KotoBot(
            id       = "translator",
            name     = "Переводчик",
            handle   = "@translator",
            initials = "🌐",
            color    = Color(0xFF3276FF),
            tagline  = "Переводит в реальном времени. 40+ языков.",
            category = "Утилиты",
            users    = "95k",
            verified = true,
            featured = false,
            commands = listOf(
                BotCommand("/ru", "перевести на русский"),
                BotCommand("/en", "перевести на английский"),
            ),
        ),
        KotoBot(
            id       = "notes",
            name     = "Заметки",
            handle   = "@notes",
            initials = "🗒",
            color    = Color(0xFFE74C6F),
            tagline  = "Быстрые заметки, списки покупок, напоминания.",
            category = "Продуктивность",
            users    = "64k",
            verified = false,
            featured = false,
            commands = listOf(
                BotCommand("/new", "новая заметка"),
                BotCommand("/list", "все заметки"),
            ),
        ),
    )

    fun bot(id: String): KotoBot? = all.firstOrNull { it.id == id }

    fun byCategory(): Map<String, List<KotoBot>> =
        all.groupBy { it.category }.toSortedMap()
}
