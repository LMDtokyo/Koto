package run.koto.desktop.ui.nav

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

/**
 * Navigation model for a Telegram-style two-pane desktop layout.
 *
 *   - The **sidebar** is always present and always shows the chat list, independent of
 *     what's happening in the main pane.
 *   - The **main pane** is driven by a stack of [Screen]s. Clicking a chat row pushes
 *     `Chat(id)`; clicking Settings pushes `Settings`; back arrow on that pane pops.
 *
 * Some screens are **fullscreen overlays** that replace the whole window (auth, call,
 * lock). They are handled by [App.overlay] rather than the main pane stack — the
 * distinction is enforced in [run.koto.desktop.ui.KotoApp] so a call screen can't be
 * accidentally pushed into the main pane.
 */
@Immutable
sealed interface Screen {

    /** Main-pane slots. */
    data object Empty                                : Screen              // no chat selected
    data class  Chat(val convId: String)             : Screen              // live chat in main pane
    data object Settings                             : Screen
    data class  SettingsSub(val section: String)     : Screen
    data object NewChat                              : Screen
    data object NewGroup                             : Screen
    data class  Contact(val id: String)              : Screen
    data object Stories                              : Screen
    data object Safety                               : Screen
    data class  SafetyDetail(val convId: String)     : Screen
    data object Bots                                 : Screen
    data class  BotForge(val step: String = "root")  : Screen
    data object Archive                              : Screen              // archived chat list — replaces main

    /** Fullscreen overlays — [App.overlay] only. */
    data object Welcome                              : Screen
    data object Register                             : Screen
    data object Login                                : Screen
    data object Lock                                 : Screen
    data class  Call(val peerId: String, val video: Boolean) : Screen
}

enum class NavTransition { PUSH, POP, NONE }

/**
 * Stack-based router for a single target (either the sidebar or the main pane). Uses a
 * snapshot-backed list — cheap, deterministic, and transparent to [AnimatedContent].
 */
class NavStack(initial: Screen) {
    private val _stack: SnapshotStateList<Screen> = mutableStateListOf(initial)
    var lastTransition: NavTransition by mutableStateOf(NavTransition.NONE)
        private set

    val current: Screen get() = _stack.last()
    val depth: Int     get() = _stack.size
    val stack: List<Screen> get() = _stack

    fun push(screen: Screen) {
        lastTransition = NavTransition.PUSH
        _stack.add(screen)
    }

    fun pop(): Boolean {
        if (_stack.size <= 1) return false
        lastTransition = NavTransition.POP
        _stack.removeAt(_stack.lastIndex)
        return true
    }

    fun resetTo(screen: Screen) {
        lastTransition = NavTransition.NONE
        _stack.clear()
        _stack.add(screen)
    }
}
