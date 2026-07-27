#ifndef KATAS_FEED_PARSER_HPP
#define KATAS_FEED_PARSER_HPP

#include <cstddef>
#include <cstdint>
#include <functional>
#include <istream>
#include <stdexcept>
#include <string>
#include <vector>

namespace katas {

// Provided verbatim — do not modify.
enum class ErrorKind { WrongFieldCount, EmptySymbol, InvalidBid, InvalidAsk, InvalidQty };

// Provided verbatim — do not modify.
struct Quote {
    std::string symbol;
    double bid{};
    double ask{};
    std::uint64_t qty{};
    bool operator==(const Quote&) const = default;
};

// Provided verbatim — do not modify.
struct ParseError {
    std::size_t line{};
    ErrorKind kind{};
    bool operator==(const ParseError&) const = default;
};

// Provided verbatim — do not modify.
struct ParseResult {
    std::vector<Quote> quotes;
    std::vector<ParseError> errors;
};

// You implement this. Stream `in` line by line, parse each non-skipped line, and call on_quote /
// on_error with the 1-based physical line number. Design the field-splitting and number parsing
// yourself — use std::string_view + std::from_chars (no allocation per field, no exceptions).
inline void parse_feed(std::istream& in,
                       const std::function<void(std::size_t line, const Quote&)>& on_quote,
                       const std::function<void(const ParseError&)>& on_error) {
    (void)in;
    (void)on_quote;
    (void)on_error;
    throw std::logic_error("TODO: implement");
}

// You implement this. Collect parse_feed's output into a ParseResult (quotes + errors, in order).
inline ParseResult parse_all(std::istream& in) {
    (void)in;
    throw std::logic_error("TODO: implement");
}

} // namespace katas

#endif // KATAS_FEED_PARSER_HPP
