#ifndef KATAS_TICK_BUFFER_HPP
#define KATAS_TICK_BUFFER_HPP

#include <cstdint>
#include <stdexcept>
#include <vector>

namespace katas {

// Provided verbatim — do not modify.
struct Tick {
    std::uint64_t seq{};
    double price{};
    std::uint32_t qty{};
    bool operator==(const Tick&) const = default;
};

// You implement this. Design the storage and lifetime management yourself; keep the public signatures
// below. Replace every `throw std::logic_error("TODO: implement")` with your code.
template <typename T>
class TickBuffer {
public:
    explicit TickBuffer(std::size_t capacity) { (void)capacity; throw std::logic_error("TODO: implement"); }
    ~TickBuffer() {}

    TickBuffer(const TickBuffer&) = delete;
    TickBuffer& operator=(const TickBuffer&) = delete;
    TickBuffer(TickBuffer&&) = delete;
    TickBuffer& operator=(TickBuffer&&) = delete;

    void record(const T& tick) { (void)tick; throw std::logic_error("TODO: implement"); }
    const T& latest() const { throw std::logic_error("TODO: implement"); }
    std::vector<T> snapshot() const { throw std::logic_error("TODO: implement"); }
    std::size_t size() const noexcept { return 0; }
    std::size_t capacity() const noexcept { return 0; }
    bool empty() const noexcept { return false; }
    bool full() const noexcept { return false; }
};

} // namespace katas

#endif // KATAS_TICK_BUFFER_HPP
