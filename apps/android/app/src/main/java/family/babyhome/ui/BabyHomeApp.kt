package family.babyhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
                    if (appState.connecting) {
                        CircularProgressIndicator(Modifier.padding(24.dp))
                        Text("正在连接家庭相册…")
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
                )
            }
            composable("timeline") {
                TimelineScreen(onMoment = { id -> navController.navigate("moment/$id") }, viewModel = timelineViewModel)
            }
            composable("publish") {
                PublishScreen(onPublished = {
                    // 发布完成后无条件重新加载，确保首页立刻出现新动态。
                    timelineViewModel.loadInitial()
                    navController.popBackStack()
                })
            }
            composable("profile") {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("我的家庭相册", style = MaterialTheme.typography.headlineMedium)
                    Text("每一刻，都值得珍藏", Modifier.padding(vertical = 16.dp))
                    ListItem(headlineContent = { Text("发布照片与视频") }, trailingContent = { Text("›") },
                        modifier = Modifier.clickable { navController.navigate("publish") })
                    ListItem(headlineContent = { Text("服务器设置") }, trailingContent = { Text("›") },
                        modifier = Modifier.clickable { navController.navigate("server_settings") })
                    ListItem(headlineContent = { Text("版本") }, trailingContent = { Text(family.babyhome.BuildConfig.VERSION_NAME) })
                }
            }
            composable("moment/{momentId}") { entry ->
                MomentDetailScreen(
                    momentId = requireNotNull(entry.arguments?.getString("momentId")),
                    state = timelineState,
                    onBack = { navController.popBackStack() },
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
            composable("gallery/{momentId}/{initialIndex}") {
                MediaGalleryScreen(onBack = navController::popBackStack)
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
