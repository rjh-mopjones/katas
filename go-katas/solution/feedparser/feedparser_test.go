package feedparser

import (
	"strings"
	"testing"
)

// canonicalFeed is the shared fixture used by every language port of this kata.
// Line 3 is a genuine empty line; line 1 is a comment. Both are skipped but
// still count toward the physical line numbers of the later records.
const canonicalFeed = `# market data feed
LIV-MUN|1.95|2.05|1000

ARS-CHE|1.50|1.60|500
|1.0|2.0|10
BAD|x|2.0|10
TOO|1.0|2.0
NEG|1.0|2.0|-5`

func TestParseAll_CanonicalFeed(t *testing.T) {
	quotes, errs, scanErr := ParseAll(strings.NewReader(canonicalFeed))
	if scanErr != nil {
		t.Fatalf("scanErr = %v, want nil", scanErr)
	}

	wantQuotes := []Quote{
		{Symbol: "LIV-MUN", Bid: 1.95, Ask: 2.05, Qty: 1000},
		{Symbol: "ARS-CHE", Bid: 1.50, Ask: 1.60, Qty: 500},
	}
	if len(quotes) != len(wantQuotes) {
		t.Fatalf("got %d quotes, want %d: %+v", len(quotes), len(wantQuotes), quotes)
	}
	for i, want := range wantQuotes {
		if quotes[i] != want {
			t.Errorf("quote[%d] = %+v, want %+v", i, quotes[i], want)
		}
	}

	wantErrs := []ParseError{
		{Line: 5, Kind: EmptySymbol},
		{Line: 6, Kind: InvalidBid},
		{Line: 7, Kind: WrongFieldCount},
		{Line: 8, Kind: InvalidQty},
	}
	if len(errs) != len(wantErrs) {
		t.Fatalf("got %d errors, want %d: %+v", len(errs), len(wantErrs), errs)
	}
	for i, want := range wantErrs {
		if errs[i] != want {
			t.Errorf("error[%d] = %+v, want %+v", i, errs[i], want)
		}
	}
}

// TestParse_StreamsInOrder pins the streaming primitive directly: fn must fire
// once per non-skipped line, in feed order, with the correct physical line
// number, and exactly one of (valid quote, error) each time.
func TestParse_StreamsInOrder(t *testing.T) {
	type event struct {
		line int
		q    Quote
		err  *ParseError
	}
	var events []event
	// err != nil XOR valid quote: a nil err must carry a non-zero quote, a
	// non-nil err must carry the zero quote.
	scanErr := Parse(strings.NewReader(canonicalFeed), func(line int, q Quote, err *ParseError) {
		if err != nil && q != (Quote{}) {
			t.Errorf("line %d: error carried non-zero quote %+v", line, q)
		}
		if err == nil && q == (Quote{}) {
			t.Errorf("line %d: nil error carried zero quote", line)
		}
		events = append(events, event{line, q, err})
	})
	if scanErr != nil {
		t.Fatalf("scanErr = %v, want nil", scanErr)
	}

	wantLines := []int{2, 4, 5, 6, 7, 8}
	if len(events) != len(wantLines) {
		t.Fatalf("got %d events, want %d", len(events), len(wantLines))
	}
	for i, want := range wantLines {
		if events[i].line != want {
			t.Errorf("event[%d] line = %d, want %d", i, events[i].line, want)
		}
	}
}

func TestParse_EachErrorVariant(t *testing.T) {
	cases := []struct {
		name string
		feed string
		want ParseError
	}{
		{"wrong field count too few", "TOO|1.0|2.0", ParseError{Line: 1, Kind: WrongFieldCount}},
		{"wrong field count too many", "A|1.0|2.0|10|extra", ParseError{Line: 1, Kind: WrongFieldCount}},
		{"empty symbol", "|1.0|2.0|10", ParseError{Line: 1, Kind: EmptySymbol}},
		{"invalid bid", "BAD|x|2.0|10", ParseError{Line: 1, Kind: InvalidBid}},
		{"invalid ask", "BAD|1.0|y|10", ParseError{Line: 1, Kind: InvalidAsk}},
		{"invalid qty non-numeric", "BAD|1.0|2.0|z", ParseError{Line: 1, Kind: InvalidQty}},
		{"invalid qty negative", "NEG|1.0|2.0|-5", ParseError{Line: 1, Kind: InvalidQty}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			quotes, errs, scanErr := ParseAll(strings.NewReader(tc.feed))
			if scanErr != nil {
				t.Fatalf("scanErr = %v, want nil", scanErr)
			}
			if len(quotes) != 0 {
				t.Fatalf("got %d quotes, want 0: %+v", len(quotes), quotes)
			}
			if len(errs) != 1 {
				t.Fatalf("got %d errors, want 1: %+v", len(errs), errs)
			}
			if errs[0] != tc.want {
				t.Errorf("error = %+v, want %+v", errs[0], tc.want)
			}
		})
	}
}

func TestParse_ValidationOrderStopsAtFirst(t *testing.T) {
	// An empty symbol AND a bad bid: field-count passes, empty-symbol fires
	// first per the fixed order.
	quotes, errs, _ := ParseAll(strings.NewReader("|x|2.0|10"))
	if len(quotes) != 0 || len(errs) != 1 {
		t.Fatalf("got quotes=%+v errs=%+v", quotes, errs)
	}
	if errs[0].Kind != EmptySymbol {
		t.Errorf("kind = %v, want EmptySymbol (should win over InvalidBid)", errs[0].Kind)
	}
}

func TestParse_EmptyInput(t *testing.T) {
	quotes, errs, scanErr := ParseAll(strings.NewReader(""))
	if scanErr != nil {
		t.Fatalf("scanErr = %v, want nil", scanErr)
	}
	if len(quotes) != 0 {
		t.Errorf("got %d quotes, want 0", len(quotes))
	}
	if len(errs) != 0 {
		t.Errorf("got %d errors, want 0", len(errs))
	}
}

func TestParse_CommentAndBlankOnly(t *testing.T) {
	feed := "# header\n\n   \n#\t another comment\n\t\n"
	quotes, errs, scanErr := ParseAll(strings.NewReader(feed))
	if scanErr != nil {
		t.Fatalf("scanErr = %v, want nil", scanErr)
	}
	if len(quotes) != 0 || len(errs) != 0 {
		t.Fatalf("comment/blank-only feed yielded quotes=%+v errs=%+v", quotes, errs)
	}
}

func TestParse_LineNumbersCountSkippedLines(t *testing.T) {
	// Two skipped lines before the first record: the record is physical line 3.
	feed := "# comment\n\nGOOD|1.0|2.0|5\nBAD|x|2.0|5"
	quotes, errs, _ := ParseAll(strings.NewReader(feed))
	if len(quotes) != 1 {
		t.Fatalf("got %d quotes, want 1", len(quotes))
	}
	if quotes[0].Symbol != "GOOD" {
		t.Errorf("symbol = %q, want GOOD", quotes[0].Symbol)
	}
	if len(errs) != 1 {
		t.Fatalf("got %d errors, want 1", len(errs))
	}
	if errs[0].Line != 4 {
		t.Errorf("error line = %d, want 4", errs[0].Line)
	}
}

func TestParse_TrimsWhitespaceAroundRecord(t *testing.T) {
	// Leading/trailing whitespace on the whole line is trimmed before the blank
	// check; the record itself parses normally.
	quotes, errs, _ := ParseAll(strings.NewReader("  LIV-MUN|1.95|2.05|1000  "))
	if len(errs) != 0 {
		t.Fatalf("unexpected errors: %+v", errs)
	}
	if len(quotes) != 1 || quotes[0] != (Quote{Symbol: "LIV-MUN", Bid: 1.95, Ask: 2.05, Qty: 1000}) {
		t.Fatalf("got %+v", quotes)
	}
}

func TestErrKind_String(t *testing.T) {
	cases := map[ErrKind]string{
		WrongFieldCount: "WrongFieldCount",
		EmptySymbol:     "EmptySymbol",
		InvalidBid:      "InvalidBid",
		InvalidAsk:      "InvalidAsk",
		InvalidQty:      "InvalidQty",
	}
	for k, want := range cases {
		if got := k.String(); got != want {
			t.Errorf("ErrKind(%d).String() = %q, want %q", int(k), got, want)
		}
	}
}

func TestParseError_Error(t *testing.T) {
	e := &ParseError{Line: 6, Kind: InvalidBid}
	want := "feedparser: InvalidBid at line 6"
	if got := e.Error(); got != want {
		t.Errorf("Error() = %q, want %q", got, want)
	}
}
