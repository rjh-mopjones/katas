package feedparser

import "io"

type Quote struct {
	Symbol   string
	Bid, Ask float64
	Qty      uint64
}

type ErrKind int

const (
	WrongFieldCount ErrKind = iota
	EmptySymbol
	InvalidBid
	InvalidAsk
	InvalidQty
)

func (k ErrKind) String() string { panic("TODO: implement") }

type ParseError struct {
	Line int
	Kind ErrKind
}

func (e *ParseError) Error() string { panic("TODO: implement") }

func Parse(r io.Reader, fn func(line int, q Quote, err *ParseError)) error {
	panic("TODO: implement")
}

func ParseAll(r io.Reader) (quotes []Quote, errs []ParseError, scanErr error) {
	panic("TODO: implement")
}
