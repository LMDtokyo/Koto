package run.koto.desktop.data.remote

import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS trust manager that pins the gateway's certificate by its Subject Public Key Info
 * (SPKI) SHA-256. Pinning on the public key rather than the leaf cert means key rotation
 * is safe as long as the CSR is signed by the same long-lived private key — the pin does
 * NOT need to be updated when the server renews its certificate.
 *
 * Two lines of defence:
 *   1. Platform chain validation — [defaultTrustManager] still runs first. A cert that
 *      fails normal CA validation is rejected before pin check.
 *   2. Pin match — at least one certificate in the chain must match one of [pinnedSpkiSha256].
 *
 * Bypasses chain validation for [bypassHosts] (dev localhost / 127.0.0.1) so local
 * `docker compose up` development doesn't require a trusted cert. Production hosts are
 * always pinned.
 */
class PinnedTrustManager(
    private val pinnedSpkiSha256 : Set<String>,
    private val bypassHosts      : Set<String> = emptySet(),
    private val defaultTrustManager: X509TrustManager = platformDefault(),
) : X509TrustManager {

    private val log = LoggerFactory.getLogger(PinnedTrustManager::class.java)

    override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTrustManager.acceptedIssuers

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        defaultTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        defaultTrustManager.checkServerTrusted(chain, authType)

        if (pinnedSpkiSha256.isEmpty()) return

        val leaf = chain.firstOrNull() ?: throw CertificateException("empty chain")
        val peerHost = leaf.subjectX500Principal.name.commonName()
        if (peerHost != null && peerHost in bypassHosts) {
            log.debug("skipping pin check for bypass host {}", peerHost)
            return
        }

        val matches = chain.any { cert ->
            val spkiHash = sha256(cert.publicKey.encoded)
            val hex      = spkiHash.toHex()
            if (hex in pinnedSpkiSha256) true else false
        }
        if (!matches) {
            val presented = chain.joinToString(",") { sha256(it.publicKey.encoded).toHex() }
            throw CertificateException("pin failure: no SPKI in chain matched. presented=$presented pinned=$pinnedSpkiSha256")
        }
    }

    companion object {
        private fun platformDefault(): X509TrustManager {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun ByteArray.toHex(): String {
            val hex = "0123456789abcdef"
            val out = CharArray(size * 2)
            for (i in indices) {
                val b = this[i].toInt() and 0xFF
                out[i * 2]     = hex[b ushr 4]
                out[i * 2 + 1] = hex[b and 0x0F]
            }
            return String(out)
        }

        private fun String.commonName(): String? =
            split(",").firstOrNull { it.trim().startsWith("CN=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
    }
}
