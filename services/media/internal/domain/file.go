// Package domain defines media file entities.
package domain

import (
	"context"
	"time"
)

// File represents a metadata record for an uploaded encrypted file.
// The file body is stored in MinIO; the server never sees the plaintext.
type File struct {
	ID          string
	AccountID   string     // uploader
	ObjectKey   string     // MinIO object key
	ContentType string     // MIME type of the encrypted blob
	SizeBytes   int64
	UploadedAt  time.Time
	ExpiresAt   *time.Time
	IsPublic    bool       // true → any authenticated user may download (e.g. avatars)
}

// FileRepository persists file metadata.
type FileRepository interface {
	// Save stores file metadata after successful upload.
	Save(ctx context.Context, f File) error

	// Get returns file metadata by ID.
	Get(ctx context.Context, id string) (File, error)

	// Delete removes file metadata (and the caller should delete from MinIO).
	Delete(ctx context.Context, id string) error
}
