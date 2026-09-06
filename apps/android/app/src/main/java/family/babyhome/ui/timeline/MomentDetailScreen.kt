package family.babyhome.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    var text by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    LaunchedEffect(detail.deleted) { if (detail.deleted) onDeleted() }
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
                    AsyncImage(cover.thumbnailUrl, "封面，点击查看", modifier = Modifier.fillMaxWidth().height(260.dp)
                        .clickable { onAsset(0) }, contentScale = ContentScale.Crop)
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
                            Card(onClick = { onAsset(rowIndex * 3 + columnIndex) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)) {
                                AsyncImage(asset.thumbnailUrl, "点击查看照片或视频", modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
                                if (asset.assetType == "VIDEO") Text("▶ 视频", Modifier.padding(4.dp))
                            }
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
