package org.linuxdo.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
private fun LoginCard(
    container: AppContainer,
    siteTitle: String,
    onLoggedIn: (User) -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var secondFactor by remember { mutableStateOf<SecondFactorRequiredException?>(null) }
    var useBackupCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            "账号密码，或使用站点支持的网页登录方式",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("用户名或邮箱", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(10.dp))
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

        val challenge = secondFactor
        if (challenge == null) {
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (login.isBlank() || password.isBlank()) {
                        error = "用户名和密码都不能为空"
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        container.repository.login(login, password)
                            .onSuccess { u ->
                                loading = false
                                onLoggedIn(u)
                            }
                            .onFailure { e ->
                                loading = false
                                if (e is SecondFactorRequiredException) {
                                    secondFactor = e
                                    useBackupCode = !e.totpEnabled && e.backupEnabled
                                    code = ""
                                    error = null
                                } else {
                                    error = e.message ?: "登录失败"
                                }
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
        } else {
            Spacer(Modifier.height(14.dp))
            Text(
                "两步验证",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (challenge.totpEnabled || challenge.backupEnabled) {
                    if (challenge.backupEnabled) "输入验证器代码，或改用备用码。"
                    else "输入验证器中的 6 位验证码。"
                } else {
                    "该账号仅启用了安全密钥，请在「使用其他方式登录」里完成验证。"
                },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

            if (challenge.totpEnabled || challenge.backupEnabled) {
                if (challenge.totpEnabled && challenge.backupEnabled) {
                    TextButton(
                        onClick = {
                            useBackupCode = !useBackupCode
                            code = ""
                            error = null
                        },
                    ) {
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
                    onValueChange = { code = it },
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
                    onClick = {
                        if (code.isBlank()) {
                            error = "请输入验证码"
                            return@Button
                        }
                        verifying = true
                        error = null
                        scope.launch {
                            container.repository.loginWithSecondFactor(
                                login = login,
                                password = password,
                                useBackupCode = useBackupCode || !challenge.totpEnabled,
                                code = code,
                            ).onSuccess { u ->
                                verifying = false
                                onLoggedIn(u)
                            }.onFailure { e ->
                                verifying = false
                                error = e.message ?: "验证失败"
                            }
                        }
                    },
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
                TextButton(
                    onClick = {
                        secondFactor = null
                        code = ""
                        error = null
                    },
                    enabled = !verifying,
                ) {
                    Text("返回密码登录", fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
            Text(
                "或",
                Modifier.padding(horizontal = 10.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = { webLogin = true },
            enabled = !loading && !verifying,
        ) {
            Text("使用其他方式登录", fontSize = 13.sp)
        }
        Text(
            "支持站点自带的 OAuth / SSO / 两步验证等网页方式",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }

    if (webLogin) {
        WebLoginDialog(
            baseUrl = container.sessionStore.baseUrl,
            container = container,
            onDismiss = { webLogin = false },
            onLoggedIn = { u ->
                webLogin = false
                onLoggedIn(u)
            },
        )
    }
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
