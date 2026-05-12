package run.koto.ui.screens.chat

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import run.koto.domain.model.ChatItem
import run.koto.domain.model.MessageUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─── Thread-local formatters (SimpleDateFormat is NOT thread-safe) ────────────

private val timeFmtHolder = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}
private val dayFmtHolder = ThreadLocal.withInitial {
    SimpleDateFormat("d MMMM", Locale("ru"))
}

/**
 * Pure function: converts a flat ImmutableList<MessageUi> into a structured
 * ImmutableList<ChatItem> with date separators and grouping metadata.
 *
 * Call only from viewModelScope on Dispatchers.Default — NOT in composition.
 * See RESEARCH.md Pitfall 7 and Pattern 1.
 *
 * groupWindowMs: 60 000 ms — messages within 60s from same sender share a group (no tail on intermediates).
 */
fun buildChatItems(
    messages     : ImmutableList<MessageUi>,
    nowMs        : Long = System.currentTimeMillis(),
    groupWindowMs: Long = 60_000L,
): ImmutableList<ChatItem> {
    if (messages.isEmpty()) return emptyList<ChatItem>().toImmutableList()

    val result = mutableListOf<ChatItem>()
    for (i in messages.indices) {
        val msg  = messages[i]
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)

        // Insert date separator when day changes
        if (prev == null || !sameDay(prev.sentAt, msg.sentAt)) {
            result += ChatItem.DateSeparator(formatDateLabel(msg.sentAt, nowMs))
        }

        // Tail/avatar logic: isLast = next message breaks the group
        val isLast = next == null
            || next.senderId != msg.senderId
            || (next.sentAt - msg.sentAt) > groupWindowMs

        result += ChatItem.Message(
            msg           = msg,
            showTail      = isLast,
            showAvatar    = !msg.isOutgoing && isLast,
            formattedTime = formatMessageTime(msg.sentAt),
        )
    }
    return result.toImmutableList()
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private fun formatMessageTime(ms: Long): String =
    timeFmtHolder.get()!!.format(Date(ms))

private fun formatDateLabel(ms: Long, nowMs: Long): String {
    val msgCal   = Calendar.getInstance().apply { timeInMillis = ms }
    val todayCal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val ystrdCal = Calendar.getInstance().apply {
        timeInMillis = nowMs
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return when {
        sameDay(msgCal.timeInMillis, todayCal.timeInMillis)  -> "Сегодня"
        sameDay(msgCal.timeInMillis, ystrdCal.timeInMillis)  -> "Вчера"
        else -> dayFmtHolder.get()!!.format(Date(ms))
    }
}

internal fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca[Calendar.YEAR] == cb[Calendar.YEAR] &&
           ca[Calendar.DAY_OF_YEAR] == cb[Calendar.DAY_OF_YEAR]
}
