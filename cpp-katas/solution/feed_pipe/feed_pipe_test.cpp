#include "harness.hpp"

#include "feed_pipe.hpp"

#include <optional>
#include <stdexcept>

using katas::FeedEvent;
using katas::FeedPipe;

KATA_TEST(capacity_zero_throws) {
    EXPECT_THROWS(FeedPipe<int>(0), std::invalid_argument);
}

KATA_TEST(pop_empty_returns_nullopt) {
    FeedPipe<int> p(4);
    EXPECT_FALSE(p.try_pop().has_value());
}

KATA_TEST(push_then_pop_fifo) {
    FeedPipe<int> p(4);
    EXPECT_TRUE(p.try_push(1));
    EXPECT_TRUE(p.try_push(2));
    EXPECT_TRUE(p.try_push(3));
    EXPECT_EQ(p.try_pop().value(), 1);
    EXPECT_EQ(p.try_pop().value(), 2);
    EXPECT_EQ(p.try_pop().value(), 3);
    EXPECT_FALSE(p.try_pop().has_value());
}

KATA_TEST(push_fails_when_full) {
    FeedPipe<int> p(3); // holds 3 usable elements
    EXPECT_TRUE(p.try_push(10));
    EXPECT_TRUE(p.try_push(20));
    EXPECT_TRUE(p.try_push(30));
    EXPECT_FALSE(p.try_push(40)); // full
    EXPECT_EQ(p.try_pop().value(), 10);
    EXPECT_TRUE(p.try_push(40)); // space again
}

KATA_TEST(wraps_around_repeatedly) {
    FeedPipe<int> p(2);
    for (int round = 0; round < 100; ++round) {
        EXPECT_TRUE(p.try_push(round));
        EXPECT_TRUE(p.try_push(round + 1000));
        EXPECT_FALSE(p.try_push(-1)); // full
        EXPECT_EQ(p.try_pop().value(), round);
        EXPECT_EQ(p.try_pop().value(), round + 1000);
        EXPECT_FALSE(p.try_pop().has_value()); // empty
    }
}

KATA_TEST(carries_feed_events) {
    FeedPipe<FeedEvent> p(8);
    FeedEvent e{42, 7, 100.25};
    EXPECT_TRUE(p.try_push(e));
    EXPECT_EQ(p.try_pop().value(), e);
}

KATA_TEST(move_only_payload_survives) {
    FeedPipe<std::optional<int>> p(4); // stand-in for a move-friendly payload
    EXPECT_TRUE(p.try_push(std::optional<int>{5}));
    auto out = p.try_pop();
    EXPECT_TRUE(out.has_value());
    EXPECT_EQ(out.value().value(), 5);
}

KATA_MAIN()
