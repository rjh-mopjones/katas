//! Tick Parser — a **zero-copy** decoder for a market-data line.
//!
//! # The component
//!
//! A feed handler reads pipe-delimited quote lines off the wire — `"LIV-MUN|1.95|2.05|1234"`
//! (symbol, bid, ask, sequence) — millions per second. `parse` turns one line into a [`Quote`]. The
//! interesting constraint is *zero allocation on the hot path*: the parsed `Quote` **borrows** the
//! symbol straight out of the input buffer instead of copying it into a `String`.
//!
//! # Why the lifetime is the whole point
//!
//! [`Quote`] is generic over a lifetime `'a`, and `symbol: &'a str` points into the caller's line.
//! The signature `fn parse(line: &str) -> Result<Quote<'_>, ParseError>` ties the returned quote's
//! lifetime to the input: the borrow checker then *guarantees at compile time* that a `Quote` can
//! never outlive the buffer it points into. There is no runtime cost and no way to get a dangling
//! reference — the classic Rust trade of a lifetime annotation for a whole class of bugs that a
//! C++ `string_view` would let you write.
//!
//! Contrast a `symbol: String` field: correct, but it heap-allocates and copies the bytes on every
//! single line. At feed volumes that allocator traffic is the bottleneck. `&str` borrows; `String`
//! owns — pick borrow on the hot path.
//!
//! # Errors
//!
//! [`ParseError`] is a small enum implementing [`std::error::Error`], so callers can `?`-propagate it
//! or match on the specific failure. Parsing the numeric fields uses `str::parse` and maps the
//! generic `ParseFloatError`/`ParseIntError` into a domain-specific variant — you rarely want to
//! surface `std`'s parse errors directly.
//!
//! # Alternatives
//!
//! For a real wire format you'd reach for a parser-combinator crate (`winnow`/`nom`) or `serde`; this
//! kata does it by hand to make the borrow explicit. `split` here does two cheap passes (count, then
//! read) and allocates nothing.

use std::error::Error;
use std::fmt;

/// A parsed quote whose `symbol` borrows the input line — no allocation.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Quote<'a> {
    pub symbol: &'a str,
    pub bid: f64,
    pub ask: f64,
    pub seq: u64,
}

/// Why a line failed to parse.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    /// The line did not have exactly four `|`-separated fields.
    WrongFieldCount { expected: usize, got: usize },
    /// The symbol field was empty.
    EmptySymbol,
    /// The bid field was not a valid number.
    InvalidBid,
    /// The ask field was not a valid number.
    InvalidAsk,
    /// The sequence field was not a valid unsigned integer.
    InvalidSeq,
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ParseError::WrongFieldCount { expected, got } => {
                write!(f, "expected {expected} fields, got {got}")
            }
            ParseError::EmptySymbol => write!(f, "empty symbol"),
            ParseError::InvalidBid => write!(f, "invalid bid"),
            ParseError::InvalidAsk => write!(f, "invalid ask"),
            ParseError::InvalidSeq => write!(f, "invalid seq"),
        }
    }
}

impl Error for ParseError {}

/// Parse one `symbol|bid|ask|seq` line into a [`Quote`] that borrows `line`.
pub fn parse(line: &str) -> Result<Quote<'_>, ParseError> {
    let count = line.split('|').count();
    if count != 4 {
        return Err(ParseError::WrongFieldCount {
            expected: 4,
            got: count,
        });
    }

    // count == 4, so all four `next()` calls succeed.
    let mut fields = line.split('|');
    let symbol = fields.next().unwrap();
    let bid = fields
        .next()
        .unwrap()
        .parse::<f64>()
        .map_err(|_| ParseError::InvalidBid)?;
    let ask = fields
        .next()
        .unwrap()
        .parse::<f64>()
        .map_err(|_| ParseError::InvalidAsk)?;
    let seq = fields
        .next()
        .unwrap()
        .parse::<u64>()
        .map_err(|_| ParseError::InvalidSeq)?;

    if symbol.is_empty() {
        return Err(ParseError::EmptySymbol);
    }

    Ok(Quote {
        symbol,
        bid,
        ask,
        seq,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_a_valid_line() {
        let line = "LIV-MUN|1.95|2.05|1234";
        let q = parse(line).expect("valid line");
        assert_eq!(q.symbol, "LIV-MUN");
        assert_eq!(q.bid, 1.95);
        assert_eq!(q.ask, 2.05);
        assert_eq!(q.seq, 1234);
    }

    #[test]
    fn symbol_borrows_the_input_no_copy() {
        let line = String::from("ARS-CHE|1.50|1.60|7");
        let q = parse(&line).unwrap();
        // The parsed symbol points *into* the original buffer — zero copy.
        assert!(std::ptr::eq(q.symbol.as_ptr(), line.as_ptr()));
    }

    #[test]
    fn rejects_wrong_field_count() {
        assert_eq!(
            parse("A|1.0|2.0"),
            Err(ParseError::WrongFieldCount {
                expected: 4,
                got: 3
            })
        );
        assert_eq!(
            parse("A|1.0|2.0|1|extra"),
            Err(ParseError::WrongFieldCount {
                expected: 4,
                got: 5
            })
        );
    }

    #[test]
    fn rejects_empty_symbol() {
        assert_eq!(parse("|1.0|2.0|1"), Err(ParseError::EmptySymbol));
    }

    #[test]
    fn rejects_bad_numbers() {
        assert_eq!(parse("A|x|2.0|1"), Err(ParseError::InvalidBid));
        assert_eq!(parse("A|1.0|y|1"), Err(ParseError::InvalidAsk));
        assert_eq!(parse("A|1.0|2.0|z"), Err(ParseError::InvalidSeq));
        assert_eq!(parse("A|1.0|2.0|-1"), Err(ParseError::InvalidSeq)); // seq is unsigned
    }

    #[test]
    fn error_implements_display() {
        let e = ParseError::WrongFieldCount {
            expected: 4,
            got: 2,
        };
        assert_eq!(e.to_string(), "expected 4 fields, got 2");
    }
}
