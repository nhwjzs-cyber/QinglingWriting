package com.qingling.writing.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qingling.writing.core.datastore.SettingsStore
import com.qingling.writing.core.privacy.PrivacyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** No manuscript UI (including external capture) is composed until persisted consent is read. */
@Composable
fun PrivacyGate(store: SettingsStore, onDecline: () -> Unit, content: @Composable () -> Unit) {
    val accepted by store.privacyAccepted.collectAsState<Boolean, Boolean?>(initial = null)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    when (accepted) {
        true -> content()
        else -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (accepted == null) Text("正在读取隐私设置…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else PrivacyPolicyDialog(requireConsent = true, saving = saving, error = error,
                    onDismiss = { if (!saving) onDecline() }, onAccept = {
                        if (!saving) {
                            saving = true
                            error = null
                            scope.launch {
                                try { store.acceptPrivacyPolicy() }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { error = "同意记录未保存，请检查存储空间后重试。" }
                                finally { saving = false }
                            }
                        }
                    })
            }
        }
    }
}

/** The first-launch gate and settings use the same complete, bundled policy text. */
@Composable
fun PrivacyPolicyDialog(requireConsent: Boolean = false, onDismiss: () -> Unit,
    onAccept: () -> Unit = {}, saving: Boolean = false, error: String? = null) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val policy by produceState<String?>(initialValue = null, context, retry) {
        value = null
        value = withContext(Dispatchers.IO) {
            try { context.assets.open(PrivacyPolicy.ASSET).bufferedReader().use { it.readText() } }
            catch (_: java.io.IOException) { "" }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        dismissOnBackPress = !saving, dismissOnClickOutside = !requireConsent,
        usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(.9f)
            .testTag("privacy_policy_window"), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints {
            val compact = maxHeight < 500.dp
            Column(Modifier.padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Text("隐私政策", style = MaterialTheme.typography.headlineSmall)
                if (!compact) {
                Text(PrivacyPolicy.SUMMARY, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("开发者不收集或上传你的个人信息与稿件。写作数据保存在本机，由你管理。",
                    style = MaterialTheme.typography.bodyMedium)
                }
                Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                        when {
                            policy == null -> Text("正在加载完整政策…")
                            policy!!.isBlank() -> {
                                Text("完整政策暂时无法读取，请重试。", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { retry++ }) { Text("重新加载") }
                            }
                            else -> Text(policy!!, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp))
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (requireConsent) {
                    if (!compact) Text("请阅读以上政策。同意后可使用轻灵写作；不同意将退出应用。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAccept, enabled = !saving && !policy.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (saving) "正在保存…" else "同意并继续")
                    }
                    TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("不同意并退出") }
                } else Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
            }
        }
    }
}
