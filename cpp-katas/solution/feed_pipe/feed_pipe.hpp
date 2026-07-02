// feed_pipe.hpp — a wait-free single-producer / single-consumer ring buffer.
//
// # The component
//
// A market-data feed handler runs a tight parse loop on one thread: it reads UDP datagrams, decodes
// them into FeedEvents, and hands each event to the strategy thread. The strategy thread consumes
// events and updates its books. This is a classic hand-off across exactly two threads — one producer,
// one consumer — and it is on the latency-critical path, so it must not take a lock, must not
// allocate, and must never lose, duplicate, or reorder an event. A dropped event is a missed fill; a
// duplicated event is a phantom position.
//
// # Why single-producer / single-consumer changes everything
//
// A general multi-producer/multi-consumer queue needs CAS loops and careful reclamation. But with
// exactly one producer and one consumer you can do far better: the producer owns the write index
// (tail) and the consumer owns the read index (head). Each index has a single writer, so it needs no
// CAS — just an atomic load/store with the right memory ordering. That is what makes this ring
// *wait-free* on both sides: try_push and try_pop each run in a bounded number of steps with no
// spinning and no locks.
//
// # The memory ordering (the crux)
//
//   - The producer writes the payload into slot `tail`, THEN publishes the new tail with a
//     release store. The consumer reads tail with an acquire load; when it sees the new tail, the
//     release/acquire pair guarantees it also sees the payload the producer wrote first. So the
//     non-atomic slot read is safe — it is ordered *after* the producer's write by happens-before.
//   - Symmetrically, the consumer reads the payload from slot `head`, THEN publishes the new head
//     with a release store; the producer acquire-loads head before it is allowed to overwrite that
//     slot. So the producer never overwrites a slot the consumer is still reading.
//
// Because the two atomics fence the payload accesses, the slots themselves can be plain (non-atomic)
// `T` — at any instant a given slot has a single accessor, and the release/acquire edges order the
// hand-off. (Contrast the seqlock kata, where reader and writer genuinely touch the payload at the
// same time, so its payload must be atomic.) ThreadSanitizer is clean here because the ordering is
// real, not assumed.
//
// # Capacity
//
// The ring stores `capacity` usable elements in `capacity + 1` slots: the one always-empty slot is
// how the code distinguishes "full" (tail + 1 == head) from "empty" (head == tail) without a
// separate size counter that both threads would have to contend on.
//
// # Alternatives / extensions
//
// head_ and tail_ sit on their own cache lines (alignas) to avoid false sharing — two threads
// writing adjacent atomics on the same line would ping-pong the line between cores and destroy the
// throughput this structure exists to provide. A bounded MPMC queue (e.g. Vyukov's) generalises to
// many producers/consumers at the cost of a per-slot sequence number and CAS; use it only when you
// actually have more than one of each. Benchmarking against a std::mutex + std::queue is the kata
// extension.
#ifndef KATAS_FEED_PIPE_HPP
#define KATAS_FEED_PIPE_HPP

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <new>
#include <optional>
#include <stdexcept>
#include <utility>
#include <vector>

namespace katas {

// FeedEvent is the fixture handed across the pipe. Provided verbatim in both trees.
struct FeedEvent {
    std::uint64_t seq{};
    std::uint32_t symbol_id{};
    double price{};

    bool operator==(const FeedEvent&) const = default;
};

// FeedPipe is a wait-free SPSC ring. Exactly ONE thread may call try_push and exactly ONE (other)
// thread may call try_pop; those two may run fully concurrently.
template <typename T>
class FeedPipe {
public:
    // capacity is the number of elements the pipe can hold; it must be > 0.
    explicit FeedPipe(std::size_t capacity)
        : capacity_(capacity), slots_(capacity + 1) {
        if (capacity == 0) throw std::invalid_argument("FeedPipe capacity must be > 0");
    }

    FeedPipe(const FeedPipe&) = delete;
    FeedPipe& operator=(const FeedPipe&) = delete;

    // Producer side. Returns false (without blocking) if the pipe is full.
    bool try_push(const T& value) { return emplace(value); }
    bool try_push(T&& value) { return emplace(std::move(value)); }

    // Consumer side. Returns std::nullopt (without blocking) if the pipe is empty.
    std::optional<T> try_pop() {
        const std::size_t head = head_.load(std::memory_order_relaxed);
        if (head == tail_.load(std::memory_order_acquire)) return std::nullopt; // empty

        T value = std::move(slots_[head]);
        head_.store(next(head), std::memory_order_release);
        return value;
    }

    std::size_t capacity() const noexcept { return capacity_; }

private:
    template <typename U>
    bool emplace(U&& value) {
        const std::size_t tail = tail_.load(std::memory_order_relaxed);
        const std::size_t nxt = next(tail);
        if (nxt == head_.load(std::memory_order_acquire)) return false; // full

        slots_[tail] = std::forward<U>(value);
        tail_.store(nxt, std::memory_order_release);
        return true;
    }

    std::size_t next(std::size_t i) const noexcept {
        return i + 1 == slots_.size() ? 0 : i + 1;
    }

    static constexpr std::size_t kCacheLine = 64;

    std::size_t capacity_;
    std::vector<T> slots_;

    // Producer and consumer indices on separate cache lines — no false sharing on the hot path.
    alignas(kCacheLine) std::atomic<std::size_t> head_{0};
    alignas(kCacheLine) std::atomic<std::size_t> tail_{0};
};

} // namespace katas

#endif // KATAS_FEED_PIPE_HPP
