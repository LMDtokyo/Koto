package postgres

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/koto-messenger/koto/services/notification/internal/domain"
)

type DeviceRepo struct {
	pool *pgxpool.Pool
}

func NewDeviceRepo(pool *pgxpool.Pool) *DeviceRepo {
	return &DeviceRepo{pool: pool}
}

func (r *DeviceRepo) Upsert(ctx context.Context, d domain.Device) error {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO devices (account_id, token, platform, bundle_id, updated_at)
		 VALUES ($1, $2, $3, $4, NOW())
		 ON CONFLICT (account_id, token) DO UPDATE SET updated_at = NOW()`,
		d.AccountID, d.Token, uint8(d.Platform), d.BundleID,
	)
	return err
}

func (r *DeviceRepo) Remove(ctx context.Context, deviceID string) error {
	_, err := r.pool.Exec(ctx, `DELETE FROM devices WHERE id = $1`, deviceID)
	return err
}

func (r *DeviceRepo) RemoveToken(ctx context.Context, token string) error {
	_, err := r.pool.Exec(ctx, `DELETE FROM devices WHERE token = $1`, token)
	return err
}

func (r *DeviceRepo) ListForAccount(ctx context.Context, accountID string) ([]domain.Device, error) {
	rows, err := r.pool.Query(ctx,
		`SELECT id, account_id, token, platform, bundle_id FROM devices WHERE account_id = $1`,
		accountID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var devices []domain.Device
	for rows.Next() {
		var d domain.Device
		var platform uint8
		if err := rows.Scan(&d.ID, &d.AccountID, &d.Token, &platform, &d.BundleID); err != nil {
			return nil, err
		}
		d.Platform = domain.Platform(platform)
		devices = append(devices, d)
	}
	return devices, rows.Err()
}
