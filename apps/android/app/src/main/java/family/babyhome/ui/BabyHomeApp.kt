package family.babyhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import family.babyhome.ui.timeline.TimelineViewModel
import family.babyhome.ui.timeline.MomentDetailScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import family.babyhome.ui.home.HomeScreen
import family.babyhome.ui.media.MediaGalleryScreen
import family.babyhome.ui.publish.PublishScreen
import family.babyhome.ui.settings.ServerSettingsScreen
import family.babyhome.ui.timeline.TimelineScreen
import family.babyhome.ui.baby.BabyProfileScreen
import family.babyhome.ui.family.FamilyScreen

private data class MainDestination(val route: String, val label: String)

private val mainDestinations = listOf(
    MainDestination("home", "时光轴"),
    MainDestination("profile", "我的"),
)

@Composable
fun BabyHomeApp(viewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainDestinations.map { it.route }
    val timelineViewModel: TimelineViewModel = hiltViewModel()
    val timelineState by timelineViewModel.state.collectAsStateWithLifecycle()
    val appState by viewModel.state.collectAsStateWithLifecycle()

    appState.update?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            title = { Text("发现新版本 ${release.versionName}") },
            text = {
                Column {
                    Text("安装包大小：${release.sizeBytes / 1024 / 1024} MB")
                    appState.updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUpdate, enabled = !appState.updating) {
                    Text("稍后")
                }
            },
            confirmButton = {
                Button(onClick = viewModel::installUpdate, enabled = !appState.updating) {
                    Text(if (appState.updating) "正在下载…" else "下载并安装")
                }
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            if (currentRoute == "home") FloatingActionButton(onClick = { navController.navigate("publish") }) { Text("＋") }
        },
        bottomBar = {
            if (showBottomBar) BabyBottomBar(navController, currentRoute)
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "connect",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("connect") {
                LaunchedEffect(appState.connected) {
                    if (appState.connected) {
                        timelineViewModel.loadInitial()
                        navController.navigate("home") { popUpTo("connect") { inclusive = true } }
                    }
                }
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("之之成长手册", style = MaterialTheme.typography.headlineMedium)
                    if (appState.selectingIdentity) {
                        Text("你是宝宝的哪位家人？", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleLarge)
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(appState.members.size) { index ->
                                val member = appState.members[index]
                                TextButton(onClick = { viewModel.selectIdentity(member.username) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text("${member.relationship} · ${member.username}${if(member.role == "ADMIN") "（管理员）" else ""}")
                                }
                            }
                        }
                        Text("没有找到自己？请家庭管理员先添加成员。")
                    } else if (appState.connecting || appState.connected) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                        Text(if (appState.connected) "正在打开时光轴…" else "正在连接家庭相册…")
                    } else {
                        Text(appState.error ?: "暂时无法进入相册", Modifier.padding(vertical = 20.dp))
                        Button(onClick = viewModel::connect) { Text("重新连接") }
                        Button(onClick = { navController.navigate("server_settings") }, Modifier.padding(top = 8.dp)) {
                            Text("服务器设置")
                        }
                    }
                }
            }
            composable("home") {
                TimelineScreen(
                    onMoment = { id -> navController.navigate("moment/$id") },
                    viewModel = timelineViewModel,
                    onEditBaby = { navController.navigate("baby_profile") },
                )
            }
            composable("timeline") {
                TimelineScreen(onMoment = { id -> navController.navigate("moment/$id") }, viewModel = timelineViewModel, onEditBaby = { navController.navigate("baby_profile") })
            }
            composable("publish") {
                PublishScreen(onBack = { navController.popBackStack() }, onPublished = { eventDate ->
                    // Reload first, then focus the date of the newly published media.
                    timelineViewModel.loadInitial(scrollTargetDate = eventDate)
                    navController.popBackStack()
                })
            }
            composable("profile") {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("我的家庭相册", style = MaterialTheme.typography.headlineMedium)
                    Text("当前身份：${appState.identity?.relationship ?: "家庭管理员"}", Modifier.padding(top=12.dp))
                    ListItem(headlineContent={Text("家庭成员")},trailingContent={Text("›")},modifier=Modifier.clickable{navController.navigate("family")})
                    ListItem(headlineContent={Text("切换我的身份")},trailingContent={Text("›")},modifier=Modifier.clickable{
                        viewModel.switchIdentity()
                        navController.navigate("connect"){popUpTo("home"){inclusive=true}}
                    })
                    Text("每一刻，都值得珍藏", Modifier.padding(vertical = 16.dp))
                    ListItem(headlineContent = { Text("发布照片与视频") }, trailingContent = { Text("›") },
                        modifier = Modifier.clickable { navController.navigate("publish") })
                    if (timelineState.baby?.canEdit == true) ListItem(headlineContent = { Text("编辑宝宝名片") }, trailingContent = { Text("›") }, modifier = Modifier.clickable { navController.navigate("baby_profile") })
                    ListItem(headlineContent = { Text("服务器设置") }, trailingContent = { Text("›") },
                        modifier = Modifier.clickable { navController.navigate("server_settings") })
                    ListItem(headlineContent = { Text("版本") }, trailingContent = { Text(family.babyhome.BuildConfig.VERSION_NAME) })
                }
            }
            composable("moment/{momentId}") { entry ->
                MomentDetailScreen(
                    momentId = requireNotNull(entry.arguments?.getString("momentId")),
                    state = timelineState,
                    onBack = {
                        // Re-fetch changes made while viewing details, but keep the existing list and scroll position.
                        timelineViewModel.refresh()
                        navController.popBackStack()
                    },
                    onDeleted = {
                        // 删除详情后回到时光轴前先重新取数，不能继续显示旧缓存。
                        timelineViewModel.removeMoment(requireNotNull(entry.arguments?.getString("momentId")))
                        timelineViewModel.loadInitial()
                        navController.popBackStack()
                    },
                    onAsset = { index ->
                        val momentId = requireNotNull(entry.arguments?.getString("momentId"))
                        navController.navigate("gallery/$momentId/$index")
                    },
                    onLoadMore = timelineViewModel::loadMore,
                )
            }
            composable("server_settings") { ServerSettingsScreen() }
            composable("family") { FamilyScreen(onBack = { navController.popBackStack(); viewModel.connect() }) }
            composable("baby_profile") {
                BabyProfileScreen(onBack = { navController.popBackStack() }, onSaved = {
                    timelineViewModel.refresh()
                    navController.popBackStack()
                })
            }
            composable("gallery/{momentId}/{initialIndex}") {
                MediaGalleryScreen()
            }
        }
    }
}

@Composable
private fun BabyBottomBar(navController: NavHostController, currentRoute: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        NavigationBar(
            modifier = Modifier.height(76.dp).padding(horizontal = 42.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            mainDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (destination.route == "home") Icons.Filled.Home else Icons.Filled.Person,
                            contentDescription = destination.label,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(destination.label) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String, onSettings: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(message, modifier = Modifier.padding(vertical = 12.dp))
        onSettings?.let { Button(onClick = it) { Text("服务器设置") } }
    }
}
