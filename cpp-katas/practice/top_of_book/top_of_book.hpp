#ifndef KATAS_TOP_OF_BOOK_HPP
#define KATAS_TOP_OF_BOOK_HPP

#include <cstdint>

namespace katas {

// Provided verbatim — do not modify. `seq` lets you (and your tests) detect a torn read.
struct Quote {
    double bid_px{};
    std::uint32_t bid_qty{};
    double ask_px{};
    std::uint32_t ask_qty{};
    std::uint64_t seq{};

    bool operator==(const Quote&) const = default;
};

// You implement this. One writer thread calls publish(); many reader threads call read()
// concurrently and must always get a consistent quote without ever blocking the writer. Design the
// synchronisation yourself (a seqlock is the intended approach — see the README).
class TopOfBook {
public:
    TopOfBook() = default;

    TopOfBook(const TopOfBook&) = delete;
    TopOfBook& operator=(const TopOfBook&) = delete;

    void publish(const Quote& q) noexcept { (void)q; /* TODO: implement */ }
    Quote read() const noexcept { return Quote{}; /* TODO: implement */ }
    std::uint64_t sequence() const noexcept { return 0; /* TODO: implement */ }
};

} // namespace katas

#endif // KATAS_TOP_OF_BOOK_HPP
