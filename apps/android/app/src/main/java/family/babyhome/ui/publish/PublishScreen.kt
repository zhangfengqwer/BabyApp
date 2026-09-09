package family.babyhome.ui.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@Composable
fun PublishScreen(onPublished: (String) -> Unit, viewModel: PublishViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Android Photo Picker 本身会依据系统版本限制一次可选数量；100 足够覆盖
    // 常见的一日相册导入，也不再人为限制为 10 个。
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(100), viewModel::addUris)
    LaunchedEffect(state.published) { if (state.published) onPublished(state.eventDate) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("发布成长动态", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.eventDate,
                onValueChange = viewModel::eventDate,
                enabled = !state.publishing,
                label = { Text("事件日期（YYYY-MM-DD）") },
                supportingText = { Text("按这一天计算宝宝年龄，老照片请填写当时日期") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.content,
                onValueChange = viewModel::content,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("记录这一刻") },
                minLines = 4,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::location,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("地点（可选）") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                enabled = !state.publishing,
            ) { Text("选择照片或视频") }
        }
        items(state.items, key = { it.media.uri.toString() }) { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(model = item.media.uri, contentDescription = item.media.fileName, modifier = Modifier.height(64.dp).weight(0.25f))
                Column(Modifier.weight(0.75f)) {
                    Text(item.media.fileName, maxLines = 1)
                    Text(
                        when (item.status) {
                            UploadStatus.WAITING -> "等待上传"
                            UploadStatus.UPLOADING -> "上传中 ${(item.progress * 100).toInt()}%"
                            UploadStatus.UPLOADED -> "上传完成"
                            UploadStatus.FAILED -> "上传失败"
                        },
                        color = if (item.status == UploadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.status == UploadStatus.UPLOADING) LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    if (!state.publishing) OutlinedButton(onClick = { viewModel.remove(item.media.uri) }) { Text("移除") }
                }
            }
        }
        item {
            state.message?.let { Text(it, color = if (it.contains("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::publish, enabled = !state.publishing, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.publishing) "正在发布…" else if (state.items.any { it.status == UploadStatus.FAILED }) "重试失败项" else "发布")
            }
        }
    }
}
