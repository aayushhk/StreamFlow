package com.example.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable
fun VideoPlayerScreen(
    streamUrl: String,
    streamTitle: String,
    isInPipMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Player State
    var player by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isBuffering by remember { mutableStateOf(false) }

    // Custom Subtitle selection state
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var customSubtitleInput by remember { mutableStateOf("") }

    // Preset subtities for testing
    val subtitlePresets = listOf(
        "None" to null,
        "English Live Captions (Preset)" to "https://raw.githubusercontent.com/andreyvit/subtitle-tools/master/sample.srt",
        "French Captions (Preset)" to "https://playertest.longtailvideo.com/adaptive/captions/flemish.vtt"
    )

    // Track selection state
    var showTrackSelectionDialog by remember { mutableStateOf(false) }
    var availableVideoTracks by remember { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var currentTrackOverrideLabel by remember { mutableStateOf("Auto") }

    // Control visibility
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    val activity = context.findActivity()
    val window = activity?.window
    val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    // Auto-hide controls effect
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    // Keep updating current position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let {
                currentPosition = it.currentPosition
                duration = it.duration
            }
            delay(1000)
        }
    }

    var localPlayer: ExoPlayer? = null
    var castPlayerComponent: CastPlayer? = null

    // Init player function
    fun initializePlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(defaultDataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
        localPlayer = exoPlayer

        // Initialize CastPlayer
        try {
            val castContext = CastContext.getSharedInstance(context)
            castPlayerComponent = CastPlayer(castContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                player?.let { duration = it.duration }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // Read available video tracks
                val tracksList = mutableListOf<VideoTrackOption>()
                var activeLabel = "Auto"

                val trackGroups = tracks.groups
                for (group in trackGroups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val res = "${format.width}x${format.height}"
                            val label = if (format.width > 0) "$res (${format.frameRate.toInt()}fps)" else "Stream Quality $i"
                            tracksList.add(VideoTrackOption(group, i, label, isSelected))
                            if (isSelected) {
                                activeLabel = label
                            }
                        }
                    }
                }
                availableVideoTracks = tracksList
                currentTrackOverrideLabel = activeLabel
            }
        }
        
        exoPlayer.addListener(playerListener)
        castPlayerComponent?.addListener(playerListener)

        // Prepare Media Source
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
        
        // Add custom subtitle if specified
        selectedSubtitleUrl?.let { subUrl ->
            val mimeType = if (subUrl.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
        }

        val mediaItem = mediaItemBuilder.build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        
        castPlayerComponent?.setMediaItem(mediaItem)

        // Setup session listener to switch players
        castPlayerComponent?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                // Transfer from local to cast
                val currentPosition = localPlayer?.currentPosition ?: C.TIME_UNSET
                localPlayer?.pause()
                castPlayerComponent?.seekTo(currentPosition)
                castPlayerComponent?.prepare()
                castPlayerComponent?.play()
                player = castPlayerComponent
            }

            override fun onCastSessionUnavailable() {
                // Transfer from cast to local
                val currentPosition = castPlayerComponent?.currentPosition ?: C.TIME_UNSET
                castPlayerComponent?.stop()
                localPlayer?.seekTo(currentPosition)
                localPlayer?.prepare()
                localPlayer?.play()
                player = localPlayer
            }
        })

        if (castPlayerComponent?.isCastSessionAvailable == true) {
            player = castPlayerComponent
            castPlayerComponent?.prepare()
        } else {
            player = localPlayer
            localPlayer?.prepare()
        }
    }

    // Initialize/Re-initialize player when streamUrl or selectedSubtitle changes
    LaunchedEffect(streamUrl, selectedSubtitleUrl) {
        localPlayer?.release()
        castPlayerComponent?.release()
        initializePlayer()
    }

    // Dispose player safely
    DisposableEffect(Unit) {
        onDispose {
            localPlayer?.release()
            castPlayerComponent?.release()
        }
    }

    // Helper functions
    val togglePlayPause: () -> Unit = {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_container")
    ) {
        // Player Surface View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use our highly customized Jetpack Compose overlaid UI
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isInPipMode) {
                        showControls = !showControls
                    }
                }
        )

        // Buffering circular indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp
            )
        }

        // Custom Overlay Controls (Hides completely in Picture-In-Picture mode)
        if (!isInPipMode) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it / 5 },
                exit = fadeOut() + slideOutVertically { -it / 5 }
            ) {
                // Dark translucent vignette over player for control readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    // 1. Header (Back, Info, Enter PiP Action)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                                .testTag("player_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = streamTitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Live HLS Stream • Active Cache",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }

                        // Picture in Picture Action Button
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val activity = context as? Activity
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                                .testTag("pip_mode_button")
                        ) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP Mode", tint = Color.White)
                        }
                    }

                    // 2. Playback Speed Selector, Subtitles config, Quality Track button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                player?.let {
                                    val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                    it.seekTo(newPos)
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(
                            onClick = togglePlayPause,
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        IconButton(
                            onClick = {
                                player?.let {
                                    val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                    it.seekTo(newPos)
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Fast Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // 3. Footer Control Bar (Progress, Track Picker, Speed Picker, Custom subtitles)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        // Slider Progress (Visible mostly for VOD, if duration > 0)
                        if (duration > 0 && duration != C.TIME_UNSET) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )

                                Slider(
                                    value = currentPosition.toFloat(),
                                    onValueChange = { newValue ->
                                        currentPosition = newValue.toLong()
                                        player?.seekTo(currentPosition)
                                    },
                                    valueRange = 0f..duration.toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )

                                Text(
                                    text = formatTime(duration),
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Toolbar functions button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Subtitles trigger
                            Button(
                                onClick = { showSubtitleDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("subtitle_button")
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedSubtitleUrl == null) "Subtitles: Off" else "Subtitles: On",
                                    fontSize = 12.sp
                                )
                            }

                            // Track selection button (quality)
                            Button(
                                onClick = { showTrackSelectionDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("track_selection_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Quality", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$currentTrackOverrideLabel",
                                    fontSize = 12.sp
                                )
                            }

                            // Playback speed picker button
                            Button(
                                onClick = {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.5f -> 0.75f
                                        0.75f -> 1.0f
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 0.5f
                                    }
                                    player?.setPlaybackSpeed(playbackSpeed)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("speed_picker_button")
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "Play Speed", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${playbackSpeed}x", fontSize = 12.sp)
                            }

                            // Live button
                            Button(
                                onClick = { player?.seekToDefaultPosition() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red.copy(alpha = 0.8f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("live_button")
                            ) {
                                Icon(Icons.Default.Adjust, contentDescription = "Live", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Live", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            // Fullscreen toggle
                            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White
                                )
                            }
                            
                            // Cast Button
                            CastButton(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    // 1. Custom Subtitle Picker Dialog
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Custom Subtitle", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Configure subtitles presets or paste custom link (.srt / .vtt supported):",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Presets
                    subtitlePresets.forEach { (name, url) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSubtitleUrl = url
                                    showSubtitleDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubtitleUrl == url,
                                onClick = {
                                    selectedSubtitleUrl = url
                                    showSubtitleDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontSize = 15.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Dialog Custom Input Text
                    TextField(
                        value = customSubtitleInput,
                        onValueChange = { customSubtitleInput = it },
                        label = { Text("Custom Subtitle URL") },
                        placeholder = { Text("https://example.com/sub.srt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customSubtitleInput.isNotBlank()) {
                            selectedSubtitleUrl = customSubtitleInput
                        }
                        showSubtitleDialog = false
                    }
                ) {
                    Text("Load URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Track Selection Dialog (Adaptive Bitrate Quality)
    if (showTrackSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showTrackSelectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Quality / Stream Track", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (availableVideoTracks.isEmpty()) {
                        Text(
                            "This HLS stream does not advertise multiple video qualities, or loading tracks details...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            // Option 1: Automatic Adaptive Quality
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player?.let {
                                                // Clear overrides to enable ExoPlayer adaptive bitrate resolution
                                                val params = it.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverrides()
                                                    .build()
                                                it.trackSelectionParameters = params
                                            }
                                            currentTrackOverrideLabel = "Auto"
                                            showTrackSelectionDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentTrackOverrideLabel == "Auto",
                                        onClick = {
                                            player?.let {
                                                val params = it.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverrides()
                                                    .build()
                                                it.trackSelectionParameters = params
                                            }
                                            currentTrackOverrideLabel = "Auto"
                                            showTrackSelectionDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto (Adaptive Quality)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Option 2: Hard limits quality overrides
                            items(availableVideoTracks) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player?.let {
                                                val params = it.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(
                                                            option.group.mediaTrackGroup,
                                                            option.trackIndex
                                                        )
                                                    )
                                                    .build()
                                                it.trackSelectionParameters = params
                                            }
                                            currentTrackOverrideLabel = option.label
                                            showTrackSelectionDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = option.isSelected && currentTrackOverrideLabel != "Auto",
                                        onClick = {
                                            player?.let {
                                                val params = it.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(
                                                        TrackSelectionOverride(
                                                            option.group.mediaTrackGroup,
                                                            option.trackIndex
                                                        )
                                                    )
                                                    .build()
                                                it.trackSelectionParameters = params
                                            }
                                            currentTrackOverrideLabel = option.label
                                            showTrackSelectionDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(option.label, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTrackSelectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// Track selection details data structure
@UnstableApi
data class VideoTrackOption(
    val group: androidx.media3.common.Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

// Timer format helper
private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSecs = millis / 1000
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}
