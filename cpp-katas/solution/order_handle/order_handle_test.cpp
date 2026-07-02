#include "harness.hpp"

#include "order_handle.hpp"

#include <stdexcept>
#include <utility>

using katas::Order;
using katas::OrderHandle;
using katas::OrderPool;

KATA_TEST(null_handle_owns_nothing) {
    OrderHandle h;
    EXPECT_FALSE(static_cast<bool>(h));
    EXPECT_EQ(h.use_count(), 0L);
    EXPECT_TRUE(h.get() == nullptr);
}

KATA_TEST(acquire_checks_out_one_slot) {
    OrderPool pool(4);
    EXPECT_EQ(pool.live(), 0u);
    EXPECT_EQ(pool.capacity(), 4u);

    OrderHandle h = pool.acquire(42, 100.5, 10);
    EXPECT_TRUE(static_cast<bool>(h));
    EXPECT_EQ(pool.live(), 1u);
    EXPECT_EQ(h.use_count(), 1L);
    EXPECT_EQ(h->id, 42u);
    EXPECT_EQ(h->price, 100.5);
    EXPECT_EQ(h->qty, 10u);
    EXPECT_EQ((*h).id, 42u);
}

KATA_TEST(copy_shares_ownership_of_same_order) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(7, 10.0, 1);
    OrderHandle b = a; // copy: a second owner of the SAME order

    EXPECT_EQ(a.use_count(), 2L);
    EXPECT_EQ(b.use_count(), 2L);
    EXPECT_TRUE(a.get() == b.get()); // same underlying Order
    EXPECT_EQ(pool.live(), 1u);      // still one slot — shared, not a second acquire
}

KATA_TEST(dropping_one_copy_leaves_the_order_live) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(7, 10.0, 1);
    {
        OrderHandle b = a;
        EXPECT_EQ(a.use_count(), 2L);
    } // b drops here
    EXPECT_EQ(a.use_count(), 1L);
    EXPECT_EQ(pool.live(), 1u); // still checked out — a holds it
}

KATA_TEST(dropping_the_last_handle_reclaims_the_slot) {
    OrderPool pool(4);
    {
        OrderHandle a = pool.acquire(7, 10.0, 1);
        EXPECT_EQ(pool.live(), 1u);
    } // last handle drops
    EXPECT_EQ(pool.live(), 0u); // reclaimed exactly once
}

KATA_TEST(reset_drops_the_reference) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(7, 10.0, 1);
    EXPECT_EQ(pool.live(), 1u);
    a.reset();
    EXPECT_FALSE(static_cast<bool>(a));
    EXPECT_EQ(a.use_count(), 0L);
    EXPECT_EQ(pool.live(), 0u);
}

KATA_TEST(move_transfers_ownership_and_nulls_source) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(9, 55.0, 3);
    EXPECT_EQ(a.use_count(), 1L);

    OrderHandle b = std::move(a);
    EXPECT_TRUE(static_cast<bool>(b));
    EXPECT_EQ(b.use_count(), 1L);   // count unchanged by the move
    EXPECT_FALSE(static_cast<bool>(a)); // source is now null
    EXPECT_EQ(a.use_count(), 0L);
    EXPECT_EQ(pool.live(), 1u);     // still exactly one slot live
    EXPECT_EQ(b->id, 9u);
}

KATA_TEST(move_assign_releases_previous_order) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(1, 1.0, 1);
    OrderHandle b = pool.acquire(2, 2.0, 2);
    EXPECT_EQ(pool.live(), 2u);

    b = std::move(a); // b's old order must be reclaimed here
    EXPECT_EQ(pool.live(), 1u);
    EXPECT_EQ(b->id, 1u);
    EXPECT_FALSE(static_cast<bool>(a));
}

KATA_TEST(self_copy_assignment_is_safe) {
    OrderPool pool(4);
    OrderHandle h = pool.acquire(11, 12.0, 4);

    OrderHandle& alias = h;
    h = alias; // self-assignment must not free the order it keeps pointing at

    EXPECT_TRUE(static_cast<bool>(h));
    EXPECT_EQ(h.use_count(), 1L);
    EXPECT_EQ(h->id, 11u);
    EXPECT_EQ(pool.live(), 1u);
}

KATA_TEST(copy_assign_between_handles_to_same_order_is_safe) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(11, 12.0, 4);
    OrderHandle b = a; // both share the one order, count 2
    EXPECT_EQ(a.use_count(), 2L);

    b = a; // assigning between two handles to the SAME order must stay at count 2, not free it
    EXPECT_EQ(a.use_count(), 2L);
    EXPECT_TRUE(a.get() == b.get());
    EXPECT_EQ(pool.live(), 1u);
}

KATA_TEST(copy_assign_releases_old_and_shares_new) {
    OrderPool pool(4);
    OrderHandle a = pool.acquire(1, 1.0, 1);
    OrderHandle b = pool.acquire(2, 2.0, 2);
    EXPECT_EQ(pool.live(), 2u);

    b = a; // b's old order (id 2) reclaimed; b now shares a's (id 1)
    EXPECT_EQ(pool.live(), 1u);
    EXPECT_EQ(a.use_count(), 2L);
    EXPECT_EQ(b.use_count(), 2L);
    EXPECT_EQ(b->id, 1u);
    EXPECT_TRUE(a.get() == b.get());
}

KATA_TEST(pool_exhaustion_throws) {
    OrderPool pool(2);
    OrderHandle a = pool.acquire(1, 1.0, 1);
    OrderHandle b = pool.acquire(2, 2.0, 2);
    EXPECT_EQ(pool.live(), 2u);
    EXPECT_THROWS(pool.acquire(3, 3.0, 3), std::runtime_error);
}

KATA_TEST(reclaimed_slot_is_reused_by_a_later_acquire) {
    OrderPool pool(1);
    {
        OrderHandle a = pool.acquire(1, 1.0, 1);
        EXPECT_EQ(pool.live(), 1u);
    } // slot reclaimed
    EXPECT_EQ(pool.live(), 0u);
    OrderHandle b = pool.acquire(2, 2.0, 2); // reuses the same slot
    EXPECT_EQ(pool.live(), 1u);
    EXPECT_EQ(b->id, 2u);
    EXPECT_EQ(b.use_count(), 1L);
}

KATA_TEST(three_subsystems_share_one_order_reclaimed_once) {
    // The whole point: price level + id-index + risk engine each hold a handle to the one order.
    OrderPool pool(4);
    OrderHandle price_level = pool.acquire(100, 250.0, 20);
    OrderHandle id_index = price_level;
    OrderHandle risk = price_level;

    EXPECT_EQ(price_level.use_count(), 3L);
    EXPECT_EQ(pool.live(), 1u);
    EXPECT_TRUE(price_level.get() == id_index.get());
    EXPECT_TRUE(price_level.get() == risk.get());

    id_index.reset();
    EXPECT_EQ(price_level.use_count(), 2L);
    EXPECT_EQ(pool.live(), 1u);

    risk.reset();
    EXPECT_EQ(price_level.use_count(), 1L);
    EXPECT_EQ(pool.live(), 1u);

    price_level.reset(); // last owner lets go
    EXPECT_EQ(pool.live(), 0u); // returned to the pool exactly once
}

KATA_MAIN()
