use std::error::Error;
use std::fmt;

/// A binary arithmetic operator. Provided verbatim.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Op {
    Add,
    Sub,
    Mul,
    Div,
}

/// The abstract syntax tree of a parsed expression. Provided verbatim.
///
/// Children are [`Box`]ed because the type is recursive — a node contains sub-nodes, which would
/// otherwise give the enum an infinite size.
#[derive(Debug, Clone, PartialEq)]
pub enum Expr {
    Num(f64),
    Neg(Box<Expr>),
    Bin {
        op: Op,
        lhs: Box<Expr>,
        rhs: Box<Expr>,
    },
}

/// Why an expression failed to parse or evaluate. Provided verbatim (the public contract).
#[derive(Debug, Clone, PartialEq)]
pub enum CalcError {
    UnexpectedChar(char),
    UnexpectedEof,
    UnexpectedToken,
    TrailingInput,
    DivideByZero,
}

impl fmt::Display for CalcError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl Error for CalcError {}

/// Parse and evaluate an arithmetic expression, returning its `f64` value.
///
/// Support `+ - * /`, parentheses, unary minus, integer/decimal literals, and arbitrary whitespace.
/// `* /` bind tighter than `+ -`; all operators are left-associative.
pub fn eval(_input: &str) -> Result<f64, CalcError> {
    todo!("tokenize, parse into an Expr, then evaluate the tree")
}
