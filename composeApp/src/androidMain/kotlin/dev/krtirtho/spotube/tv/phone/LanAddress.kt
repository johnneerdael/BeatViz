/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.krtirtho.spotube.tv.phone

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Picks the IPv4 address a phone on the same Wi-Fi can reach (ported from
 * BeatVisualizer / Flow-TV). Lower rank is better; null rejects the address.
 */
object LanAddress {
    private val virtualPrefixes = listOf("tun", "tap", "utun", "wg", "ppp", "veth", "br-", "zt")
    private val virtualSubstrings = listOf(
        "docker", "virbr", "vboxnet", "vmnet", "tailscale", "vethernet", "mullvad", "wsl",
    )

    fun resolve(): String? = runCatching {
        val pairs = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { addr -> addr.hostAddress?.let { nif.name to it } }
            }
        rank(pairs).firstOrNull()
    }.getOrNull()

    fun rank(interfaces: List<Pair<String, String>>): List<String> =
        interfaces.mapIndexedNotNull { index, (name, ip) -> rankOf(name, ip)?.let { Triple(it, index, ip) } }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }

    private fun isVirtual(name: String): Boolean {
        val n = name.lowercase()
        return virtualPrefixes.any { n.startsWith(it) } || virtualSubstrings.any { n.contains(it) }
    }

    private fun rankOf(name: String, ip: String): Int? {
        val octets = ip.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return null
        val a = octets[0]!!
        val b = octets[1]!!
        val range = when {
            a == 127 || (a == 169 && b == 254) || a == 0 || a in 224..239 -> return null
            a == 192 && b == 168 -> 0
            a == 10 -> 1
            a == 172 && b == 17 -> 4 // Docker's default bridge: demoted, not rejected
            a == 172 && b in 16..31 -> 2
            a == 100 && b in 64..127 -> 5 // CGNAT / Tailscale
            else -> 3
        }
        return if (isVirtual(name)) 8 + range else range
    }
}
