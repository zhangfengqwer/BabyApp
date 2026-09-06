package family.babyhome.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import family.babyhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AppUpdateApi,
) {
    suspend fun check(serverUrl: String): Result<AppRelease?> = runCatching {
        api.version("${serverUrl.trimEnd('/')}/app/version").data
            .takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    suspend fun downloadAndInstall(serverUrl: String, release: AppRelease): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            error("请允许之之成长手册安装未知应用，然后再次点击更新")
        }

        val apk = withContext(Dispatchers.IO) {
            val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
            val destination = File(updateDirectory, "zhizhi-growth-${release.versionName}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            api.download("${serverUrl.trimEnd('/')}${release.downloadPath}").use { body ->
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                    }
                }
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualSha256.equals(release.sha256, ignoreCase = true)) {
                destination.delete()
                "安装包校验失败，请重新下载"
            }
            destination
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }
}
