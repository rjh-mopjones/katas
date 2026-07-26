# rust-katas — thin wrapper over cargo (mirrors go-katas/cpp-katas Makefiles).
.PHONY: help solution practice clippy test miri clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  %-12s %s\n", $$1, $$2}'

solution: ## Run the reference test suite (must be GREEN)
	cargo test -p solution

practice: ## Compile the practice skeletons (proves they build; NO tests ship here)
	cargo build -p practice

clippy: ## Lint the reference crate (warnings are errors)
	cargo clippy -p solution -- -D warnings

test: ## Run the whole workspace test suite
	cargo test --workspace

miri: ## Verify the unsafe `spscring` capstone under Miri (needs: rustup component add miri)
	cargo +nightly miri test -p solution spscring

clean: ## Remove build artifacts
	cargo clean
