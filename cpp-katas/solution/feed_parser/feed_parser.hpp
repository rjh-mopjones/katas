// feed_parser.hpp — a streaming market-data feed parser: pipe-delimited text -> Quotes, allocation-free
// per field, no exceptions on the parse path.
//
// # The component
//
// A feed handler ingests a text feed of quote records — `SYMBOL|BID|ASK|QTY`, one per line — off a
// socket or a replay file, and turns it into typed `Quote`s the book can consume. It is on the ingest
// hot path: millions of lines, and a single malformed line (a truncated packet, a vendor bug, a stray
// comment) must NOT throw, allocate wildly, or abort the stream — it must be reported with its line
// number and skipped, and the good lines must keep flowing.
//
// # Why string_view + from_chars, and why not stringstream / stod
//
// The obvious parse is `std::getline` into a string, `std::stringstream` / `std::stod` per field. That
// is wrong three times over on this path. `stod` *throws* (`std::invalid_argument` / `out_of_range`) on
// bad input — so a malformed field aborts unless every call is wrapped in try/catch, and exceptions on
// the hot path are exactly what a feed handler avoids. `stringstream` allocates and is locale-sensitive
// (a `,` decimal locale silently misparses `1.95`). And splitting into `std::string` fields copies bytes
// we already hold. The right tool is `std::string_view` to slice the line's fields with zero copies, and
// `std::from_chars` to parse each slice: it never allocates, never throws (it returns a `std::errc` and a
// past-the-end pointer), and is locale-independent by construction. The one unavoidable copy is the
// symbol into `Quote::symbol` — see the lifetime caveat below.
//
// # The canonical contract the tests pin
//
// Feed = text lines `SYMBOL|BID|ASK|QTY`. After trimming surrounding whitespace:
//   - a BLANK line, or one starting with `#`, is SKIPPED — not a record and not an error — but it still
//     advances the 1-based physical line counter (line numbers track the file, not the record index).
//   - otherwise the line must have EXACTLY 4 `|`-separated fields. Validation runs in a fixed order and
//     stops at the first failure, so each bad line yields exactly one error:
//       field-count != 4        -> WrongFieldCount
//       symbol empty (after trim)-> EmptySymbol
//       bid not a float          -> InvalidBid
//       ask not a float          -> InvalidAsk
//       qty not a non-negative int-> InvalidQty   (a leading '-' is rejected: from_chars on an unsigned
//                                                   type refuses the sign, so "-5" -> InvalidQty)
//   - every error carries the 1-based physical line number where it occurred.
//
// # The lifetime caveat (why symbol is copied)
//
// `parse_feed` reuses ONE `std::string` line buffer across `getline` calls, so every `string_view` into
// it dangles the moment the next line is read. Fields are therefore views *for the duration of parsing
// one line only*; anything that must outlive the line (the symbol, stored in `Quote`) is copied into an
// owning `std::string`. Handing a caller a `string_view` into the recycled buffer would be a dangling
// read — the same class of bug as returning a pointer to a local.
//
// # The money angle
//
// A parser that throws on a malformed line lets one corrupt packet abort the whole feed — the book goes
// stale and every downstream strategy trades on frozen prices. A locale-sensitive parse silently reads
// `1,95` (or, worse, `1.95` under a comma locale) as the wrong number and mis-marks the book. Line-
// numbered errors are what lets ops pinpoint the offending record in a multi-gigabyte replay.
//
// # Alternatives
//
// A generated parser (Ragel / a hand-written state machine over the raw byte stream) avoids even the
// per-line getline copy and is what the very hottest handlers use. `std::regex` is the opposite extreme
// — allocating, slow, and throw-happy — and is the wrong tool here. This kata is the string_view +
// from_chars middle ground: allocation-free field parsing, no exceptions, ~20 lines, and the exact
// technique to reach for when someone hands you a delimited text feed.
#ifndef KATAS_FEED_PARSER_HPP
#define KATAS_FEED_PARSER_HPP

#include <charconv>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <istream>
#include <string>
#include <string_view>
#include <vector>

namespace katas {

// Which field of a record was malformed. One error is emitted per bad line, at the first failure in
// the fixed validation order (count -> symbol -> bid -> ask -> qty).
enum class ErrorKind { WrongFieldCount, EmptySymbol, InvalidBid, InvalidAsk, InvalidQty };

// A parsed quote. `symbol` OWNS its bytes (copied out of the recycled line buffer, which is why it is a
// std::string and not a string_view). Provided verbatim in both trees.
struct Quote {
    std::string symbol;
    double bid{};
    double ask{};
    std::uint64_t qty{};
    bool operator==(const Quote&) const = default;
};

// A malformed line: which kind of failure, at which 1-based physical line number. Provided verbatim.
struct ParseError {
    std::size_t line{};
    ErrorKind kind{};
    bool operator==(const ParseError&) const = default;
};

// The collected outcome of parse_all: the good quotes and the bad lines, in stream order.
struct ParseResult {
    std::vector<Quote> quotes;
    std::vector<ParseError> errors;
};

namespace detail {

// Trim ASCII whitespace from both ends of a view — no allocation, just narrows the window.
inline std::string_view trim(std::string_view s) noexcept {
    const auto is_ws = [](char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == '\v'; };
    while (!s.empty() && is_ws(s.front())) s.remove_prefix(1);
    while (!s.empty() && is_ws(s.back())) s.remove_suffix(1);
    return s;
}

// Parse a whole view as a double via from_chars. Returns false (no throw) unless the ENTIRE view is a
// valid number — a trailing garbage char (e.g. "1.0x") leaves ptr short of the end, which we reject.
inline bool parse_double(std::string_view s, double& out) noexcept {
    if (s.empty()) return false;
    const char* first = s.data();
    const char* last = s.data() + s.size();
    auto [ptr, ec] = std::from_chars(first, last, out);
    return ec == std::errc{} && ptr == last;
}

// Parse a whole view as an unsigned 64-bit integer. from_chars on an unsigned type rejects a leading
// '-' on its own (returns errc::invalid_argument), which is exactly the "reject negative qty" rule.
inline bool parse_u64(std::string_view s, std::uint64_t& out) noexcept {
    if (s.empty()) return false;
    const char* first = s.data();
    const char* last = s.data() + s.size();
    auto [ptr, ec] = std::from_chars(first, last, out);
    return ec == std::errc{} && ptr == last;
}

} // namespace detail

// Streaming parser. Reads `in` line by line with std::getline into ONE reused buffer; for each line
// that is neither blank nor a comment it parses the four fields and invokes exactly one of the
// callbacks with the 1-based physical line number. Never throws on malformed input, never allocates per
// field (only the symbol is copied, into the Quote handed to on_quote).
inline void parse_feed(std::istream& in,
                       const std::function<void(std::size_t line, const Quote&)>& on_quote,
                       const std::function<void(const ParseError&)>& on_error) {
    std::string buffer;
    std::size_t line_no = 0;

    while (std::getline(in, buffer)) {
        ++line_no; // physical line number, 1-based, advanced for EVERY line including skipped ones

        std::string_view line = detail::trim(buffer);
        if (line.empty() || line.front() == '#') continue; // blank / comment: skip, not an error

        // Split into up to 5 field views (4 valid + 1 overflow sentinel) without allocating.
        std::string_view fields[5];
        std::size_t n = 0;
        std::size_t start = 0;
        while (n < 5) {
            std::size_t bar = line.find('|', start);
            if (bar == std::string_view::npos) {
                fields[n++] = line.substr(start);
                break;
            }
            fields[n++] = line.substr(start, bar - start);
            start = bar + 1;
        }

        if (n != 4) {
            on_error({line_no, ErrorKind::WrongFieldCount});
            continue;
        }

        std::string_view symbol = detail::trim(fields[0]);
        if (symbol.empty()) {
            on_error({line_no, ErrorKind::EmptySymbol});
            continue;
        }

        double bid{};
        if (!detail::parse_double(detail::trim(fields[1]), bid)) {
            on_error({line_no, ErrorKind::InvalidBid});
            continue;
        }

        double ask{};
        if (!detail::parse_double(detail::trim(fields[2]), ask)) {
            on_error({line_no, ErrorKind::InvalidAsk});
            continue;
        }

        std::uint64_t qty{};
        if (!detail::parse_u64(detail::trim(fields[3]), qty)) {
            on_error({line_no, ErrorKind::InvalidQty});
            continue;
        }

        // symbol is copied here — the string_view dangles once the next getline overwrites `buffer`.
        on_quote(line_no, Quote{std::string(symbol), bid, ask, qty});
    }
}

// Collector built on parse_feed: drains the stream into a ParseResult (quotes + errors in order).
inline ParseResult parse_all(std::istream& in) {
    ParseResult result;
    parse_feed(
        in,
        [&](std::size_t, const Quote& q) { result.quotes.push_back(q); },
        [&](const ParseError& e) { result.errors.push_back(e); });
    return result;
}

} // namespace katas

#endif // KATAS_FEED_PARSER_HPP
