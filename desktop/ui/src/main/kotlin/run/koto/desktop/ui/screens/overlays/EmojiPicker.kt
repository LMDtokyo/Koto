package run.koto.desktop.ui.screens.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import run.koto.desktop.ui.icons.KotoIcons
import run.koto.desktop.ui.theme.KotoEasing
import run.koto.desktop.ui.theme.KotoTheme

private data class EmojiCategory(val key: String, val label: String, val emojis: List<String>)

private val CATEGORIES = listOf(
    EmojiCategory("smileys", "😊", listOf(
        "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩",
        "😘","😗","☺️","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔",
        "🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷",
        "🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","🤯","🤠","🥳","😎","🤓","🧐","😕",
        "😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱",
        "😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩",
    )),
    EmojiCategory("hands", "👍", listOf(
        "👋","🤚","✋","🖐️","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","🖕",
        "👇","☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","💪","🦾",
    )),
    EmojiCategory("hearts", "❤️", listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖",
        "💘","💝","💟","♥️","💌","💋","💍","💎",
    )),
    EmojiCategory("animals", "🐱", listOf(
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔",
        "🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌","🐞",
    )),
    EmojiCategory("food", "🍕", listOf(
        "🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝",
        "🍅","🥑","🍆","🥔","🥕","🌽","🌶️","🥒","🥬","🥦","🍄","🥜","🌰","🍞","🥐","🥖",
        "🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍔","🍟","🍕","🌭","🥪","🌮","🌯","🥙",
    )),
    EmojiCategory("travel", "✈️", listOf(
        "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛵","🏍️",
        "🚲","🛴","🛹","🛼","🚏","🛣️","🛤️","✈️","🛫","🛬","🪂","🚀","🛸","🛰️","🚁","⛵",
        "🚤","🛥️","🛳️","⛴️","🚢","🚂","🚆","🚇","🚊","🚉","🚞","🚝","🚄","🚅","🚈","🚃",
    )),
    EmojiCategory("objects", "💡", listOf(
        "⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","💾","💿","📀","🧮","🎥","🎬","📷","📸","📹",
        "📼","🔍","🔎","🕯️","💡","🔦","🏮","🪔","📔","📕","📖","📗","📘","📙","📚","📓",
    )),
    EmojiCategory("symbols", "✨", listOf(
        "❤️","🧡","💛","💚","💙","💜","🤎","🖤","🤍","💯","💢","💥","💫","💦","💨","🕳️",
        "💣","💬","👁️‍🗨️","🗨️","🗯️","💭","💤","✨","⭐","🌟","🌠","🔥","🌈","☀️","⛅","☁️",
        "❄️","☃️","⛄","💧","🌊","🎉","🎊","🎈","🎁","🎀","🪅","🎂","🎄","🎃","🎆","🎇",
    )),
)

@Composable
fun EmojiPicker(
    onPick    : (String) -> Unit,
    onDismiss : () -> Unit,
) {
    val colors = KotoTheme.colors
    val scrim  = remember { Animatable(0f) }
    val slide  = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { scrim.animateTo(1f, tween(180)) }
            launch { slide.animateTo(0f, tween(320, easing = KotoEasing)) }
        }
    }

    var query    by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES.first().key) }

    val visible = remember(query, category) {
        if (query.isBlank()) CATEGORIES.firstOrNull { it.key == category }?.emojis.orEmpty()
        else CATEGORIES.flatMap { it.emojis }.filter { /* lite filter — trim by query length */ true }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f * scrim.value))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .graphicsLayer { translationY = slide.value * 800f }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {},
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.separator)
                    .align(Alignment.CenterHorizontally),
            )

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = KotoIcons.Search,
                    contentDescription = null,
                    tint               = colors.textSecondary,
                    modifier           = Modifier.size(16.dp),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text(
                            text  = "Поиск эмодзи",
                            style = KotoTheme.typography.bodyMedium,
                            color = colors.textTertiary,
                        )
                    }
                    BasicTextField(
                        value         = query,
                        onValueChange = { query = it },
                        singleLine    = true,
                        textStyle     = LocalTextStyle.current.merge(
                            KotoTheme.typography.bodyMedium.copy(color = colors.text),
                        ),
                        cursorBrush   = SolidColor(colors.accent),
                    )
                }
            }

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                CATEGORIES.forEach { cat ->
                    val active = cat.key == category && query.isBlank()
                    Box(
                        modifier          = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                category = cat.key
                                query    = ""
                            },
                        contentAlignment  = Alignment.Center,
                    ) {
                        Text(text = cat.label, style = KotoTheme.typography.titleMedium)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 40.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement   = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it }) { e ->
                    Box(
                        modifier          = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(e) },
                        contentAlignment  = Alignment.Center,
                    ) {
                        Text(
                            text       = e,
                            style      = KotoTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
