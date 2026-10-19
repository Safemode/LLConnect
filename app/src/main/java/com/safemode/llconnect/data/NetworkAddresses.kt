package com.safemode.llconnect.data

/**
 * Best-effort check of whether a host is on a private/local network. Used to decide whether to warn
 * about sending credentials over plain HTTP: a LAN LubeLogger over http is normal, but plain http
 * to a public host sends the API key or password in the clear.
 *
 * Treated as local: loopback, the RFC1918 private ranges, link-local, CGNAT (100.64/10), unique
 * local IPv6 (fc00::/7), the IPv6 loopback, bare hostnames with no dot (LAN names), and the common
 * local TLD suffixes (.local, .lan, .home, .internal).
 */
object NetworkAddresses {

    private val localSuffixes = listOf(".local", ".lan", ".home", ".internal", ".localhost")

    fun isLikelyLocal(host: String): Boolean {
        val h = host.trim().removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore(':').lowercase()
        if (h.isEmpty()) return true // nothing entered yet - don't warn
        if (h == "localhost") return true
        if (localSuffixes.any { h.endsWith(it) }) return true
        if (!h.contains('.') && !h.contains(':')) return true // bare hostname -> LAN

        // IPv6 private/loopback.
        if (h.contains(':')) {
            return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")
        }

        val octets = h.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (a, b) = octets[0] to octets[1]
        return when {
            a == 127 -> true                      // loopback
            a == 10 -> true                        // 10.0.0.0/8
            a == 192 && b == 168 -> true           // 192.168.0.0/16
            a == 172 && b in 16..31 -> true        // 172.16.0.0/12
            a == 169 && b == 254 -> true           // link-local
            a == 100 && b in 64..127 -> true       // CGNAT 100.64.0.0/10
            else -> false
        }
    }
}
