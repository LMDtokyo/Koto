# syntax=docker/dockerfile:1
FROM golang:1.24-alpine AS builder
WORKDIR /build
COPY go.work go.work.sum* ./
COPY pkg/logger/go.mod      pkg/logger/go.sum*      ./pkg/logger/
COPY pkg/errors/go.mod      pkg/errors/go.sum*      ./pkg/errors/
COPY pkg/token/go.mod       pkg/token/go.sum*       ./pkg/token/
COPY services/gateway/go.mod      services/gateway/go.sum*      ./services/gateway/
COPY services/auth/go.mod         services/auth/go.sum*         ./services/auth/
COPY services/chat/go.mod         services/chat/go.sum*         ./services/chat/
COPY services/user/go.mod         services/user/go.sum*         ./services/user/
COPY services/notification/go.mod services/notification/go.sum* ./services/notification/
COPY services/media/go.mod        services/media/go.sum*        ./services/media/
COPY services/bot/go.mod          services/bot/go.sum*          ./services/bot/
COPY services/moderation/go.mod   services/moderation/go.sum*   ./services/moderation/
RUN --mount=type=cache,target=/root/.cache/go --mount=type=cache,target=/go/pkg/mod go work sync
COPY pkg/ ./pkg/
COPY services/user/ ./services/user/
RUN --mount=type=cache,target=/root/.cache/go --mount=type=cache,target=/go/pkg/mod \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /app/user ./services/user/cmd/server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=builder /app/user /user
EXPOSE 18003
USER nonroot:nonroot
ENTRYPOINT ["/user"]
