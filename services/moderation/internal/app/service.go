// Package app — use-cases moderation сервиса.
package app

import (
	"context"

	"github.com/koto-messenger/koto/services/moderation/internal/domain"
)

type Service struct {
	reports    domain.ReportRepository
	actions    domain.ActionRepository
	classifier domain.Classifier
}

func New(reports domain.ReportRepository, actions domain.ActionRepository, classifier domain.Classifier) *Service {
	return &Service{reports: reports, actions: actions, classifier: classifier}
}

// SubmitReportInput описывает payload, который клиент отправляет на /v1/moderation/report.
type SubmitReportInput struct {
	ReporterID     string
	ReportedID     string
	ConversationID string
	MessageID      string
	Reason         string
	Plaintext      string
	Context        []domain.ContextMessage
}

// SubmitReport принимает жалобу, прогоняет ИИ-классификатор, сохраняет в БД.
// Если категория считается «critical» (csam, terrorism) — auto-action ban.
func (s *Service) SubmitReport(ctx context.Context, in SubmitReportInput) (domain.Report, error) {
	report := domain.Report{
		ReporterID:     in.ReporterID,
		ReportedID:     in.ReportedID,
		ConversationID: in.ConversationID,
		MessageID:      in.MessageID,
		Reason:         in.Reason,
		Plaintext:      in.Plaintext,
		Context:        in.Context,
		Status:         "pending",
	}

	// Auto-classify (best-effort, не блокирует submit).
	if s.classifier != nil && in.Plaintext != "" {
		if cat, score, err := s.classifier.Classify(ctx, in.Plaintext); err == nil {
			report.Classification = cat
			report.ClassificationScore = score
			if isCritical(cat) {
				// Жёсткие категории — сразу ban для reported и пометка report'у "actioned".
				_ = s.actions.Upsert(ctx, domain.Action{
					AccountID: in.ReportedID,
					Action:    "ban",
					Reason:    "auto: " + cat,
				})
				report.Status = "actioned"
			}
		}
	}

	return s.reports.Create(ctx, report)
}

// ListPending — для админ-панели модераторов.
func (s *Service) ListPending(ctx context.Context, limit int) ([]domain.Report, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	return s.reports.ListByStatus(ctx, "pending", limit)
}

// CheckAccountStatus — на бэке других сервисов проверять, заблокирован ли отправитель.
// Простой proxy. TODO: подключить к chat-service в SendMessage флоу.
func (s *Service) CheckAccountStatus(ctx context.Context, accountID string) (domain.Action, error) {
	return s.actions.Get(ctx, accountID)
}

func isCritical(category string) bool {
	switch category {
	case "csam", "child_sexual_abuse", "terrorism", "weapons_illicit", "drugs_illicit":
		return true
	}
	return false
}
