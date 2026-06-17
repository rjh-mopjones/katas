package priceladder

type Ladder struct{}

func NewLadder() *Ladder { panic("TODO: implement") }

func (l *Ladder) Adjust(market string, delta float64) { panic("TODO: implement") }

func (l *Ladder) Set(market string, price float64) { panic("TODO: implement") }

func (l *Ladder) Price(market string) (float64, bool) { panic("TODO: implement") }
