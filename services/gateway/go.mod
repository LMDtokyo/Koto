module github.com/koto-messenger/koto/services/gateway

go 1.23.0

require (
	github.com/coder/websocket v1.8.14
	github.com/go-chi/chi/v5 v5.2.1
	github.com/koto-messenger/koto/pkg/logger v0.0.0
	github.com/koto-messenger/koto/pkg/token v0.0.0
	github.com/nats-io/nats.go v1.38.0
	github.com/rs/zerolog v1.33.0
)

require (
	github.com/golang-jwt/jwt/v5 v5.2.1 // indirect
	github.com/klauspost/compress v1.18.0 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	github.com/nats-io/nkeys v0.4.9 // indirect
	github.com/nats-io/nuid v1.0.1 // indirect
	golang.org/x/crypto v0.36.0 // indirect
	golang.org/x/sys v0.31.0 // indirect
)

replace (
	github.com/koto-messenger/koto/pkg/errors => ../../pkg/errors
	github.com/koto-messenger/koto/pkg/logger => ../../pkg/logger
	github.com/koto-messenger/koto/pkg/token => ../../pkg/token
)
