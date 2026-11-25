package org.linuxdo.android.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DoH 配置。部分网络环境下 linux.do 的 A 记录会被污染，
 * 走 HTTPS 查询可以绕过（与 iOS 版的内置 DoH 列表同一思路）。
 *
 * 主解析器用阿里 223.5.5.5，失败回落 Google。两者都用 IP 直连做 bootstrap，
 * 避免「解析器自己的域名还要靠系统 DNS」。
 */
object Doh {

    private val client: OkHttpClient by lazy { OkHttpClient() }

    private val primary: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(client)
            .url("https://223.5.5.5/dns-query".toHttpUrlOrNull()!!)
            .bootstrapDnsHosts(InetAddress.getByName("223.5.5.5"))
            .build()
    }

    private val fallback: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(client)
            .url("https://dns.google/dns-query".toHttpUrlOrNull()!!)
            .build()
    }

    val resolver: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            try {
                primary.lookup(hostname)
            } catch (e: Exception) {
                fallback.lookup(hostname)
            }
    }

    fun dns(): Dns = resolver
}
