// Package errors defines domain-level sentinel errors and an AppError wrapper.
package errors

import (
	"errors"
	"fmt"
	"net/http"
)

// Sentinel errors — use errors.Is(err, ErrNotFound) to test.
var (
	ErrNotFound      = errors.New("not found")
	ErrAlreadyExists = errors.New("already exists")
	ErrUnauthorized  = errors.New("unauthorized")
	ErrForbidden     = errors.New("forbidden")
	ErrInvalidInput  = errors.New("invalid input")
	ErrInternal      = errors.New("internal error")
)

// AppError wraps a sentinel with a human-readable message and an optional cause.
type AppError struct {
	Sentinel error
	Message  string
	Cause    error
}

func (e *AppError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("%s: %v", e.Message, e.Cause)
	}
	return e.Message
}

// Unwrap allows errors.Is / errors.As to traverse to Sentinel.
func (e *AppError) Unwrap() error { return e.Sentinel }

// New wraps a sentinel error with a message.
func New(sentinel error, msg string) *AppError {
	return &AppError{Sentinel: sentinel, Message: msg}
}

// Wrap wraps a sentinel with a message and an underlying cause.
func Wrap(sentinel error, msg string, cause error) *AppError {
	return &AppError{Sentinel: sentinel, Message: msg, Cause: cause}
}

// HTTPStatus maps a sentinel error to an HTTP status code.
func HTTPStatus(err error) int {
	switch {
	case errors.Is(err, ErrNotFound):
		return http.StatusNotFound
	case errors.Is(err, ErrAlreadyExists):
		return http.StatusConflict
	case errors.Is(err, ErrUnauthorized):
		return http.StatusUnauthorized
	case errors.Is(err, ErrForbidden):
		return http.StatusForbidden
	case errors.Is(err, ErrInvalidInput):
		return http.StatusBadRequest
	default:
		return http.StatusInternalServerError
	}
}

// Is re-exports errors.Is.
var Is = errors.Is

// As re-exports errors.As.
var As = errors.As
