package family.babyhome.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import family.babyhome.data.network.MomentDto
import family.babyhome.domain.baby.BabyAgeCalculator
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Honey = Color(0xFFF4CA36)

fun MomentDto.albumDate(): LocalDate =
    Instant.parse(eventDate).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onMoment: (String) -> Unit,
    viewModel: TimelineViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var calendar by rememberSaveable { mutableStateOf(false) }
    var indexOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val birthday = state.baby?.birthday?.take(10)?.let(LocalDate::parse)
    val months = state.moments.groupBy { YearMonth.from(it.albumDate()) }.toSortedMap(reverseOrder())
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 4
        }
    }

    LaunchedEffect(shouldLoadMore, state.nextCursor) {
        if (shouldLoadMore && state.nextCursor != null) viewModel.loadMore()
    }
    LaunchedEffect(state.scrollTargetDate, state.moments.size) {
        val target = state.scrollTargetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (target != null && !state.loading) {
            val momentIndex = state.moments.indexOfFirst { it.albumDate() == target }
            if (momentIndex >= 0) listState.scrollToItem(momentIndex + 2)
            viewModel.consumeScrollTarget()
        }
    }
    if (indexOpen) {
        ModalBottomSheet(onDismissRequest = { indexOpen = false }) {
            Text("按宝宝年龄查找", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
            Text("当前已加载的成长记录", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.moments.distinctBy { YearMonth.from(it.albumDate()) }) { moment ->
                    TextButton(onClick = {
                        calendar = false
                        indexOpen = false
                        scope.launch { listState.scrollToItem(state.moments.indexOf(moment) + 2) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(birthday?.let { BabyAgeCalculator.format(it, moment.albumDate()) }.orEmpty() +
                            " · " + moment.albumDate().format(DateTimeFormatter.ofPattern("yyyy年M月")))
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                    item(key = "album-header") {
                        AlbumHeader(
                            calendar = calendar,
                            onTimeline = { calendar = false },
                            onCalendar = { calendar = true },
                            onAgeIndex = { indexOpen = true },
                        )
                    }
                    item(key = "baby") {
                        BabyProfileCard(
                            name = state.baby?.nickname ?: state.baby?.name ?: "宝宝",
                            birthday = birthday,
                            // 头像只能来自宝宝资料，不能跟随最新动态照片变化。
                            avatarUrl = state.avatarUrl,
                            refreshVersion = state.refreshVersion,
                        )
                    }
                    if (!calendar) {
                        items(state.moments, key = { it.id }) { moment ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(Honey))
                                    Spacer(Modifier.width(10.dp))
                                    Text(birthday?.let { BabyAgeCalculator.format(it, moment.albumDate()) }.orEmpty(), fontWeight = FontWeight.Bold)
                                    Text(" · " + moment.albumDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(12.dp))
                                Card(onClick = { onMoment(moment.id) }, shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    MomentCollage(moment, state.refreshVersion)
                                    if (!moment.content.isNullOrBlank()) Text(moment.content,
                                        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), maxLines = 3)
                                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(moment.author.nickname, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("♡ ${moment._count.likes}   留言 ${moment._count.comments}")
                                    }
                                }
                            }
                        }
                    } else {
                        items(months.keys.toList(), key = { it.toString() }) { month ->
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                                Text("${month.year}年 ${month.monthValue}月", Modifier.align(Alignment.CenterHorizontally).padding(12.dp),
                                    style = MaterialTheme.typography.titleLarge)
                                Row { listOf("日","一","二","三","四","五","六").forEach { Text(it, Modifier.weight(1f).padding(8.dp)) } }
                                val offset = month.atDay(1).dayOfWeek.value % 7
                                val cells = (0 until ((offset + month.lengthOfMonth() + 6) / 7) * 7).toList()
                                cells.chunked(7).forEach { week ->
                                    Row {
                                        week.forEach { cell ->
                                            val day = cell - offset + 1
                                            val date = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                                            val records = months[month].orEmpty().filter { it.albumDate() == date }
                                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(6.dp))
                                                .background(if (date == null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable(enabled = records.isNotEmpty()) {
                                                    onMoment(records.first().id)
                                                }) {
                                                records.firstOrNull()?.assets?.firstOrNull()?.let {
                                                    AlbumThumbnail(it.thumbnailUrl, state.refreshVersion, null, Modifier.fillMaxSize())
                                                }
                                                if (date != null) Text("$day", Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(3.dp))
                                                if (records.isNotEmpty()) Text("•", Modifier.align(Alignment.BottomStart).padding(3.dp), color = Honey)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.moments.isEmpty()) item { Text("还没有成长记录，点击 ＋ 留下第一刻", Modifier.padding(24.dp)) }
                    state.error?.let { message -> item { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
                    if (state.loadingMore) item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    calendar: Boolean,
    onTimeline: () -> Unit,
    onCalendar: () -> Unit,
    onAgeIndex: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("之", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("之之成长手册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("把每一天，留给长大的你", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onAgeIndex) { Text("按年龄") }
            }
            TabRow(
                selectedTabIndex = if (calendar) 1 else 0,
                containerColor = Color.Transparent,
                divider = {},
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Tab(selected = !calendar, onClick = onTimeline, text = { Text("时光轴") })
                Tab(selected = calendar, onClick = onCalendar, text = { Text("日历") })
            }
        }
    }
}

@Composable
private fun BabyProfileCard(
    name: String,
    birthday: LocalDate?,
    avatarUrl: String?,
    refreshVersion: Int,
) {
    val today = LocalDate.now()
    val shape = RoundedCornerShape(28.dp)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), shape),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(82.dp).clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AlbumThumbnail(avatarUrl, refreshVersion, "宝宝照片", Modifier.fillMaxSize())
                    } else {
                        Text(name.takeLast(1), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        birthday?.let { BabyAgeCalculator.format(it, today) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)) {
                        Text("今天 · ${today.format(DateTimeFormatter.ofPattern("M月d日"))}",
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(Modifier.fillMaxWidth()) {
                ProfileFact(
                    label = "生日",
                    value = birthday?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: "未设置",
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)))
                ProfileFact(
                    label = "来到家里",
                    value = birthday?.let { "第 ${ChronoUnit.DAYS.between(it, today) + 1} 天" } ?: "珍藏每一天",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProfileFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun MomentCollage(moment: MomentDto, refreshVersion: Int) {
    val assets = moment.assets.take(3)
    if (assets.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(if (assets.size == 1) 280.dp else 320.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.weight(1.3f).fillMaxHeight()) {
            AlbumThumbnail(assets[0].thumbnailUrl, refreshVersion, "查看成长记录", Modifier.fillMaxSize())
            if (assets[0].assetType == "VIDEO") Text("▶ 视频", Modifier.padding(10.dp).background(Color.Black.copy(alpha=.5f)), color = Color.White)
        }
        if (assets.size > 1) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            assets.drop(1).forEachIndexed { index, asset ->
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AlbumThumbnail(asset.thumbnailUrl, refreshVersion, null, Modifier.fillMaxSize())
                    if (asset.assetType == "VIDEO") Text("▶ 视频", Modifier.padding(8.dp).background(Color.Black.copy(alpha=.5f)), color = Color.White)
                    if (index == assets.size - 2 && moment.assets.size > 3) Text("共 ${moment.assets.size} 张/段",
                        Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=.5f)).padding(6.dp), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AlbumThumbnail(
    url: String?,
    refreshVersion: Int,
    description: String?,
    modifier: Modifier,
) {
    var retryAttempt by remember(url, refreshVersion) { mutableIntStateOf(0) }
    val refreshedUrl = url?.let { "$it&refresh=$refreshVersion&attempt=$retryAttempt" }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(refreshedUrl).crossfade(180).build(),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Honey)
            }
        },
        error = {
            LaunchedEffect(retryAttempt) {
                if (retryAttempt < 10) {
                    delay(if (retryAttempt < 3) 1_500L else 4_000L)
                    retryAttempt++
                }
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (retryAttempt < 10) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("照片暂未就绪", textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )
}
