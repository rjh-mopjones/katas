// Proves the skeleton header compiles (its bodies throw at runtime; this only checks the build).
// Not linked into any executable — the practice_compile OBJECT target just compiles it.
#include "order_handle.hpp"

namespace {
[[maybe_unused]] void uses_api() {
    using katas::Order;
    using katas::OrderHandle;
    using katas::OrderPool;
    OrderHandle* h = nullptr;
    OrderPool* p = nullptr;
    Order o{};
    (void)h;
    (void)p;
    (void)o;
}
} // namespace
