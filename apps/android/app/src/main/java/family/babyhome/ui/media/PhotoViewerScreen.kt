package family.babyhome.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PhotoViewerScreen(onBack: () -> Unit, viewModel: MediaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var zoom by remember(state.previewUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(state.previewUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(state.previewUrl) { mutableFloatStateOf(0f) }
    val imageModifier = Modifier.fillMaxSize().pointerInput(state.previewUrl) {
        detectTransformGestures { _, pan, scale, _ ->
            zoom = (zoom * scale).coerceIn(1f, 4f)
            offsetX = (offsetX + pan.x).coerceIn(-size.width * (zoom-1)/2, size.width * (zoom-1)/2)
            offsetY = (offsetY + pan.y).coerceIn(-size.height * (zoom-1)/2, size.height * (zoom-1)/2)
        }
    }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offsetX; translationY = offsetY }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.thumbnailUrl == null || state.previewUrl == null || state.accessToken == null) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            val thumbnailRequest = ImageRequest.Builder(context)
                .data(state.thumbnailUrl)
                .addHeader("Authorization", "Bearer ${state.accessToken}")
                .build()
            val request = ImageRequest.Builder(context)
                .data(state.previewUrl)
                .addHeader("Authorization", "Bearer ${state.accessToken}")
                .crossfade(true)
                .build()
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
            )
            SubcomposeAsyncImage(
                model = request,
                contentDescription = "全屏照片",
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
                loading = {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.Center).size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize()) {
                        Text("无法加载大图，请返回后重试", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                },
            )
        }
        Button(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Text("返回")
        }
    }
}
