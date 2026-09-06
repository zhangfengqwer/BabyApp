package family.babyhome.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import family.babyhome.domain.baby.BabyAgeCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import coil.compose.AsyncImage

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPublish: () -> Unit,
    onAsset: (assetId: String, assetType: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(state.error!!)
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::refresh) { Text("重试") }
        }
        else -> state.data?.let { home ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(home.baby.nickname ?: home.baby.name, style = MaterialTheme.typography.headlineMedium)
                            Text(BabyAgeCalculator.format(LocalDate.parse(home.baby.birthday.take(10)), LocalDate.now()))
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("今天 · yyyy年M月d日")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Button(onClick = onPublish) { Text("＋ 发布") }
                            Button(onClick = onSettings) { Text("服务器") }
                        }
                    }
                }
                items(home.moments, key = { it.id }) { moment ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(moment.author.nickname, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Instant.parse(moment.eventDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(moment.content.orEmpty())
                            moment.assets.firstOrNull()?.let { asset ->
                                Spacer(Modifier.height(12.dp))
                                AsyncImage(
                                    model = asset.thumbnailUrl,
                                    contentDescription = if (asset.assetType == "VIDEO") "视频缩略图" else "成长照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clickable { onAsset(asset.immichAssetId, asset.assetType) },
                                )
                                if (asset.assetType == "VIDEO") Text("▶ 视频", style = MaterialTheme.typography.labelLarge)
                                if (moment.assets.size > 1) Text("共 ${moment.assets.size} 个媒体文件")
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("❤️ ${moment._count.likes}    💬 ${moment._count.comments}")
                        }
                    }
                }
            }
        }
    }
}
