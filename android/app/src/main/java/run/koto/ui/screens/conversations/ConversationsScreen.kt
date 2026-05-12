package run.koto.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import run.koto.domain.model.ConversationUi
import run.koto.ui.theme.KotoTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ConversationsScreen(
    viewModel               : ConversationsViewModel = hiltViewModel(),
    onOpenChat              : (String) -> Unit,
    onOpenSettings          : () -> Unit,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val state        by viewModel.state.collectAsStateWithLifecycle()
    val showNewChat  by viewModel.showNewChatSheet.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val colors       = KotoTheme.colors

    // ── Search state ──────────────────────────────────────────────────────────
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingQuery   by remember { mutableStateOf("") }

    // 200ms debounce: LaunchedEffect restarts on each keystroke, delays 200ms, then updates ViewModel
    LaunchedEffect(pendingQuery) {
        delay(200)
        viewModel.onSearchQueryChange(pendingQuery)
    }

    // ── Filter conversations based on current searchQuery from state ──────────
    // Keys: exact fields (not whole state object) — prevents unnecessary re-filtering
    val filteredConvs = remember(state.conversations, state.pinnedConvs, state.searchQuery) {
        val q = state.searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.conversations to state.pinnedConvs
        } else {
            state.conversations.filter { c ->
                c.displayName.lowercase().contains(q) ||
                c.lastMessage.lowercase().contains(q)
            }.toImmutableList() to state.pinnedConvs.filter { c ->
                c.displayName.lowercase().contains(q)
            }.toImmutableList()
        }
    }
    val (filteredRegular, filteredPinned) = filteredConvs

    val headerProfile by viewModel.headerProfile.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            ConversationsTopBar(
                searchExpanded = searchExpanded,
                searchQuery    = pendingQuery,
                onSearchToggle = { searchExpanded = !searchExpanded },
                onQueryChange  = { pendingQuery = it },
                onSettings     = onOpenSettings,
                onNewChat      = viewModel::showNewChat,
                avatarInitial  = headerProfile.initial,
                avatarUrl      = headerProfile.avatarUrl,
                accountId      = headerProfile.accountId,
            )
        },
    ) { padding ->
        KotoPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh    = viewModel::refresh,
            modifier     = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.searchQuery.isNotEmpty() && filteredRegular.isEmpty() && filteredPinned.isEmpty() -> {
                        SearchEmptyState(query = state.searchQuery)
                    }
                    state.conversations.isEmpty() && state.pinnedConvs.isEmpty() && !state.isLoading -> {
                        EmptyState()
                    }
                    else -> ConversationList(
                        pinnedConvs             = filteredPinned,
                        conversations           = filteredRegular,
                        onOpen                  = onOpenChat,
                        onArchive               = viewModel::onArchive,
                        onPin                   = viewModel::onPin,
                        onMute                  = viewModel::onMute,
                        sharedTransitionScope   = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.align(Alignment.Center),
                        color       = colors.primary,
                        strokeWidth = 2.5.dp,
                        trackColor  = colors.surfaceVariant,
                    )
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            onDismiss   = viewModel::hideNewChat,
            onStartChat = { accountId -> viewModel.startNewChat(accountId, onOpenChat) },
        )
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsTopBar(
    searchExpanded : Boolean,
    searchQuery    : String,
    onSearchToggle : () -> Unit,
    onQueryChange  : (String) -> Unit,
    onSettings     : () -> Unit,
    onNewChat      : () -> Unit,
    avatarInitial  : String,
    avatarUrl      : String,
    accountId      : String,
) {
    val colors = KotoTheme.colors

    TopAppBar(
        navigationIcon = {
            // User avatar — tap opens Settings. Signal pattern.
            if (!searchExpanded) {
                HeaderAvatar(
                    initial   = avatarInitial,
                    avatarUrl = avatarUrl,
                    accountId = accountId,
                    onClick   = onSettings,
                )
            }
        },
        title = {
            androidx.compose.animation.Crossfade(
                targetState   = searchExpanded,
                animationSpec = androidx.compose.animation.core.tween(200),
                label         = "topBarContent",
            ) { isSearching ->
                if (isSearching) {
                    AnimatedSearchBar(
                        expanded       = true,
                        query          = searchQuery,
                        onQueryChange  = onQueryChange,
                        onExpandToggle = onSearchToggle,
                        modifier       = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "Koto",
                        style      = KotoTheme.typography.titleLarge,
                        color      = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        actions = {
            if (!searchExpanded) {
                IconButton(onClick = onNewChat) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Новый чат",
                        tint               = colors.onSurface,
                    )
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector        = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint               = colors.onSurface,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
    )
}

/**
 * 32dp avatar in the top bar — uses the cached avatar URL if the user
 * uploaded one, otherwise falls back to a gradient circle with the first
 * letter of their display name (or "K" if no name is set).
 */
@Composable
private fun HeaderAvatar(
    initial   : String,
    avatarUrl : String,
    accountId : String,
    onClick   : () -> Unit,
) {
    val colors   = KotoTheme.colors
    val gradient = remember(accountId) {
        run.koto.ui.theme.avatarGradient(accountId.ifBlank { "koto" })
    }
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl.isNotBlank()) {
            coil3.compose.AsyncImage(
                model              = avatarUrl,
                contentDescription = "Открыть настройки",
                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text       = initial.ifBlank { "K" },
                color      = colors.onPrimary,
                fontWeight = FontWeight.Bold,
                style      = KotoTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Unused legacy. Kept empty to avoid import churn; compiler will inline-strip it.
 */
@Suppress("unused")
@Composable
private fun KotoLogoTitle() {
    // replaced by HeaderAvatar + plain "Koto" title, kept to avoid unrelated diffs
    val colors = KotoTheme.colors
    val typo   = KotoTheme.typography
    Row {
        Text(
            "Koto",
            style      = typo.titleLarge,
            color      = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Search Empty State ───────────────────────────────────────────────────────

@Composable
private fun SearchEmptyState(query: String) {
    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typo    = KotoTheme.typography

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.SearchOff,
            contentDescription = null,
            tint               = colors.onSurfaceMuted,
            modifier           = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text  = "No results for \"$query\"",
            style = typo.bodyLarge,
            color = colors.onSurfaceMuted,
        )
    }
}

// ─── Conversation List ────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ConversationList(
    pinnedConvs             : ImmutableList<ConversationUi>,
    conversations           : ImmutableList<ConversationUi>,
    onOpen                  : (String) -> Unit,
    onArchive               : (String) -> Unit,
    onPin                   : (String) -> Unit,
    onMute                  : (String) -> Unit,
    sharedTransitionScope   : SharedTransitionScope? = null,
    animatedVisibilityScope : AnimatedVisibilityScope? = null,
) {
    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        // 88dp leaves room for the floating bottom nav bar (60dp height +
        // 16dp top margin + 12dp bottom margin) + extra breathing space.
        contentPadding = PaddingValues(bottom = 104.dp),
    ) {
        // ── Pinned section (CL-03) ────────────────────────────────────────────
        if (pinnedConvs.isNotEmpty()) {
            item(key = "header-pinned", contentType = "header") {
                PinnedSectionHeader()
            }
            items(
                items       = pinnedConvs,
                key         = { c -> "pinned-${c.id}" },
                contentType = { "conversation" },
            ) { conv ->
                ConversationItem(
                    conv                    = conv,
                    onClick                 = { onOpen(conv.id) },
                    modifier                = Modifier.animateItem(),
                    sharedTransitionScope   = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 76.dp),
                    color     = colors.divider,
                    thickness = 0.5.dp,
                )
            }
            item(key = "pinned-section-divider", contentType = "sectionDivider") {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = spacing.lg),
                    color     = colors.divider,
                )
            }
        }

        // ── Regular conversations (CL-02: swipeable) ─────────────────────────
        items(
            items       = conversations,
            key         = { c -> c.id },
            contentType = { "swipeable-conversation" },
        ) { conv ->
            SwipeableConversationItem(
                conv                    = conv,
                onClick                 = { onOpen(conv.id) },
                onArchive               = onArchive,
                onPin                   = onPin,
                onMute                  = onMute,
                modifier                = Modifier.animateItem(),
                sharedTransitionScope   = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            HorizontalDivider(
                modifier  = Modifier.padding(start = 76.dp),
                color     = colors.divider,
                thickness = 0.5.dp,
            )
        }
    }
}

// ─── Pinned Section Header (CL-03) ───────────────────────────────────────────

@Composable
private fun PinnedSectionHeader() {
    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typo    = KotoTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            imageVector        = Icons.Default.PushPin,
            contentDescription = null,
            tint               = colors.onSurfaceMuted,
            modifier           = Modifier.size(14.dp),
        )
        Text(
            text  = "Pinned",
            style = typo.labelMedium,
            color = colors.onSurfaceMuted,
        )
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typo    = KotoTheme.typography

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", style = typo.displayLarge.copy(fontSize = 40.sp))
        }
        Spacer(Modifier.height(spacing.xl))
        Text(
            "Нет чатов",
            style      = typo.titleMedium,
            color      = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            "Нажмите + чтобы начать\nновый разговор",
            style     = typo.bodyMedium,
            color     = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── New Chat Sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(onDismiss: () -> Unit, onStartChat: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    val colors  = KotoTheme.colors
    val spacing = KotoTheme.spacing
    val typo    = KotoTheme.typography

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = colors.surface,
        tonalElevation   = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.divider)
            )
        },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxl)
                .padding(bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                "Новый чат",
                style      = typo.titleMedium,
                color      = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value         = input,
                onValueChange = { input = it },
                label         = { Text("Account ID или @username", color = colors.onSurfaceMuted) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = colors.primary,
                    unfocusedBorderColor = colors.divider,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = colors.primary,
                    focusedLabelColor    = colors.primary,
                ),
                singleLine    = true,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (input.isNotBlank())
                            Brush.linearGradient(listOf(colors.primary, colors.primary))
                        else
                            Brush.linearGradient(listOf(colors.surface, colors.surface))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick   = { if (input.isNotBlank()) onStartChat(input.trim()) },
                    modifier  = Modifier.fillMaxSize(),
                    enabled   = input.isNotBlank(),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                ) {
                    Text(
                        "Начать чат",
                        color = if (input.isNotBlank()) Color.White else colors.onSurfaceMuted,
                    )
                }
            }
        }
    }
}
