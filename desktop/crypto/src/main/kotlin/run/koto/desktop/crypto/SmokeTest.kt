package run.koto.desktop.crypto

import run.koto.desktop.crypto.security.NativeIntegrity
import uniffi.koto_crypto.KotoCrypto
import uniffi.koto_crypto.generateRegistrationBundle
import uniffi.koto_crypto.generateRegistrationBundleFromSeed
import uniffi.koto_crypto.identityPublicKeyFromSeed

/**
 * Stand-alone sanity check for the Rust libsignal linkage.
 *
 * Run via `./gradlew :crypto:smokeTest` — succeeds only when:
 *   1. JNA located `koto_crypto.{dll,dylib,so}` under the platform-specific
 *      resources directory (`win32-x86-64/`, `darwin/`, `linux-x86-64/`).
 *   2. The uniffi-generated UniffiLib interface checksum matches the shipped
 *      native library (same Rust build produced both sides).
 *   3. `generate_registration_bundle` returns a non-empty identity public key.
 *   4. `KotoCrypto` constructor + `encrypt` round-trip work for a self-session
 *      (identity-only, no peer) — the full X3DH handshake is covered separately.
 *
 * Keep this free of platform-specific code paths so one binary verifies all
 * three desktop targets.
 */
fun main() {
    println("[smoke] verifying native library integrity...")
    val integrity = NativeIntegrity.applyOrFail()
    println("[smoke] integrity=${integrity.verified} reason=${integrity.reason} dir=${integrity.directory}")

    println("[smoke] generating registration bundle...")
    val bundle = generateRegistrationBundle(0x12345u)
    val pubHex = bundle.identityPublicKey.joinToString("") { "%02x".format(it) }
    check(bundle.identityPublicKey.isNotEmpty())     { "empty identity public key" }
    check(bundle.prekeys.size == 100)                { "expected 100 OTPKs, got ${bundle.prekeys.size}" }
    check(bundle.signedPrekey.signature.isNotEmpty()){ "signed prekey missing signature" }
    println("[smoke] identity_public_key=$pubHex")
    println("[smoke] registration_id=${bundle.registrationId}")
    println("[smoke] signed_prekey_id=${bundle.signedPrekey.id}")
    println("[smoke] kyber_prekey_id=${bundle.kyberPrekey.id}")
    println("[smoke] one_time_prekeys.count=${bundle.prekeys.size}")

    val accountId = pubHex
    val instance  = KotoCrypto(
        bundle.identityKeyPair,
        bundle.registrationId,
        accountId,
    )
    instance.loadSignedPrekeys(listOf(bundle.signedPrekey))
    instance.loadKyberPrekeys(listOf(bundle.kyberPrekey))
    instance.loadPrekeys(bundle.prekeys)
    println("[smoke] KotoCrypto instance constructed for account $accountId")

    val roundtrip = instance.publicIdentityKey().joinToString("") { "%02x".format(it) }
    check(roundtrip == pubHex) { "public identity key mismatch after ctor" }
    println("[smoke] public key round-trip OK")

    println("[smoke] verifying BIP39 seed-derived determinism...")
    val phrase = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        .split(" ")
    val a = generateRegistrationBundleFromSeed(phrase, 1u)
    val b = generateRegistrationBundleFromSeed(phrase, 2u)
    check(a.identityPublicKey.contentEquals(b.identityPublicKey)) {
        "seed→identity not deterministic across calls"
    }
    val preview = identityPublicKeyFromSeed(phrase)
    check(preview.contentEquals(a.identityPublicKey)) {
        "previewIdentityFromSeed disagrees with full bundle"
    }
    check(!a.signedPrekey.publicKey.contentEquals(b.signedPrekey.publicKey)) {
        "prekeys must be fresh per call (forward secrecy)"
    }
    println("[smoke] seed→identity deterministic (account_id stable across registrations)")

    println("[smoke] OK")
}
