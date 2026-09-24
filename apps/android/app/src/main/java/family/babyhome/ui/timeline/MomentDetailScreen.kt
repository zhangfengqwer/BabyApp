package family.babyhome.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import family.babyhome.data.network.MomentAssetDto
import family.babyhome.domain.baby.BabyAgeCalculator
import java.time.LocalDate

@Composable
fun MomentDetailScreen(
    momentId: String,
    state: TimelineUiState,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onAsset: (Int) -> Unit,
    onLoadMore: () -> Unit,
    viewModel: MomentDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.state.collectAsStateWithLifecycle()
    val moment = detail.moment
    var commentText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var choosingCover by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var confirmingSelectionDelete by remember { mutableStateOf(false) }
    val selectedAssetIds = remember { mutableStateListOf<String>() }
    var text by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    BackHandler { if (selecting) { selecting = false; selectedAssetIds.clear() } else onBack() }
    LaunchedEffect(detail.deleted) { if (detail.deleted) onDeleted() }
    LaunchedEffect(moment?.assets) {
        if (selecting && selectedAssetIds.isNotEmpty() && moment != null && selectedAssetIds.none { id -> moment.assets.any { it.id == id } }) {
            selecting = false
            selectedAssetIds.clear()
        }
    }
    if (confirmingSelectionDelete) {
        AlertDialog(onDismissRequest = { confirmingSelectionDelete = false }, title = { Text("删除选中的 ${selectedAssetIds.size} 个媒体？") },
            text = { Text("将从这条记录移除所选照片、视频，并把服务器原件移入 Immich 回收站。若原件还被其他记录或头像使用，删除会被阻止。") },
            confirmButton = { TextButton(onClick = { viewModel.removeAssets(selectedAssetIds.toList()); confirmingSelectionDelete = false }, enabled = !detail.busy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmingSelectionDelete = false }) { Text("取消") } })
    }
    if(editing) AlertDialog(onDismissRequest={editing=false},title={Text("编辑记录")},text={
        Column { OutlinedTextField(text,{text=it},label={Text("文字")}); OutlinedTextField(location,{location=it},label={Text("地点")}) }
    },confirmButton={TextButton(onClick={viewModel.edit(text,location);editing=false}){Text("保存")}},
        dismissButton={TextButton(onClick={editing=false}){Text("取消")}})
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("删除这条记录？")},
        text={Text("会删除这条记录及留言，并把服务器原件移入 Immich 回收站。被其他地方引用的原件会阻止删除。")},
        confirmButton={TextButton(onClick={viewModel.delete();deleting=false}){Text("删除记录")}},
        dismissButton={TextButton(onClick={deleting=false}){Text("取消")}})
    if(choosingCover && moment != null) AlertDialog(onDismissRequest={choosingCover=false},title={Text("选择封面")},
        text={Column { moment.assets.forEachIndexed { index,a ->
            TextButton(onClick={viewModel.cover(a.immichAssetId);choosingCover=false}) { Text("第 ${index+1} 个媒体") }
        }}},confirmButton={TextButton(onClick={choosingCover=false}){Text("取消")}})
    Column(Modifier.fillMaxSize()) {
        if (selecting && moment != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    if (selectedAssetIds.size == moment.assets.size) selectedAssetIds.clear()
                    else { selectedAssetIds.clear(); selectedAssetIds.addAll(moment.assets.map { it.id }) }
                }, enabled = !detail.busy) { Text(if (selectedAssetIds.size == moment.assets.size) "取消全选" else "全选") }
                Text("选中了${selectedAssetIds.size}项", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { selecting = false; selectedAssetIds.clear() }) { Text("关闭") }
            }
        } else TextButton(onClick = onBack) { Text("‹ 返回") }
        detail.error?.let { Text(it, Modifier.padding(16.dp), color=MaterialTheme.colorScheme.error) }
        if(detail.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (moment == null) {
            Text("正在查找这条成长记录", Modifier.padding(24.dp))
            if (state.loading || state.loadingMore) CircularProgressIndicator()
            else Button(onClick = viewModel::reload) { Text("重新加载") }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (!selecting) item {
                moment.assets.firstOrNull()?.let { cover ->
                    DetailMediaTile(cover, moment.canEdit && !detail.busy, false, false,
                        onOpen = { onAsset(0) },
                        onLongPress = { selecting = true; selectedAssetIds.clear(); selectedAssetIds.add(cover.id) },
                        modifier = Modifier.fillMaxWidth().height(260.dp))
                }
                Column(Modifier.padding(20.dp)) {
                    Text(state.baby?.let { BabyAgeCalculator.format(LocalDate.parse(it.birthday.take(10)), moment.albumDate()) }.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(moment.albumDate().toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            moment.assets.chunked(3).forEachIndexed { rowIndex, row ->
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { columnIndex, asset ->
                            DetailMediaTile(asset, moment.canEdit && !detail.busy, selecting, asset.id in selectedAssetIds,
                                onOpen = {
                                    if (selecting) {
                                        if (asset.id in selectedAssetIds) selectedAssetIds.remove(asset.id) else selectedAssetIds.add(asset.id)
                                    } else onAsset(rowIndex * 3 + columnIndex)
                                },
                                onLongPress = { selecting = true; if (asset.id !in selectedAssetIds) selectedAssetIds.add(asset.id) },
                                modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                        repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!moment.content.isNullOrBlank()) Text(moment.content, style = MaterialTheme.typography.bodyLarge)
                    moment.location?.takeIf { it.isNotBlank() }?.let { Text("地点 · $it") }
                    if (selecting) return@Column
                    Text("记录人 · ${moment.author.nickname}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row {
                        TextButton(onClick=viewModel::like,enabled=!detail.busy) { Text("${if(moment.likedByMe) "♥" else "♡"} ${moment._count.likes}") }
                        Text("留言 ${moment._count.comments}",Modifier.padding(12.dp))
                    }
                    if(moment.canEdit) Row {
                        TextButton(onClick={text=moment.content.orEmpty();location=moment.location.orEmpty();editing=true},enabled=!detail.busy){Text("编辑")}
                        TextButton(onClick={choosingCover=true},enabled=!detail.busy && moment.assets.isNotEmpty()){Text("设置封面")}
                        TextButton(onClick={deleting=true},enabled=!detail.busy){Text("删除")}
                    }
                    detail.comments.forEach { c -> Column {
                        Text(c.user.nickname,style=MaterialTheme.typography.labelLarge)
                        Text(c.content)
                    }}
                    if(detail.cursor != null) TextButton(onClick=viewModel::moreComments,enabled=!detail.busy){Text("更早留言")}
                    OutlinedTextField(commentText,{commentText=it},label={Text("写评论…")},modifier=Modifier.fillMaxWidth())
                    Button(onClick={viewModel.comment(commentText);commentText=""},enabled=!detail.busy && commentText.isNotBlank()){Text("发送")}
                }
            }
        }
        if (selecting) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已选 ${selectedAssetIds.size} 项")
                Button(onClick = { confirmingSelectionDelete = true }, enabled = selectedAssetIds.isNotEmpty() && !detail.busy) { Text("删除所选") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailMediaTile(
    asset: MomentAssetDto, canEdit: Boolean, selecting: Boolean, selected: Boolean,
    onOpen: () -> Unit, onLongPress: () -> Unit, modifier: Modifier,
) {
    Card(modifier = modifier.combinedClickable(
        onClick = onOpen,
        onLongClick = if (canEdit) onLongPress else null,
        onLongClickLabel = if (canEdit) "选择这个媒体" else null,
    ), shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(asset.thumbnailUrl, if (asset.assetType == "VIDEO") "查看视频" else "查看照片",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (asset.assetType == "VIDEO") Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f)) {
                Text("▶ 视频", Modifier.padding(4.dp), style = MaterialTheme.typography.labelMedium)
            }
            if (selecting) {
                Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp), color = if (selected) Color(0xFF31BD72) else Color(0x55000000), shape = CircleShape, border = BorderStroke(2.dp, Color.White)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
