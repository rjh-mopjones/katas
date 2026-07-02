// order_handle.hpp — an intrusive reference-counted handle to a pooled Order.
//
// # The component
//
// A matching engine keeps live orders in a fixed pool and hands the *same* Order out to several
// subsystems at once: it rests in a price level's queue, it is indexed by client order id, and the
// risk engine holds it to track exposure. Each of those is a separate owner. The Order must stay
// alive while *any* owner still references it, and return to the pool the instant the *last* owner
// lets go. An OrderHandle is that owning reference — an intrusive_ptr by hand — and OrderPool is the
// slab it recycles slots from.
//
// # Why an intrusive count
//
// The reference count lives *inside* the Order (`ref_count`), not in a separate control block. That
// is the "intrusive" model: one cache line holds the payload and its count, and a raw `Order*` can be
// promoted back to an owning handle without a lookup — exactly what a hot matching loop wants. The
// price is discipline: only OrderHandle may touch `ref_count`, and the rule of five must keep the
// count exactly balanced. Contrast std::shared_ptr, whose count sits in a *separate* heap-allocated
// control block reached through the pointer — external refcounting, one extra indirection and
// allocation per shared object.
//
// # The trap
//
// This is the rule of five on a *shared* resource, so every special member matters:
//
//   - Copy bumps the count (a new owner appears); copy-assignment must be correct under
//     self-assignment (`h = h;`) and under assigning two handles that already share the SAME order —
//     bump the new before releasing the old, or guard `this == &other`, or you free the very Order you
//     are about to point at (use-after-free).
//   - Move steals the pointer and nulls the source, transferring ownership without touching the count.
//   - Destructor / reset decrements; the Order is reclaimed on the single 0-transition. Reclaim must
//     happen *exactly once* — a missed decrement leaks a pool slot, a double decrement hands the slot
//     to a future acquire() while an owner still holds it.
//
// # The money angle
//
// If an Order is recycled while it still rests in the book — because a handle double-freed it, or a
// missed increment let the count hit zero early — a future acquire() overwrites that slot with a new
// client's order. The next match against the stale price-level pointer fills a *recycled* slot:
// phantom fills, orders matched at the wrong price, exposure attributed to the wrong account. A
// leaked slot is milder but real: the pool exhausts and acquire() starts throwing mid-session.
//
// # Concurrency caveat
//
// `ref_count` is a plain `int` — this kata is single-threaded (one matching thread owns the book). A
// pool shared across threads would need `std::atomic<int>` for the count (with acquire/release on the
// decrement-to-zero) and a lock-free free-list instead of the linear slot scan below.
//
// # Alternatives
//
//   - std::shared_ptr<Order> with a custom deleter that returns the slot to the pool instead of
//     calling delete — external count, but a control-block allocation per order.
//   - boost::intrusive_ptr<Order> + intrusive_ptr_add_ref/release free functions — the library form of
//     exactly this pattern. This kata is that written by hand so the count mechanics are explicit.
#ifndef KATAS_ORDER_HANDLE_HPP
#define KATAS_ORDER_HANDLE_HPP

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <vector>

namespace katas {

// The pooled resource. Carries its own intrusive refcount, managed ONLY by OrderHandle. A ref_count
// of 0 means "back in the pool" — the slot is free for a future acquire() to reuse.
struct Order {
    std::uint64_t id{};
    double price{};
    std::uint32_t qty{};
    int ref_count{0}; // intrusive; 0 means the slot is free
};

class OrderPool;

// The intrusive handle — the SUT. Holds a single raw Order*; the pool discovers free slots by
// scanning for ref_count == 0, so no back-pointer to the pool is needed.
class OrderHandle {
public:
    // Null handle: owns nothing.
    OrderHandle() noexcept : order_(nullptr) {}

    // Dropping an owner: release our reference, reclaiming the Order on the last drop.
    ~OrderHandle() { reset(); }

    // Copy: a new owner appears, so bump the shared count.
    OrderHandle(const OrderHandle& other) noexcept : order_(other.order_) {
        if (order_ != nullptr) ++order_->ref_count;
    }

    // Copy-assign. Bump the source's count *before* releasing our own: if `this` and `other` share the
    // same Order (or are literally the same handle), releasing first could reclaim the slot we are
    // about to keep pointing at. Incrementing first makes self-assignment and same-order assignment
    // both safe without a separate `this == &other` guard.
    OrderHandle& operator=(const OrderHandle& other) noexcept {
        Order* incoming = other.order_;
        if (incoming != nullptr) ++incoming->ref_count; // claim new first
        release();                                      // then drop old (safe even if same slot)
        order_ = incoming;
        return *this;
    }

    // Move: steal the pointer and null the source. Ownership transfers; the count is unchanged.
    OrderHandle(OrderHandle&& other) noexcept : order_(other.order_) { other.order_ = nullptr; }

    OrderHandle& operator=(OrderHandle&& other) noexcept {
        if (this != &other) {
            release();                // drop what we currently own
            order_ = other.order_;    // steal
            other.order_ = nullptr;   // leave the source null
        }
        return *this;
    }

    Order* get() const noexcept { return order_; }
    Order& operator*() const noexcept { return *order_; }
    Order* operator->() const noexcept { return order_; }
    explicit operator bool() const noexcept { return order_ != nullptr; }

    // The managed Order's intrusive count, or 0 if this handle is null.
    long use_count() const noexcept { return order_ != nullptr ? order_->ref_count : 0; }

    // Drop this handle's reference and become null. Reclaims the Order (ref_count hits 0) if last.
    void reset() noexcept {
        release();
        order_ = nullptr;
    }

private:
    // OrderPool adopts a freshly-checked-out Order (ref_count already 1) into a handle.
    friend class OrderPool;
    explicit OrderHandle(Order* adopted) noexcept : order_(adopted) {}

    // Decrement the shared count; when it reaches 0 the slot is free again. Does NOT null order_ —
    // callers repoint or null it. Idempotent guards keep null handles harmless.
    void release() noexcept {
        if (order_ != nullptr && --order_->ref_count == 0) {
            // Reclaimed: the slot is now free. A future acquire() will find it by scanning. We leave
            // the fields as-is; acquire() overwrites them on reuse.
        }
    }

    Order* order_;
};

// A tiny fixed pool of Orders. The backing storage lives for the pool's lifetime; "reclaim" means a
// slot's ref_count returned to 0 and is reusable — never a delete.
class OrderPool {
public:
    explicit OrderPool(std::size_t capacity) : slots_(capacity) {}

    // Check out a free slot: set its fields, set ref_count = 1, and adopt it into a handle. Throws if
    // every slot is currently live.
    OrderHandle acquire(std::uint64_t id, double price, std::uint32_t qty) {
        for (Order& slot : slots_) {
            if (slot.ref_count == 0) {
                slot.id = id;
                slot.price = price;
                slot.qty = qty;
                slot.ref_count = 1;
                return OrderHandle(&slot);
            }
        }
        throw std::runtime_error("OrderPool exhausted");
    }

    // Orders currently checked out (ref_count > 0).
    std::size_t live() const noexcept {
        std::size_t n = 0;
        for (const Order& slot : slots_) {
            if (slot.ref_count > 0) ++n;
        }
        return n;
    }

    std::size_t capacity() const noexcept { return slots_.size(); }

private:
    std::vector<Order> slots_;
};

} // namespace katas

#endif // KATAS_ORDER_HANDLE_HPP
