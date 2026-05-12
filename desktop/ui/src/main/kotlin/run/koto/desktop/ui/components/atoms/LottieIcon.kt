package run.koto.desktop.ui.components.atoms

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.useResource
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

@Composable
fun LottieIcon(
    name        : String,
    modifier    : Modifier = Modifier,
    iterations  : Int      = Compottie.IterateForever,
    speed       : Float    = 1f,
    colorFilter : ColorFilter? = null,
) {
    val json = remember(name) {
        useResource("lottie/$name.json") { it.readBytes().decodeToString() }
    }
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(json)
    }
    Image(
        painter            = rememberLottiePainter(
            composition = composition,
            iterations  = iterations,
            speed       = speed,
        ),
        contentDescription = null,
        modifier           = modifier,
        colorFilter        = colorFilter,
    )
}
