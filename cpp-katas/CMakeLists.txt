# cpp-katas — root build.
#
# Two mirrored trees, built independently (mirrors the C# .sln / Go two-module split):
#   solution/  — full reference implementations + the hand-rolled test suite (must stay GREEN)
#   practice/  — blank skeletons only; a compile-check target proves they build (no tests)
#
# The `katas_common` INTERFACE target is this project's `Directory.Build.props`: the C++ standard,
# warning flags, and the optional sanitizer are declared once here and linked by every target.
cmake_minimum_required(VERSION 3.20)
project(cpp_katas CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "" FORCE)
endif()

# Shared compile settings inherited by every target.
add_library(katas_common INTERFACE)
target_compile_features(katas_common INTERFACE cxx_std_20)
target_compile_options(katas_common INTERFACE -Wall -Wextra)

# Optional sanitizer — the ThreadSanitizer build is the `go test -race` analogue.
#   cmake -B build-tsan -DKATAS_SANITIZE=thread
set(KATAS_SANITIZE "" CACHE STRING "Sanitizer to enable: thread | address | undefined")
if(KATAS_SANITIZE)
  message(STATUS "Sanitizer enabled: ${KATAS_SANITIZE}")
  target_compile_options(katas_common INTERFACE -fsanitize=${KATAS_SANITIZE} -g -fno-omit-frame-pointer)
  target_link_options(katas_common INTERFACE -fsanitize=${KATAS_SANITIZE})
endif()

enable_testing()
add_subdirectory(solution)
add_subdirectory(practice)
