package family.babyhome.ui.media

import android.app.Activity
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun MediaGalleryScreen(viewModel: MediaGalleryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { state.items.size })
    val zoomLevels = remember { mutableStateMapOf<String, Float>() }
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(state.items, state.initialIndex) {
        if (state.items.isNotEmpty()) pagerState.scrollToPage(state.initialIndex)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> Text(
                "正在打开照片…",
                color = Color.White.copy(alpha = .72f),
                modifier = Modifier.align(Alignment.Center),
            )
            state.error != null -> {
                Text(state.error!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                Button(onClick = viewModel::load, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                    Text("重新加载")
                }
            }
            state.accessToken != null -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = state.items.getOrNull(pagerState.currentPage)?.let {
                    (zoomLevels[it.assetId] ?: 1f) <= 1.01f
                } ?: true,
            ) { page ->
                val item = state.items[page]
                if (item.assetType == "VIDEO") {
                    GalleryVideo(
                        item = item,
                        accessToken = state.accessToken!!,
                        active = page == pagerState.currentPage,
                    )
                } else {
                    GalleryPhoto(
                        item = item,
                        accessToken = state.accessToken!!,
                        onZoomChanged = { zoomLevels[item.assetId] = it },
                    )
                }
            }
        }

        if (state.items.isNotEmpty()) {
            Text(
                "${pagerState.currentPage + 1} / ${state.items.size}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp)
                    .background(Color.Black.copy(alpha = .45f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun GalleryPhoto(
    item: GalleryMediaItem,
    accessToken: String,
    onZoomChanged: (Float) -> Unit,
) {
    val context = LocalContext.current
    var zoom by remember(item.assetId) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.assetId) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.assetId) { mutableFloatStateOf(0f) }
    val imageTransform = Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom
        scaleY = zoom
        translationX = offsetX
        translationY = offsetY
    }
    val gestures = Modifier.fillMaxSize().pointerInput(item.assetId) {
        awaitEachGesture {
            var event = awaitPointerEvent()
            while (event.changes.any { it.pressed }) {
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2 || zoom > 1.01f) {
                    val nextZoom = (zoom * if (pressedCount >= 2) event.calculateZoom() else 1f).coerceIn(1f, 4f)
                    val pan = event.calculatePan()
                    val maxX = size.width * (nextZoom - 1f) / 2f
                    val maxY = size.height * (nextZoom - 1f) / 2f
                    zoom = nextZoom
                    offsetX = if (nextZoom <= 1.01f) 0f else (offsetX + pan.x).coerceIn(-maxX, maxX)
                    offsetY = if (nextZoom <= 1.01f) 0f else (offsetY + pan.y).coerceIn(-maxY, maxY)
                    onZoomChanged(zoom)
                    event.changes.forEach { it.consume() }
                }
                event = awaitPointerEvent()
            }
        }
    }
    val thumbnail = remember(item.thumbnailUrl, accessToken) {
        ImageRequest.Builder(context).data(item.thumbnailUrl)
            .addHeader("Authorization", "Bearer $accessToken").build()
    }
    val preview = remember(item.previewUrl, accessToken) {
        ImageRequest.Builder(context).data(item.previewUrl)
            .addHeader("Authorization", "Bearer $accessToken").crossfade(true).build()
    }

    Box(gestures.background(Color.Black)) {
        // Keep a neutral gallery background. ContentScale.Fit preserves every
        // part of the photo; only unavoidable unused space stays black.
        AsyncImage(
            thumbnail,
            null,
            Modifier.align(Alignment.Center).then(imageTransform),
            contentScale = ContentScale.Fit,
        )
        SubcomposeAsyncImage(
            model = preview,
            contentDescription = "全屏照片",
            modifier = Modifier.align(Alignment.Center).then(imageTransform),
            contentScale = ContentScale.Fit,
            // The thumbnail is already visible underneath. Keeping this slot
            // empty avoids covering the photo with a loading indicator.
            loading = {},
            error = { Text("无法加载大图", color = Color.White, modifier = Modifier.align(Alignment.Center)) },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun GalleryVideo(item: GalleryMediaItem, accessToken: String, active: Boolean) {
    val context = LocalContext.current
    val player = remember(item.videoUrl, accessToken, active) {
        if (!active) null else {
            val dataSource = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                mapOf("Authorization" to "Bearer $accessToken"),
            )
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(item.videoUrl))
                    prepare()
                    playWhenReady = true
                }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    Box(Modifier.fillMaxSize()) {
        if (player == null) GalleryLoadingIndicator(Modifier.align(Alignment.Center))
        else AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GalleryLoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(24.dp),
        color = Color.White,
        strokeWidth = 2.dp,
    )
}
