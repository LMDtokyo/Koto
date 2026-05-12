module github.com/koto-messenger/koto/services/notification

go 1.23.0

require (
	github.com/go-chi/chi/v5 v5.2.1
	github.com/jackc/pgx/v5 v5.7.2
	github.com/koto-messenger/koto/pkg/errors v0.0.0
	github.com/koto-messenger/koto/pkg/logger v0.0.0
	github.com/nats-io/nats.go v1.38.0
	github.com/sideshow/apns2 v0.25.0
)

require (
	github.com/golang-jwt/jwt/v4 v4.4.1 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	github.com/klauspost/compress v1.18.0 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	github.com/nats-io/nkeys v0.4.9 // indirect
	github.com/nats-io/nuid v1.0.1 // indirect
	github.com/rs/zerolog v1.33.0 // indirect
	github.com/stretchr/testify v1.9.0 // indirect
	golang.org/x/crypto v0.36.0 // indirect
	golang.org/x/net v0.38.0 // indirect
	golang.org/x/sync v0.12.0 // indirect
	golang.org/x/sys v0.31.0 // indirect
	golang.org/x/text v0.23.0 // indirect
)

replace (
	github.com/koto-messenger/koto/pkg/errors => ../../pkg/errors
	github.com/koto-messenger/koto/pkg/logger => ../../pkg/logger
)
