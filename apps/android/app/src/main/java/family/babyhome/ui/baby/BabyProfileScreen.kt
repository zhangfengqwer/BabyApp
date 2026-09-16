package family.babyhome.ui.baby

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyProfileScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: BabyProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val enabled = state.canEdit && !state.saving && !state.loading
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(viewModel::avatar) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    Scaffold(
        topBar = { TopAppBar(title = { Text("宝宝名片") }, navigationIcon = { TextButton(onClick = onBack, enabled = !state.saving) { Text("返回") } }) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(onClick = { viewModel.save() }, enabled = enabled, modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (state.saving) "正在保存…" else "保存名片")
                }
            }
        },
    ) { padding ->
        if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val avatar = state.selectedAvatar ?: state.avatarUrl
                    if (avatar != null) AsyncImage(avatar, "宝宝固定头像", Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Surface(Modifier.size(104.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Text(state.nickname.ifBlank { state.name }.takeLast(1).ifBlank { "宝" }, style = MaterialTheme.typography.headlineLarge) } }
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("更换头像") }
                    Text("头像只在这里修改，不会随新照片变化", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("关于宝宝", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(state.name, { viewModel.fields(name = it) }, enabled = enabled, label = { Text("姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(state.nickname, { viewModel.fields(nickname = it) }, enabled = enabled, label = { Text("昵称 · 可选") }, supportingText = { Text("时光轴名片优先显示昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedButton(onClick = {
                val date = runCatching { LocalDate.parse(state.birthday) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, y, m, d -> viewModel.fields(birthday = LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp)) { Text("出生日期  ${state.birthday}") }
            Text("修改生日后，所有时光轴的宝宝年龄会重新计算。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(state.description, { viewModel.fields(description = it) }, enabled = enabled, label = { Text("一句话介绍 · 可选") }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            if (!state.canEdit && state.id.isNotBlank()) Text("只有管理员可以修改宝宝名片", color = MaterialTheme.colorScheme.error)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); if (state.id.isBlank()) TextButton(onClick = { viewModel.reload() }) { Text("重新加载") } }
        }
    }
}
