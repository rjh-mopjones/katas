# cpp-katas — thin wrapper over CMake + CTest (mirrors go-katas/Makefile).
BUILD      ?= build
TSAN_BUILD ?= build-tsan

.PHONY: help configure solution solution-tsan practice test clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  %-16s %s\n", $$1, $$2}'

configure: ## Configure the CMake build (no sanitizer)
	cmake -B $(BUILD) -S .

solution: configure ## Build everything + run the reference suite (must be GREEN)
	cmake --build $(BUILD)
	ctest --test-dir $(BUILD) --output-on-failure

solution-tsan: ## Rebuild under ThreadSanitizer and run — the `go test -race` analogue
	cmake -B $(TSAN_BUILD) -S . -DKATAS_SANITIZE=thread
	cmake --build $(TSAN_BUILD)
	ctest --test-dir $(TSAN_BUILD) --output-on-failure

practice: configure ## Compile the practice skeletons (proves they build; NO tests ship here)
	cmake --build $(BUILD) --target practice_compile

test: solution ## Alias for `solution`

clean: ## Remove the build directories
	rm -rf $(BUILD) $(TSAN_BUILD)
