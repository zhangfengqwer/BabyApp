package family.babyhome.ui.timeline

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    var removingAsset by remember { mutableStateOf<MomentAssetDto?>(null) }
    var deleteVisibleId by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    BackHandler { if (deleteVisibleId != null) deleteVisibleId = null else onBack() }
    LaunchedEffect(detail.deleted) { if (detail.deleted) onDeleted() }
    removingAsset?.let { asset ->
        AlertDialog(onDismissRequest = { removingAsset = null }, title = { Text("删除这个${if (asset.assetType == "VIDEO") "视频" else "照片"}？") },
            text = { Text("只从这一天的时光轴移除，其他照片、视频和留言不受影响，Immich 原文件仍保留。") },
            confirmButton = { TextButton(onClick = { viewModel.removeAsset(asset.id); removingAsset = null; deleteVisibleId = null }, enabled = !detail.busy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { removingAsset = null }) { Text("取消") } })
    }
    if(editing) AlertDialog(onDismissRequest={editing=false},title={Text("编辑记录")},text={
        Column { OutlinedTextField(text,{text=it},label={Text("文字")}); OutlinedTextField(location,{location=it},label={Text("地点")}) }
    },confirmButton={TextButton(onClick={viewModel.edit(text,location);editing=false}){Text("保存")}},
        dismissButton={TextButton(onClick={editing=false}){Text("取消")}})
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("删除这条记录？")},
        text={Text("会删除动态及其留言，Immich 中的原始照片和视频仍保留。")},
        confirmButton={TextButton(onClick={viewModel.delete();deleting=false}){Text("删除记录")}},
        dismissButton={TextButton(onClick={deleting=false}){Text("取消")}})
    if(choosingCover && moment != null) AlertDialog(onDismissRequest={choosingCover=false},title={Text("选择封面")},
        text={Column { moment.assets.forEachIndexed { index,a ->
            TextButton(onClick={viewModel.cover(a.immichAssetId);choosingCover=false}) { Text("第 ${index+1} 个媒体") }
        }}},confirmButton={TextButton(onClick={choosingCover=false}){Text("取消")}})
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        detail.error?.let { Text(it, Modifier.padding(16.dp), color=MaterialTheme.colorScheme.error) }
        if(detail.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (moment == null) {
            Text("正在查找这条成长记录", Modifier.padding(24.dp))
            if (state.loading || state.loadingMore) CircularProgressIndicator()
            else Button(onClick = viewModel::reload) { Text("重新加载") }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                moment.assets.firstOrNull()?.let { cover ->
                    DetailMediaTile(cover, moment.canEdit && !detail.busy, deleteVisibleId == cover.id,
                        onOpen = { deleteVisibleId = null; onAsset(0) },
                        onLongPress = { deleteVisibleId = cover.id }, onDelete = { removingAsset = cover },
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
                            DetailMediaTile(asset, moment.canEdit && !detail.busy, deleteVisibleId == asset.id,
                                onOpen = { deleteVisibleId = null; onAsset(rowIndex * 3 + columnIndex) },
                                onLongPress = { deleteVisibleId = asset.id }, onDelete = { removingAsset = asset },
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailMediaTile(
    asset: MomentAssetDto, canDelete: Boolean, showDelete: Boolean,
    onOpen: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit, modifier: Modifier,
) {
    Card(modifier = modifier.combinedClickable(
        onClick = onOpen,
        onLongClick = if (canDelete) onLongPress else null,
        onLongClickLabel = if (canDelete) "显示删除按钮" else null,
    ), shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(asset.thumbnailUrl, if (asset.assetType == "VIDEO") "查看视频" else "查看照片",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (asset.assetType == "VIDEO") Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f)) {
                Text("▶ 视频", Modifier.padding(4.dp), style = MaterialTheme.typography.labelMedium)
            }
            if (canDelete && showDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp).semantics {
                    contentDescription = if (asset.assetType == "VIDEO") "删除这个视频" else "删除这张照片"
                }) {
                    Surface(Modifier.size(28.dp), color = Color(0xDD302C28), shape = CircleShape) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("−", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
