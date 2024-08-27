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

import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Hosts
import com.hippo.ehviewer.Settings
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

object EhDns : Dns {
    private val hosts = EhApplication.hosts
    private val builtInHosts: MutableMap<String, List<InetAddress>> = mutableMapOf()

    init {
        /* Pair(ip: String!, blockedByCCP: Boolean!) */
        val ehHosts = arrayOf(
            Pair("104.20.18.168", false),
            Pair("104.20.19.168", false),
            Pair("172.67.2.238", false),
        )
        val ehgtHosts = arrayOf(
            Pair("81.171.10.48", false),
            Pair("178.162.139.24", false),
            Pair("62.112.8.21", false),
            Pair("89.39.106.43", false),
            Pair("109.236.85.28", false),
            Pair("2a00:7c80:0:123::3a85", false),
            Pair("2a00:7c80:0:12d::38a1", false),
            Pair("2a00:7c80:0:13b::37a4", false),
        )
        val exHosts = arrayOf(
            Pair("178.175.128.251", false),
            Pair("178.175.128.252", false),
            Pair("178.175.128.253", false),
            Pair("178.175.128.254", false),
            Pair("178.175.129.251", false),
            Pair("178.175.129.252", false),
            Pair("178.175.129.253", false),
            Pair("178.175.129.254", false),
            Pair("178.175.132.19", false),
            Pair("178.175.132.20", false),
            Pair("178.175.132.21", false),
            Pair("178.175.132.22", false),
        )

        put(
            "e-hentai.org",
            *ehHosts,
        )
        put(
            "forums.e-hentai.org",
            *ehHosts,
        )
        put(
            "repo.e-hentai.org",
            *ehHosts,
        )
        put(
            "api.e-hentai.org",
            *ehHosts,
            Pair("5.79.104.110", false),
            Pair("37.48.81.204", false),
            Pair("37.48.92.161", false),
            Pair("212.7.200.104", false),
            Pair("212.7.202.51", false),
        )
        put(
            "ehgt.org",
            *ehgtHosts,
        )
        put(
            "gt0.ehgt.org",
            *ehgtHosts,
        )
        put(
            "gt1.ehgt.org",
            *ehgtHosts,
        )
        put(
            "gt2.ehgt.org",
            *ehgtHosts,
        )
        put(
            "gt3.ehgt.org",
            *ehgtHosts,
        )
        put(
            "ul.ehgt.org",
            *ehgtHosts,
        )

        put(
            "exhentai.org",
            *exHosts,
        )
        put(
            "s.exhentai.org",
            *exHosts,
        )

        put(
            "raw.githubusercontent.com",
            Pair("151.101.0.133", false),
            Pair("151.101.64.133", false),
            Pair("151.101.128.133", false),
            Pair("151.101.192.133", false),
        )
    }

    private fun put(
        host: String,
        vararg ips: Pair<String, Boolean>,
    ) {
        builtInHosts[host] = ips.mapNotNull { pair ->
            Hosts.toInetAddress(host, pair.first)
        }
    }

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        val address = hosts[hostname] ?: builtInHosts[hostname].takeIf { Settings.builtInHosts }
            ?: Dns.SYSTEM.lookup(hostname)
        return address.shuffled()
    }
}
