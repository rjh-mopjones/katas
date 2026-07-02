#ifndef KATAS_EXCHANGE_SESSION_HPP
#define KATAS_EXCHANGE_SESSION_HPP

#include <cstdint>
#include <stdexcept>
#include <string>

namespace katas {

// Provided verbatim — do not modify.
struct Order {
    std::uint64_t id{};
    double price{};
    std::uint32_t qty{};
};

// You implement this. Design the fields and the ownership/release logic yourself; keep the public
// signatures below. Replace every `throw std::logic_error("TODO: implement")` with your code.
class ExchangeSession {
public:
    explicit ExchangeSession(std::string venue) { (void)venue; throw std::logic_error("TODO: implement"); }
    ~ExchangeSession() noexcept {}

    ExchangeSession(const ExchangeSession&) = delete;
    ExchangeSession& operator=(const ExchangeSession&) = delete;

    ExchangeSession(ExchangeSession&&) noexcept {}
    ExchangeSession& operator=(ExchangeSession&&) noexcept { return *this; }

    void send(const Order& order) { (void)order; throw std::logic_error("TODO: implement"); }
    bool is_open() const noexcept { return false; }
    const std::string& venue() const noexcept { static const std::string none; return none; }
    std::size_t sent() const noexcept { return 0; }

    // Test hook: the number of sessions currently holding an open connection.
    static int live_count() noexcept { return 0; }
};

} // namespace katas

#endif // KATAS_EXCHANGE_SESSION_HPP
