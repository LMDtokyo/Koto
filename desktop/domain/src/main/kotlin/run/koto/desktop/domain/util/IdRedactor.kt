package run.koto.desktop.domain.util

/**
 * Shortens an opaque identifier (account id, message id, conversation id) so it can
 * appear in logs without leaking enough entropy to re-identify the account from a
 * captured log file.
 *
 * Behaviour:
 *   - null / empty → literal "<null>"
 *   - length ≤ 8    → unchanged (not enough to redact usefully)
 *   - otherwise     → first 4 + "…" + last 4 (e.g. `055cd3…454d`)
 */
object IdRedactor {
    fun mask(id: String?): String = when {
        id.isNullOrEmpty() -> "<null>"
        id.length <= 8     -> id
        else               -> id.substring(0, 4) + "…" + id.substring(id.length - 4)
    }
}
