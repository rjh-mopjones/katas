#ifndef KATAS_ORDER_HANDLE_HPP
#define KATAS_ORDER_HANDLE_HPP

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <vector>

namespace katas {

// Provided verbatim — do not modify. Carries its own intrusive refcount, managed ONLY by OrderHandle.
// A ref_count of 0 means the slot is free.
struct Order {
    std::uint64_t id{};
    double price{};
    std::uint32_t qty{};
    int ref_count{0};
};

class OrderPool;

// You implement this. Design the internals yourself; keep the public signatures below. Every member
// here is `noexcept`, so the skeleton bodies are stubs (do nothing / return a dummy) rather than
// `throw` — replace them with your real logic. The private friend ctor (used by OrderPool::acquire to
// adopt a checked-out Order) is declared for you; implement it too.
class OrderHandle {
public:
    OrderHandle() noexcept {}
    ~OrderHandle() {}

    OrderHandle(const OrderHandle&) noexcept {}
    OrderHandle& operator=(const OrderHandle&) noexcept { return *this; }

    OrderHandle(OrderHandle&&) noexcept {}
    OrderHandle& operator=(OrderHandle&&) noexcept { return *this; }

    Order* get() const noexcept { return nullptr; }
    Order& operator*() const noexcept { static Order dummy; return dummy; }
    Order* operator->() const noexcept { return nullptr; }
    explicit operator bool() const noexcept { return false; }
    long use_count() const noexcept { return 0; }
    void reset() noexcept {}

private:
    friend class OrderPool;
    explicit OrderHandle(Order*) noexcept {}
};

// Provided verbatim — a working fixture. Do not modify. A tiny fixed pool of Orders; the backing
// storage lives for the pool's lifetime and "reclaim" means a slot's ref_count returned to 0.
class OrderPool {
public:
    explicit OrderPool(std::size_t capacity) : slots_(capacity) {}

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
