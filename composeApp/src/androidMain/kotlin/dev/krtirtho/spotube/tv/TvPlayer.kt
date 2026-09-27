/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.krtirtho.spotube.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerInterface
import dev.krtirtho.spotube.core.audioplayer.AudioPlayerQueue
import dev.krtirtho.spotube.core.audioplayer.LoopState
import dev.krtirtho.spotube.core.audioplayer.QueueEntry
import dev.krtirtho.spotube.modules.saved_tracks.SAVED_TRACKS_COLLECTION_ID
import dev.krtirtho.spotube.modules.saved_tracks.SavedState
import dev.krtirtho.spotube.modules.saved_tracks.SavedTracksViewModel
import dev.krtirtho.spotube.modules.saved_tracks.rememberIsSavedTracks
import dev.krtirtho.spotube.modules.shell.PlayerUiState
import dev.krtirtho.spotube.modules.shell.rememberPlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val SeekStep = 10.seconds
private const val ControlsHideDelayMs = 4_000L

/** Bottom now-playing bar: track info left, transport + progress centre, full-screen right. */
@Composable
fun TvPlayerBar(
    onOpenFullScreen: () -> Unit,
    audioPlayer: AudioPlayerInterface = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    val ui = rememberPlayerUiState(audioPlayer, audioPlayerQueue)
    val hasTrack = ui.currentQueueEntry != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimens.PlayerBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(0.3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasTrack) {
                TvArtwork(url = ui.coverUrl, size = 56.dp, shape = RoundedCornerShape(4.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(ui.title, color = TvColors.TextPrimary, fontSize = TvType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ui.artists, color = TvColors.TextSecondary, fontSize = TvType.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TvLikeButton(audioPlayerQueue = audioPlayerQueue)
            }
        }
        Column(
            modifier = Modifier.weight(0.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvTransportControls(ui = ui, audioPlayer = audioPlayer, buttonSize = 36.dp, playSize = 40.dp)
            TvSeekBar(ui = ui, audioPlayer = audioPlayer)
        }
        Row(
            modifier = Modifier.weight(0.3f),
            horizontalArrangement = Arrangement.End,
        ) {
            if (hasTrack) {
                TvIconButton(icon = TvIcons.FullScreen, contentDescription = "Full screen", onClick = onOpenFullScreen)
            }
        }
    }
}

@Composable
private fun TvTransportControls(
    ui: PlayerUiState,
    audioPlayer: AudioPlayerInterface,
    buttonSize: Dp,
    playSize: Dp,
    playFocusRequester: FocusRequester? = null,
) {
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(buttonSize / 2),
    ) {
        TvIconButton(
            icon = TvIcons.Shuffle,
            contentDescription = "Shuffle",
            size = buttonSize,
            iconSize = buttonSize * 0.55f,
            tint = if (ui.isShuffling) TvColors.Accent else TvColors.TextSecondary,
            onClick = { scope.launch { audioPlayer.shuffle(!ui.isShuffling) } },
        )
        TvIconButton(
            icon = TvIcons.Previous,
            contentDescription = "Previous",
            size = buttonSize,
            iconSize = buttonSize * 0.6f,
            tint = TvColors.TextPrimary,
            onClick = { scope.launch { audioPlayer.skipToPrevious() } },
        )
        Box(
            modifier = Modifier
                .size(playSize)
                .then(if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester) else Modifier)
                .clip(CircleShape)
                .background(TvColors.TextPrimary, CircleShape)
                .tvFocusable(shape = CircleShape, focusedScale = 1.12f, onClick = {
                    scope.launch { if (ui.isPlaying) audioPlayer.pause() else audioPlayer.play() }
                }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (ui.isPlaying) TvIcons.Pause else TvIcons.Play,
                contentDescription = if (ui.isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(playSize * 0.5f),
            )
        }
        TvIconButton(
            icon = TvIcons.Next,
            contentDescription = "Next",
            size = buttonSize,
            iconSize = buttonSize * 0.6f,
            tint = TvColors.TextPrimary,
            onClick = { scope.launch { audioPlayer.skipToNext() } },
        )
        TvIconButton(
            icon = when (ui.loopState) {
                LoopState.NONE -> TvIcons.RepeatOff
                LoopState.ONE -> TvIcons.RepeatOne
                LoopState.ALL -> TvIcons.RepeatAll
            },
            contentDescription = "Repeat",
            size = buttonSize,
            iconSize = buttonSize * 0.55f,
            tint = if (ui.loopState == LoopState.NONE) TvColors.TextSecondary else TvColors.Accent,
            onClick = { scope.launch { audioPlayer.loop(ui.loopState.next()) } },
        )
    }
}

/** Progress bar; when focused, left/right seek by 10 seconds. */
@Composable
private fun TvSeekBar(
    ui: PlayerUiState,
    audioPlayer: AudioPlayerInterface,
    modifier: Modifier = Modifier,
    timeFontSize: androidx.compose.ui.unit.TextUnit = TvType.Small,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val canSeek = ui.seekDuration.inWholeMilliseconds > 0

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatDuration(ui.position.inWholeMilliseconds), color = TvColors.TextSecondary, fontSize = timeFontSize)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .onKeyEvent { event ->
                    if (!canSeek || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -SeekStep
                        Key.DirectionRight -> SeekStep
                        else -> return@onKeyEvent false
                    }
                    val target = (ui.position + delta).coerceIn(0.milliseconds, ui.seekDuration)
                    scope.launch { audioPlayer.seekTo(target) }
                    true
                }
                .focusable(enabled = canSeek, interactionSource = interactionSource),
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackHeight = if (focused) 6.dp else 4.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(TvPillShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Box(
                Modifier
                    .fillMaxWidth(ui.progress)
                    .height(trackHeight)
                    .clip(TvPillShape)
                    .background(if (focused) TvColors.Accent else TvColors.TextPrimary)
            )
        }
        Text(formatDuration(ui.displayDuration.inWholeMilliseconds), color = TvColors.TextSecondary, fontSize = timeFontSize)
    }
}

@Composable
private fun TvLikeButton(
    audioPlayerQueue: AudioPlayerQueue,
    savedTracksViewModel: SavedTracksViewModel = koinViewModel(
        key = SAVED_TRACKS_COLLECTION_ID,
        parameters = { parametersOf() },
    ),
) {
    val scope = rememberCoroutineScope()
    val currentEntry by audioPlayerQueue.currentQueueEntryFlow.collectAsStateWithLifecycle()
    val trackId = (currentEntry as? QueueEntry.StreamingTrack)?.track?.id ?: return
    val savedState = rememberIsSavedTracks(trackIds = listOf(trackId))
    val savedIds by savedTracksViewModel.savedTrackIdsFlow.collectAsStateWithLifecycle()
    val isLiked = trackId in savedIds ||
        (savedState is SavedState.Success && savedState.data.firstOrNull() == true)

    TvIconButton(
        icon = if (isLiked) TvIcons.HeartFilled else TvIcons.Heart,
        contentDescription = if (isLiked) "Remove from Liked Songs" else "Save to Liked Songs",
        size = 36.dp,
        iconSize = 20.dp,
        tint = if (isLiked) TvColors.Accent else TvColors.TextSecondary,
        onClick = {
            scope.launch {
                if (isLiked) {
                    savedTracksViewModel.removeSavedTracks(listOf(trackId))
                } else {
                    savedTracksViewModel.saveTracks(listOf(trackId))
                }
            }
        },
    )
}

/**
 * Full-screen player. The black background is left free for a visualizer;
 * cover, artists and title stay in the top-left corner at all times.
 * Transport controls appear on any remote key and hide after a few seconds.
 */
@Composable
fun TvFullScreenPlayer(
    onClose: () -> Unit,
    audioPlayer: AudioPlayerInterface = koinInject(),
    audioPlayerQueue: AudioPlayerQueue = koinInject(),
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val ui = rememberPlayerUiState(audioPlayer, audioPlayerQueue)
        var controlsVisible by remember { mutableStateOf(true) }
        var interactions by remember { mutableIntStateOf(0) }
        val rootFocus = remember { FocusRequester() }
        val playFocus = remember { FocusRequester() }

        LaunchedEffect(controlsVisible, interactions) {
            if (controlsVisible) {
                delay(ControlsHideDelayMs)
                controlsVisible = false
            }
        }
        LaunchedEffect(controlsVisible) {
            // Hidden controls can't hold focus; park it on the root so keys still arrive.
            runCatching { if (controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isNavigationKey = event.key in RevealKeys
                    if (!isNavigationKey) return@onPreviewKeyEvent false
                    interactions++
                    if (!controlsVisible) {
                        controlsVisible = true
                        true // the first press only reveals the controls
                    } else {
                        false
                    }
                }
                .focusable(),
        ) {
            // Reserved for the visualizer (MilkDrop) — drawn behind the track info.

            TvNowPlayingInfo(
                ui = ui,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp, top = 36.dp),
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 96.dp, end = 96.dp, top = 48.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvTransportControls(
                        ui = ui,
                        audioPlayer = audioPlayer,
                        buttonSize = 48.dp,
                        playSize = 60.dp,
                        playFocusRequester = playFocus,
                    )
                    TvSeekBar(ui = ui, audioPlayer = audioPlayer, timeFontSize = TvType.Body)
                }
            }
        }
    }
}

private val RevealKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
)

@Composable
private fun TvNowPlayingInfo(ui: PlayerUiState, modifier: Modifier = Modifier) {
    if (ui.currentQueueEntry == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(2.dp))
                .padding(2.dp),
        ) {
            TvArtwork(url = ui.coverUrl, size = 116.dp, shape = RoundedCornerShape(0.dp))
        }
        Column(
            modifier = Modifier.width(900.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                ui.artists,
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ui.title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 34.sp,
                lineHeight = 40.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
