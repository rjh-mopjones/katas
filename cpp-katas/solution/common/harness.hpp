// harness.hpp — a tiny, dependency-free test harness.
//
// The repo rule for every language module is "standard library only": Go uses the stdlib `testing`
// package, this C++ module uses this ~100-line header instead of GoogleTest/Catch2. It gives the
// three things a framework provides — auto-registration, assertions, and a filtering runner — and
// nothing else.
//
// Usage (one executable per kata):
//
//   #include "harness.hpp"
//   #include "my_kata.hpp"
//
//   KATA_TEST(does_the_thing) {
//       EXPECT_EQ(2 + 2, 4);
//       ASSERT_TRUE(ptr != nullptr);   // ASSERT_* aborts this test; EXPECT_* records and continues
//       EXPECT_THROWS(v.at(99), std::out_of_range);
//   }
//
//   KATA_MAIN()   // expands to main(); runs every KATA_TEST, optional argv[1] substring filter
//
// A concurrency kata puts its extra stress cases in a second .cpp with no KATA_MAIN(): every TU that
// includes this header registers into one shared registry, so a single main() in the *_test.cpp runs
// them all. The gated-start helper `kata::StartGate` is the C++ analogue of Go's `close(start)`.
#ifndef KATAS_HARNESS_HPP
#define KATAS_HARNESS_HPP

#include <atomic>
#include <cstdio>
#include <functional>
#include <latch>
#include <ostream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

namespace kata {

struct TestCase {
    std::string name;
    std::function<void()> fn;
};

// A function-local static registry sidesteps static-initialisation-order fiasco: every self-
// registering TU touches the same vector through this accessor, whatever order they initialise in.
inline std::vector<TestCase>& registry() {
    static std::vector<TestCase> tests;
    return tests;
}

// Failure count for the test currently executing; reset by the runner before each test.
inline int& current_failures() {
    static int failures = 0;
    return failures;
}

struct Registrar {
    Registrar(std::string name, std::function<void()> fn) {
        registry().push_back({std::move(name), std::move(fn)});
    }
};

// Thrown by ASSERT_* to abort the current test only (the runner catches it).
struct AssertionFailure {};

// Stream a value for a failure message, or "<?>" if it has no operator<<.
template <typename T>
std::string show(const T& v) {
    if constexpr (requires(std::ostream& os, const T& x) { os << x; }) {
        std::ostringstream oss;
        oss << v;
        return oss.str();
    } else {
        return "<?>";
    }
}

inline void report(const char* file, int line, const std::string& msg) {
    std::fprintf(stderr, "    %s:%d: %s\n", file, line, msg.c_str());
    ++current_failures();
}

// StartGate releases N worker threads simultaneously to maximise contention — the analogue of the
// Go concurrency-test idiom `start := make(chan struct{}); ...; close(start)`. Workers call wait();
// the test thread calls open() once all workers are spawned.
class StartGate {
public:
    explicit StartGate() : latch_(1) {}
    void wait() { latch_.wait(); }
    void open() { latch_.count_down(); }

private:
    std::latch latch_;
};

} // namespace kata

#define KATA_TEST(test_name)                                                          \
    static void test_name();                                                          \
    static ::kata::Registrar kata_reg_##test_name{#test_name, &test_name};            \
    static void test_name()

#define EXPECT_TRUE(cond)                                                             \
    do {                                                                             \
        if (!(cond)) ::kata::report(__FILE__, __LINE__, "expected true: " #cond);   \
    } while (0)

#define EXPECT_FALSE(cond)                                                           \
    do {                                                                             \
        if (cond) ::kata::report(__FILE__, __LINE__, "expected false: " #cond);     \
    } while (0)

// Operands are bound BY VALUE, not by reference: an expression like `opt.value()` returns a
// reference into a temporary that dies at the end of the statement, so a stored reference would
// dangle (clang's -Wdangling-gsl). Copying the operands sidesteps that; the trade-off is that the
// compared types must be copyable, which is fine for test assertions.
#define EXPECT_EQ(a, b)                                                              \
    do {                                                                             \
        auto ka_a = (a);                                                            \
        auto ka_b = (b);                                                            \
        if (!(ka_a == ka_b))                                                        \
            ::kata::report(__FILE__, __LINE__,                                      \
                           "expected " #a " == " #b ", got " + ::kata::show(ka_a) + \
                               " vs " + ::kata::show(ka_b));                        \
    } while (0)

#define EXPECT_NE(a, b)                                                              \
    do {                                                                             \
        auto ka_a = (a);                                                            \
        auto ka_b = (b);                                                            \
        if (ka_a == ka_b)                                                           \
            ::kata::report(__FILE__, __LINE__,                                      \
                           "expected " #a " != " #b ", both " + ::kata::show(ka_a));\
    } while (0)

#define ASSERT_TRUE(cond)                                                            \
    do {                                                                             \
        if (!(cond)) {                                                              \
            ::kata::report(__FILE__, __LINE__, "assert true: " #cond);             \
            throw ::kata::AssertionFailure{};                                       \
        }                                                                            \
    } while (0)

#define ASSERT_EQ(a, b)                                                              \
    do {                                                                             \
        auto ka_a = (a);                                                            \
        auto ka_b = (b);                                                            \
        if (!(ka_a == ka_b)) {                                                      \
            ::kata::report(__FILE__, __LINE__,                                      \
                           "assert " #a " == " #b ", got " + ::kata::show(ka_a) +   \
                               " vs " + ::kata::show(ka_b));                        \
            throw ::kata::AssertionFailure{};                                       \
        }                                                                            \
    } while (0)

#define EXPECT_THROWS(expr, ExcType)                                                 \
    do {                                                                             \
        bool ka_threw = false;                                                      \
        try {                                                                        \
            (void)(expr);                                                           \
        } catch (const ExcType&) {                                                  \
            ka_threw = true;                                                        \
        } catch (...) {                                                             \
            ::kata::report(__FILE__, __LINE__, "wrong exception type from: " #expr);\
            ka_threw = true;                                                        \
        }                                                                            \
        if (!ka_threw)                                                              \
            ::kata::report(__FILE__, __LINE__,                                      \
                           "expected " #ExcType " from: " #expr);                   \
    } while (0)

#define KATA_MAIN()                                                                 \
    int main(int argc, char** argv) {                                               \
        const char* filter = argc > 1 ? argv[1] : nullptr;                          \
        int ran = 0, failed = 0;                                                    \
        for (auto& tc : ::kata::registry()) {                                       \
            if (filter && tc.name.find(filter) == std::string::npos) continue;      \
            ::kata::current_failures() = 0;                                          \
            ++ran;                                                                   \
            try {                                                                    \
                tc.fn();                                                             \
            } catch (const ::kata::AssertionFailure&) {                             \
            } catch (const std::exception& e) {                                     \
                ::kata::report(__FILE__, __LINE__,                                  \
                               std::string("uncaught std::exception: ") + e.what());\
            } catch (...) {                                                          \
                ::kata::report(__FILE__, __LINE__, "uncaught exception");           \
            }                                                                        \
            if (::kata::current_failures() > 0) {                                    \
                ++failed;                                                            \
                std::fprintf(stderr, "[FAIL] %s\n", tc.name.c_str());               \
            } else {                                                                 \
                std::fprintf(stdout, "[ ok ] %s\n", tc.name.c_str());               \
            }                                                                        \
        }                                                                            \
        std::fprintf(stdout, "\n%d/%d passed\n", ran - failed, ran);                \
        return failed == 0 ? 0 : 1;                                                  \
    }

#endif // KATAS_HARNESS_HPP
