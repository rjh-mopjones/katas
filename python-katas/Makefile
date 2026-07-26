# python-katas — thin wrapper over pytest/ruff (mirrors postgres-katas' venv model).
VENV := .venv
PY   := $(VENV)/bin/python
PYTEST := $(VENV)/bin/pytest

.PHONY: help venv solution practice lint test clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  %-12s %s\n", $$1, $$2}'

venv: ## Create the virtualenv and install pytest
	python3 -m venv $(VENV)
	$(VENV)/bin/pip install -q -r requirements.txt
	@echo "✓ venv ready"

solution: ## Run the reference test suite (must be GREEN)
	$(PYTEST) solution -q

practice: ## Check the practice skeletons parse & import (proves they build; NO tests ship here)
	python3 -m compileall -q practice
	python3 -c "import importlib, pkgutil, practice; \
[importlib.import_module('practice.' + m.name) for m in pkgutil.iter_modules(practice.__path__)]"
	@echo "✓ skeletons import"

lint: ## Lint solution + practice with ruff
	ruff check solution practice

test: solution ## Alias for `solution`

clean: ## Remove venv and caches
	rm -rf $(VENV) .pytest_cache .ruff_cache
	find . -name __pycache__ -type d -prune -exec rm -rf {} +
