// top_of_book.hpp — a single-writer / many-reader quote publisher built on a seqlock.
//
// # The component
//
// A market-data thread continuously publishes the best bid/ask for a symbol (a multi-word Quote).
// Dozens of strategy threads read the *latest* quote on every tick of their own loops. The read path
// is the hot path — it must never block the writer and never block each other — and a reader must
// always see a **consistent** quote: all of {bid, ask, seq} from one single publish, never a mix of
// a new bid with a stale ask. A torn quote is not a cosmetic glitch: acting on (new_bid, old_ask)
// can make you cross your own book or quote a price that never existed.
//
// # Why a seqlock
//
// The access pattern is extremely read-heavy with exactly one writer. A mutex serialises readers
// against the writer (and, with a plain mutex, against each other). An RWMutex lets readers run
// together but still has the writer take an exclusive lock and readers do atomic bookkeeping. A
// seqlock lets readers run **wait-free and write-free**: they take no lock and modify no shared
// state. The writer bumps a sequence counter to an odd value before writing and back to even after;
// a reader snapshots the counter, copies the payload, and re-reads the counter — if it changed or
// was odd, a write overlapped and the reader retries. Readers never block the writer; the writer
// never waits for readers. The cost: readers may spin briefly under a burst of writes, and there
// must be a single writer (two writers would corrupt the odd/even protocol — use a mutex among
// writers, or a different structure, if you have many).
//
// # Why the payload fields are atomic (the subtle part)
//
// The textbook seqlock copies the payload with plain, non-atomic loads/stores and relies on the
// sequence check for correctness. Under the C++ memory model that is a **data race** (concurrent
// non-atomic access to the same object), i.e. undefined behaviour, and ThreadSanitizer correctly
// flags it. Boehm & Adve's "Can Seqlocks Get Along With Programming Language Memory Models?" is the
// canonical reference for this gap. The fix that keeps the seqlock algorithm intact *and* is
// well-defined: store each payload field as a `std::atomic` accessed with `memory_order_relaxed`.
// Relaxed atomics cost the same as plain accesses on the platforms we target (x86/ARM) but make the
// accesses race-free by definition, so TSan is clean. The sequence counter still provides the
// *group* consistency that relaxed per-field atomics do not (a reader could otherwise read bid from
// publish N and ask from publish N+1); the retry loop rejects exactly those mixed reads.
//
// # Alternative
//
// A double-buffer (two Quote slots + an atomic active-index the writer flips) also gives wait-free
// reads and, unlike a seqlock, never makes readers retry — at the cost of two payload copies and a
// slightly trickier reclamation story. The seqlock wins when the payload is small and writes are
// frequent enough that a second buffer's cache footprint matters. Benchmarking the two is the kata
// extension.
#ifndef KATAS_TOP_OF_BOOK_HPP
#define KATAS_TOP_OF_BOOK_HPP

#include <atomic>
#include <cstdint>

namespace katas {

// Quote is an immutable snapshot of a symbol's two-sided top of book. `seq` is the publisher's
// monotonic update number; it lets a reader (and the tests) assert that every field came from one
// publish — a torn read shows fields that disagree.
struct Quote {
    double bid_px{};
    std::uint32_t bid_qty{};
    double ask_px{};
    std::uint32_t ask_qty{};
    std::uint64_t seq{};

    bool operator==(const Quote&) const = default;
};

// TopOfBook publishes the latest Quote for one symbol. Safe for ONE writer thread calling publish()
// and ANY number of reader threads calling read(). The zero state is a well-formed empty quote.
class TopOfBook {
public:
    TopOfBook() = default;

    TopOfBook(const TopOfBook&) = delete;
    TopOfBook& operator=(const TopOfBook&) = delete;

    // publish installs a new quote. Single writer only.
    //
    // The sequence goes even -> odd (write in progress) -> even (done). Readers that observe an odd
    // count, or a count that changes across their read, retry. The release fences order the payload
    // stores after the odd transition and before the even transition, so a reader that sees the even
    // count with an acquire fence also sees all the payload stores.
    void publish(const Quote& q) noexcept {
        const std::uint64_t s = seq_.load(std::memory_order_relaxed);
        seq_.store(s + 1, std::memory_order_relaxed); // enter write: odd
        std::atomic_thread_fence(std::memory_order_release);

        bid_px_.store(q.bid_px, std::memory_order_relaxed);
        bid_qty_.store(q.bid_qty, std::memory_order_relaxed);
        ask_px_.store(q.ask_px, std::memory_order_relaxed);
        ask_qty_.store(q.ask_qty, std::memory_order_relaxed);
        qseq_.store(q.seq, std::memory_order_relaxed);

        std::atomic_thread_fence(std::memory_order_release);
        seq_.store(s + 2, std::memory_order_relaxed); // exit write: even
    }

    // read returns the latest consistent quote. Wait-free with respect to other readers; it takes no
    // lock and writes no shared state. It may spin only while a publish is actually in flight.
    Quote read() const noexcept {
        Quote out;
        for (;;) {
            const std::uint64_t before = seq_.load(std::memory_order_acquire);
            if (before & 1u) continue; // a write is in progress; wait for it to finish

            out.bid_px = bid_px_.load(std::memory_order_relaxed);
            out.bid_qty = bid_qty_.load(std::memory_order_relaxed);
            out.ask_px = ask_px_.load(std::memory_order_relaxed);
            out.ask_qty = ask_qty_.load(std::memory_order_relaxed);
            out.seq = qseq_.load(std::memory_order_relaxed);

            std::atomic_thread_fence(std::memory_order_acquire);
            const std::uint64_t after = seq_.load(std::memory_order_relaxed);
            if (before == after) return out; // no write overlapped our read
        }
    }

    // The current sequence number (even when idle). Exposed for tests/diagnostics.
    std::uint64_t sequence() const noexcept { return seq_.load(std::memory_order_relaxed); }

private:
    std::atomic<std::uint64_t> seq_{0};

    // Payload as per-field relaxed atomics — see the header note on why these are atomic, not plain.
    std::atomic<double> bid_px_{0.0};
    std::atomic<std::uint32_t> bid_qty_{0};
    std::atomic<double> ask_px_{0.0};
    std::atomic<std::uint32_t> ask_qty_{0};
    std::atomic<std::uint64_t> qseq_{0};
};

} // namespace katas

#endif // KATAS_TOP_OF_BOOK_HPP
