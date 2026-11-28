package org.linuxdo.android.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * DoH 配置。部分网络环境下 linux.do 的 A 记录会被污染，
 * 走 HTTPS 查询可以绕过（与 iOS 版的内置 DoH 列表同一思路）。
 *
 * 主路用始皇的 DoH（子域可任意替换），备用 1mol氚的 DoH；
 * 两路都失败时回落系统 DNS。只接管 linux.do / *.linux.do，
 * 自建站点与图片 CDN 仍走系统解析。
 */
object Doh {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun doh(url: String): DnsOverHttps = DnsOverHttps.Builder()
        .client(client)
        .url(url.toHttpUrlOrNull()!!)
        .build()

    /** 始皇的 DoH：`xxx` 可以随便换，例如 linuxdo / ios-app */
    private val primary: DnsOverHttps by lazy {
        doh("https://linuxdo.ddd.oaifree.com/query-dns")
    }

    /** 1mol氚的 DoH */
    private val fallback: DnsOverHttps by lazy {
        doh("https://edge.1molchuan.top/linuxdo")
    }

    private fun needsCustomDns(hostname: String): Boolean =
        hostname == "linux.do" || hostname.endsWith(".linux.do")

    val resolver: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!needsCustomDns(hostname)) return Dns.SYSTEM.lookup(hostname)
            for (dohResolver in listOf(primary, fallback)) {
                try {
                    val addresses = dohResolver.lookup(hostname)
                    if (addresses.isNotEmpty()) return addresses
                } catch (_: Exception) {
                    // 换下一路
                }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    fun dns(): Dns = resolver
}
