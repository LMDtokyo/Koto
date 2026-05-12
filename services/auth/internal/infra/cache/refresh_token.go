// Package cache implements RefreshTokenRepository using Dragonfly (Redis-compatible).
package cache

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	apperrors "github.com/koto-messenger/koto/pkg/errors"
)

const (
	refreshTokenPrefix = "rt:"
	rotationPrefix     = "rrt:"
)

// RefreshTokenCache implements domain.RefreshTokenRepository.
type RefreshTokenCache struct{ rdb *redis.Client }

// NewRefreshTokenCache creates a new cache-backed refresh token store.
func NewRefreshTokenCache(rdb *redis.Client) *RefreshTokenCache {
	return &RefreshTokenCache{rdb: rdb}
}

func (c *RefreshTokenCache) Store(ctx context.Context, token, accountID string, ttl time.Duration) error {
	key := refreshTokenPrefix + token
	err := c.rdb.Set(ctx, key, accountID, ttl).Err()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "store refresh token", err)
	}
	return nil
}

func (c *RefreshTokenCache) Lookup(ctx context.Context, token string) (string, error) {
	key := refreshTokenPrefix + token
	accountID, err := c.rdb.Get(ctx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return "", apperrors.New(apperrors.ErrNotFound, "refresh token not found or expired")
		}
		return "", apperrors.Wrap(apperrors.ErrInternal, "lookup refresh token", err)
	}
	return accountID, nil
}

func (c *RefreshTokenCache) Revoke(ctx context.Context, token string) error {
	key := refreshTokenPrefix + token
	err := c.rdb.Del(ctx, key).Err()
	if err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "revoke refresh token", err)
	}
	return nil
}

func (c *RefreshTokenCache) RememberRotation(ctx context.Context, oldToken string, payload []byte, ttl time.Duration) error {
	if err := c.rdb.Set(ctx, rotationPrefix+oldToken, payload, ttl).Err(); err != nil {
		return apperrors.Wrap(apperrors.ErrInternal, "remember rotation", err)
	}
	return nil
}

func (c *RefreshTokenCache) LookupRotation(ctx context.Context, oldToken string) ([]byte, error) {
	b, err := c.rdb.Get(ctx, rotationPrefix+oldToken).Bytes()
	if err != nil {
		if err == redis.Nil {
			return nil, apperrors.New(apperrors.ErrNotFound, "no rotation cached")
		}
		return nil, apperrors.Wrap(apperrors.ErrInternal, "lookup rotation", err)
	}
	return b, nil
}

// NewRedisClient creates a Redis/Dragonfly client.
func NewRedisClient(addr, password string) *redis.Client {
	return redis.NewClient(&redis.Options{
		Addr:         addr,
		Password:     password,
		DB:           0,
		PoolSize:     20,
		MinIdleConns: 5,
		DialTimeout:  3 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
	})
}

// Ping verifies connectivity.
func Ping(ctx context.Context, rdb *redis.Client) error {
	if err := rdb.Ping(ctx).Err(); err != nil {
		return fmt.Errorf("dragonfly ping: %w", err)
	}
	return nil
}
