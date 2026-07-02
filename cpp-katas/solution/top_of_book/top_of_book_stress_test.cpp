// Race-stress for the seqlock quote publisher. Run under ThreadSanitizer (the -race analogue):
//   cmake -B build-tsan -DKATAS_SANITIZE=thread && cmake --build build-tsan && ctest --test-dir build-tsan -R top_of_book
//
// One writer publishes self-consistent quotes (every field derived from a single counter v); many
// readers spin on read() and assert the quote they get is internally consistent. A torn read (fields
// from different publishes) breaks the relation and is caught even without the sanitizer; TSan proves
// the accesses are race-free. All readers are released together via a StartGate to maximise overlap.
#include "harness.hpp"

#include "top_of_book.hpp"

#include <cstdint>
#include <thread>
#include <vector>

using katas::Quote;
using katas::TopOfBook;

namespace {

// encode builds a Quote whose fields are all derived from one value v, so a reader can verify they
// belong to a single publish.
Quote encode(std::uint64_t v) {
    return Quote{
        static_cast<double>(v),                       // bid_px
        static_cast<std::uint32_t>(v),                // bid_qty
        static_cast<double>(v) + 0.5,                 // ask_px
        static_cast<std::uint32_t>(v) + 1u,           // ask_qty
        v,                                            // seq
    };
}

// consistent reports whether q could have been produced by a single encode() call.
bool consistent(const Quote& q) {
    return q.bid_px == static_cast<double>(q.seq) &&
           q.ask_px == static_cast<double>(q.seq) + 0.5 &&
           q.bid_qty == static_cast<std::uint32_t>(q.seq) &&
           q.ask_qty == static_cast<std::uint32_t>(q.seq) + 1u;
}

} // namespace

KATA_TEST(seqlock_no_torn_reads_under_contention) {
    TopOfBook tob;
    tob.publish(encode(0));

    constexpr int kReaders = 8;
    constexpr std::uint64_t kWrites = 200000;

    kata::StartGate gate;
    std::atomic<bool> torn{false};

    std::vector<std::jthread> readers;
    readers.reserve(kReaders);
    for (int r = 0; r < kReaders; ++r) {
        readers.emplace_back([&] {
            gate.wait();
            while (tob.sequence() < kWrites * 2) {
                if (!consistent(tob.read())) {
                    torn.store(true, std::memory_order_relaxed);
                    return;
                }
            }
        });
    }

    std::jthread writer([&] {
        gate.wait();
        for (std::uint64_t v = 1; v <= kWrites; ++v) tob.publish(encode(v));
    });

    gate.open();
    writer.join();
    readers.clear(); // join all readers

    EXPECT_FALSE(torn.load(std::memory_order_relaxed));
    EXPECT_TRUE(consistent(tob.read()));
    EXPECT_EQ(tob.read().seq, kWrites);
}
