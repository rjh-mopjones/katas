//! Calculator — a hand-written **recursive-descent** arithmetic evaluator.
//!
//! # The component
//!
//! [`eval`] takes a string like `"1 + 2 * (3 - 4) / 5"` and returns the `f64` it evaluates to.
//! It supports `+ - * /`, parentheses, unary minus, integer and decimal literals, and arbitrary
//! whitespace. `* /` bind tighter than `+ -`, and every operator is left-associative — so `10-2-3`
//! is `(10-2)-3 == 5`, not `10-(2-3)`. This is the canonical "write a calculator" senior interview
//! ask; the whole exercise is turning a flat string into structured meaning.
//!
//! # Why precedence forces a structure
//!
//! You cannot evaluate an expression left-to-right in one pass and get precedence right: by the time
//! you read the `*` in `1 + 2 * 3` you have already consumed and (wrongly) added the `1 + 2`. The
//! fix is to build a tree first — an **abstract syntax tree** ([`Expr`]) — where the shape encodes the
//! precedence, then evaluate the tree bottom-up. `1 + 2 * 3` parses to `Add(1, Mul(2, 3))`, and
//! evaluating it multiplies before it adds *because that is how the tree is shaped*.
//!
//! # Why `Box` for the recursive enum
//!
//! [`Expr`] refers to itself — a `Bin` node holds two child `Expr`s. A naively recursive
//! `enum Expr { Bin { lhs: Expr, rhs: Expr } }` has no finite size (an `Expr` would contain two
//! `Expr`s which each contain two more…), so the compiler rejects it. [`Box<Expr>`] breaks the cycle:
//! the child lives on the heap and the node stores a pointer of known size. `Box` is the idiomatic
//! way to make a recursive data type in Rust — the same reason a hand-rolled linked list or tree
//! needs it.
//!
//! # Recursive descent
//!
//! The parser is a set of mutually-recursive functions, one per precedence level, mirroring the
//! grammar:
//!
//! ```text
//! expr   := term   (('+' | '-') term)*     // lowest precedence, left-assoc
//! term   := factor (('*' | '/') factor)*   // higher precedence, left-assoc
//! factor := '-' factor | '(' expr ')' | number
//! ```
//!
//! Each level parses the level above it, then loops while it sees one of *its* operators — the loop
//! is what makes the operators left-associative. Precedence falls out of which function calls which.
//! We tokenize first (string → `Vec<Token>`) so the parser matches on tokens, not characters, and a
//! cursor tracks position; leftover tokens after the top-level `expr` mean [`TrailingInput`].
//!
//! # Error propagation
//!
//! Every parse/eval step returns `Result<_, CalcError>` and uses `?` to bubble the first failure out
//! — an unexpected character, an early end of input, a stray token, or a division by zero. [`CalcError`]
//! implements [`std::error::Error`], so a caller can `?`-propagate it or match a specific variant.
//!
//! # Alternatives
//!
//! For a real language you would reach for a **parser-combinator** crate (`winnow`/`nom`) or a parser
//! generator rather than hand-writing descent. For expressions specifically, a **Pratt parser**
//! (top-down operator precedence) or the **shunting-yard** algorithm handle precedence with a table
//! instead of a function-per-level, which scales better when you have many operators. This kata does
//! it by hand to make the grammar and the AST explicit.
//!
//! [`TrailingInput`]: CalcError::TrailingInput

use std::error::Error;
use std::fmt;

/// A binary arithmetic operator.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Op {
    Add,
    Sub,
    Mul,
    Div,
}

/// The abstract syntax tree of a parsed expression.
///
/// Children of a [`Expr::Bin`] and the operand of a [`Expr::Neg`] are [`Box`]ed because the type is
/// recursive — a node contains sub-nodes, which would otherwise give the enum an infinite size.
#[derive(Debug, Clone, PartialEq)]
pub enum Expr {
    /// A literal number.
    Num(f64),
    /// Unary negation, e.g. `-x`.
    Neg(Box<Expr>),
    /// A binary operation `lhs op rhs`.
    Bin {
        op: Op,
        lhs: Box<Expr>,
        rhs: Box<Expr>,
    },
}

/// Why an expression failed to parse or evaluate.
#[derive(Debug, Clone, PartialEq)]
pub enum CalcError {
    /// The tokenizer hit a character it did not recognise.
    UnexpectedChar(char),
    /// Input ended while the parser still expected more (e.g. `1 +`).
    UnexpectedEof,
    /// A token appeared where the grammar did not allow it (e.g. `1 2`).
    UnexpectedToken,
    /// The expression parsed, but tokens were left over (e.g. `1+2)`).
    TrailingInput,
    /// Evaluation divided by zero.
    DivideByZero,
}

impl fmt::Display for CalcError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CalcError::UnexpectedChar(c) => write!(f, "unexpected character {c:?}"),
            CalcError::UnexpectedEof => write!(f, "unexpected end of input"),
            CalcError::UnexpectedToken => write!(f, "unexpected token"),
            CalcError::TrailingInput => write!(f, "trailing input after expression"),
            CalcError::DivideByZero => write!(f, "division by zero"),
        }
    }
}

impl Error for CalcError {}

/// A lexical token — the parser consumes these instead of raw characters.
#[derive(Debug, Clone, Copy, PartialEq)]
enum Token {
    Num(f64),
    Plus,
    Minus,
    Star,
    Slash,
    LParen,
    RParen,
}

/// Turn the raw input into a flat list of [`Token`]s, skipping whitespace.
fn tokenize(input: &str) -> Result<Vec<Token>, CalcError> {
    let mut tokens = Vec::new();
    let chars: Vec<char> = input.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        match c {
            ' ' | '\t' | '\n' | '\r' => i += 1,
            '+' => {
                tokens.push(Token::Plus);
                i += 1;
            }
            '-' => {
                tokens.push(Token::Minus);
                i += 1;
            }
            '*' => {
                tokens.push(Token::Star);
                i += 1;
            }
            '/' => {
                tokens.push(Token::Slash);
                i += 1;
            }
            '(' => {
                tokens.push(Token::LParen);
                i += 1;
            }
            ')' => {
                tokens.push(Token::RParen);
                i += 1;
            }
            c if c.is_ascii_digit() || c == '.' => {
                let start = i;
                while i < chars.len() && (chars[i].is_ascii_digit() || chars[i] == '.') {
                    i += 1;
                }
                let text: String = chars[start..i].iter().collect();
                let value = text
                    .parse::<f64>()
                    .map_err(|_| CalcError::UnexpectedChar(c))?;
                tokens.push(Token::Num(value));
            }
            other => return Err(CalcError::UnexpectedChar(other)),
        }
    }
    Ok(tokens)
}

/// A recursive-descent parser over a token slice, tracking a cursor.
struct Parser<'a> {
    tokens: &'a [Token],
    pos: usize,
}

impl<'a> Parser<'a> {
    fn new(tokens: &'a [Token]) -> Self {
        Parser { tokens, pos: 0 }
    }

    fn peek(&self) -> Option<Token> {
        self.tokens.get(self.pos).copied()
    }

    fn advance(&mut self) -> Option<Token> {
        let t = self.tokens.get(self.pos).copied();
        if t.is_some() {
            self.pos += 1;
        }
        t
    }

    /// `expr := term (('+' | '-') term)*` — lowest precedence, left-associative.
    fn parse_expr(&mut self) -> Result<Expr, CalcError> {
        let mut lhs = self.parse_term()?;
        while let Some(tok) = self.peek() {
            let op = match tok {
                Token::Plus => Op::Add,
                Token::Minus => Op::Sub,
                _ => break,
            };
            self.advance();
            let rhs = self.parse_term()?;
            lhs = Expr::Bin {
                op,
                lhs: Box::new(lhs),
                rhs: Box::new(rhs),
            };
        }
        Ok(lhs)
    }

    /// `term := factor (('*' | '/') factor)*` — higher precedence, left-associative.
    fn parse_term(&mut self) -> Result<Expr, CalcError> {
        let mut lhs = self.parse_factor()?;
        while let Some(tok) = self.peek() {
            let op = match tok {
                Token::Star => Op::Mul,
                Token::Slash => Op::Div,
                _ => break,
            };
            self.advance();
            let rhs = self.parse_factor()?;
            lhs = Expr::Bin {
                op,
                lhs: Box::new(lhs),
                rhs: Box::new(rhs),
            };
        }
        Ok(lhs)
    }

    /// `factor := '-' factor | '(' expr ')' | number`.
    fn parse_factor(&mut self) -> Result<Expr, CalcError> {
        match self.advance() {
            Some(Token::Num(n)) => Ok(Expr::Num(n)),
            Some(Token::Minus) => Ok(Expr::Neg(Box::new(self.parse_factor()?))),
            Some(Token::LParen) => {
                let inner = self.parse_expr()?;
                match self.advance() {
                    Some(Token::RParen) => Ok(inner),
                    Some(_) => Err(CalcError::UnexpectedToken),
                    None => Err(CalcError::UnexpectedEof),
                }
            }
            Some(_) => Err(CalcError::UnexpectedToken),
            None => Err(CalcError::UnexpectedEof),
        }
    }
}

/// Walk the AST and compute its value, propagating [`CalcError::DivideByZero`].
fn eval_expr(expr: &Expr) -> Result<f64, CalcError> {
    match expr {
        Expr::Num(n) => Ok(*n),
        Expr::Neg(inner) => Ok(-eval_expr(inner)?),
        Expr::Bin { op, lhs, rhs } => {
            let l = eval_expr(lhs)?;
            let r = eval_expr(rhs)?;
            match op {
                Op::Add => Ok(l + r),
                Op::Sub => Ok(l - r),
                Op::Mul => Ok(l * r),
                Op::Div => {
                    if r == 0.0 {
                        Err(CalcError::DivideByZero)
                    } else {
                        Ok(l / r)
                    }
                }
            }
        }
    }
}

/// Parse and evaluate an arithmetic expression, returning its `f64` value.
///
/// Supports `+ - * /`, parentheses, unary minus, integer/decimal literals, and arbitrary whitespace.
/// `* /` bind tighter than `+ -`; all operators are left-associative.
pub fn eval(input: &str) -> Result<f64, CalcError> {
    let tokens = tokenize(input)?;
    let mut parser = Parser::new(&tokens);
    let expr = parser.parse_expr()?;
    if parser.pos != tokens.len() {
        return Err(CalcError::TrailingInput);
    }
    eval_expr(&expr)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn precedence_multiplies_before_adding() {
        assert_eq!(eval("1+2*3"), Ok(7.0));
    }

    #[test]
    fn parentheses_override_precedence() {
        assert_eq!(eval("(1+2)*3"), Ok(9.0));
    }

    #[test]
    fn unary_minus_on_a_literal() {
        assert_eq!(eval("-5"), Ok(-5.0));
    }

    #[test]
    fn unary_minus_after_operator() {
        assert_eq!(eval("2 + -3"), Ok(-1.0));
    }

    #[test]
    fn arbitrary_whitespace_is_ignored() {
        assert_eq!(eval("  1  +  2  "), Ok(3.0));
    }

    #[test]
    fn decimal_literals() {
        assert_eq!(eval("3.5*2"), Ok(7.0));
    }

    #[test]
    fn subtraction_is_left_associative() {
        assert_eq!(eval("10-2-3"), Ok(5.0));
    }

    #[test]
    fn division_is_left_associative() {
        assert_eq!(eval("8/2/2"), Ok(2.0));
    }

    #[test]
    fn division_by_zero_is_an_error() {
        assert_eq!(eval("1/0"), Err(CalcError::DivideByZero));
    }

    #[test]
    fn trailing_paren_is_rejected() {
        assert_eq!(eval("1+2)"), Err(CalcError::TrailingInput));
    }

    #[test]
    fn two_numbers_with_no_operator_is_rejected() {
        assert_eq!(eval("1 2"), Err(CalcError::TrailingInput));
    }

    #[test]
    fn empty_input_is_rejected() {
        assert_eq!(eval(""), Err(CalcError::UnexpectedEof));
    }

    #[test]
    fn bad_character_reports_the_char() {
        assert_eq!(eval("1+@"), Err(CalcError::UnexpectedChar('@')));
    }

    #[test]
    fn dangling_operator_is_unexpected_eof() {
        assert_eq!(eval("1 +"), Err(CalcError::UnexpectedEof));
    }

    #[test]
    fn nested_expression() {
        assert_eq!(eval("1 + 2 * (3 - 4) / 2"), Ok(0.0));
    }

    #[test]
    fn builds_the_expected_ast() {
        // 1+2*3 => Add(Num(1), Mul(Num(2), Num(3)))
        let tokens = tokenize("1+2*3").unwrap();
        let mut p = Parser::new(&tokens);
        let ast = p.parse_expr().unwrap();
        assert_eq!(
            ast,
            Expr::Bin {
                op: Op::Add,
                lhs: Box::new(Expr::Num(1.0)),
                rhs: Box::new(Expr::Bin {
                    op: Op::Mul,
                    lhs: Box::new(Expr::Num(2.0)),
                    rhs: Box::new(Expr::Num(3.0)),
                }),
            }
        );
    }

    #[test]
    fn error_implements_display() {
        assert_eq!(CalcError::DivideByZero.to_string(), "division by zero");
        assert!(!CalcError::UnexpectedChar('@').to_string().is_empty());
    }
}
