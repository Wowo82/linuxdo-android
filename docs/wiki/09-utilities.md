# 09 · 工具函数参考

`util/` 下三个文件都是**纯 JVM 逻辑**，不依赖 Android API，因此可以在普通单元测试中直接运行。

| 文件 | 职责 | 主要使用者 |
|---|---|---|
| [Html.kt](../../app/src/main/java/org/linuxdo/android/util/Html.kt) | HTML → 纯文本、解实体、头像 / 图片地址补全 | 列表摘要、搜索 blurb、头像 |
| [TimeFmt.kt](../../app/src/main/java/org/linuxdo/android/util/TimeFmt.kt) | ISO8601 时间格式化、数字缩写 | 列表、详情、通知、资料 |
| [TopicLink.kt](../../app/src/main/java/org/linuxdo/android/util/TopicLink.kt) | 深链解析 | `MainActivity.consumeDeepLink` |

---

## 1. `Html`

### 1.1 `Html.toPlain(html: String?): String`

把 Discourse 返回的 HTML 转成适合列表摘要的纯文本：

```text
1. 删除 <script> / <style> 块（替换为空格）
2. <br> → "\n"
3. </p> → "\n"
4. 删除其余所有 <...> 标签（替换为空格）
5. 解 HTML 实体（见 unescape）
6. 压缩空格 / 制表符 / &nbsp; 为单个空格
7. 去掉每行首尾空格（\n 两侧）
8. 3 个以上连续换行压缩为 2 个
9. trim
```

示例：

| 输入 | 输出 |
|---|---|
| `<p>hi &#1114112; there</p>` | `hi there`（越界实体被丢弃，不崩） |
| `<p>a</p><p>b</p>` | `a\nb` |
| `<pre><code>code</code></pre>` | `code` |
| `<script>alert(1)</script>` | 空串 |

**限制**：不做完整 HTML 解析；表格、列表符号、代码高亮都会丢失结构，只保留文字。

### 1.2 `Html.unescape(s: String): String`

顺序是安全性的关键：

```text
1. decodeNumericEntities(s)        // &#65; / &#x41;，合法码点直接转字符，越界丢空
2. 命名实体替换：
     &nbsp; → 空格
     &lt; <   &gt; >   &quot; "   &#39; '   &apos; '
3. &amp; → &                       // 必须最后，否则会二次解码
4. stripInvalidNumericEntities(s)  // 清掉解 &amp; 后新冒出的非法数字实体
```

为什么这样排序：

- 如果先解 `&amp;`，`&amp;#1114112;` 会变成 `&#1114112;`，再交给数字实体解码 / `Character.toChars`
  就会抛出 `IllegalArgumentException`（列表摘要直接崩）；
- 先解数字实体、把 `&amp;` 留到最后，再扫一遍丢掉「非法的数字实体字面量」，既保证
  `&amp;#65;` 显示为字面量 `&#65;`（不重复解码），又不会被畸形输入炸掉。

私有辅助：

| 函数 | 行为 |
|---|---|
| `decodeNumericEntities(s)` | 十进制 `&#(\d{1,8});`、十六进制 `&#[xX]([0-9a-fA-F]{1,8});`；非法码点替换为空串 |
| `codePointToString(cp)` | `Character.isValidCodePoint` 为 true 时 `String(Character.toChars(cp))`，否则空串 |
| `stripInvalidNumericEntities(s)` | 只删除非法的数字实体字面量，合法项原样保留 |

### 1.3 `Html.firstImageSrc(html: String?): String?`

```kotlin
Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
```

取第一个 `<img>` 的 `src`；没有则 null。**当前只在单测中使用**，UI 未使用。

### 1.4 `Html.isCjkDominant(text: String): Boolean`

统计非空白字符中的 CJK 比例：

| 区间 | 范围 |
|---|---|
| 中日韩统一表意文字 | `0x4E00..0x9FFF` |
| 日文假名 | `0x3040..0x30FF` |
| 韩文音节 | `0xAC00..0xD7AF` |

比例 > 0.3 返回 true。**当前无调用方**，原计划用于控制断词 / 排版。

### 1.5 `avatarUrl(template, size, baseUrl): String?`

把 Discourse 的 `avatar_template` 转成指定尺寸的绝对地址：

```kotlin
fun avatarUrl(template: String?, size: Int, baseUrl: String): String? {
    if (template.isNullOrBlank()) return null
    val sized = template.replace("{size}", size.toString())
    return toAbsoluteUrl(sized, baseUrl)
}
```

模板形态与结果：

| 模板 | 结果（baseUrl = `https://bbs.example.com`） |
|---|---|
| `/user_avatar/bbs.example.com/u/{size}/1_2.png` | `https://bbs.example.com/user_avatar/.../96/1_2.png` |
| `//cdn.example.com/a/{size}/x.png` | `https://cdn.example.com/a/96/x.png` |
| `https://avatars.githubusercontent.com/u/1` | 原样（`{size}` 若存在也会被替换） |
| `null` / 空白 | null |

### 1.6 `absolutize(url, baseUrl): String?`

把正文图片 / 上传地址补成绝对地址：

| 输入形态 | 规则 |
|---|---|
| `http://…` / `https://…` | 原样返回 |
| `//host/path` | 补 `https:` |
| `/path` | 拼当前站点，**保留站点子路径**（如 `https://host/bbs` + `/uploads/a.png`） |
| `path`（无前导斜杠） | `baseUrl + "/" + path` |
| null / 空白 | null |

示例（来自 `SiteUrlTest`）：

```text
absolutize("/uploads/a.png", "https://bbs.example.com")
  → https://bbs.example.com/uploads/a.png

absolutize("/uploads/a.png", "https://host/bbs")
  → https://host/bbs/uploads/a.png

absolutize("/u/a.png", "https://host/")
  → https://host/u/a.png        // 不会出现双斜杠
```

### 1.7 为什么地址必须传 `baseUrl`

早期版本把域名写死为 `https://linux.do`，切换到自建 Discourse 后头像 / 图片全部 404。
现在所有地址函数都要求显式传入当前站点，UI 层通过 `LocalSiteBaseUrl` 注入
（见 08 §11.3）。`SiteUrlTest` 专门覆盖了这条回归。

---

## 2. `TimeFmt` 与 `shortCount`

文件：[TimeFmt.kt](../../app/src/main/java/org/linuxdo/android/util/TimeFmt.kt)

### 2.1 `TimeFmt.relative(iso: String?): String`

输入 Discourse 的 ISO8601 时间，输出相对时间：

| 条件（`secs = now - t` 的秒数） | 输出 |
|---|---|
| `secs < 0`（未来时间） | 刚刚 |
| `secs < 60` | 刚刚 |
| `secs < 3600` | `N 分钟前` |
| `secs < 86_400` | `N 小时前` |
| `secs < 86_400 * 30` | `N 天前` |
| `secs < 86_400 * 365` | `N 个月前` |
| 其他 | `N 年前` |

解析失败或空值返回空串。

### 2.2 `TimeFmt.absolute(iso): String`

系统时区，格式 `yyyy-MM-dd HH:mm`：

```kotlin
"%04d-%02d-%02d %02d:%02d".format(year, monthValue, dayOfMonth, hour, minute)
```

### 2.3 `TimeFmt.dateOnly(iso): String`

系统时区，格式 `yyyy-MM-dd`。用于「注册于 …」。

### 2.4 时间解析

```kotlin
private fun parse(iso: String?): Instant? =
    try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (e: DateTimeParseException) {
        runCatching { Instant.parse(iso) }.getOrNull()
    }
```

先按带时区的 `OffsetDateTime` 解析，失败再按 `Instant`（`Z` 结尾）兜底。
Discourse 偶尔省略时区，这个顺序能覆盖两种写法。

### 2.5 `Int.shortCount(): String`

| 输入 | 输出 |
|---|---|
| `999` | `999` |
| `1234` | `1.2k` |
| `1_000` | `1k`（`1.0k` 去掉 `.0`） |
| `1_500_000` | `1.5m` |
| `1_000_000` | `1m` |

实现：

```kotlin
when {
    this < 1000 -> toString()
    this < 1_000_000 -> String.format("%.1fk", this / 1000.0).replace(".0k", "k")
    else -> String.format("%.1fm", this / 1_000_000.0).replace(".0m", "m")
}
```

使用位置：列表回复数 / 浏览数、详情点赞数、通知合并数量。

---

## 3. `TopicLink`

文件：[TopicLink.kt](../../app/src/main/java/org/linuxdo/android/util/TopicLink.kt)

### 3.1 目标模型

```kotlin
object TopicLink {
    data class Target(val topicId: Long, val postNumber: Int? = null)

    fun parse(url: String?): Target?
    fun parsePath(path: String?): Target?
}
```

`parse` 先去掉 scheme / host / query / fragment，再交给 `parsePath`。
纯字符串实现（不用 `android.net.Uri`），因此可直接 JVM 单测。

### 3.2 支持的链接形态

| 输入 | 结果 |
|---|---|
| `https://linux.do/t/12345` | `Target(12345, null)` |
| `https://linux.do/t/some-slug-here/12345` | `Target(12345, null)` |
| `https://linux.do/t/some-slug/12345/7` | `Target(12345, 7)` |
| `https://linux.do/t/12345/7` | `Target(12345, 7)`（无 slug） |
| `https://linux.do/t/slug/99.json?u=foo` | `Target(99, null)` |
| `https://linux.do/t/slug/99/3.json#post_3` | `Target(99, 3)` |
| `//linux.do/t/7` | `Target(7, null)` |
| `linux.do/t/7` | `Target(7, null)` |
| `https://linux.do/t/release-2024-notes/2024` | `Target(2024, null)`（slug 含数字，取最后一段） |

拒绝（返回 null）：

| 输入 | 原因 |
|---|---|
| `https://linux.do/u/someone` | 第一段不是 `t` |
| `https://linux.do/c/dev/12` | 同上 |
| `https://linux.do/` | 路径为空 |
| `https://linux.do/t/0` | id 必须 > 0 |
| `https://linux.do/t/slug/0/5` | id 必须 > 0 |
| `null` / `""` | 空输入 |

### 3.3 解析算法

`pathOf(url)`：

```text
1. 有 "://"：取 scheme 后第一个 '/' 起的部分
2. 以 "//" 开头：取协议相对 host 后的第一个 '/' 起的部分
3. 无 scheme：若第一段（到 '/' 为止）含 '.' 或 ':'，视为 host 丢掉
4. 去掉 '?' 之后与 '#' 之后
5. 为空则返回 "/"
```

`parsePath(path)`：

```text
1. 去 query / fragment，trim('/')
2. 按 '/' 分段；要求至少 2 段且 seg[0] == "t"（忽略大小写）
3. parts = seg[1..] 每段取 substringBefore('.')（去掉 .json）
4. 按段数判定：
   1 段        → topicId = numeric(0)
   2 段        → 两段都数字：id = a, post = b
                 仅 b 数字：id = b（/t/slug/{id}）
                 仅 a 数字：id = a（/t/{id}/{post 非数字}）
                 否则 null
   3 段及以上   → id = numeric(1), post = numeric(2)
5. numeric 要求 > 0；topicId <= 0 → null
```

`postNumber` 还会 `.toInt().takeIf { it > 0 }`，非法值视为 null。

### 3.4 测试覆盖

[TopicLinkTest.kt](../../app/src/test/java/org/linuxdo/android/util/TopicLinkTest.kt) 覆盖：
绝对 URL、slug、带楼层、`.json` / query / fragment、协议相对、省略 scheme、
slug 含数字、非 `/t/` 路径、0 与负数 id。

---

## 4. 工具函数使用矩阵

| 函数 | 列表 | 详情 | 搜索 | 通知 | 资料 | 深链 | 测试 |
|---|---|---|---|---|---|---|---|
| `Html.toPlain` | 摘要 | — | blurb | — | — | — | 是 |
| `Html.unescape` | 间接 | 间接 | 间接 | 间接 | — | — | 是 |
| `Html.firstImageSrc` | — | — | — | — | — | — | 是 |
| `Html.isCjkDominant` | — | — | — | — | — | — | — |
| `avatarUrl` / `absolutize` | 缩略图 | 头像 | — | — | 头像 | — | 是 |
| `TimeFmt.relative` | 是 | 是 | 是 | 是 | 是 | — | 否 |
| `TimeFmt.dateOnly` | — | — | — | — | 是 | — | 否 |
| `shortCount` | 是 | 是 | — | 是 | — | — | 否 |
| `TopicLink.parse` | — | — | — | — | — | 是 | 是 |
