package run.koto.desktop.ui.screens.auth

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.Mnemonics.MnemonicCode
import cash.z.ecc.android.bip39.Mnemonics.WordCount

/**
 * BIP39 phrase length used across register/login. 12 words = 128 bits of
 * entropy, the default in MetaMask, Trezor, Phantom, Ledger.
 */
const val SEED_WORD_COUNT = 12

/**
 * Official BIP39 English wordlist (2048 words). Used for typo-highlight in the
 * recovery screen — real identity derivation happens in the Rust crypto crate
 * via [run.koto.desktop.crypto.KotoCryptoProvider.previewIdentityFromSeed].
 */
val KOTO_WORDS: List<String> by lazy { Mnemonics.getCachedWords("en") }

/** Generate a fresh checksummed BIP39 12-word phrase. */
fun pickSeed(): List<String> = MnemonicCode(WordCount.COUNT_12).words.map { String(it) }

/** Stages of the first-run flow. */
enum class AuthStage { Welcome, Register, Login }

/** Registration sub-step. */
enum class RegisterStep { ShowSeed, ConfirmSeed, EnterName }
