// Package feedparser turns a text market-data feed into typed Quotes by
// streaming it one line at a time, reporting malformed lines instead of
// aborting on the first bad record.
//
// The feed is the sort of thing a betting exchange or a price vendor drops onto
// a socket or a file: one record per line, pipe-delimited, `SYMBOL|BID|ASK|QTY`.
// The naive approach — read the whole payload into memory, split on "\n",
// range over the slice — works on a toy fixture and falls over on a real one: a
// day's tick feed is gigabytes, and slurping it means holding all of it resident
// at once. Parse instead reads through a bufio.Scanner, so memory stays bounded
// to a single line no matter how long the feed is; the process can begin
// emitting quotes before the producer has finished writing.
//
// # Streaming vs slurping
//
// The design contract is that Parse never materialises the whole input. It pulls
// one line, decodes it, hands the result to a callback, and moves on — the line
// buffer is reused. That keeps a live feed's footprint flat and lets a caller act
// on early quotes immediately (backpressure, early cancellation) rather than
// waiting for EOF. ParseAll is the convenience wrapper that *does* collect
// everything into slices, for the common case where the feed is small and you
// just want the results; it is written on top of Parse, not the other way round,
// so the streaming primitive stays the foundation.
//
// # Per-line error reporting with physical line numbers
//
// A market feed is dirty: a truncated record, a symbol that never arrived, a
// price that came through as "x". Aborting the whole parse on the first bad line
// would throw away every good quote after it. Instead each line is decoded
// independently and a malformed one yields a ParseError carrying the 1-based
// physical line number — the number you would see in an editor — so an operator
// can jump straight to the offending record. Crucially the line counter advances
// for *every* physical line, including the blank and comment lines that are
// skipped, so the reported number always matches the source file.
//
// # Skip rules and validation order
//
// After trimming surrounding whitespace, a blank line or one starting with '#'
// is a non-record: skipped silently, neither a Quote nor an error, but still
// counted for line numbering. A record must have exactly four '|'-separated
// fields; fields are then validated in a fixed order — field-count, then empty
// symbol, then bid, then ask, then qty — so the same malformed line always
// produces the same first error across every implementation of this kata.
//
// # The symbol is an owned copy
//
// bufio.Scanner reuses its internal buffer between Scan calls, and Text() returns
// a fresh Go string (a copy) rather than aliasing that buffer, so the Symbol we
// store stays valid after the scanner advances. That is a Go convenience worth
// naming: in a zero-copy parser written in C, Rust, or with Go's Scanner.Bytes(),
// the symbol would be a view into a buffer the next read overwrites, and you would
// have to copy it deliberately to keep it. Here Text() has already paid for that
// copy, so Quote.Symbol is safe to retain without extra care — at the cost of one
// allocation per line that a true zero-copy design would avoid.
//
// No third-party dependencies: bufio + strconv from the standard library.
package feedparser

import (
	"bufio"
	"io"
	"strconv"
	"strings"
)

// Quote is one fully-parsed market-data record: a two-sided price for a symbol
// together with the available quantity.
type Quote struct {
	Symbol   string
	Bid, Ask float64
	Qty      uint64
}

// ErrKind classifies why a line failed to parse. The variants correspond to the
// fixed validation order applied to each record.
type ErrKind int

const (
	// WrongFieldCount: the line did not split into exactly four fields.
	WrongFieldCount ErrKind = iota
	// EmptySymbol: the symbol field was empty.
	EmptySymbol
	// InvalidBid: the bid field was not a valid float.
	InvalidBid
	// InvalidAsk: the ask field was not a valid float.
	InvalidAsk
	// InvalidQty: the qty field was not a non-negative integer.
	InvalidQty
)

// String returns the stable name of the error kind, matching the variant
// identifiers so error messages read the same across every language port.
func (k ErrKind) String() string {
	switch k {
	case WrongFieldCount:
		return "WrongFieldCount"
	case EmptySymbol:
		return "EmptySymbol"
	case InvalidBid:
		return "InvalidBid"
	case InvalidAsk:
		return "InvalidAsk"
	case InvalidQty:
		return "InvalidQty"
	default:
		return "ErrKind(" + strconv.Itoa(int(k)) + ")"
	}
}

// ParseError is a malformed line: a kind plus the 1-based physical line number
// it occurred on. It implements error so a single bad record can flow through
// the callback (or be collected) using ordinary error handling.
type ParseError struct {
	Line int
	Kind ErrKind
}

// Error renders the kind and line, e.g. "feedparser: InvalidBid at line 6".
func (e *ParseError) Error() string {
	return "feedparser: " + e.Kind.String() + " at line " + strconv.Itoa(e.Line)
}

// Parse streams r line by line and invokes fn once per non-skipped line, in
// order, with that line's 1-based physical number.
//
// For each record fn receives exactly one of a valid Quote (err == nil) or a
// non-nil *ParseError (with the zero Quote) — never both, never neither. Blank
// and '#'-comment lines are skipped without calling fn, but still advance the
// line counter. Parse returns only a scanner/IO error (e.g. a line longer than
// bufio's buffer or an underlying read failure); malformed records are reported
// through fn, not returned, so one bad line never stops the stream.
func Parse(r io.Reader, fn func(line int, q Quote, err *ParseError)) error {
	scanner := bufio.NewScanner(r)
	line := 0
	for scanner.Scan() {
		line++
		trimmed := strings.TrimSpace(scanner.Text())
		if trimmed == "" || strings.HasPrefix(trimmed, "#") {
			continue
		}
		q, perr := parseLine(line, trimmed)
		fn(line, q, perr)
	}
	return scanner.Err()
}

// parseLine decodes one already-trimmed, non-skipped record, applying the fixed
// validation order and stamping any error with the physical line number.
func parseLine(line int, text string) (Quote, *ParseError) {
	fields := strings.Split(text, "|")
	if len(fields) != 4 {
		return Quote{}, &ParseError{Line: line, Kind: WrongFieldCount}
	}

	symbol := fields[0]
	if symbol == "" {
		return Quote{}, &ParseError{Line: line, Kind: EmptySymbol}
	}

	bid, err := strconv.ParseFloat(fields[1], 64)
	if err != nil {
		return Quote{}, &ParseError{Line: line, Kind: InvalidBid}
	}

	ask, err := strconv.ParseFloat(fields[2], 64)
	if err != nil {
		return Quote{}, &ParseError{Line: line, Kind: InvalidAsk}
	}

	qty, err := strconv.ParseUint(fields[3], 10, 64)
	if err != nil {
		return Quote{}, &ParseError{Line: line, Kind: InvalidQty}
	}

	return Quote{Symbol: symbol, Bid: bid, Ask: ask, Qty: qty}, nil
}

// ParseAll is a convenience collector built on Parse: it drains r fully and
// returns every valid Quote and every ParseError in feed order, plus any
// scanner/IO error. It is the batch-oriented counterpart to the streaming
// Parse — use it when the feed is small enough to hold the results in memory.
func ParseAll(r io.Reader) (quotes []Quote, errs []ParseError, scanErr error) {
	scanErr = Parse(r, func(_ int, q Quote, perr *ParseError) {
		if perr != nil {
			errs = append(errs, *perr)
			return
		}
		quotes = append(quotes, q)
	})
	return quotes, errs, scanErr
}
