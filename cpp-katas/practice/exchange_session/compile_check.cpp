// Proves the skeleton header compiles (its bodies throw at runtime; this only checks the build).
// Not linked into any executable — the practice_compile OBJECT target just compiles it.
#include "exchange_session.hpp"

namespace {
[[maybe_unused]] void uses_api() {
    using katas::ExchangeSession;
    using katas::Order;
    ExchangeSession* s = nullptr;
    Order o{};
    (void)s;
    (void)o;
}
} // namespace
