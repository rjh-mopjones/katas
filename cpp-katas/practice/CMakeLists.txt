# practice/ — blank skeletons the learner fills in. NO tests ship here (writing them is the exercise).
#
# The one thing we DO verify is that the skeletons still compile: `practice_compile` builds a small
# translation unit per kata that includes the skeleton header and instantiates its API. This is the
# C++ analogue of `go build ./...` / `dotnet build practice`. Warnings-as-errors is intentionally OFF
# (skeletons are work-in-progress and their throw-only bodies may trip pedantic warnings).
add_library(practice_compile OBJECT
  exchange_session/compile_check.cpp
  tick_buffer/compile_check.cpp
  order_handle/compile_check.cpp
  top_of_book/compile_check.cpp
  feed_pipe/compile_check.cpp
)
target_link_libraries(practice_compile PRIVATE katas_common)
target_include_directories(practice_compile PRIVATE
  exchange_session
  tick_buffer
  order_handle
  top_of_book
  feed_pipe
)
