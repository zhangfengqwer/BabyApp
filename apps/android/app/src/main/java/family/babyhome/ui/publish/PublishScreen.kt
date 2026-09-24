package family.babyhome.ui.publish

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import family.babyhome.domain.publish.PublishDatePlanner
import java.time.LocalDate
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.app.Activity
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(onPublished: (String) -> Unit, onBack: () -> Unit = {}, birthday: String? = null, viewModel: PublishViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locked = state.publishing || state.completedDates.isNotEmpty()
    val groups = state.items.groupBy { PublishDatePlanner.date(it.media.capturedAt, state.eventDate, state.automaticDates) }.toSortedMap(reverseOrder())
    var choosingMedia by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        viewModel.addUris(uris)
        choosingMedia = false
    }
    var deletionIssue by remember { mutableStateOf<String?>(null) }
    var cleanupLaunched by remember { mutableStateOf(false) }
    var unsupportedDeletionCount by remember { mutableIntStateOf(0) }
    var requestedDeleteUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val remaining = withContext(Dispatchers.IO) { requestedDeleteUris.count { LocalMediaDeletion.exists(context, it) } }
                deletionIssue = when {
                    remaining > 0 -> "系统确认已完成，但仍有 $remaining 个本地文件未删除。可在“我的”中重试清理。"
                    unsupportedDeletionCount > 0 -> "$unsupportedDeletionCount 个系统相册文件只允许读取，请在相册中手动删除。"
                    else -> null
                }
                if (deletionIssue == null) onPublished(state.publishedDate ?: state.eventDate)
            }
        }
        else deletionIssue = "发布已经成功，但本地删除已取消。可在“我的”中重试清理。"
    }
    val mediaPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    var permissionChecked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionChecked = true }
    LaunchedEffect(choosingMedia) {
        if (choosingMedia && !permissionChecked) {
            if (mediaPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) permissionChecked = true
            else permissionLauncher.launch(mediaPermissions)
        }
    }
    LaunchedEffect(state.published) {
        if (state.published && !cleanupLaunched) {
            cleanupLaunched = true
            PublishedMediaHistory.mark(context, state.items.map { it.media.uri })
            val localUris = state.items.mapNotNull { item -> LocalMediaDeletion.itemUri(context, item.media.uri, item.media.mimeType) }.distinct()
            val unsupported = state.items.size - localUris.size
            unsupportedDeletionCount = unsupported
            if (localUris.isEmpty()) {
                if (unsupported > 0) deletionIssue = "发布已经成功。系统相册返回的文件只允许读取，请在相册中手动删除本地原件。"
                else onPublished(state.publishedDate ?: state.eventDate)
            } else if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    requestedDeleteUris = localUris
                    val request = MediaStore.createDeleteRequest(context.contentResolver, localUris)
                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure { error ->
                    Log.w("PublishScreen", "Unable to request local media deletion", error)
                    deletionIssue = "发布已经成功，但无法请求删除本地文件。可在“我的”中重试清理。"
                }
            } else {
                deletionIssue = "发布已经成功。此 Android 版本需要在相册中手动删除本地文件。"
            }
        }
    }
    deletionIssue?.let { message ->
        AlertDialog(onDismissRequest = {}, title = { Text("本地文件未删除") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { onPublished(state.publishedDate ?: state.eventDate) }) { Text("知道了") } })
    }
    if (choosingMedia && permissionChecked) {
        LocalMediaPicker(
            birthday = birthday,
            onDone = { uris -> viewModel.addUris(uris); choosingMedia = false },
            onSkip = { choosingMedia = false },
            onSystemPicker = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        )
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("留下这一刻") }, navigationIcon = { TextButton(onClick = onBack, enabled = !state.publishing) { Text("返回") } }) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
                    Text(if (groups.size > 1) "将按拍摄日期发布 ${groups.size} 条时光轴" else "照片和视频珍藏在自己的家庭服务器", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.publish() }, enabled = !state.publishing && !state.readingMedia,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (state.publishing) "正在发布…" else if (state.completedDates.isNotEmpty()) "继续发布剩余日期" else "发布到时光轴")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("把每一天，留给长大的你", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("不同日期自动分开归档，同一天追加到已有记录。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("按拍摄日期归档", fontWeight = FontWeight.SemiBold); Text("关闭后全部使用手选日期", style = MaterialTheme.typography.bodyMedium) }
                    Switch(state.automaticDates, viewModel::automaticDates, enabled = !locked)
                }
                OutlinedButton(onClick = {
                    val date = runCatching { LocalDate.parse(state.eventDate) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, y, m, d -> viewModel.eventDate(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }, enabled = !locked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${if (state.automaticDates) "无拍摄时间时使用" else "归档日期"}  ${state.eventDate}")
                }
            }
            item {
                OutlinedButton(onClick = { choosingMedia = true }, enabled = !locked && !state.readingMedia,
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text(if (state.readingMedia) "正在读取拍摄时间…" else "＋  选择照片或视频") }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("记事与地点 · 可选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(state.content, viewModel::content, enabled = !locked, placeholder = { Text("想记住的小事") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(state.location, viewModel::location, enabled = !locked, placeholder = { Text("地点") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    if (groups.size > 1) Text("文字和地点用于每个日期，可发布后分别编辑。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            groups.forEach { (date, media) ->
                item(key = date) {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$date  ·  ${media.size} 个媒体${if (date in state.completedDates) "  ·  已发布" else ""}", fontWeight = FontWeight.SemiBold)
                            media.forEach { item ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
                                        SubcomposeAsyncImage(item.media.uri, item.media.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                            loading = { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } },
                                            error = { Box(contentAlignment = Alignment.Center) { Text("预览不可用", style = MaterialTheme.typography.labelSmall) } })
                                        if (item.media.mimeType.startsWith("video/")) Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)) { Text("▶", Modifier.padding(4.dp)) }
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(if (item.media.mimeType.startsWith("video/")) "视频" else "照片", fontWeight = FontWeight.Medium)
                                        Text(when(item.status) { UploadStatus.WAITING -> if (item.media.capturedAt == null) "未读到拍摄时间，使用手选日期" else "拍摄时间已识别"; UploadStatus.UPLOADING -> "上传中 ${(item.progress * 100).toInt()}%"; UploadStatus.UPLOADED -> "上传完成"; UploadStatus.FAILED -> "上传失败，可重试" }, style = MaterialTheme.typography.bodyMedium)
                                        if (item.status == UploadStatus.UPLOADING) LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                                    }
                                    if (!locked) TextButton(onClick = { viewModel.remove(item.media.uri) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("移除") }
                                }
                            }
                        }
                    }
                }
            }
            state.message?.let { message -> item { Text(message, color = if (message.contains("失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}
