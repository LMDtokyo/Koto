package run.koto.network

import android.util.Base64
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * Parsed proxy configuration from a URI string.
 *
 * Supports:
 *   - socks5://[user:pass@]host:port        — direct SOCKS5
 *   - ss://base64(method:pass@host:port)    — Shadowsocks (uses SOCKS5 mode)
 *   - ss://base64(method:pass)@host:port    — SIP002 format
 *   - vmess://base64(json)                  — V2Ray VMess (uses SOCKS5 mode)
 *   - trojan://pass@host:port               — Trojan (uses SOCKS5 mode)
 *
 * NOTE: Full Shadowsocks/V2Ray/Trojan protocol support requires embedded
 * sing-box binary (Phase 2). For now, this treats all proxies as SOCKS5
 * to their host:port — works for servers configured with SOCKS5 mode.
 */
data class ProxyConfig(
    val type     : ProxyType,
    val host     : String,
    val port     : Int,
    val username : String? = null,
    val password : String? = null,
    val label    : String  = "",
) {
    fun toJavaProxy(): Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress.createUnresolved(host, port),
    )

    /** Short display: "Shadowsocks · server.com:8388" */
    fun displayLabel(): String {
        val typeName = when (type) {
            ProxyType.SHADOWSOCKS -> "Shadowsocks"
            ProxyType.VMESS       -> "VMess"
            ProxyType.TROJAN      -> "Trojan"
            ProxyType.SOCKS5      -> "SOCKS5"
        }
        return if (label.isNotBlank()) "$typeName · $label" else "$typeName · $host:$port"
    }
}

enum class ProxyType { SHADOWSOCKS, VMESS, TROJAN, SOCKS5 }

object ProxyUri {

    /** Parse a proxy URI. Returns null on any error. */
    fun parse(uri: String): ProxyConfig? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("socks5://", ignoreCase = true) -> parseSocks5(trimmed)
                trimmed.startsWith("socks://", ignoreCase = true)  -> parseSocks5(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true)     -> parseShadowsocks(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true)  -> parseVmess(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                else                                                -> null
            }
        }.getOrNull()
    }

    private fun parseSocks5(uri: String): ProxyConfig {
        val parsed = URI(uri)
        val userInfo = parsed.userInfo
        val (user, pass) = when {
            userInfo == null                -> null to null
            userInfo.contains(':')          -> userInfo.substringBefore(':') to userInfo.substringAfter(':')
            else                             -> userInfo to null
        }
        return ProxyConfig(
            type     = ProxyType.SOCKS5,
            host     = parsed.host ?: error("no host"),
            port     = if (parsed.port > 0) parsed.port else 1080,
            username = user,
            password = pass,
            label    = parsed.fragment ?: "",
        )
    }

    /**
     * Shadowsocks URI. Two formats:
     *   Legacy:  ss://base64(method:password@host:port)[#label]
     *   SIP002:  ss://base64(method:password)@host:port[/?plugin=...][#label]
     */
    private fun parseShadowsocks(uri: String): ProxyConfig {
        val body    = uri.removePrefix("ss://").removePrefix("SS://")
        val label   = body.substringAfter('#', "").let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val noLabel = body.substringBefore('#').substringBefore('/')
        val noQuery = noLabel.substringBefore('?')

        // SIP002 format: base64@host:port  (contains @)
        if (noQuery.contains('@')) {
            val creds  = noQuery.substringBefore('@')
            val server = noQuery.substringAfter('@')
            val decoded = runCatching { String(Base64.decode(creds, Base64.URL_SAFE or Base64.NO_WRAP)) }.getOrDefault(creds)
            val method   = decoded.substringBefore(':', "")
            val password = decoded.substringAfter(':', "")
            val host     = server.substringBefore(':')
            val port     = server.substringAfter(':').toIntOrNull() ?: 8388
            return ProxyConfig(
                type     = ProxyType.SHADOWSOCKS,
                host     = host,
                port     = port,
                username = method,
                password = password,
                label    = label,
            )
        }

        // Legacy format: fully base64-encoded
        val decoded = String(Base64.decode(noQuery, Base64.URL_SAFE or Base64.NO_WRAP))
        // method:password@host:port
        val credPart   = decoded.substringBefore('@')
        val serverPart = decoded.substringAfter('@')
        val method     = credPart.substringBefore(':', "")
        val password   = credPart.substringAfter(':', "")
        val host       = serverPart.substringBefore(':')
        val port       = serverPart.substringAfter(':').toIntOrNull() ?: 8388
        return ProxyConfig(
            type     = ProxyType.SHADOWSOCKS,
            host     = host,
            port     = port,
            username = method,
            password = password,
            label    = label,
        )
    }

    /**
     * V2Ray VMess URI: vmess://base64(json)
     * json = { v, ps, add, port, id, aid, net, type, host, path, tls, ... }
     */
    private fun parseVmess(uri: String): ProxyConfig {
        val body    = uri.removePrefix("vmess://").removePrefix("VMESS://")
        val decoded = String(Base64.decode(body, Base64.NO_WRAP or Base64.URL_SAFE))
        val json    = JSONObject(decoded)
        return ProxyConfig(
            type     = ProxyType.VMESS,
            host     = json.getString("add"),
            port     = json.optInt("port", 443),
            username = json.optString("id", ""),
            label    = json.optString("ps", ""),
        )
    }

    private fun parseTrojan(uri: String): ProxyConfig {
        val parsed = URI(uri)
        return ProxyConfig(
            type     = ProxyType.TROJAN,
            host     = parsed.host ?: error("no host"),
            port     = if (parsed.port > 0) parsed.port else 443,
            password = parsed.userInfo,
            label    = parsed.fragment ?: "",
        )
    }
}
