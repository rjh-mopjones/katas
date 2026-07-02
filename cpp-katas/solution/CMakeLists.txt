# solution/ — the reference implementations and the hand-rolled test suite (the answer key).
#
# Reference code is held to a higher bar than practice: warnings are errors here (as the C# solution
# project sets TreatWarningsAsErrors=true) so the answer key stays clean.
find_package(Threads REQUIRED)

add_library(katas_solution_flags INTERFACE)
target_link_libraries(katas_solution_flags INTERFACE katas_common)
target_compile_options(katas_solution_flags INTERFACE -Werror)

# The header-only test harness.
add_library(katas_harness INTERFACE)
target_include_directories(katas_harness INTERFACE ${CMAKE_CURRENT_SOURCE_DIR}/common)

# kata_test(<name> <sources...>) builds one test executable for a kata and registers it with CTest
# under <name>, so `ctest -R <name>` runs a single kata (the `go test ./<kata>/` analogue).
function(kata_test name)
  add_executable(${name}_test ${ARGN})
  target_include_directories(${name}_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/${name})
  target_link_libraries(${name}_test PRIVATE katas_solution_flags katas_harness Threads::Threads)
  add_test(NAME ${name} COMMAND ${name}_test)
endfunction()

kata_test(exchange_session exchange_session/exchange_session_test.cpp)
kata_test(tick_buffer      tick_buffer/tick_buffer_test.cpp)
kata_test(order_handle     order_handle/order_handle_test.cpp)
kata_test(top_of_book      top_of_book/top_of_book_test.cpp
                           top_of_book/top_of_book_stress_test.cpp)
kata_test(feed_pipe        feed_pipe/feed_pipe_test.cpp
                           feed_pipe/feed_pipe_stress_test.cpp)
