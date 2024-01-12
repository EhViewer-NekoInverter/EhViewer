/*
 * Copyright 2018 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.client

import com.hippo.ehviewer.Settings
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.internal.platform.Platform
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException

object EhDns : Dns {
    private val dnsIP = listOf(
        InetAddress.getByName("162.159.36.1"),
        InetAddress.getByName("162.159.46.1"),
        // https://r.android.com/1756590
        InetAddress.getByName("104.16.248.249"),
        InetAddress.getByName("104.16.249.249"),
        InetAddress.getByName("2606:4700::6810:f8f9"),
        InetAddress.getByName("2606:4700::6810:f9f9"),
    )
    private val dnsClient = OkHttpClient.Builder().apply {
        proxy(Proxy.NO_PROXY)
        sslSocketFactory(EhSSLSocketFactory(), Platform.get().platformTrustManager())
    }.build()
    private val doh = DnsOverHttps.Builder().apply {
        client(dnsClient)
        url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        bootstrapDnsHosts(dnsIP)
        resolvePrivateAddresses(true)
    }.build()

    // origin server IP, not cloudflare IP
    private val exIP = listOf(
        InetAddress.getByName("178.175.128.251"),
        InetAddress.getByName("178.175.128.252"),
        InetAddress.getByName("178.175.128.253"),
        InetAddress.getByName("178.175.128.254"),
        InetAddress.getByName("178.175.129.251"),
        InetAddress.getByName("178.175.129.252"),
        InetAddress.getByName("178.175.129.253"),
        InetAddress.getByName("178.175.129.254"),
        InetAddress.getByName("178.175.132.19"),
        InetAddress.getByName("178.175.132.20"),
        InetAddress.getByName("178.175.132.21"),
        InetAddress.getByName("178.175.132.22"),
    )

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        if (!Settings.doH) {
            return Dns.SYSTEM.lookup(hostname)
        }
        if (hostname == "exhentai.org" || hostname.endsWith(".exhentai.org")) {
            return exIP.shuffled()
        }
        try {
            return doh.lookup(hostname)
        } catch (e: UnknownHostException) {
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}
