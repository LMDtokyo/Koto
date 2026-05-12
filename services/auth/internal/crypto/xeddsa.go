// Package crypto implements server-side cryptographic primitives the auth
// service needs in addition to JWT issuance, namely XEdDSA signature
// verification over Curve25519 X25519 identity keys.
package crypto

import (
	"crypto/sha512"

	"filippo.io/edwards25519"
	"filippo.io/edwards25519/field"
)

// Verify checks an XEdDSA signature produced by libsignal's
// PrivateKey::calculate_signature against a 32-byte Curve25519 X25519
// public key (Montgomery u-coordinate, no DJB prefix).
//
// Returns false on any decoding or verification failure (no panic).
// Spec: https://signal.org/docs/specifications/xeddsa/
//
// The libsignal variant differs from the published spec in one detail:
// the sign bit of the Edwards public key A is not fixed to 0, but is
// stuffed into the MSB of signature[63] by the signer and used here when
// reconstructing A. See libsignal rust/core/src/curve/curve25519.rs.
func Verify(publicKey, message, signature []byte) bool {
	if len(publicKey) != 32 || len(signature) != 64 {
		return false
	}

	// Spec step: reject u >= p. u is the raw 32-byte input as a
	// little-endian integer, so even setting the high bit makes u >= p.
	if !uLessThanP(publicKey) {
		return false
	}

	// Decompose the signature: R || s. The MSB of s carries A's sign bit
	// in the libsignal variant; clear it before treating s as a scalar.
	var r [32]byte
	copy(r[:], signature[:32])
	var s [32]byte
	copy(s[:], signature[32:])
	signBit := s[31] & 0x80
	s[31] &= 0x7F
	// After clearing the sign bit, the top three bits of s must be zero
	// (equivalent to s < 2^253, a fast canonical-encoding gate matching
	// libsignal's check before scalar reduction).
	if s[31]&0xE0 != 0 {
		return false
	}

	scalarS, err := edwards25519.NewScalar().SetCanonicalBytes(s[:])
	if err != nil {
		return false
	}

	// Birational map u -> y: y = (u-1) * (u+1)^{-1} mod p. inv(0)=0 by
	// the field package's contract, which matches the XEdDSA spec.
	var u field.Element
	if _, err := u.SetBytes(publicKey); err != nil {
		return false
	}
	var one field.Element
	one.One()
	var num, den, y field.Element
	num.Subtract(&u, &one)
	den.Add(&u, &one)
	den.Invert(&den)
	y.Multiply(&num, &den)

	// Encode A as a compressed Edwards point (32-byte little-endian y
	// with the sign bit in the MSB of byte 31). Decoding it back yields
	// the full point and validates that it lies on the curve.
	aBytes := y.Bytes()
	aBytes[31] = (aBytes[31] & 0x7F) | signBit
	pointA, err := (&edwards25519.Point{}).SetBytes(aBytes)
	if err != nil {
		return false
	}

	// h = SHA-512(R || A || M) reduced mod l.
	hasher := sha512.New()
	hasher.Write(r[:])
	hasher.Write(aBytes)
	hasher.Write(message)
	var hashOut [64]byte
	hasher.Sum(hashOut[:0])
	scalarH, err := edwards25519.NewScalar().SetUniformBytes(hashOut[:])
	if err != nil {
		return false
	}

	// R_check = s*B - h*A. VarTimeDoubleScalarBaseMult computes a*A + b*B,
	// so feed it (-h, A, s).
	negH := edwards25519.NewScalar().Negate(scalarH)
	rCheck := (&edwards25519.Point{}).VarTimeDoubleScalarBaseMult(negH, pointA, scalarS)

	rCheckBytes := rCheck.Bytes()
	var diff byte
	for i := 0; i < 32; i++ {
		diff |= rCheckBytes[i] ^ r[i]
	}
	return diff == 0
}

// uLessThanP reports whether the 32-byte little-endian integer u is
// strictly less than the Curve25519 field prime p = 2^255 - 19.
func uLessThanP(u []byte) bool {
	// p in little-endian: 0xED,0xFF*30,0x7F. Compare from MSB down.
	pLE := [32]byte{
		0xED, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
		0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F,
	}
	for i := 31; i >= 0; i-- {
		if u[i] < pLE[i] {
			return true
		}
		if u[i] > pLE[i] {
			return false
		}
	}
	return false
}
