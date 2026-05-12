// Package proxy provides a simple reverse-proxy that forwards requests to upstream services.
// The gateway validates the JWT, injects X-Account-ID, then proxies the request.
package proxy

import (
	"fmt"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
)

// Upstream is an upstream service target.
type Upstream struct {
	BaseURL string
	proxy   *httputil.ReverseProxy
}

// NewUpstream creates an Upstream for baseURL (e.g. "http://auth:18001").
func NewUpstream(baseURL string) (*Upstream, error) {
	target, err := url.Parse(baseURL)
	if err != nil {
		return nil, fmt.Errorf("proxy: invalid URL %q: %w", baseURL, err)
	}

	p := httputil.NewSingleHostReverseProxy(target)

	// Rewrite incoming request so it targets the upstream host
	originalDirector := p.Director
	p.Director = func(req *http.Request) {
		originalDirector(req)
		req.Host = target.Host
		// Remove hop-by-hop headers
		req.Header.Del("Connection")
	}

	// Propagate upstream errors as 502 instead of crashing
	p.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadGateway)
		_, _ = w.Write([]byte(`{"error":"upstream unavailable"}`))
	}

	return &Upstream{BaseURL: baseURL, proxy: p}, nil
}

// Handler returns an http.Handler that strips a path prefix and proxies the request.
// stripPrefix should match what chi is mounting, e.g. "/api/auth".
func (u *Upstream) Handler(stripPrefix string) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Strip the gateway-specific prefix; upstream uses its own /v1/... path
		r.URL.Path = strings.TrimPrefix(r.URL.Path, stripPrefix)
		if r.URL.Path == "" {
			r.URL.Path = "/"
		}
		u.proxy.ServeHTTP(w, r)
	})
}

// ServeHTTP directly proxies without prefix stripping.
func (u *Upstream) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	u.proxy.ServeHTTP(w, r)
}
