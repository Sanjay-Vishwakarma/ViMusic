package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.isLandscape
import it.vfsfitvnm.vimusic.utils.semiBold

private val BottomBarHeight = 64.dp
private val BottomBarMinItemWidth = 64.dp

/**
 * Screen frame with tabs.
 *
 * Portrait: every control is in a bottom tab bar (reachable with the thumb)
 * that sits just above the mini player: an optional Back item on inner
 * screens, the tabs, and Settings on the home screen. Content keeps the full
 * width and height; nothing is at the top any more.
 * Landscape: the original navigation rail.
 *
 * Call sites are unchanged: `tabColumnContent = { Item -> Item(index, title, icon) }`.
 * `topIconButtonId == R.drawable.chevron_back` marks an inner screen (Back);
 * anything else is the home screen, where it opens Settings.
 */
@ExperimentalAnimationApi
@Composable
fun Scaffold(
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    tabColumnContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette) = LocalAppearance.current

    if (isLandscape) {
        Row(
            modifier = modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            NavigationRail(
                topIconButtonId = topIconButtonId,
                onTopIconButtonClick = onTopIconButtonClick,
                tabIndex = tabIndex,
                onTabIndexChanged = onTabChanged,
                content = { item -> tabColumnContent(item) }
            )

            TabContent(tabIndex = tabIndex, horizontal = false, content = content)
        }
        return
    }

    val insets = LocalPlayerAwareWindowInsets.current
    val isInnerScreen = topIconButtonId == R.drawable.chevron_back
    // Titles shown in the bar, so screen headers don't repeat them. The bar is
    // composed before the content (see below) and tab titles never change.
    val tabTitles = remember { HashMap<Int, String>() }

    Box(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        // Composed first (so tabTitles is filled before the content reads it),
        // drawn on top thanks to zIndex. The player-aware bottom inset puts it
        // right above the mini player and the system navigation bar.
        BottomTabBar(
            tabIndex = tabIndex,
            onTabChanged = onTabChanged,
            tabs = { item -> tabColumnContent { index, text, icon -> tabTitles[index] = text; item(index, text, icon) } },
            leading = if (isInnerScreen) {
                { BottomTabItem(text = "Back", icon = R.drawable.chevron_back, selected = false, onClick = onTopIconButtonClick) }
            } else null,
            trailing = if (!isInnerScreen) {
                { BottomTabItem(text = "Settings", icon = topIconButtonId, selected = false, onClick = onTopIconButtonClick) }
            } else null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f)
                .padding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).asPaddingValues())
        )

        // Screens pad their lists by the bottom inset: add the bar's height so
        // the last rows (and floating buttons) stay above it.
        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides insets.add(WindowInsets(bottom = BottomBarHeight)),
            LocalTabTitles provides tabTitles
        ) {
            TabContent(tabIndex = tabIndex, horizontal = true, content = content)
        }
    }
}

@ExperimentalAnimationApi
@Composable
private fun TabContent(
    tabIndex: Int,
    horizontal: Boolean,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    AnimatedContent(
        targetState = tabIndex,
        transitionSpec = {
            val forward = targetState > initialState
            val slideDirection = when {
                horizontal && forward -> AnimatedContentScope.SlideDirection.Left
                horizontal -> AnimatedContentScope.SlideDirection.Right
                forward -> AnimatedContentScope.SlideDirection.Up
                else -> AnimatedContentScope.SlideDirection.Down
            }

            val animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = Spring.StiffnessLow,
                visibilityThreshold = IntOffset.VisibilityThreshold
            )

            slideIntoContainer(slideDirection, animationSpec) with
                    slideOutOfContainer(slideDirection, animationSpec)
        },
        content = content
    )
}

/**
 * Items share the width equally; when there are too many to fit at
 * [BottomBarMinItemWidth] each (e.g. Settings' tabs), the bar scrolls.
 */
@Composable
private fun BottomTabBar(
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    tabs: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (colorPalette) = LocalAppearance.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colorPalette.background1)
    ) {
        val available = maxWidth

        Layout(
            content = {
                leading?.invoke()
                tabs { index, text, icon ->
                    BottomTabItem(
                        text = text,
                        icon = icon,
                        selected = index == tabIndex,
                        onClick = { onTabChanged(index) }
                    )
                }
                trailing?.invoke()
            },
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .height(BottomBarHeight)
        ) { measurables, _ ->
            val count = measurables.size.coerceAtLeast(1)
            val itemWidth = maxOf(BottomBarMinItemWidth.roundToPx(), available.roundToPx() / count)
            val height = BottomBarHeight.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(itemWidth, height)) }
            layout(itemWidth * placeables.size, height) {
                placeables.forEachIndexed { i, placeable -> placeable.place(i * itemWidth, 0) }
            }
        }
    }
}

@Composable
private fun BottomTabItem(text: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current

    val pill by animateColorAsState(
        targetValue = if (selected) colorPalette.accent.copy(alpha = 0.22f) else colorPalette.background1,
        animationSpec = tween(250)
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) colorPalette.accent else colorPalette.textSecondary,
        animationSpec = tween(250)
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colorPalette.text else colorPalette.textDisabled,
        animationSpec = tween(250)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(pill)
                .width(52.dp)
                .height(30.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = text,
                colorFilter = ColorFilter.tint(iconColor),
                modifier = Modifier.size(20.dp)
            )
        }
        BasicText(
            text = text,
            style = typography.xxs.semiBold.color(textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
