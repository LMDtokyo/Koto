// keygen generates a new Ed25519 keypair for JWT signing.
// Usage: go run infra/scripts/keygen/main.go
package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"os"
)

func main() {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}

	seed := priv.Seed()

	fmt.Println("# Add these to your .env file:")
	fmt.Printf("JWT_PRIVATE_KEY_SEED=%s\n", hex.EncodeToString(seed))
	fmt.Printf("JWT_PUBLIC_KEY=%s\n", hex.EncodeToString(pub))
}
