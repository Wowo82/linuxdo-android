# 11 · 构建与运行

## 1. 环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | 17 | AGP 8.7.3 与 `jvmTarget = 17` 要求 |
| Android Studio | Ladybug 或更新 | 直接打开仓库根目录即可 |
| Android SDK | Platform 35 + Build-Tools | `compileSdk = 35` |
| Gradle | 8.9（Wrapper 自动下载） | 无需本机安装 |
| 网络 | 首次构建需下载约 400 MB 依赖 | Gradle / Google Maven / Maven Central |
| 设备 / 模拟器 | Android 8.0（API 26）及以上 | `minSdk = 26` |

---

## 2. 快速开始

### 2.1 Android Studio

1. `File → Open` 选择仓库根目录（含 `settings.gradle.kts` 的目录）；
2. 等待 Gradle Sync（首次会下载 AGP / Kotlin / Compose 依赖）；
3. 选择 `app` 运行配置，连接设备或启动模拟器；
4. Run。

### 2.2 命令行

```bash
# 调试包
./gradlew :app:assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk

# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或直接由 Gradle 安装
./gradlew :app:installDebug
```

### 2.3 运行期配置

| 项 | 位置 / 方式 |
|---|---|
| 默认站点 | `BuildConfig.DEFAULT_BASE_URL`，编译期固定为 `https://linux.do` |
| 切换站点 | App 内「我的 → 服务器地址」，仅支持 https |
| 登录 | 「我的」页用户名 / 邮箱 + 密码；cookie 存在加密 prefs |
| 清除登录态 | 退出登录，或系统设置中清除应用数据 |
| 深链测试 | `adb shell am start -a android.intent.action.VIEW -d "https://linux.do/t/slug/12345/7"` |

---

## 3. 构建命令速查

| 命令 | 作用 |
|---|---|
| `./gradlew :app:assembleDebug` | 调试 APK |
| `./gradlew :app:assembleRelease` | 发布 APK（需签名配置，见 §4） |
| `./gradlew :app:testDebugUnitTest` | 运行全部 JVM 单元测试 |
| `./gradlew :app:testDebugUnitTest --tests 'org.linuxdo.android.util.HtmlTest'` | 只跑某个测试类 |
| `./gradlew :app:lint` | 静态检查 |
| `./gradlew :app:installDebug` | 构建并安装 debug |
| `./gradlew clean` | 清理构建缓存（构建异常时先试） |
| `./gradlew :app:dependencies` | 查看依赖树 |

> `assembleDebug` 不会自动跑单元测试；CI 是单独跑 `testDebugUnitTest` 的。

---

## 4. 签名配置

### 4.1 解析优先级

`app/build.gradle.kts` 的签名逻辑：

```text
是否存在密钥库：
  KEYSTORE_PATH 环境变量指向的文件存在
  或 默认路径 <repo>/keystore/linuxdo-release.jks 存在

签名参数（仅当密钥库存在）：
  storeFile     = KEYSTORE_PATH ?: <repo>/keystore/linuxdo-release.jks
  storePassword = KEYSTORE_PASSWORD ?: gradle property "keystore.password"
  keyAlias      = KEY_ALIAS ?: gradle property "key.alias" ?: "linuxdo"
  keyPassword   = KEY_PASSWORD ?: gradle property "key.password"
```

| 场景 | 结果 |
|---|---|
| 无密钥库 | release / debug 都不签名；`assembleRelease` 仍可产出未签名 APK，不报错 |
| 有密钥库 | release 与 debug 都使用该签名（debug 同签名便于覆盖安装） |
| CI | 从 GitHub Secrets 还原 `keystore/linuxdo-release.jks` 并设置四个环境变量 |

### 4.2 本地签名

仓库提供了 [keystore.properties.example](../../keystore.properties.example)：

```properties
keystore.password=你的密钥库密码
key.alias=linuxdo
key.password=你的密钥密码
```

**注意**：当前 `app/build.gradle.kts` 通过 `findProperty(...)` 读取的是 **Gradle 属性**
（`gradle.properties` 或命令行 `-P`），并不会自动加载 `keystore.properties` 文件。
本地要签名，可使用以下任一方式：

```bash
# 方式一：环境变量
export KEYSTORE_PATH=/absolute/path/linuxdo-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=linuxdo
export KEY_PASSWORD=...
./gradlew :app:assembleRelease

# 方式二：Gradle 属性（-P 或写入 ~/.gradle/gradle.properties）
./gradlew :app:assembleRelease \
  -Pkeystore.password=... -Pkey.alias=linuxdo -Pkey.password=...
```

密钥库与 `keystore.properties` 已在 `.gitignore` 中，禁止提交。

### 4.3 release 构建

- `isMinifyEnabled = true`、`isShrinkResources = true`；
- 混淆规则保留 kotlinx.serialization 的 serializer，避免反序列化在 release 下失效；
- 产物：`app/build/outputs/apk/release/app-release.apk`（已签名时）或 `app-release-unsigned.apk`。

---

## 5. CI（GitHub Actions）

文件：[.github/workflows/build.yml](../../.github/workflows/build.yml)

### 5.1 触发

| 事件 | 说明 |
|---|---|
| `push` 到 `main` | 自动构建 |
| `pull_request` 到 `main` | 自动构建 |
| `workflow_dispatch` | 手动触发，可选 `both` / `debug` / `release` |

并发策略：同 workflow + ref 只保留最新一次，旧任务取消。

### 5.2 步骤

| 步骤 | 内容 |
|---|---|
| Checkout | `actions/checkout@v4` |
| JDK | Temurin 17 |
| Gradle | `gradle/actions/setup-gradle@v3` |
| Android SDK | 使用 runner 预装 SDK；写 `local.properties` 的 `sdk.dir` |
| Restore keystore | 从 `secrets.KEYSTORE_BASE64` 解码到 `keystore/linuxdo-release.jks`；未配置时警告并继续（产物未签名） |
| Unit tests | `./gradlew :app:testDebugUnitTest --stacktrace`；失败则上传测试报告并停止 |
| Build | 按输入构建 debug / release（默认两者都建） |
| Verify signature | 用 `apksigner verify --print-certs` 输出签名证书信息 |
| Upload | debug / release APK 作为 artifact，保留 30 天 |

### 5.3 需要的 Secrets

| Secret | 用途 |
|---|---|
| `KEYSTORE_BASE64` | base64 编码的 `.jks` |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

生成 base64：

```bash
base64 -w 0 keystore/linuxdo-release.jks > keystore.base64.txt
```

---

## 6. 调试

### 6.1 看网络请求

`okhttp-logging-interceptor` 已引入但未挂载，可临时在 `HttpClients.base` 加：

```kotlin
.addInterceptor(
    okhttp3.logging.HttpLoggingInterceptor().apply {
        level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
    }
)
```

或在 `HeaderInterceptor` 中打日志。**BODY 会打印 cookie，发布前必须删除。**

### 6.2 看 Compose 重组

Android Studio → Layout Inspector → 勾选 "Show recomposition counts"。

### 6.3 看状态

- `MainState` / `FeedState` / `TopicState` / `SearchState` 都是不可变 `data class`，
  断点或日志中可以直接打印；
- 网络层错误统一 `ApiException(kind, message)`，先看 `kind` 再看 `cause`。

### 6.4 测试深链

```bash
adb shell am start -a android.intent.action.VIEW -d "https://linux.do/t/xxx/12345/7"
```

需要设备上已安装 App，且站点为 `linux.do`（Manifest 只声明了该 host）。

---

## 7. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `SDK location not found` | 没有 `local.properties` / 未设置 `ANDROID_HOME` | 在仓库根创建 `local.properties`：`sdk.dir=/path/to/Android/sdk` |
| `Unsupported class file major version` | JDK 不是 17 | 设置 `JAVA_HOME` 到 JDK 17 |
| Gradle 卡在下载 | 网络 / 镜像 | 配置代理或使用国内镜像；首次约 400 MB |
| 构建莫名其妙失败 | 增量缓存损坏 | `./gradlew clean` 后重试 |
| `assembleRelease` 产物未签名 | 没有密钥库 | 按 §4 配置，或用 debug 包 |
| debug 安装冲突 | 设备上已有不同签名的同包名应用 | 卸载旧应用后重装 |
| App 打不开站点 | DNS 污染 / 站点非 https | 换网络；自建站点必须 https；可临时启用 DoH |
| 登录返回 403 | CSRF / cookie 未正确建立 | 确认 `HeaderInterceptor` 的 Origin/Referer；清应用数据重登 |
| 写操作第一次 403、稍后成功 | CSRF token 过期，自动刷新并重放 | 正常行为；若持续失败看 `ApiException.Kind.CSRF` |
| 图片 404 | 站点切换后旧 `baseUrl` 仍被使用 | 重启 App 让 `boot()` 刷新；见 03 §12 |
| JVM 测试报 "not mocked" | `unitTests.isReturnDefaultValues` 被关闭 | 保持为 true（`HeaderInterceptor` 读 `android.os.Build`） |
| release 下 JSON 解析失败 | 混淆移除了 serializer | 检查 `proguard-rules.pro` 的 kotlinx 规则 |

---

## 8. 构建配置与源码的对应关系

| 配置 | 源码中的使用 |
|---|---|
| `DEFAULT_BASE_URL` | `SessionStore.baseUrl` 默认值、`LocalSiteBaseUrl` 默认值 |
| `VERSION_NAME` | `HeaderInterceptor` 的 UA `LDAndroid/{versionName}` |
| `versionName` | 「关于」文案 `LINUX DO Android 1.0.0` |
| `minSdk 26` | `java.time` 可直接使用（无需 desugaring） |
| `targetSdk 35` / edge-to-edge | `MainActivity.enableEdgeToEdge()` |
| `buildConfig = true` | `BuildConfig.DEFAULT_BASE_URL` / `VERSION_NAME` |
| `testOptions.returnDefaultValues` | 网络层 JVM 测试可读取 `android.os.Build` |

---

## 9. 发布前检查清单

1. `./gradlew :app:testDebugUnitTest` 通过；
2. `./gradlew :app:assembleRelease` 成功且使用预期签名；
3. 移除临时 `HttpLoggingInterceptor` / 调试日志；
4. 确认 `DEFAULT_BASE_URL` 与版本号；
5. 在 API 26 与 API 35 设备上各跑一次登录、列表、详情、发帖、深链；
6. 检查 `keystore` / `local.properties` / `*.jks` 未被提交。
