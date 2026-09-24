package family.babyhome.ui.publish

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import family.babyhome.domain.baby.BabyAgeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class DeviceMedia(val uri: Uri, val video: Boolean, val date: LocalDate, val album: String, val albumId: String, val durationMs: Long)
private sealed interface PickerRow {
    data class Header(val date: LocalDate, val media: List<DeviceMedia>) : PickerRow
    data class Tile(val media: DeviceMedia) : PickerRow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMediaPicker(birthday: String?, onDone: (List<Uri>) -> Unit, onSkip: () -> Unit, onSystemPicker: () -> Unit) {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<DeviceMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var albumMenu by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf<String?>(null) }
    var unuploaded by remember { mutableStateOf(false) }
    val publishedUris = remember { PublishedMediaHistory.read(context) }
    val selected = remember { mutableStateListOf<Uri>() }
    val gridState = rememberLazyGridState()
    val birthdayDate = remember(birthday) { runCatching { LocalDate.parse(birthday?.take(10)) }.getOrNull() }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                val result = mutableListOf<DeviceMedia>()
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf("_id", "media_type", "datetaken", "date_added", "bucket_display_name", "bucket_id") +
                    if (Build.VERSION.SDK_INT >= 29) arrayOf("duration") else emptyArray()
                val selection = "media_type IN (?, ?)"
                val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                context.contentResolver.query(collection, projection, selection, args, "date_added DESC")?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow("_id")
                    val type = cursor.getColumnIndexOrThrow("media_type")
                    val taken = cursor.getColumnIndexOrThrow("datetaken")
                    val added = cursor.getColumnIndexOrThrow("date_added")
                    val bucket = cursor.getColumnIndexOrThrow("bucket_display_name")
                    val bucketId = cursor.getColumnIndexOrThrow("bucket_id")
                    val duration = cursor.getColumnIndex("duration")
                    while (cursor.moveToNext()) {
                        val timestamp = if (!cursor.isNull(taken) && cursor.getLong(taken) > 0) cursor.getLong(taken) else cursor.getLong(added) * 1000
                        val video = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val mediaCollection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        result += DeviceMedia(
                            uri = ContentUris.withAppendedId(mediaCollection, cursor.getLong(id)),
                            video = video,
                            date = Instant.ofEpochMilli(if (timestamp > 0) timestamp else System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate(),
                            album = if (cursor.isNull(bucket)) "其他" else cursor.getString(bucket).orEmpty().ifBlank { "其他" },
                            albumId = if (cursor.isNull(bucketId)) "other" else cursor.getString(bucketId).orEmpty(),
                            durationMs = if (duration < 0 || cursor.isNull(duration)) 0 else cursor.getLong(duration),
                        )
                    }
                }
                result
            }
        }.onSuccess { media = it }.onFailure { failed = true }
        loading = false
    }

    val folders = remember(media) { media.groupBy(DeviceMedia::albumId).toList().sortedByDescending { it.second.size } }
    val albumName = folders.firstOrNull { it.first == album }?.second?.firstOrNull()?.album
    val visible = remember(media, album, unuploaded, publishedUris) {
        media.filter { item ->
            val oldFilesUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), ContentUris.parseId(item.uri)).toString()
            (album == null || item.albumId == album) &&
                (!unuploaded || (item.uri.toString() !in publishedUris && oldFilesUri !in publishedUris))
        }
    }
    val rows = remember(visible) {
        visible.groupBy(DeviceMedia::date).toSortedMap(reverseOrder()).flatMap { (date, items) ->
            listOf<PickerRow>(PickerRow.Header(date, items)) + items.map(PickerRow::Tile)
        }
    }
    val selectedMedia = media.filter { it.uri in selected }
    LaunchedEffect(album, unuploaded) { gridState.scrollToItem(0) }

    fun toggle(uri: Uri) {
        if (uri in selected) selected.remove(uri) else if (selected.size < 100) selected.add(uri)
    }
    fun toggleGroup(items: List<DeviceMedia>) {
        if (items.all { it.uri in selected }) items.forEach { selected.remove(it.uri) }
        else items.forEach { if (it.uri !in selected && selected.size < 100) selected.add(it.uri) }
    }
    fun indexAt(offset: Offset): Int? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
        offset.x >= item.offset.x && offset.x < item.offset.x + item.size.width &&
            offset.y >= item.offset.y && offset.y < item.offset.y + item.size.height
    }?.index?.takeIf { rows.getOrNull(it) is PickerRow.Tile }
    var dragStart by remember { mutableIntStateOf(-1) }
    var dragSelecting by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Box {
                        TextButton(onClick = { albumMenu = true }) { Text("☰  ${(albumName ?: "相册").take(6)}") }
                        DropdownMenu(expanded = albumMenu, onDismissRequest = { albumMenu = false }) {
                            DropdownMenuItem(text = { Text("所有文件夹（${media.size}）") }, onClick = { album = null; albumMenu = false })
                            folders.forEach { (id, files) ->
                                DropdownMenuItem(text = { Text("${files.first().album}（${files.size}）") },
                                    leadingIcon = { AsyncImage(files.first().uri, null, Modifier.size(36.dp), contentScale = ContentScale.Crop) },
                                    onClick = { album = id; albumMenu = false })
                            }
                            DropdownMenuItem(text = { Text("使用系统相册") }, onClick = { albumMenu = false; onSystemPicker() })
                        }
                    }
                },
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !unuploaded, onClick = { unuploaded = false }, label = { Text("全部") })
                        FilterChip(selected = unuploaded, onClick = { unuploaded = true }, label = { Text("未上传") })
                    }
                },
                actions = { TextButton(onClick = onSkip) { Text("关闭") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${selected.size}（照片${selectedMedia.count { !it.video }} + 视频${selectedMedia.count { it.video }}）", fontWeight = FontWeight.SemiBold)
                        Text(if (unuploaded) "未上传仅按本机发布记录筛选" else "长按并滑动连续选择 · 最多100项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onDone(selected.toList()) }, enabled = selected.isNotEmpty(), shape = RoundedCornerShape(22.dp)) { Text("下一步") }
                }
            }
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            failed || visible.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (failed) "无法读取手机相册" else if (unuploaded) "没有本机记录为未上传的媒体" else "这个相册中没有照片或视频")
                TextButton(onClick = onSystemPicker) { Text("使用系统相册选择") }
                if (selected.isNotEmpty()) TextButton(onClick = { onDone(selected.toList()) }) { Text("继续处理已选的 ${selected.size} 项") }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(rows) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragStart = indexAt(offset) ?: -1
                            if (dragStart >= 0) {
                                val uri = (rows[dragStart] as PickerRow.Tile).media.uri
                                dragSelecting = uri !in selected
                                if (dragSelecting && selected.size < 100) selected.add(uri)
                                if (!dragSelecting) selected.remove(uri)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val end = indexAt(change.position)
                            if (dragStart >= 0 && end != null) {
                                for (index in minOf(dragStart, end)..maxOf(dragStart, end)) {
                                    val uri = (rows[index] as? PickerRow.Tile)?.media?.uri ?: continue
                                    if (dragSelecting && uri !in selected && selected.size < 100) selected.add(uri)
                                    if (!dragSelecting) selected.remove(uri)
                                }
                            }
                        },
                        onDragEnd = { dragStart = -1 },
                        onDragCancel = { dragStart = -1 },
                    )
                },
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                items(rows.size, key = { index -> when (val row = rows[index]) {
                    is PickerRow.Header -> "header-${row.date}"
                    is PickerRow.Tile -> row.media.uri.toString()
                } }, span = { index -> GridItemSpan(if (rows[index] is PickerRow.Header) maxLineSpan else 1) }) { index ->
                    when (val row = rows[index]) {
                        is PickerRow.Header -> {
                            val allSelected = row.media.all { it.uri in selected }
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val dateLabel = row.date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                                Text("$dateLabel${birthdayDate?.let { " · ${BabyAgeCalculator.format(it, row.date)}" }.orEmpty()}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { toggleGroup(row.media) }) { Text(if (allSelected) "取消全选" else "全选") }
                            }
                        }
                        is PickerRow.Tile -> {
                            val item = row.media
                            val checked = item.uri in selected
                            Box(Modifier.aspectRatio(1f).padding(2.dp).clickable { toggle(item.uri) }) {
                                AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (item.video) Text("▶  ${formatDuration(item.durationMs)}", Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .56f)).padding(4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                Surface(Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp), shape = CircleShape, color = if (checked) Color(0xFF31BD72) else Color.Black.copy(alpha = .25f), border = BorderStroke(2.dp, Color.White)) {
                                    Box(contentAlignment = Alignment.Center) { if (checked) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
