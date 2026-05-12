module github.com/koto-messenger/koto/services/auth

go 1.24.0

require (
	github.com/go-chi/chi/v5 v5.2.1
	github.com/jackc/pgx/v5 v5.7.2
	github.com/koto-messenger/koto/pkg/errors v0.0.0
	github.com/koto-messenger/koto/pkg/logger v0.0.0
	github.com/koto-messenger/koto/pkg/token v0.0.0
	github.com/redis/go-redis/v9 v9.7.3
	github.com/rs/zerolog v1.33.0
)

require github.com/stretchr/testify v1.9.0 // indirect

require (
	filippo.io/edwards25519 v1.2.0
	github.com/cespare/xxhash/v2 v2.2.0 // indirect
	github.com/dgryski/go-rendezvous v0.0.0-20200823014737-9f7001d12a5f // indirect
	github.com/golang-jwt/jwt/v5 v5.2.1 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	golang.org/x/crypto v0.36.0 // indirect
	golang.org/x/sync v0.12.0 // indirect
	golang.org/x/sys v0.31.0 // indirect
	golang.org/x/text v0.23.0 // indirect
)

replace (
	github.com/koto-messenger/koto/pkg/errors => ../../pkg/errors
	github.com/koto-messenger/koto/pkg/logger => ../../pkg/logger
	github.com/koto-messenger/koto/pkg/token => ../../pkg/token
)
