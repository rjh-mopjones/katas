// Race-stress for the SPSC feed pipe. Run under ThreadSanitizer (the -race analogue):
//   cmake -B build-tsan -DKATAS_SANITIZE=thread && cmake --build build-tsan && ctest --test-dir build-tsan -R feed_pipe
//
// One producer pushes a long run of events with strictly increasing seq (0,1,2,...); one consumer
// pops them all. The consumer asserts it receives every seq exactly once, in order — proving no lost,
// duplicated, or reordered events across the hand-off. Both threads are released together via a
// StartGate. The ordering invariant catches bugs even without the sanitizer; TSan proves the payload
// accesses are race-free.
#include "harness.hpp"

#include "feed_pipe.hpp"

#include <cstdint>
#include <thread>

using katas::FeedEvent;
using katas::FeedPipe;

KATA_TEST(spsc_no_lost_or_duplicated_events) {
    constexpr std::uint64_t kEvents = 1000000;
    FeedPipe<FeedEvent> pipe(1024);

    kata::StartGate gate;
    std::atomic<bool> ok{true};
    std::uint64_t received = 0;

    std::jthread consumer([&] {
        gate.wait();
        std::uint64_t expected = 0;
        while (expected < kEvents) {
            auto ev = pipe.try_pop();
            if (!ev) continue; // pipe momentarily empty; spin
            if (ev->seq != expected) {         // out-of-order / lost / duplicated
                ok.store(false, std::memory_order_relaxed);
                return;
            }
            ++expected;
            ++received;
        }
    });

    std::jthread producer([&] {
        gate.wait();
        for (std::uint64_t s = 0; s < kEvents; ++s) {
            FeedEvent ev{s, static_cast<std::uint32_t>(s & 0xFFFF), static_cast<double>(s)};
            while (!pipe.try_push(ev)) { /* pipe full; spin until the consumer drains a slot */ }
        }
    });

    gate.open();
    producer.join();
    consumer.join();

    EXPECT_TRUE(ok.load(std::memory_order_relaxed));
    EXPECT_EQ(received, kEvents);
    EXPECT_FALSE(pipe.try_pop().has_value()); // fully drained
}
