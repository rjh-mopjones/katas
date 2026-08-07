#!/usr/bin/env bash
# Prove a kata's worked reference passes EVERY stage — i.e. the tests are real (RED on the starter,
# GREEN on the reference). Overlays solutions/kNN_*/*.java onto the starter package, runs all that
# kata's stage tests with @Disabled deactivated, then restores the starter.
#
# Usage: solutions/verify.sh 01        # or: for n in 01 02 03; do solutions/verify.sh $n; done
set -euo pipefail
cd "$(dirname "$0")/.." # repo root (mm-katas)

NN="${1:?usage: solutions/verify.sh NN   (e.g. 01)}"
kata=$(basename "$(ls -d "src/main/java/com/katas/k${NN}_"* 2>/dev/null | head -1)")
[ -n "${kata:-}" ] || {
	echo "no kata k${NN}_* found"
	exit 1
}
pkg="com/katas/${kata}"
soldir="solutions/${kata}"

backup="$(mktemp -d)"
overlaid=()
for f in "$soldir"/*.java; do
	base="$(basename "$f")"
	cp "src/main/java/${pkg}/${base}" "$backup/${base}"
	cp "$f" "src/main/java/${pkg}/${base}"
	overlaid+=("$base")
done
restore() {
	for base in "${overlaid[@]}"; do cp "$backup/${base}" "src/main/java/${pkg}/${base}"; done
	rm -rf "$backup"
}
trap restore EXIT

# Every stage test for this kata, fully-qualified, comma-joined. Deactivate @Disabled so all run.
tests="$(ls "src/test/java/${pkg}"/Stage*Test.java | sed 's#.*/##; s#\.java$##; s#^#com.katas.'"${kata}"'.#' | paste -sd, -)"
echo "→ verifying ${kata}: ${tests}"
mvn -q -Dtest="${tests}" -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition' test
echo "✓ ${kata}: all stages green against the reference"
