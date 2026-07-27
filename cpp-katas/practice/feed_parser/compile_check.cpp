// Proves the skeleton header compiles (its bodies throw at runtime; this only checks the build).
// Not linked into any executable — the practice_compile OBJECT target just compiles it.
#include "feed_parser.hpp"

#include <sstream>

// Reference the public API so the skeleton's signatures are type-checked.
[[maybe_unused]] static void use_api() {
    std::istringstream in("SYM|1.0|2.0|10\n");
    katas::ParseResult result = katas::parse_all(in);
    (void)result;

    std::istringstream in2("SYM|1.0|2.0|10\n");
    katas::parse_feed(
        in2,
        [](std::size_t, const katas::Quote&) {},
        [](const katas::ParseError&) {});
}
