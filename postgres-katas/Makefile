# postgres-katas — convenience commands.
# DB runs in Docker (postgres:17) on localhost:5433.

PSQL = PGPASSWORD=kata psql -h localhost -p 5433 -U kata -d katas

.PHONY: up down reset shell venv check check-kata

up:                ## Start the database (loads schema + seed on first run)
	docker compose up -d --wait
	@echo "✓ Postgres ready on localhost:5433  (user=kata db=katas)"

down:              ## Stop the database (keeps the data volume)
	docker compose down

reset:             ## Destroy and recreate the database from scratch
	docker compose down -v
	docker compose up -d --wait
	@echo "✓ Database reset"

shell:             ## Open a psql shell
	$(PSQL)

venv:              ## Create the grader virtualenv and install deps
	python3 -m venv checker/.venv
	checker/.venv/bin/pip install -q -r checker/requirements.txt
	@echo "✓ grader venv ready"

check:             ## Grade every kata (blank practice = all RED)
	checker/.venv/bin/pytest checker -q

check-kata:        ## Grade one kata, e.g. make check-kata KATA=04_ranking
	checker/.venv/bin/pytest checker -q -k "$(KATA)"
