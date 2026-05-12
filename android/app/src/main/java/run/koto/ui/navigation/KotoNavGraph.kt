package run.koto.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import run.koto.data.prefs.AccountPrefs
import run.koto.network.ConnectivityManager
import run.koto.ui.components.ConnectivityBanner
import run.koto.ui.screens.chat.ChatScreen
import run.koto.ui.screens.conversations.ConversationsScreen
import run.koto.ui.screens.onboarding.OnboardingScreen
import run.koto.ui.screens.settings.SettingsScreen
import run.koto.ui.theme.KotoTheme

// ─── Routes ───────────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Onboarding    : Screen("onboarding")
    object Conversations : Screen("conversations")
    object Chat          : Screen("chat/{convId}") {
        fun build(convId: String) = "chat/$convId"
    }
    object Settings      : Screen("settings")
}

// ─── Motion spec — spring physics (NAV-05) ────────────────────────────────────
// Spring-based specs replace tween() for all screen-level transitions.
// Requirements: dampingRatio=0.85, stiffness=380 per ROADMAP Phase 4 success criterion.

private val navSpringSpec = spring<IntOffset>(
    dampingRatio = 0.85f,
    stiffness    = 380f,
)
private val navFloatSpec = spring<Float>(
    dampingRatio = 0.85f,
    stiffness    = 380f,
)

// Forward push — new screen slides in from the right, current slides a bit left.
// Both sides must use identical timing or the animation looks uncoordinated.
private val pushEnter = slideInHorizontally(
    animationSpec  = navSpringSpec,
    initialOffsetX = { fullWidth -> fullWidth },
) + fadeIn(animationSpec = navFloatSpec)

private val pushExit = slideOutHorizontally(
    animationSpec  = navSpringSpec,
    targetOffsetX  = { fullWidth -> -fullWidth / 4 },
) + fadeOut(animationSpec = navFloatSpec)

private val popEnter = slideInHorizontally(
    animationSpec  = navSpringSpec,
    initialOffsetX = { fullWidth -> -fullWidth / 4 },
) + fadeIn(animationSpec = navFloatSpec)

private val popExit = slideOutHorizontally(
    animationSpec  = navSpringSpec,
    targetOffsetX  = { fullWidth -> fullWidth },
) + fadeOut(animationSpec = navFloatSpec)

// Pure crossfade for same-level tab switches — no horizontal motion.
private val fadeInOnly  = fadeIn(animationSpec  = navFloatSpec)
private val fadeOutOnly = fadeOut(animationSpec = navFloatSpec)

// ─── Route categories ────────────────────────────────────────────────────────
// Tabs share a crossfade transition; chat opens with a slide.
private val TAB_ROUTES = setOf(
    Screen.Conversations.route,
    "contacts",
    "calls",
    Screen.Settings.route,
)

private fun String?.isTab(): Boolean = this != null && this in TAB_ROUTES

// ─── Nav graph ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KotoNavGraph(
    connectivityManager : ConnectivityManager,
    accountPrefs        : AccountPrefs,
    navController       : NavHostController = rememberNavController(),
    startDestination    : String             = Screen.Onboarding.route,
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // React to session loss: if TokenAuthenticator clears tokens (refresh failed)
    // drop the user back to Onboarding and wipe the chat/conversations back stack.
    val isRegistered by accountPrefs.isRegisteredFlow()
        .collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(isRegistered) {
        if (!isRegistered && currentRoute != null && currentRoute != Screen.Onboarding.route) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Show bottom nav only on root-level tab screens; hide on chat/onboarding
    val showBottomNav = currentRoute in listOf(
        Screen.Conversations.route,
        "contacts",
        "calls",
        Screen.Settings.route,
    )

    // Transitions decided at the NavHost level based on source/target route.
    //   tab → tab       : crossfade (no horizontal motion)
    //   tab → chat      : push slide-in
    //   chat → tab      : pop slide-out
    //   anything else   : crossfade fallback

    SharedTransitionLayout {
        // NavHost in a fullscreen Box so its size is STABLE across navigation.
        // We deliberately do NOT put the bottom nav / banner in a Scaffold slot:
        // Scaffold's innerPadding animates when AnimatedVisibility toggles the
        // bottomBar, which causes the NavHost content to reflow vertically
        // mid-transition — visible as jank.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KotoTheme.colors.background),
        ) {
            NavHost(
                navController       = navController,
                startDestination    = startDestination,
                modifier            = Modifier.fillMaxSize(),
                enterTransition     = {
                    val from = initialState.destination.route
                    val to   = targetState.destination.route
                    if (to == Screen.Chat.route && from.isTab()) pushEnter else fadeInOnly
                },
                exitTransition      = {
                    val from = initialState.destination.route
                    val to   = targetState.destination.route
                    if (to == Screen.Chat.route && from.isTab()) pushExit else fadeOutOnly
                },
                popEnterTransition  = {
                    val from = initialState.destination.route
                    val to   = targetState.destination.route
                    if (from == Screen.Chat.route && to.isTab()) popEnter else fadeInOnly
                },
                popExitTransition   = {
                    val from = initialState.destination.route
                    val to   = targetState.destination.route
                    if (from == Screen.Chat.route && to.isTab()) popExit else fadeOutOnly
                },
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onRegistered = {
                            navController.navigate(Screen.Conversations.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Conversations.route) {
                    ConversationsScreen(
                        onOpenChat              = { convId ->
                            navController.navigate(Screen.Chat.build(convId))
                        },
                        onOpenSettings          = { navController.navigate(Screen.Settings.route) },
                        sharedTransitionScope   = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                    )
                }

                composable(
                    route     = Screen.Chat.route,
                    arguments = listOf(navArgument("convId") { type = NavType.StringType }),
                ) { back ->
                    val convId = back.arguments?.getString("convId") ?: return@composable
                    ChatScreen(
                        convId                  = convId,
                        onBack                  = { navController.popBackStack() },
                        sharedTransitionScope   = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                composable("contacts") { PlaceholderScreen("Contacts") }
                composable("calls")    { PlaceholderScreen("Calls")    }
            }

            // Top overlay — connectivity banner. Only takes vertical space when
            // the server is unreachable; otherwise it is a zero-height spacer
            // and cannot shift NavHost content.
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                ConnectivityBanner(connectivityManager)
            }

            // Bottom overlay — floating tab bar. Slides in/out without touching
            // NavHost's layout. Chat destination shows nothing here; its own
            // MessageInputBar handles the bottom edge inside the Chat composable.
            AnimatedVisibility(
                visible  = showBottomNav,
                enter    = slideInVertically(animationSpec = navSpringSpec) { it } +
                           fadeIn(animationSpec = navFloatSpec),
                exit     = slideOutVertically(animationSpec = navSpringSpec) { it } +
                           fadeOut(animationSpec = navFloatSpec),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                KotoBottomNavBar(
                    currentRoute  = currentRoute,
                    onTabSelected = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(KotoTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KotoTheme.typography.titleLarge, color = KotoTheme.colors.onSurface)
    }
}
