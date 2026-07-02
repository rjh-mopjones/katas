// exchange_session.hpp — a move-only RAII handle to one venue connection.
//
// # The component
//
// Every strategy that trades a venue holds an ExchangeSession: constructing one "connects" (claims a
// session slot), and destroying one "disconnects" exactly once. Sessions are expensive, unique
// resources — you never want two live handles believing they own the same connection, and you never
// want a connection to leak (slot never freed) or be closed twice (a later order sent on a recycled
// slot). That is precisely the ownership model of a file descriptor, a socket, or a std::unique_ptr.
//
// # Why move-only
//
// A connection has *one* owner. Copying a handle would give two objects that each think they must
// close the connection — a double-close on the second destruction. So the copy constructor and copy
// assignment are deleted, and ownership is transferred by *move*: the moved-from session is left
// closed and inert (is_open() == false), so its destructor does nothing. This is the same rule of
// five reasoning behind unique_ptr, ifstream, and thread.
//
// # The invariants the tests pin
//
//   - Constructing increments live_count(); destroying an *open* session decrements it. So after any
//     balanced set of scopes, live_count() returns to zero — the leak / double-close detector.
//   - A moved-from session is closed: it does not decrement live_count() again on destruction, and
//     send() on it throws.
//   - Move assignment releases the assignee's current connection first (decrement), then steals.
//   - The destructor is noexcept: a resource-releasing destructor must never throw, or a second
//     exception during stack unwinding calls std::terminate.
//
// # Alternatives
//
// You could reach for std::shared_ptr<Connection> with a custom deleter for shared ownership, but a
// venue session is single-owner by nature; shared ownership only invites the question "who really
// closes it?". unique_ptr with a custom deleter is the closest stdlib equivalent — this kata is that
// pattern written by hand so the move mechanics are explicit.
#ifndef KATAS_EXCHANGE_SESSION_HPP
#define KATAS_EXCHANGE_SESSION_HPP

#include <cstdint>
#include <stdexcept>
#include <string>
#include <utility>

namespace katas {

// Order is the fixture the session transmits. Provided verbatim in both trees.
struct Order {
    std::uint64_t id{};
    double price{};
    std::uint32_t qty{};
};

// live_sessions() is the process-wide count of open connections — the test hook that proves every
// connection is released exactly once. (Single-threaded kata; a real venue pool would track this per
// connection object, not globally.)
inline int& live_sessions() {
    static int n = 0;
    return n;
}

class ExchangeSession {
public:
    // Connecting claims a session slot.
    explicit ExchangeSession(std::string venue) : venue_(std::move(venue)), open_(true) {
        ++live_sessions();
    }

    // Releases the connection exactly once. noexcept: a destructor that frees a resource must not
    // throw during stack unwinding.
    ~ExchangeSession() noexcept { close(); }

    ExchangeSession(const ExchangeSession&) = delete;
    ExchangeSession& operator=(const ExchangeSession&) = delete;

    // Move steals ownership; the source is left closed so its destructor is a no-op.
    ExchangeSession(ExchangeSession&& other) noexcept
        : venue_(std::move(other.venue_)), open_(other.open_), sent_(other.sent_) {
        other.open_ = false;
    }

    ExchangeSession& operator=(ExchangeSession&& other) noexcept {
        if (this != &other) {
            close();                          // release what we currently own, first
            venue_ = std::move(other.venue_);
            open_ = other.open_;
            sent_ = other.sent_;
            other.open_ = false;              // leave the source inert
        }
        return *this;
    }

    // Transmits an order. Throws if the connection is closed (including a moved-from handle).
    void send(const Order& order) {
        if (!open_) throw std::logic_error("send on a closed ExchangeSession");
        (void)order;
        ++sent_;
    }

    bool is_open() const noexcept { return open_; }
    const std::string& venue() const noexcept { return venue_; }
    std::size_t sent() const noexcept { return sent_; }

    static int live_count() noexcept { return live_sessions(); }

private:
    void close() noexcept {
        if (open_) {
            --live_sessions();
            open_ = false;
        }
    }

    std::string venue_;
    bool open_{false};
    std::size_t sent_{0};
};

} // namespace katas

#endif // KATAS_EXCHANGE_SESSION_HPP
