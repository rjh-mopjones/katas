// Proves the skeleton header compiles. Not linked into any executable.
#include "top_of_book.hpp"

namespace {
[[maybe_unused]] void uses_api() {
    katas::TopOfBook tob;
    katas::Quote q{};
    tob.publish(q);
    (void)tob.read();
    (void)tob.sequence();
}
} // namespace
