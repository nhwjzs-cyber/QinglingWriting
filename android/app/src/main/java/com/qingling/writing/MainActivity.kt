package com.qingling.writing

import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import android.os.Bundle
import android.content.Intent
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.qingling.writing.core.design.MotionPolicy
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.qingling.writing.app.LocalAppContainer
import com.qingling.writing.app.QinglingWritingApp
import com.qingling.writing.core.datastore.ThemeMode
import com.qingling.writing.core.design.QinglingWritingTheme
import com.qingling.writing.feature.settings.AppLockControl
import com.qingling.writing.feature.settings.LocalAppLockControl
import kotlinx.coroutines.launch

/**
 * 唯一 Activity。
 * enableEdgeToEdge + WindowInsets:软键盘避让在编辑器页用 Modifier.imePadding() 处理(ask 第 3 条)。
 * 主题三态(G04):默认深色;浅色/跟随系统由用户在设置页选择并经 DataStore 持久化,
 * collectAsState 初值 DARK 保证首帧即深色(系统浅色时首次启动仍为深色)。
 */
open class MainActivity : ComponentActivity() {
    protected open val captureOnly=false
    private var pendingCapture by mutableStateOf<String?>(null)
    private var widgetResumeRequest by mutableIntStateOf(0)
    private fun receiveCapture(intent: Intent) {
        val incoming=when(intent.action) {
            "com.qingling.writing.QUICK_NOTE" -> ""
            Intent.ACTION_SEND -> if(intent.type=="text/plain") intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty() else null
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
            else -> null
        }
        if(incoming!=null) pendingCapture=if(pendingCapture.isNullOrBlank()) incoming else if(incoming.isBlank()) pendingCapture else pendingCapture+"\n\n"+incoming
    }
    private var lockEnabled by mutableStateOf<Boolean?>(null)
    private var locked by mutableStateOf(true)
    private var pendingAuth: LockAction? = null
    private var credentialLaunched = false
    private enum class LockAction { UNLOCK, ENABLE, DISABLE }
    private val credentialResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        credentialLaunched = false
        if (result.resultCode == RESULT_OK) authenticationSucceeded() else pendingAuth = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveCapture(intent)
        if (intent.action == com.qingling.writing.feature.widget.WritingWidget.ACTION_RESUME) widgetResumeRequest++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState?.containsKey("pending_capture")==true) pendingCapture=savedInstanceState.getString("pending_capture")
        else receiveCapture(intent)
        // Protect the first frame and the app switcher while the preference is loading.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = (application as WritingApplication).container
        lifecycleScope.launch {
            container.settingsStore.appLockEnabled.collect { enabled ->
                val previous = lockEnabled
                lockEnabled = enabled
                if (!enabled) locked = false else if (previous == null) locked = true
                if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent {
            val themeMode by container.settingsStore.themeMode.collectAsState(initial = ThemeMode.DARK)
            val reduceMotion by container.settingsStore.reduceMotion.collectAsState(initial = false)
            val coverStyles by container.settingsStore.coverStyles.collectAsState(initial=emptyMap())
            MotionPolicy(reduceMotion) {
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            QinglingWritingTheme(darkTheme = dark) {
                com.qingling.writing.feature.settings.PrivacyGate(container.settingsStore, onDecline = { finishAndRemoveTask() }) {
                val hidden = lockEnabled == null || (lockEnabled == true && locked)
                CompositionLocalProvider(LocalAppContainer provides container, com.qingling.writing.core.model.LocalCoverStyles provides coverStyles,
                    LocalAppLockControl provides AppLockControl(lockEnabled == true,
                        { enable -> authenticate(if (enable) LockAction.ENABLE else LockAction.DISABLE) }, contentAccessible = !hidden)) {
                    Box(Modifier.fillMaxSize()) {
                        if(!captureOnly) Surface(if (hidden) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background) { QinglingWritingApp(resumeRequest = widgetResumeRequest) }
                        if(!captureOnly && !hidden) com.qingling.writing.feature.settings.ThemeIntroduction()
                        val captureState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                        if(!hidden&&pendingCapture!=null)captureState.SaveableStateProvider("capture"){com.qingling.writing.feature.planning.QuickNoteSheet(
                            onDismiss={pendingCapture=null;captureState.removeState("capture");if(captureOnly)finish()},initialText=pendingCapture.orEmpty(),onDraftChanged={pendingCapture=it})}
                        if (hidden) {
                            BackHandler { }
                            // Dialogs own separate windows; a content-only overlay cannot cover them.
                            Dialog(onDismissRequest = {}, properties = DialogProperties(
                                dismissOnBackPress = false, dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                                securePolicy = SecureFlagPolicy.SecureOn)) {
                            Surface(Modifier.fillMaxSize().clickable { }, color = MaterialTheme.colorScheme.background) {
                                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (lockEnabled == null) "正在读取安全设置…" else "轻灵写作已锁定", style = MaterialTheme.typography.headlineSmall)
                                    if (lockEnabled == true) {
                                        Text("使用设备锁屏密码或可用的指纹验证", modifier = Modifier.padding(vertical = 16.dp))
                                        Button(onClick = { authenticate(LockAction.UNLOCK) }) { Text("解锁") }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
            }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingCapture?.let {outState.putString("pending_capture",it)}
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (lockEnabled != false) {
            locked = true
            currentFocus?.let { view ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        super.onPause()
    }

    private fun authenticate(action: LockAction) {
        if (pendingAuth != null) return
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            Toast.makeText(this, "请先在手机系统设置中启用屏幕锁定", Toast.LENGTH_LONG).show()
            return
        }
        pendingAuth = action
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                val manager = getSystemService(BiometricManager::class.java)
                if (manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS) {
                    BiometricPrompt.Builder(this).setTitle("验证身份").setSubtitle("轻灵写作应用锁")
                        .setAllowedAuthenticators(allowed).build()
                        .authenticate(CancellationSignal(), mainExecutor, biometricCallback())
                } else showCredential()
            } else if (Build.VERSION.SDK_INT == 29 && getSystemService(BiometricManager::class.java).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricPrompt.Builder(this).setTitle("验证身份").setSubtitle("轻灵写作应用锁")
                    .setNegativeButton("使用设备密码", mainExecutor) { _, _ -> showCredential() }.build()
                    .authenticate(CancellationSignal(), mainExecutor, biometricCallback())
            } else showCredential()
        } catch (_: Exception) { pendingAuth = null; Toast.makeText(this, "无法启动身份验证，请检查设备安全设置", Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    private fun showCredential() {
        val intent = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager)
            .createConfirmDeviceCredentialIntent("验证身份", "轻灵写作应用锁")
        if (intent == null) { pendingAuth = null; Toast.makeText(this, "请先设置设备屏幕锁", Toast.LENGTH_LONG).show() }
        else try { credentialLaunched = true; credentialResult.launch(intent) } catch (_: Exception) { credentialLaunched = false; pendingAuth = null; Toast.makeText(this, "无法启动设备身份验证", Toast.LENGTH_LONG).show() }
    }

    @android.annotation.TargetApi(28)
    private fun biometricCallback() = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authenticationSucceeded() }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (credentialLaunched) return
            pendingAuth = null
        }
    }

    private fun authenticationSucceeded() {
        val action = pendingAuth ?: return
        pendingAuth = null
        when (action) {
            LockAction.UNLOCK -> locked = !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            LockAction.ENABLE, LockAction.DISABLE -> lifecycleScope.launch {
                try {
                    (application as WritingApplication).container.settingsStore.setAppLockEnabled(action == LockAction.ENABLE)
                    locked = action == LockAction.ENABLE && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "应用锁设置未保存，请重试", Toast.LENGTH_LONG).show() }
            }
        }
    }
}
