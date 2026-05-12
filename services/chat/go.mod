module github.com/koto-messenger/koto/services/chat

go 1.23

require (
	github.com/go-chi/chi/v5 v5.2.1
	github.com/nats-io/nats.go v1.38.0
	github.com/koto-messenger/koto/pkg/errors v0.0.0
	github.com/koto-messenger/koto/pkg/logger v0.0.0
	github.com/koto-messenger/koto/pkg/token v0.0.0
	github.com/redis/go-redis/v9 v9.7.3
	github.com/rs/zerolog v1.33.0
	github.com/gocql/gocql v1.7.0
	github.com/gofrs/uuid/v5 v5.3.0
)

replace (
	github.com/koto-messenger/koto/pkg/errors => ../../pkg/errors
	github.com/koto-messenger/koto/pkg/logger => ../../pkg/logger
	github.com/koto-messenger/koto/pkg/token  => ../../pkg/token
)
