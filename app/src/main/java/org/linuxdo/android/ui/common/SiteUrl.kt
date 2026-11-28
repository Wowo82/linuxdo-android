package org.linuxdo.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.util.absolutize
import org.linuxdo.android.util.avatarUrl

/**
 * 当前站点地址。
 *
 * 头像 / 缩略图 / 正文图片都必须落到**用户配置的站点**：
 * 原来 `avatarUrl` / `absolutize` 把域名写死成 `https://linux.do`，
 * 「我的 → 服务器地址」改成自建 Discourse 后图片全指向官方站（404）。
 *
 * 用 CompositionLocal 而不是每次读 SharedPreferences：
 * 后者是同步 IO，且在 Compose 里读不到「值变了」——切站点后不会触发重组。
 */
val LocalSiteBaseUrl = compositionLocalOf { BuildConfig.DEFAULT_BASE_URL }

@Composable
fun siteBaseUrl(): String = LocalSiteBaseUrl.current

/** 把 cooked 正文里的相对图片地址补成当前站点的绝对地址 */
@Composable
fun absoluteImageUrl(url: String?): String? = absolutize(url, siteBaseUrl())

/** `avatar_template` 按当前站点展开成指定尺寸的头像地址 */
@Composable
fun avatarUrlOf(template: String?, size: Int): String? =
    avatarUrl(template, size, siteBaseUrl())
