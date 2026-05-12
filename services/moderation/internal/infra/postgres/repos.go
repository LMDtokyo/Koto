package postgres

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/koto-messenger/koto/services/moderation/internal/domain"
)

type ReportRepo struct {
	pool *pgxpool.Pool
}

func NewReportRepo(pool *pgxpool.Pool) *ReportRepo {
	return &ReportRepo{pool: pool}
}

func (r *ReportRepo) Create(ctx context.Context, report domain.Report) (domain.Report, error) {
	contextJSON, _ := json.Marshal(report.Context)
	row := r.pool.QueryRow(ctx, `
		INSERT INTO moderation_reports (
			reporter_id, reported_id, conversation_id, message_id, reason,
			plaintext, context, classification, classification_score, status
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
		RETURNING id, created_at`,
		report.ReporterID, report.ReportedID, report.ConversationID, report.MessageID,
		report.Reason, report.Plaintext, contextJSON,
		report.Classification, report.ClassificationScore, report.Status,
	)
	if err := row.Scan(&report.ID, &report.CreatedAt); err != nil {
		return domain.Report{}, err
	}
	return report, nil
}

func (r *ReportRepo) GetByID(ctx context.Context, id string) (domain.Report, error) {
	var report domain.Report
	var contextJSON []byte
	err := r.pool.QueryRow(ctx, `
		SELECT id, reporter_id, reported_id, conversation_id, message_id, reason,
		       COALESCE(plaintext, ''), COALESCE(context, '[]'::jsonb),
		       COALESCE(classification, ''), COALESCE(classification_score, 0),
		       status, COALESCE(moderator_id, ''), COALESCE(moderator_note, ''),
		       created_at, reviewed_at
		FROM moderation_reports WHERE id = $1`, id,
	).Scan(
		&report.ID, &report.ReporterID, &report.ReportedID, &report.ConversationID, &report.MessageID, &report.Reason,
		&report.Plaintext, &contextJSON,
		&report.Classification, &report.ClassificationScore,
		&report.Status, &report.ModeratorID, &report.ModeratorNote,
		&report.CreatedAt, &report.ReviewedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Report{}, errors.New("report not found")
	}
	if err != nil {
		return domain.Report{}, err
	}
	_ = json.Unmarshal(contextJSON, &report.Context)
	return report, nil
}

func (r *ReportRepo) ListByStatus(ctx context.Context, status string, limit int) ([]domain.Report, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, reporter_id, reported_id, conversation_id, message_id, reason,
		       COALESCE(plaintext, ''), COALESCE(context, '[]'::jsonb),
		       COALESCE(classification, ''), COALESCE(classification_score, 0),
		       status, COALESCE(moderator_id, ''), COALESCE(moderator_note, ''),
		       created_at, reviewed_at
		FROM moderation_reports
		WHERE status = $1
		ORDER BY created_at DESC
		LIMIT $2`, status, limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.Report
	for rows.Next() {
		var report domain.Report
		var contextJSON []byte
		if err := rows.Scan(
			&report.ID, &report.ReporterID, &report.ReportedID, &report.ConversationID, &report.MessageID, &report.Reason,
			&report.Plaintext, &contextJSON,
			&report.Classification, &report.ClassificationScore,
			&report.Status, &report.ModeratorID, &report.ModeratorNote,
			&report.CreatedAt, &report.ReviewedAt,
		); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(contextJSON, &report.Context)
		out = append(out, report)
	}
	return out, rows.Err()
}

func (r *ReportRepo) UpdateStatus(ctx context.Context, id, status, moderatorID, note string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE moderation_reports
		SET status = $2, moderator_id = $3, moderator_note = $4, reviewed_at = NOW()
		WHERE id = $1`,
		id, status, moderatorID, note,
	)
	return err
}

type ActionRepo struct {
	pool *pgxpool.Pool
}

func NewActionRepo(pool *pgxpool.Pool) *ActionRepo {
	return &ActionRepo{pool: pool}
}

func (a *ActionRepo) Upsert(ctx context.Context, action domain.Action) error {
	_, err := a.pool.Exec(ctx, `
		INSERT INTO moderation_actions (account_id, action, reason, moderator_id, expires_at)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (account_id) DO UPDATE
			SET action = EXCLUDED.action,
			    reason = EXCLUDED.reason,
			    moderator_id = EXCLUDED.moderator_id,
			    expires_at = EXCLUDED.expires_at`,
		action.AccountID, action.Action, action.Reason, action.ModeratorID, action.ExpiresAt,
	)
	return err
}

func (a *ActionRepo) Get(ctx context.Context, accountID string) (domain.Action, error) {
	var action domain.Action
	err := a.pool.QueryRow(ctx, `
		SELECT account_id, action, COALESCE(reason, ''), COALESCE(moderator_id, ''),
		       expires_at, created_at
		FROM moderation_actions WHERE account_id = $1`, accountID,
	).Scan(&action.AccountID, &action.Action, &action.Reason, &action.ModeratorID, &action.ExpiresAt, &action.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return domain.Action{}, errors.New("no active action")
	}
	return action, err
}

func (a *ActionRepo) Remove(ctx context.Context, accountID string) error {
	_, err := a.pool.Exec(ctx, `DELETE FROM moderation_actions WHERE account_id = $1`, accountID)
	return err
}
