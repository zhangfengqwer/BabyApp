package family.babyhome.ui.publish

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PublishedMediaCleanupEntry() {
    val context = LocalContext.current
    if (PublishedMediaHistory.read(context).isEmpty()) return
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<LocalDeleteCandidate>>(emptyList()) }
    var confirming by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) message = "已取消删除，本地文件仍保留。"
        else scope.launch {
            val remaining = withContext(Dispatchers.IO) { candidates.count { LocalMediaDeletion.exists(context, it.uri) } }
            message = if (remaining == 0) "本次选中的本地文件已删除。" else "$remaining 个文件仍在手机上，请在系统相册中删除。"
        }
    }
    ListItem(
        headlineContent = { Text("重试删除已发布的本地文件") },
        supportingContent = { Text(if (checking) "正在查找本机文件…" else "用于发布成功后本地删除失败的情况") },
        trailingContent = { Text("›") },
        modifier = Modifier.clickable(enabled = !checking) {
            if (Build.VERSION.SDK_INT < 30) {
                message = "此 Android 版本需要在系统相册中手动删除本地文件。"
            } else scope.launch {
                checking = true
                candidates = withContext(Dispatchers.IO) { LocalMediaDeletion.previouslyPublishedOnDevice(context) }
                checking = false
                if (candidates.isEmpty()) message = "没有找到可由应用删除的已发布本地文件；系统相册选择的只读文件请手动删除。"
                else confirming = true
            }
        },
    )
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("删除本地文件？") },
        text = {
            Column {
                Text("找到 ${candidates.size} 个已发布但仍在手机上的文件。只删除本机原件，家庭服务器中的记录和照片不受影响。")
                candidates.take(6).forEach { Text("· ${it.name}", maxLines = 1) }
                if (candidates.size > 6) Text("以及其他 ${candidates.size - 6} 个文件")
            }
        },
        confirmButton = { TextButton(onClick = {
            confirming = false
            runCatching {
                val request = MediaStore.createDeleteRequest(context.contentResolver, candidates.map { it.uri })
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure { error ->
                Log.w("PublishedMediaCleanup", "Unable to request local media deletion", error)
                message = "无法请求删除这些本地文件，请在系统相册中手动删除。"
            }
        }) { Text("请求系统删除") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
    )
    message?.let { value -> AlertDialog(
        onDismissRequest = { message = null }, title = { Text("本地文件") }, text = { Text(value) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } },
    ) }
}
