package pricecache

type Price struct {
	Bid, Ask float64
	Seq      uint64
}

type PriceCache struct{}

func NewPriceCache() *PriceCache { panic("TODO: implement") }

func (c *PriceCache) Set(market string, p Price) { panic("TODO: implement") }

func (c *PriceCache) Get(market string) (Price, bool) { panic("TODO: implement") }

func (c *PriceCache) Snapshot() map[string]Price { panic("TODO: implement") }
