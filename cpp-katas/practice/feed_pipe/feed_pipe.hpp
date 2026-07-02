#ifndef KATAS_FEED_PIPE_HPP
#define KATAS_FEED_PIPE_HPP

#include <cstddef>
#include <cstdint>
#include <optional>
#include <stdexcept>
#include <utility>

namespace katas {

// Provided verbatim — do not modify.
struct FeedEvent {
    std::uint64_t seq{};
    std::uint32_t symbol_id{};
    double price{};

    bool operator==(const FeedEvent&) const = default;
};

// You implement this. Exactly ONE thread calls try_push and exactly ONE (other) thread calls
// try_pop; those two may run fully concurrently. Design the ring + the atomics yourself.
template <typename T>
class FeedPipe {
public:
    explicit FeedPipe(std::size_t capacity) { (void)capacity; throw std::logic_error("TODO: implement"); }

    FeedPipe(const FeedPipe&) = delete;
    FeedPipe& operator=(const FeedPipe&) = delete;

    bool try_push(const T& value) { (void)value; throw std::logic_error("TODO: implement"); }
    bool try_push(T&& value) { (void)value; throw std::logic_error("TODO: implement"); }
    std::optional<T> try_pop() { throw std::logic_error("TODO: implement"); }
    std::size_t capacity() const noexcept { return 0; }
};

} // namespace katas

#endif // KATAS_FEED_PIPE_HPP
