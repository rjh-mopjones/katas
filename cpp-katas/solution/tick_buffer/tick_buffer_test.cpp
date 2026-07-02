#include "harness.hpp"

#include "tick_buffer.hpp"

#include <stdexcept>
#include <vector>

using katas::Tick;
using katas::TickBuffer;

KATA_TEST(empty_buffer_has_no_ticks) {
    TickBuffer<Tick> buf(4);
    EXPECT_EQ(buf.size(), 0u);
    EXPECT_EQ(buf.capacity(), 4u);
    EXPECT_TRUE(buf.empty());
    EXPECT_FALSE(buf.full());
    EXPECT_THROWS(buf.latest(), std::out_of_range);
    EXPECT_TRUE(buf.snapshot().empty());
}

KATA_TEST(capacity_zero_throws) {
    EXPECT_THROWS(TickBuffer<Tick>(0), std::invalid_argument);
}

KATA_TEST(record_below_capacity_keeps_all) {
    TickBuffer<Tick> buf(4);
    buf.record(Tick{1, 100.0, 10});
    buf.record(Tick{2, 101.0, 5});

    EXPECT_EQ(buf.size(), 2u);
    EXPECT_FALSE(buf.empty());
    EXPECT_FALSE(buf.full());
    EXPECT_EQ(buf.latest(), (Tick{2, 101.0, 5}));

    std::vector<Tick> expected{Tick{1, 100.0, 10}, Tick{2, 101.0, 5}};
    EXPECT_EQ(buf.snapshot(), expected);
}

KATA_TEST(latest_returns_most_recent) {
    TickBuffer<Tick> buf(3);
    buf.record(Tick{1, 10.0, 1});
    EXPECT_EQ(buf.latest(), (Tick{1, 10.0, 1}));
    buf.record(Tick{2, 20.0, 2});
    EXPECT_EQ(buf.latest(), (Tick{2, 20.0, 2}));
    buf.record(Tick{3, 30.0, 3});
    EXPECT_EQ(buf.latest(), (Tick{3, 30.0, 3}));
}

KATA_TEST(fill_exactly_to_capacity) {
    TickBuffer<Tick> buf(3);
    buf.record(Tick{1, 10.0, 1});
    buf.record(Tick{2, 20.0, 2});
    buf.record(Tick{3, 30.0, 3});

    EXPECT_EQ(buf.size(), 3u);
    EXPECT_TRUE(buf.full());

    std::vector<Tick> expected{Tick{1, 10.0, 1}, Tick{2, 20.0, 2}, Tick{3, 30.0, 3}};
    EXPECT_EQ(buf.snapshot(), expected);
    EXPECT_EQ(buf.latest(), (Tick{3, 30.0, 3}));
}

KATA_TEST(overwrite_oldest_when_exceeding_capacity) {
    TickBuffer<Tick> buf(3);
    for (std::uint64_t s = 1; s <= 5; ++s) {
        buf.record(Tick{s, 10.0 * static_cast<double>(s), static_cast<std::uint32_t>(s)});
    }
    // Only the last 3 survive, oldest -> newest.
    EXPECT_EQ(buf.size(), 3u);
    EXPECT_TRUE(buf.full());

    std::vector<Tick> expected{Tick{3, 30.0, 3}, Tick{4, 40.0, 4}, Tick{5, 50.0, 5}};
    EXPECT_EQ(buf.snapshot(), expected);
    EXPECT_EQ(buf.latest(), (Tick{5, 50.0, 5}));
}

KATA_TEST(wrap_many_times_stays_consistent) {
    TickBuffer<Tick> buf(2);
    for (std::uint64_t s = 1; s <= 100; ++s) {
        buf.record(Tick{s, static_cast<double>(s), 1});
    }
    std::vector<Tick> expected{Tick{99, 99.0, 1}, Tick{100, 100.0, 1}};
    EXPECT_EQ(buf.snapshot(), expected);
    EXPECT_EQ(buf.latest(), (Tick{100, 100.0, 1}));
}

KATA_TEST(capacity_one_always_holds_latest) {
    TickBuffer<Tick> buf(1);
    buf.record(Tick{1, 10.0, 1});
    EXPECT_TRUE(buf.full());
    EXPECT_EQ(buf.latest(), (Tick{1, 10.0, 1}));
    buf.record(Tick{2, 20.0, 2});
    EXPECT_EQ(buf.size(), 1u);
    EXPECT_EQ(buf.latest(), (Tick{2, 20.0, 2}));
    std::vector<Tick> expected{Tick{2, 20.0, 2}};
    EXPECT_EQ(buf.snapshot(), expected);
}

// A non-trivial element type that counts live instances, to prove the buffer constructs and destroys
// each element exactly once — no leaks (overwrite-without-destroy) and no double-destroy.
namespace {
int& counted_live() {
    static int n = 0;
    return n;
}

struct Counted {
    int value{0};
    Counted() { ++counted_live(); }
    explicit Counted(int v) : value(v) { ++counted_live(); }
    Counted(const Counted& o) : value(o.value) { ++counted_live(); }
    Counted& operator=(const Counted&) = default;
    ~Counted() { --counted_live(); }
    bool operator==(const Counted&) const = default;
};
} // namespace

KATA_TEST(non_trivial_type_lifetime_is_balanced) {
    EXPECT_EQ(counted_live(), 0);
    {
        TickBuffer<Counted> buf(3);
        for (int i = 0; i < 10; ++i) {
            buf.record(Counted{i});
            // Never more live elements than the capacity — overwrites destroy before constructing.
            EXPECT_TRUE(counted_live() <= 3);
        }
        EXPECT_EQ(counted_live(), 3); // full buffer holds exactly capacity live elements
        EXPECT_EQ(buf.latest().value, 9);
    }
    EXPECT_EQ(counted_live(), 0); // destructor released every live element exactly once
}

KATA_MAIN()
