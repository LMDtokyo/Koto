package run.koto.desktop.ui.components.atoms

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import java.awt.Desktop
import java.net.URI

private val URL_REGEX = Regex(
    """https?://[^\s<>"]+|www\.[^\s<>"]+""",
    RegexOption.IGNORE_CASE,
)
private val BOLD_REGEX  = Regex("""\*\*(.+?)\*\*""")
private val CODE_REGEX  = Regex("""`([^`\n]+)`""")
private val ITAL_REGEX  = Regex("""(?<![*\w])\*(?!\s)([^*\n]+?)\*(?!\w)""")

@Composable
fun MessageRichText(
    text     : String,
    style    : TextStyle,
    color    : Color,
    linkColor: Color,
    modifier : Modifier = Modifier,
) {
    val annotated = remember(text, color, linkColor) {
        buildRichString(text, color, linkColor)
    }
    ClickableText(
        text     = annotated,
        style    = style.copy(color = color),
        modifier = modifier,
        onClick  = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                runCatching { Desktop.getDesktop().browse(URI(normalizeUrl(it.item))) }
            }
        },
    )
}

private fun buildRichString(text: String, body: Color, link: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        // First pass: tokenize into spans by precedence: code > bold > italic > url > plain
        val tokens = tokenize(text)
        tokens.forEach { tok ->
            when (tok) {
                is Tok.Plain -> append(tok.s)
                is Tok.Code  -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(tok.s) }
                is Tok.Bold  -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tok.s) }
                is Tok.Italic-> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(tok.s) }
                is Tok.Url   -> {
                    val start = length
                    withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) {
                        append(tok.s)
                    }
                    addStringAnnotation("URL", tok.s, start, length)
                }
            }
        }
    }
}

private sealed interface Tok {
    @JvmInline value class Plain (val s: String) : Tok
    @JvmInline value class Code  (val s: String) : Tok
    @JvmInline value class Bold  (val s: String) : Tok
    @JvmInline value class Italic(val s: String) : Tok
    @JvmInline value class Url   (val s: String) : Tok
}

private fun tokenize(text: String): List<Tok> {
    val out = mutableListOf<Tok>()
    var rest = text
    while (rest.isNotEmpty()) {
        val matches = listOfNotNull(
            CODE_REGEX.find(rest)?.let { it to (Tok.Code (it.groupValues[1]) as Tok) },
            BOLD_REGEX.find(rest)?.let { it to (Tok.Bold (it.groupValues[1]) as Tok) },
            ITAL_REGEX.find(rest)?.let { it to (Tok.Italic(it.groupValues[1]) as Tok) },
            URL_REGEX .find(rest)?.let { it to (Tok.Url  (it.value)         as Tok) },
        )
        if (matches.isEmpty()) {
            out += Tok.Plain(rest); break
        }
        val (m, tok) = matches.minBy { it.first.range.first }
        if (m.range.first > 0) out += Tok.Plain(rest.substring(0, m.range.first))
        out += tok
        rest = rest.substring(m.range.last + 1)
    }
    return out
}

private fun normalizeUrl(raw: String): String =
    if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
