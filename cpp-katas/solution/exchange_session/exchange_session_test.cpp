#include "harness.hpp"

#include "exchange_session.hpp"

#include <stdexcept>
#include <utility>
#include <vector>

using katas::ExchangeSession;
using katas::Order;

KATA_TEST(construct_opens_and_counts) {
    EXPECT_EQ(ExchangeSession::live_count(), 0);
    {
        ExchangeSession s("LSE");
        EXPECT_TRUE(s.is_open());
        EXPECT_EQ(s.venue(), std::string("LSE"));
        EXPECT_EQ(ExchangeSession::live_count(), 1);
    }
    EXPECT_EQ(ExchangeSession::live_count(), 0); // released exactly once on scope exit
}

KATA_TEST(send_counts_while_open) {
    ExchangeSession s("XETRA");
    s.send(Order{1, 100.5, 10});
    s.send(Order{2, 100.6, 5});
    EXPECT_EQ(s.sent(), 2u);
}

KATA_TEST(move_construct_transfers_ownership) {
    EXPECT_EQ(ExchangeSession::live_count(), 0);
    ExchangeSession a("NYSE");
    a.send(Order{1, 10.0, 1});
    EXPECT_EQ(ExchangeSession::live_count(), 1);

    ExchangeSession b(std::move(a));
    // Moving does not open a second connection...
    EXPECT_EQ(ExchangeSession::live_count(), 1);
    // ...the target owns it, the source is inert.
    EXPECT_TRUE(b.is_open());
    EXPECT_FALSE(a.is_open());
    EXPECT_EQ(b.venue(), std::string("NYSE"));
    EXPECT_EQ(b.sent(), 1u); // transferred state
}

KATA_TEST(moved_from_session_is_closed) {
    ExchangeSession a("CME");
    ExchangeSession b(std::move(a));
    EXPECT_THROWS(a.send(Order{1, 1.0, 1}), std::logic_error);
    (void)b;
}

KATA_TEST(no_double_close_after_move) {
    EXPECT_EQ(ExchangeSession::live_count(), 0);
    {
        ExchangeSession a("ICE");
        ExchangeSession b(std::move(a));
        EXPECT_EQ(ExchangeSession::live_count(), 1);
    } // both a and b destruct here; only the real owner decrements
    EXPECT_EQ(ExchangeSession::live_count(), 0);
}

KATA_TEST(move_assign_releases_previous_connection) {
    EXPECT_EQ(ExchangeSession::live_count(), 0);
    ExchangeSession a("A");
    ExchangeSession b("B");
    EXPECT_EQ(ExchangeSession::live_count(), 2);

    b = std::move(a); // b's old connection to "B" must be released here
    EXPECT_EQ(ExchangeSession::live_count(), 1);
    EXPECT_EQ(b.venue(), std::string("A"));
    EXPECT_FALSE(a.is_open());
}

KATA_TEST(sessions_move_into_a_registry) {
    EXPECT_EQ(ExchangeSession::live_count(), 0);
    std::vector<ExchangeSession> registry;
    registry.reserve(4);
    for (int i = 0; i < 4; ++i) registry.emplace_back("V" + std::to_string(i));
    EXPECT_EQ(ExchangeSession::live_count(), 4);
    registry.clear();
    EXPECT_EQ(ExchangeSession::live_count(), 0);
}

KATA_MAIN()
