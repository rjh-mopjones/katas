use std::error::Error;
use std::fmt;

/// A parsed quote whose `symbol` borrows the input line — no allocation. Provided verbatim.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Quote<'a> {
    pub symbol: &'a str,
    pub bid: f64,
    pub ask: f64,
    pub seq: u64,
}

/// Why a line failed to parse. Provided verbatim (the public contract).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    WrongFieldCount { expected: usize, got: usize },
    EmptySymbol,
    InvalidBid,
    InvalidAsk,
    InvalidSeq,
}

impl fmt::Display for ParseError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl Error for ParseError {}

/// Parse one `symbol|bid|ask|seq` line into a [`Quote`] that borrows `line` (zero copy).
pub fn parse(_line: &str) -> Result<Quote<'_>, ParseError> {
    todo!("implement zero-copy parse")
}
