// Package middleware provides gateway-level HTTP middleware.
package middleware

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/koto-messenger/koto/pkg/token"
)

// JWTAuth validates the Authorization: Bearer <token> header and injects
// X-Account-ID into the request before forwarding to upstream services.
func JWTAuth(mgr *token.Manager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				writeError(w, http.StatusUnauthorized, "missing Authorization header")
				return
			}

			parts := strings.SplitN(authHeader, " ", 2)
			if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
				writeError(w, http.StatusUnauthorized, "invalid Authorization format")
				return
			}

			claims, err := mgr.Verify(parts[1])
			if err != nil {
				writeError(w, http.StatusUnauthorized, "invalid or expired token")
				return
			}

			// Inject verified account ID for upstream services
			r.Header.Set("X-Account-ID", claims.AccountID)

			next.ServeHTTP(w, r)
		})
	}
}

// RateLimit is a placeholder for a token-bucket rate limiter.
// Replace with a real implementation (e.g. golang.org/x/time/rate) before production.
func RateLimit(next http.Handler) http.Handler {
	return next // TODO: implement per-account rate limiting
}

func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": msg})
}
