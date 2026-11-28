package org.linuxdo.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.CaptchaRequiredException
import org.linuxdo.android.data.SecondFactorRequiredException
import org.linuxdo.android.data.model.User
import org.linuxdo.android.ui.theme.AvatarFallback
import org.linuxdo.android.util.TimeFmt
import org.linuxdo.android.ui.common.avatarUrlOf

/**
 * 「我的」页：未登录时显示登录表单；已登录显示资料 + 设置项。
 * 登录走 Discourse 的 POST /session（表单 login + password）。
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    user: User?,
    siteTitle: String,
    onLoggedIn: (User) -> Unit,
    onLogout: () -> Unit,
) {
    var serverDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (user == null) {
            LoginCard(container = container, siteTitle = siteTitle, onLoggedIn = onLoggedIn)
        } else {
            ProfileCard(user)
        }

        Spacer(Modifier.height(10.dp))

        SettingsGroup {
            SettingRow("服务器地址", container.sessionStore.baseUrl) { serverDialog = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            SettingRow("关于", "LINUX DO Android 1.0.0 · 非官方客户端")
        }

        if (user != null) {
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onLogout() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Logout,
                        null,
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("退出登录", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (serverDialog) {
        ServerDialog(
            current = container.sessionStore.baseUrl,
            onDismiss = { serverDialog = false },
            onSave = { url ->
                serverDialog = false
                container.sessionStore.baseUrl = url
                container.sessionStore.clearCookies()
                container.repository.invalidateLists()
            },
        )
    }
}

private enum class LoginMode { PASSWORD, EMAIL_CODE }

@Composable
private fun LoginCard(
    container: AppContainer,
    siteTitle: String,
    onLoggedIn: (User) -> Unit,
) {
    var mode by remember { mutableStateOf(LoginMode.PASSWORD) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var captchaBlocked by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }

    var secondFactor by remember { mutableStateOf<SecondFactorRequiredException?>(null) }
    var useBackupCode by remember { mutableStateOf(false) }
    var secondFactorCode by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    var webLoginPath by remember { mutableStateOf<String?>(null) }
    var forgotDialog by remember { mutableStateOf(false) }
    var captchaDialog by remember { mutableStateOf(false) }
    val loginBaseUrl = container.sessionStore.baseUrl
    val captchaSiteKey = remember(loginBaseUrl) { hcaptchaSiteKeyFor(loginBaseUrl) }
    val scope = rememberCoroutineScope()

    fun handleFailure(e: Throwable) {
        when (e) {
            is SecondFactorRequiredException -> {
                secondFactor = e
                useBackupCode = !e.totpEnabled && e.backupEnabled
                secondFactorCode = ""
                error = null
                captchaBlocked = false
            }
            is CaptchaRequiredException -> {
                error = e.message ?: "需要完成人机验证"
                captchaBlocked = true
            }
            else -> {
                val msg = e.message ?: "登录失败"
                error = msg
                captchaBlocked = msg.contains("captcha", ignoreCase = true) ||
                    msg.contains("人机验证")
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            AvatarFallback(name = siteTitle.take(1), size = 64)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "登录 $siteTitle",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "账号密码、邮箱验证码，或使用站点支持的网页登录",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )

        // ---------- 第三方 / 通行密钥：在网页里完成 ----------
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderButton("使用 Google 登录", Modifier.weight(1f)) {
                webLoginPath = "/auth/google_oauth2"
            }
            ProviderButton("使用 GitHub 登录", Modifier.weight(1f)) {
                webLoginPath = "/auth/github"
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderButton("使用 Discord 登录", Modifier.weight(1f)) {
                webLoginPath = "/auth/discord"
            }
            ProviderButton("使用 Apple 登录", Modifier.weight(1f)) {
                webLoginPath = "/auth/apple"
            }
        }
        Spacer(Modifier.height(8.dp))
        ProviderButton("使用通行密钥登录", Modifier.fillMaxWidth()) {
            webLoginPath = "/login"
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "第三方与通行密钥登录会在网页中完成",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )

        // ---------- 账号密码 / 邮箱验证码 ----------
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            Text(
                "或使用账号",
                Modifier.padding(horizontal = 10.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
        }
        Spacer(Modifier.height(12.dp))

        val challenge = secondFactor
        when {
            challenge != null -> TwoFactorPanel(
                challenge = challenge,
                code = secondFactorCode,
                onCodeChange = { secondFactorCode = it },
                useBackupCode = useBackupCode,
                onToggleBackup = {
                    useBackupCode = !useBackupCode
                    secondFactorCode = ""
                    error = null
                },
                verifying = verifying,
                error = error,
                onVerify = {
                    if (secondFactorCode.isBlank()) {
                        error = "请输入验证码"
                    } else {
                        verifying = true
                        error = null
                        scope.launch {
                            val result = if (mode == LoginMode.EMAIL_CODE) {
                                container.repository.verifyLoginCode(
                                    email = email,
                                    code = emailCode,
                                    useBackupCode = useBackupCode || !challenge.totpEnabled,
                                    secondFactorToken = secondFactorCode,
                                )
                            } else {
                                container.repository.loginWithSecondFactor(
                                    login = login,
                                    password = password,
                                    useBackupCode = useBackupCode || !challenge.totpEnabled,
                                    code = secondFactorCode,
                                )
                            }
                            result.onSuccess { u ->
                                verifying = false
                                onLoggedIn(u)
                            }.onFailure { e ->
                                verifying = false
                                handleFailure(e)
                            }
                        }
                    }
                },
                onCancel = {
                    secondFactor = null
                    secondFactorCode = ""
                    error = null
                },
                onWebLogin = { webLoginPath = "/login" },
            )

            mode == LoginMode.PASSWORD -> {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("用户名或邮箱", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                TextButton(
                    onClick = {
                        if (email.isBlank() && login.contains("@")) email = login.trim()
                        mode = LoginMode.EMAIL_CODE
                        codeSent = false
                        emailCode = ""
                        error = null
                        captchaBlocked = false
                    },
                ) {
                    Text("通过电子邮件向我发送一次性登录代码", fontSize = 11.5.sp)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("密码", fontSize = 13.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                TextButton(onClick = { forgotDialog = true }) {
                    Text("我忘记密码了", fontSize = 11.5.sp)
                }

                LoginError(
                    error = error,
                    showWebFallback = captchaBlocked,
                    onCaptcha = if (captchaSiteKey != null) ({ captchaDialog = true }) else null,
                    onWebLogin = { webLoginPath = "/login" },
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (login.isBlank() || password.isBlank()) {
                            error = "用户名和密码都不能为空"
                            return@Button
                        }
                        loading = true
                        error = null
                        captchaBlocked = false
                        scope.launch {
                            container.repository.login(login, password)
                                .onSuccess { u ->
                                    loading = false
                                    onLoggedIn(u)
                                }
                                .onFailure { e ->
                                    loading = false
                                    handleFailure(e)
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            else -> {
                if (!codeSent) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("邮箱", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "登录代码会发送到该邮箱；若站点开启人机验证，请改用网页登录。",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )

                    LoginError(
                        error = error,
                        showWebFallback = captchaBlocked,
                        onWebLogin = { webLoginPath = "/login" },
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || !email.contains("@")) {
                                error = "请输入正确的邮箱"
                                return@Button
                            }
                            loading = true
                            error = null
                            captchaBlocked = false
                            scope.launch {
                                container.repository.requestLoginCode(email)
                                    .onSuccess {
                                        loading = false
                                        codeSent = true
                                        error = null
                                    }
                                    .onFailure { e ->
                                        loading = false
                                        handleFailure(e)
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        enabled = !loading,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("发送登录代码", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Text(
                        "验证码已发送到 $email",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailCode,
                        onValueChange = { emailCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("6 位登录代码", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )

                    LoginError(
                        error = error,
                        showWebFallback = captchaBlocked,
                        onWebLogin = { webLoginPath = "/login" },
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (emailCode.isBlank()) {
                                error = "请输入登录代码"
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                container.repository.verifyLoginCode(email, emailCode)
                                    .onSuccess { u ->
                                        loading = false
                                        onLoggedIn(u)
                                    }
                                    .onFailure { e ->
                                        loading = false
                                        handleFailure(e)
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        enabled = !loading,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("验证并登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                emailCode = ""
                                error = null
                                codeSent = false
                            },
                        ) { Text("换个邮箱", fontSize = 11.5.sp) }
                        TextButton(
                            onClick = {
                                loading = true
                                error = null
                                scope.launch {
                                    container.repository.requestLoginCode(email)
                                        .onSuccess { loading = false }
                                        .onFailure { e ->
                                            loading = false
                                            handleFailure(e)
                                        }
                                }
                            },
                            enabled = !loading,
                        ) { Text("重新发送", fontSize = 11.5.sp) }
                    }
                }

                TextButton(
                    onClick = {
                        mode = LoginMode.PASSWORD
                        secondFactor = null
                        emailCode = ""
                        codeSent = false
                        error = null
                        captchaBlocked = false
                    },
                ) {
                    Text("返回密码登录", fontSize = 12.sp)
                }
            }
        }
    }

    if (forgotDialog) {
        ForgotPasswordDialog(
            container = container,
            initialLogin = login,
            onDismiss = { forgotDialog = false },
        )
    }

    if (captchaDialog && captchaSiteKey != null) {
        HcaptchaDialog(
            baseUrl = loginBaseUrl,
            siteKey = captchaSiteKey,
            onDismiss = { captchaDialog = false },
            onSolved = { token ->
                captchaDialog = false
                loading = true
                error = null
                captchaBlocked = false
                scope.launch {
                    container.repository.submitHcaptchaToken(token)
                        .onSuccess {
                            // 验证 token 已落到同一个服务端会话，重新提交密码登录
                            container.repository.login(login, password)
                                .onSuccess { u ->
                                    loading = false
                                    onLoggedIn(u)
                                }
                                .onFailure { e ->
                                    loading = false
                                    handleFailure(e)
                                }
                        }
                        .onFailure { e ->
                            loading = false
                            error = "人机验证提交失败：${e.message ?: "请重试"}"
                        }
                }
            },
        )
    }

    val path = webLoginPath
    if (path != null) {
        WebLoginDialog(
            baseUrl = container.sessionStore.baseUrl,
            startPath = path,
            container = container,
            onDismiss = { webLoginPath = null },
            // 网页里的人机验证加载不出来时，给一个不依赖验证码的登录出口
            onUseEmailCode = {
                webLoginPath = null
                mode = LoginMode.EMAIL_CODE
                codeSent = false
                emailCode = ""
                error = null
                captchaBlocked = false
                if (email.isBlank() && login.contains("@")) email = login.trim()
            },
            onLoggedIn = { u ->
                webLoginPath = null
                onLoggedIn(u)
            },
        )
    }
}

@Composable
private fun ProviderButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoginError(
    error: String?,
    showWebFallback: Boolean,
    onWebLogin: () -> Unit,
    onCaptcha: (() -> Unit)? = null,
) {
    if (error == null) return
    Spacer(Modifier.height(8.dp))
    Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    if (showWebFallback) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onCaptcha != null) {
                TextButton(onClick = onCaptcha) {
                    Text("完成人机验证", fontSize = 11.5.sp)
                }
            }
            TextButton(onClick = onWebLogin) {
                Text("改用网页登录", fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun TwoFactorPanel(
    challenge: SecondFactorRequiredException,
    code: String,
    onCodeChange: (String) -> Unit,
    useBackupCode: Boolean,
    onToggleBackup: () -> Unit,
    verifying: Boolean,
    error: String?,
    onVerify: () -> Unit,
    onCancel: () -> Unit,
    onWebLogin: () -> Unit,
) {
    val canCode = challenge.totpEnabled || challenge.backupEnabled
    Column(Modifier.fillMaxWidth()) {
        Text(
            "两步验证",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (canCode) {
                if (challenge.backupEnabled) "输入验证器代码，或改用备用码。" else "输入验证器中的 6 位验证码。"
            } else {
                "该账号仅启用了安全密钥，请在网页登录里完成验证。"
            },
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        if (canCode) {
            if (challenge.totpEnabled && challenge.backupEnabled) {
                TextButton(onClick = onToggleBackup) {
                    Text(
                        if (useBackupCode) "改用验证器代码" else "改用备用码",
                        fontSize = 12.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(if (useBackupCode) "备用码" else "6 位验证码", fontSize = 13.sp)
                },
                singleLine = true,
                keyboardOptions = if (useBackupCode) {
                    KeyboardOptions(imeAction = ImeAction.Done)
                } else {
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    )
                },
                shape = RoundedCornerShape(10.dp),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                enabled = !verifying,
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("验证并登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            TextButton(onClick = onCancel, enabled = !verifying) {
                Text("返回登录", fontSize = 12.5.sp)
            }
            if (challenge.securityKeyEnabled) {
                TextButton(onClick = onWebLogin) {
                    Text("改用安全密钥 / 网页登录", fontSize = 12.sp)
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onWebLogin,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("使用网页登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onCancel) {
                Text("返回登录", fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    container: AppContainer,
    initialLogin: String,
    onDismiss: () -> Unit,
) {
    var value by remember {
        mutableStateOf(if (initialLogin.contains("@")) initialLogin.trim() else "")
    }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("找回密码", fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    "输入注册邮箱或用户名，站点会发送重置邮件。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("邮箱或用户名", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = if (success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isBlank()) {
                        message = "请输入邮箱或用户名"
                        success = false
                        return@TextButton
                    }
                    sending = true
                    message = null
                    scope.launch {
                        container.repository.forgotPassword(value)
                            .onSuccess {
                                message = "如果该账号存在，重置邮件已发送，请查收。"
                                success = true
                            }
                            .onFailure {
                                message = it.message ?: "发送失败"
                                success = false
                            }
                        sending = false
                    }
                },
                enabled = !sending,
            ) {
                Text(if (sending) "发送中…" else "发送重置邮件", fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text("关闭", fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun ProfileCard(user: User) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val url = avatarUrlOf(user.avatarTemplate, 192)
            Box(Modifier.size(56.dp)) {
                AvatarFallback(name = user.displayName, size = 56)
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    user.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "@${user.username}",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user.admin || user.moderator) {
                        Tag("管理员", MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(5.dp))
                    }
                    user.mainFlair?.let {
                        Tag(it, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(5.dp))
                    }
                    Tag("TL${user.trustLevel}", MaterialTheme.colorScheme.secondary)
                }
            }
        }

        user.bioExcerpt?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            user.timeRead?.let { Stat("阅读时长", "${it / 60} 分钟") }
            user.badgeCount?.let { Stat("徽章", it.toString()) }
            user.profileViewCount?.let { Stat("主页访问", it.toString()) }
        }
        user.createdAt?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                "注册于 ${TimeFmt.dateOnly(it)}" +
                    (user.lastSeenAt?.let { s -> " · 最近活跃 ${TimeFmt.relative(s)}" } ?: ""),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 9.5.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址", fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    "填写自建 Discourse 站点根地址，例如 https://meta.discourse.org",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleaned = text.trim().trimEnd('/')
                    if (cleaned.startsWith("http")) onSave(cleaned)
                },
            ) { Text("保存", fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 13.sp) }
        },
    )
}
