package run.koto.desktop.domain.model

/**
 * User-configurable proxy for outbound traffic. Today we support only SOCKS5 and HTTP/HTTPS
 * — the two primitives every transport layer already understands. Exotic schemes like
 * `ss://` or `vmess://` are out of scope; they require an external local daemon and do not
 * belong in the client process.
 */
sealed interface UserProxy {
    val host : String
    val port : Int

    data class Socks5(
        override val host : String,
        override val port : Int,
        val username : String? = null,
        val password : String? = null,
    ) : UserProxy

    data class Http(
        override val host : String,
        override val port : Int,
        val username : String? = null,
        val password : String? = null,
    ) : UserProxy
}

enum class TorState {
    /** Tor is off. All traffic goes through [UserProxy] (if any) or direct. */
    DISABLED,

    /** Daemon starting, bootstrap incomplete. Outbound traffic blocked while in this state. */
    STARTING,

    /** Daemon bootstrapped, SOCKS port open. All traffic should go through Tor. */
    RUNNING,

    /** Start-up failed. Caller should surface the error; traffic falls back per [TransportMode.ON_FAILURE]. */
    FAILED,
}

/**
 * Persisted network preferences.
 *
 *   - [torEnabled]: user toggled Tor on. Combined with [TorState] at runtime to pick transport.
 *   - [userProxy] : user-supplied proxy, used when Tor is off. Ignored when Tor is running.
 */
data class NetworkPreferences(
    val torEnabled : Boolean    = false,
    val userProxy  : UserProxy? = null,
)
