// tick_buffer.hpp — a fixed-capacity rolling window of the last N market ticks, allocation-free per tick.
//
// # The component
//
// A pricing/analytics loop keeps the last N ticks for a symbol — the raw material for a rolling VWAP,
// a last-N-trades feed, a short-horizon momentum signal. It sees millions of ticks a second, so the
// window is a *bounded* history that recycles storage: once it holds N ticks, each new tick overwrites
// the oldest. The hard rule on this hot path is **zero heap churn per tick** — you allocate the backing
// store once at construction and never again, no matter how many ticks stream through.
//
// # Why raw storage, and why not vector<T>
//
// The obvious backing store is `std::vector<T>` or `new T[capacity]`, but both *default-construct*
// every T up front (and `vector` may reallocate as it grows). That is wrong twice over: a Tick has no
// meaningful "empty" value, and default-constructing N of them is work you never asked for. What you
// actually want is N slots of *raw, uninitialised, correctly-aligned* memory, with a live T constructed
// in a slot only when a tick is recorded there and destroyed when it is overwritten or the buffer dies.
// So the store is `::operator new(sizeof(T)*capacity, align_val_t(alignof(T)))` — a bag of bytes — and
// element lifetime is managed by hand: `::new (slot) T(tick)` to construct (placement-new),
// `std::destroy_at(slot)` to destroy.
//
// # The invariants the tests pin
//
//   - `record` appends; while size() < capacity() it fills empty slots, and once full it *overwrites*
//     the oldest slot — but it must `destroy_at` the element living there BEFORE placement-newing the
//     replacement, or the overwritten T leaks (its destructor never runs). Destroy-before-construct.
//   - The ring is `(head_ + count_) % capacity_` for the write slot and `head_` for the oldest;
//     overwriting advances `head_`. Get the modular arithmetic wrong and snapshot()/latest() read the
//     wrong slot or an already-destroyed one.
//   - The destructor destroys *exactly* the count_ live elements (walking from head_, wrapping) and
//     then frees the raw store with the matching sized, aligned `::operator delete`. No leak, no
//     double-destroy of a slot that was never constructed.
//   - The type is non-copyable and non-movable: manual lifetime management makes correct copy/move
//     fiddly, and the kata's point is the storage discipline, not the rule of five (that is the
//     extension task).
//
// # The money angle
//
// A per-tick allocation stalls the pricing loop under GC-like malloc contention and blows the latency
// budget on the exact ticks that move the market. A leaked element (overwrite without destroy) is a
// slow bleed that eventually OOMs a long-running session; reading a destroyed slot (bad ring math)
// feeds a stale or garbage price into the VWAP and mis-marks the book.
//
// # Alternatives
//
// `boost::circular_buffer` is exactly this container, production-hardened. A `std::deque<T>` with a
// manual cap (pop_front when it exceeds N) is allocation-light but not allocation-free and has worse
// locality. This kata is the hand-written version so the placement-new / destroy-at / ring mechanics
// are explicit.
#ifndef KATAS_TICK_BUFFER_HPP
#define KATAS_TICK_BUFFER_HPP

#include <cstdint>
#include <memory>
#include <new>
#include <stdexcept>
#include <vector>

namespace katas {

// Tick is the fixture the buffer stores. Provided verbatim in both trees.
struct Tick {
    std::uint64_t seq{};
    double price{};
    std::uint32_t qty{};
    bool operator==(const Tick&) const = default; // so EXPECT_EQ works
};

template <typename T>
class TickBuffer {
public:
    // Allocates raw, uninitialised, aligned storage for `capacity` elements — no T is constructed yet.
    explicit TickBuffer(std::size_t capacity)
        : capacity_(capacity),
          data_(capacity == 0
                    ? throw std::invalid_argument("TickBuffer capacity must be > 0")
                    : static_cast<T*>(::operator new(sizeof(T) * capacity,
                                                     std::align_val_t(alignof(T))))) {}

    // Destroys the live elements (exactly count_ of them, oldest-first with wrap) then frees the store.
    ~TickBuffer() {
        for (std::size_t i = 0; i < count_; ++i) {
            std::destroy_at(slot(head_ + i));
        }
        ::operator delete(data_, sizeof(T) * capacity_, std::align_val_t(alignof(T)));
    }

    TickBuffer(const TickBuffer&) = delete;
    TickBuffer& operator=(const TickBuffer&) = delete;
    TickBuffer(TickBuffer&&) = delete;
    TickBuffer& operator=(TickBuffer&&) = delete;

    // Appends a tick. While not full it constructs into the next free slot; when full it destroys the
    // oldest element first, then placement-news the replacement into that slot and advances head_.
    void record(const T& tick) {
        T* write = slot(head_ + count_);
        if (count_ == capacity_) {
            std::destroy_at(write);          // release the element we are about to overwrite, FIRST
            ::new (static_cast<void*>(write)) T(tick);
            head_ = index(head_ + 1);        // oldest advances; size stays at capacity
        } else {
            ::new (static_cast<void*>(write)) T(tick);
            ++count_;
        }
    }

    // The most-recently recorded tick. Throws std::out_of_range if the buffer is empty.
    const T& latest() const {
        if (count_ == 0) throw std::out_of_range("TickBuffer::latest on empty buffer");
        return *slot(head_ + count_ - 1);
    }

    // A copy of the live window, oldest -> newest.
    std::vector<T> snapshot() const {
        std::vector<T> out;
        out.reserve(count_);
        for (std::size_t i = 0; i < count_; ++i) {
            out.push_back(*slot(head_ + i));
        }
        return out;
    }

    std::size_t size() const noexcept { return count_; }
    std::size_t capacity() const noexcept { return capacity_; }
    bool empty() const noexcept { return count_ == 0; }
    bool full() const noexcept { return count_ == capacity_; }

private:
    // Map an unwrapped logical position onto a physical slot pointer. `logical` may be up to
    // head_ + count_ (< 2 * capacity_), so one modulo brings it back into range.
    T* slot(std::size_t logical) noexcept { return data_ + index(logical); }
    const T* slot(std::size_t logical) const noexcept { return data_ + index(logical); }
    std::size_t index(std::size_t logical) const noexcept { return logical % capacity_; }

    std::size_t capacity_;
    T* data_;
    std::size_t head_{0};  // physical index of the oldest live element
    std::size_t count_{0}; // number of live elements
};

} // namespace katas

#endif // KATAS_TICK_BUFFER_HPP
