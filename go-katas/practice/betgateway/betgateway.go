package betgateway

import (
	"context"
	"net/http"
)

type Bet struct {
	Selection string  `json:"selection"`
	Stake     int64   `json:"stake"`
	Odds      float64 `json:"odds"`
}

type PlacedBet struct {
	ID     string `json:"bet_id"`
	Status string `json:"status"`
}

type Placer interface {
	Place(ctx context.Context, accountID string, bet Bet) (PlacedBet, error)
}

type Limiter interface {
	Allow(accountID string) bool
}

type Gateway struct{}

func NewGateway(placer Placer, limiter Limiter) *Gateway { panic("TODO: implement") }

func (g *Gateway) ServeHTTP(w http.ResponseWriter, r *http.Request) { panic("TODO: implement") }
