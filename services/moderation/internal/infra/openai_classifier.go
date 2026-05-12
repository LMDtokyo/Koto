// Package infra — OpenAI Moderation API классификатор. Free до 10М токенов/мес.
// Когда OPENAI_API_KEY не задан — возвращает category="" (классификация просто
// пропускается, репорт сохраняется как pending).
package infra

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"time"
)

type OpenAIClassifier struct {
	apiKey string
	client *http.Client
}

func NewOpenAIClassifier() *OpenAIClassifier {
	return &OpenAIClassifier{
		apiKey: os.Getenv("OPENAI_API_KEY"),
		client: &http.Client{Timeout: 8 * time.Second},
	}
}

type omRequest struct {
	Model string `json:"model"`
	Input string `json:"input"`
}

type omResponse struct {
	Results []struct {
		Flagged    bool                  `json:"flagged"`
		Categories map[string]bool       `json:"categories"`
		Scores     map[string]float64    `json:"category_scores"`
	} `json:"results"`
}

// Classify — best-effort. Если API недоступно или ключ не задан, возвращаем
// пустую категорию (не считаем за ошибку — мы все равно сохраним report как pending).
func (c *OpenAIClassifier) Classify(ctx context.Context, text string) (string, float64, error) {
	if c.apiKey == "" {
		return "", 0, nil
	}
	if len(text) > 10000 {
		text = text[:10000]
	}
	body, _ := json.Marshal(omRequest{Model: "omni-moderation-latest", Input: text})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, "https://api.openai.com/v1/moderations", bytes.NewReader(body))
	if err != nil {
		return "", 0, err
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.client.Do(req)
	if err != nil {
		return "", 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return "", 0, errors.New("openai moderation: HTTP " + resp.Status)
	}
	var parsed omResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return "", 0, err
	}
	if len(parsed.Results) == 0 {
		return "", 0, nil
	}
	r := parsed.Results[0]
	if !r.Flagged {
		return "ok", 0, nil
	}
	// Возвращаем категорию с максимальным score.
	var maxCat string
	var maxScore float64
	for cat, score := range r.Scores {
		if r.Categories[cat] && score > maxScore {
			maxCat = mapToInternal(cat)
			maxScore = score
		}
	}
	if maxCat == "" {
		maxCat = "other"
	}
	return maxCat, maxScore, nil
}

// mapToInternal — переводит категории OpenAI Moderation в наш внутренний словарь.
func mapToInternal(c string) string {
	switch c {
	case "sexual/minors":
		return "csam"
	case "violence", "violence/graphic":
		return "violence"
	case "hate", "hate/threatening":
		return "hate"
	case "harassment", "harassment/threatening":
		return "harassment"
	case "self-harm", "self-harm/intent", "self-harm/instructions":
		return "self_harm"
	case "sexual":
		return "sexual"
	case "illicit", "illicit/violent":
		return "illegal"
	}
	return c
}
