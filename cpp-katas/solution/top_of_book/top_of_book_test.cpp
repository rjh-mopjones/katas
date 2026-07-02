#include "harness.hpp"

#include "top_of_book.hpp"

using katas::Quote;
using katas::TopOfBook;

KATA_TEST(initial_read_is_empty) {
    TopOfBook tob;
    Quote q = tob.read();
    EXPECT_EQ(q, Quote{});
    EXPECT_EQ(tob.sequence(), 0u);
}

KATA_TEST(publish_then_read_round_trips) {
    TopOfBook tob;
    Quote q{1.95, 100, 2.05, 80, 1};
    tob.publish(q);
    EXPECT_EQ(tob.read(), q);
}

KATA_TEST(publish_overwrites) {
    TopOfBook tob;
    tob.publish(Quote{1.95, 100, 2.05, 80, 1});
    Quote q2{1.96, 120, 2.04, 90, 2};
    tob.publish(q2);
    EXPECT_EQ(tob.read(), q2);
}

KATA_TEST(sequence_advances_by_two_per_publish) {
    TopOfBook tob;
    EXPECT_EQ(tob.sequence(), 0u);
    tob.publish(Quote{1.0, 1, 2.0, 1, 1});
    EXPECT_EQ(tob.sequence(), 2u); // even -> odd -> even
    tob.publish(Quote{1.0, 1, 2.0, 1, 2});
    EXPECT_EQ(tob.sequence(), 4u);
}

KATA_TEST(read_is_repeatable_when_idle) {
    TopOfBook tob;
    Quote q{3.3, 7, 3.4, 9, 42};
    tob.publish(q);
    for (int i = 0; i < 1000; ++i) EXPECT_EQ(tob.read(), q);
}

KATA_MAIN()
