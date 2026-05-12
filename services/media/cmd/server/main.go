package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
	"github.com/koto-messenger/koto/pkg/logger"
	"github.com/koto-messenger/koto/pkg/token"
	"github.com/koto-messenger/koto/services/media/internal/domain"
)

var version = "dev"

const bucket = "koto-media"

func main() {
	log := logger.New("media", version, os.Getenv("LOG_LEVEL") == "debug")
	ctx := context.Background()

	// ─── Postgres ─────────────────────────────────────────────────────────────
	pool, err := pgxpool.New(ctx, mustEnv("POSTGRES_DSN"))
	if err != nil {
		log.Fatal().Err(err).Msg("postgres connect failed")
	}
	defer pool.Close()

	// mc talks to MinIO via the internal Docker hostname.
	// mcPublic signs URLs for the public endpoint clients can reach.
	mc, err := minio.New(mustEnv("MINIO_ENDPOINT"), &minio.Options{
		Creds:  credentials.NewStaticV4(mustEnv("MINIO_ACCESS_KEY"), mustEnv("MINIO_SECRET_KEY"), ""),
		Secure: os.Getenv("MINIO_USE_SSL") == "true",
	})
	if err != nil {
		log.Fatal().Err(err).Msg("minio client init failed")
	}

	publicEndpoint := os.Getenv("MINIO_PUBLIC_ENDPOINT")
	if publicEndpoint == "" {
		publicEndpoint = mustEnv("MINIO_ENDPOINT")
	}
	publicSecure := os.Getenv("MINIO_PUBLIC_USE_SSL") == "true"

	// Region must be set — otherwise the first presign call does
	// GET /{bucket}/?location= against the public endpoint which is
	// unreachable from inside the container.
	mcPublic, err := minio.New(publicEndpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(mustEnv("MINIO_ACCESS_KEY"), mustEnv("MINIO_SECRET_KEY"), ""),
		Secure: publicSecure,
		Region: "us-east-1",
	})
	if err != nil {
		log.Fatal().Err(err).Msg("minio public client init failed")
	}

	// Ensure bucket exists
	exists, _ := mc.BucketExists(ctx, bucket)
	if !exists {
		if err := mc.MakeBucket(ctx, bucket, minio.MakeBucketOptions{}); err != nil {
			log.Fatal().Err(err).Str("bucket", bucket).Msg("create bucket failed")
		}
	}
	log.Info().Str("bucket", bucket).Msg("minio ready")

	// ─── Token manager ────────────────────────────────────────────────────────
	tokenMgr, err := token.NewManagerFromPublicKey(mustEnv("JWT_PUBLIC_KEY"))
	if err != nil {
		log.Fatal().Err(err).Msg("token manager init failed")
	}

	// ─── Router ───────────────────────────────────────────────────────────────
	r := chi.NewRouter()
	r.Use(middleware.RequestID, middleware.RealIP, middleware.Recoverer)

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"status": "ok"})
	})

	r.Route("/v1/media", func(r chi.Router) {
		r.Use(jwtMiddleware(tokenMgr))

		// Request presigned upload URL
		r.Post("/upload-url", func(w http.ResponseWriter, r *http.Request) {
			var body struct {
				ContentType string `json:"content_type"`
				SizeBytes   int64  `json:"size_bytes"`
				IsPublic    bool   `json:"is_public"`
			}
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				writeError(w, 400, "invalid body")
				return
			}
			if body.SizeBytes > 100*1024*1024 { // 100 MB max
				writeError(w, 400, "file too large (max 100MB)")
				return
			}

			// Generate unique object key
			fileID := fmt.Sprintf("%d-%s", time.Now().UnixNano(), r.Header.Get("X-Account-ID"))
			objectKey := "uploads/" + fileID

			presignedURL, err := mcPublic.PresignedPutObject(ctx, bucket, objectKey, time.Hour)
			if err != nil {
				log.Error().Err(err).Msg("PresignedPutObject failed")
				writeError(w, 500, "presign failed")
				return
			}

			// Save metadata
			_, err = pool.Exec(ctx,
				`INSERT INTO files (id, account_id, object_key, content_type, size_bytes, is_public, uploaded_at)
				 VALUES ($1, $2, $3, $4, $5, $6, NOW())`,
				fileID, r.Header.Get("X-Account-ID"), objectKey, body.ContentType, body.SizeBytes, body.IsPublic,
			)
			if err != nil {
				log.Error().Err(err).Msg("save file metadata")
			}

			writeJSON(w, 200, map[string]string{
				"file_id":      fileID,
				"upload_url":   presignedURL.String(),
				"expires_in":   "3600",
			})
		})

		// Request presigned download URL
		r.Get("/{fileID}", func(w http.ResponseWriter, r *http.Request) {
			fileID    := chi.URLParam(r, "fileID")
			requester := r.Header.Get("X-Account-ID")

			var f domain.File
			err := pool.QueryRow(ctx,
				`SELECT id, account_id, object_key, content_type, size_bytes, is_public FROM files WHERE id = $1`, fileID,
			).Scan(&f.ID, &f.AccountID, &f.ObjectKey, &f.ContentType, &f.SizeBytes, &f.IsPublic)
			if err != nil {
				writeError(w, 404, "file not found")
				return
			}

			// Public files (e.g. avatars) are accessible to any authenticated user.
			// Private files require the requester to be the uploader.
			if !f.IsPublic && f.AccountID != requester {
				writeError(w, 403, "forbidden")
				return
			}

			downloadURL, err := mcPublic.PresignedGetObject(ctx, bucket, f.ObjectKey, time.Hour, nil)
			if err != nil {
				writeError(w, 500, "presign failed")
				return
			}

			writeJSON(w, 200, map[string]any{
				"file_id":      f.ID,
				"download_url": downloadURL.String(),
				"content_type": f.ContentType,
				"size_bytes":   f.SizeBytes,
				"expires_in":   "3600",
			})
		})
	})

	addr := env("HTTP_ADDR", ":18005")
	srv := &http.Server{
		Addr:        addr,
		Handler:     r,
		ReadTimeout: 10 * time.Second,
		// WriteTimeout intentionally long — supports large file metadata responses
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go func() {
		log.Info().Str("addr", addr).Msg("media service listening")
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("server error")
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	shutCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutCtx)
}

type ctxKey string
const accountIDCtxKey ctxKey = "account_id"

func jwtMiddleware(mgr *token.Manager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Accept from header (gateway injects X-Account-ID after validation)
			id := r.Header.Get("X-Account-ID")
			if id == "" {
				writeError(w, 401, "unauthorized")
				return
			}
			next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), accountIDCtxKey, id)))
		})
	}
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
func writeAppError(w http.ResponseWriter, err error) {
	writeJSON(w, apperrors.HTTPStatus(err), map[string]string{"error": err.Error()})
}
func mustEnv(key string) string {
	v := os.Getenv(key)
	if v == "" { panic(fmt.Sprintf("required env %s not set", key)) }
	return v
}
func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" { return v }
	return fallback
}
