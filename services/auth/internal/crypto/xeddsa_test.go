package crypto

import (
	"encoding/hex"
	"testing"
)

// Known-good vector lifted from libsignal's own self-test in
// rust/core/src/curve/curve25519.rs (Alice identity key, ephemeral
// public bytes as the message, and the resulting XEdDSA signature).
const (
	aliceIdentityPubHex  = "ab7e717d4a163b7d9a1d8071dfe9dcf8cdcd1cea3339b6356be84d887e322c64"
	aliceEphemeralPubHex = "05edce9d9c415ca78cb7252e72c2c4a554d3eb29485a0e1d503118d1a82d99fb4a"
	aliceSignatureHex    = "5de88ca9a89b4a115da79109c67c9c7464a3e4180274f1cb8c63c2984e286dfbede82deb9dcd9fae0bfbb821569b3d9001bd8130cd11d486cef047bd60b86e88"
)

func mustHex(t *testing.T, s string) []byte {
	t.Helper()
	b, err := hex.DecodeString(s)
	if err != nil {
		t.Fatalf("decode %q: %v", s, err)
	}
	return b
}

func TestVerify_LibsignalAliceVector(t *testing.T) {
	pub := mustHex(t, aliceIdentityPubHex)
	msg := mustHex(t, aliceEphemeralPubHex)
	sig := mustHex(t, aliceSignatureHex)

	if !Verify(pub, msg, sig) {
		t.Fatal("expected libsignal Alice vector to verify")
	}
}

func TestVerify_RejectsTamperedSignature(t *testing.T) {
	pub := mustHex(t, aliceIdentityPubHex)
	msg := mustHex(t, aliceEphemeralPubHex)

	// Flipping any single bit must invalidate the signature. libsignal's
	// own test runs the same loop over all 64 bytes; we sample a handful
	// that exercise R, s, and the sign-bit-bearing s[31].
	for _, idx := range []int{0, 15, 31, 32, 47, 63} {
		sig := mustHex(t, aliceSignatureHex)
		sig[idx] ^= 0x01
		if Verify(pub, msg, sig) {
			t.Fatalf("verification accepted a tampered signature at byte %d", idx)
		}
	}
}

func TestVerify_RejectsTamperedMessage(t *testing.T) {
	pub := mustHex(t, aliceIdentityPubHex)
	sig := mustHex(t, aliceSignatureHex)

	msg := mustHex(t, aliceEphemeralPubHex)
	msg[0] ^= 0x01
	if Verify(pub, msg, sig) {
		t.Fatal("verification accepted a tampered message")
	}
}

func TestVerify_RejectsPublicKeyAtOrAboveP(t *testing.T) {
	msg := mustHex(t, aliceEphemeralPubHex)
	sig := mustHex(t, aliceSignatureHex)

	// p = 2^255 - 19. Test boundary values that all violate u < p.
	cases := map[string][]byte{
		"all_ones": bytesAllOnes(),
		"p_exact":  pLE(),
		"p_plus_1": pLEPlusOne(),
	}
	for name, pub := range cases {
		if Verify(pub, msg, sig) {
			t.Fatalf("verification accepted public key %s (u >= p)", name)
		}
	}
}

func TestVerify_RejectsWrongLengths(t *testing.T) {
	pub := mustHex(t, aliceIdentityPubHex)
	msg := mustHex(t, aliceEphemeralPubHex)
	sig := mustHex(t, aliceSignatureHex)

	if Verify(pub[:31], msg, sig) {
		t.Fatal("accepted 31-byte public key")
	}
	if Verify(append(pub, 0x00), msg, sig) {
		t.Fatal("accepted 33-byte public key")
	}
	if Verify(pub, msg, sig[:63]) {
		t.Fatal("accepted 63-byte signature")
	}
	if Verify(pub, msg, append(sig, 0x00)) {
		t.Fatal("accepted 65-byte signature")
	}
}

func bytesAllOnes() []byte {
	b := make([]byte, 32)
	for i := range b {
		b[i] = 0xFF
	}
	return b
}

func pLE() []byte {
	b := make([]byte, 32)
	b[0] = 0xED
	for i := 1; i < 31; i++ {
		b[i] = 0xFF
	}
	b[31] = 0x7F
	return b
}

func pLEPlusOne() []byte {
	b := pLE()
	// p + 1 = 0xEE,0xFF*30,0x7F (no carry since 0xED+1=0xEE).
	b[0] = 0xEE
	return b
}
