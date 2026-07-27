#include "harness.hpp"

#include "feed_parser.hpp"

#include <cstddef>
#include <sstream>
#include <string>
#include <vector>

using katas::ErrorKind;
using katas::ParseError;
using katas::ParseResult;
using katas::Quote;
using katas::parse_all;
using katas::parse_feed;

namespace {

// The canonical sample feed shared by all six language ports — its output is the contract.
const char* kCanonicalFeed =
    "# market data feed\n"
    "LIV-MUN|1.95|2.05|1000\n"
    "\n"
    "ARS-CHE|1.50|1.60|500\n"
    "|1.0|2.0|10\n"
    "BAD|x|2.0|10\n"
    "TOO|1.0|2.0\n"
    "NEG|1.0|2.0|-5\n";

ParseResult parse_str(const std::string& feed) {
    std::istringstream in(feed);
    return parse_all(in);
}

} // namespace

KATA_TEST(canonical_feed_yields_exact_quotes_and_errors) {
    ParseResult r = parse_str(kCanonicalFeed);

    std::vector<Quote> expected_quotes{
        Quote{"LIV-MUN", 1.95, 2.05, 1000},
        Quote{"ARS-CHE", 1.50, 1.60, 500},
    };
    EXPECT_EQ(r.quotes, expected_quotes);

    std::vector<ParseError> expected_errors{
        ParseError{5, ErrorKind::EmptySymbol},
        ParseError{6, ErrorKind::InvalidBid},
        ParseError{7, ErrorKind::WrongFieldCount},
        ParseError{8, ErrorKind::InvalidQty},
    };
    EXPECT_EQ(r.errors, expected_errors);
}

KATA_TEST(empty_input_yields_nothing) {
    ParseResult r = parse_str("");
    EXPECT_TRUE(r.quotes.empty());
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(comment_and_blank_only_yields_nothing) {
    ParseResult r = parse_str("# header\n\n   \n#another\n\t\n");
    EXPECT_TRUE(r.quotes.empty());
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(single_valid_quote) {
    ParseResult r = parse_str("LIV-MUN|1.95|2.05|1000\n");
    std::vector<Quote> expected{Quote{"LIV-MUN", 1.95, 2.05, 1000}};
    EXPECT_EQ(r.quotes, expected);
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(final_line_without_trailing_newline_is_parsed) {
    ParseResult r = parse_str("LIV-MUN|1.95|2.05|1000");
    std::vector<Quote> expected{Quote{"LIV-MUN", 1.95, 2.05, 1000}};
    EXPECT_EQ(r.quotes, expected);
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(wrong_field_count_too_few) {
    ParseResult r = parse_str("TOO|1.0|2.0\n");
    EXPECT_TRUE(r.quotes.empty());
    std::vector<ParseError> expected{ParseError{1, ErrorKind::WrongFieldCount}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(wrong_field_count_too_many) {
    ParseResult r = parse_str("TOO|1.0|2.0|10|extra\n");
    EXPECT_TRUE(r.quotes.empty());
    std::vector<ParseError> expected{ParseError{1, ErrorKind::WrongFieldCount}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(empty_symbol) {
    ParseResult r = parse_str("|1.0|2.0|10\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::EmptySymbol}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(invalid_bid) {
    ParseResult r = parse_str("BAD|x|2.0|10\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::InvalidBid}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(invalid_ask) {
    ParseResult r = parse_str("BAD|1.0|y|10\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::InvalidAsk}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(invalid_qty_non_numeric) {
    ParseResult r = parse_str("BAD|1.0|2.0|z\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::InvalidQty}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(negative_qty_is_invalid) {
    ParseResult r = parse_str("NEG|1.0|2.0|-5\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::InvalidQty}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(non_integer_qty_is_invalid) {
    ParseResult r = parse_str("FRC|1.0|2.0|1.5\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::InvalidQty}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(validation_order_stops_at_first_failure) {
    // Every field is bad; the first check in order (field-count is fine, symbol empty) wins.
    ParseResult r = parse_str("|x|y|-1\n");
    std::vector<ParseError> expected{ParseError{1, ErrorKind::EmptySymbol}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(surrounding_whitespace_is_trimmed) {
    ParseResult r = parse_str("  LIV-MUN | 1.95 | 2.05 | 1000  \n");
    std::vector<Quote> expected{Quote{"LIV-MUN", 1.95, 2.05, 1000}};
    EXPECT_EQ(r.quotes, expected);
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(zero_qty_is_valid) {
    ParseResult r = parse_str("ZER|1.0|2.0|0\n");
    std::vector<Quote> expected{Quote{"ZER", 1.0, 2.0, 0}};
    EXPECT_EQ(r.quotes, expected);
    EXPECT_TRUE(r.errors.empty());
}

KATA_TEST(physical_line_numbers_count_skipped_lines) {
    // Two comments + a blank precede the bad record, so it is physical line 4.
    ParseResult r = parse_str("# a\n# b\n\nTOO|1.0|2.0\n");
    std::vector<ParseError> expected{ParseError{4, ErrorKind::WrongFieldCount}};
    EXPECT_EQ(r.errors, expected);
}

KATA_TEST(parse_feed_reports_line_numbers_to_callbacks) {
    std::istringstream in(kCanonicalFeed);
    std::vector<std::size_t> quote_lines;
    std::vector<std::size_t> error_lines;
    parse_feed(
        in,
        [&](std::size_t line, const Quote&) { quote_lines.push_back(line); },
        [&](const ParseError& e) { error_lines.push_back(e.line); });

    std::vector<std::size_t> expected_quote_lines{2, 4};
    std::vector<std::size_t> expected_error_lines{5, 6, 7, 8};
    EXPECT_EQ(quote_lines, expected_quote_lines);
    EXPECT_EQ(error_lines, expected_error_lines);
}

KATA_MAIN()
