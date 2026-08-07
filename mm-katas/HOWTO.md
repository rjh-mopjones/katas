# How to use mm-katas

## The loop (per kata, ~90 min)

1. `./kata 01` — prints **Stage 1 only** and starts a stopwatch. Read the spec, note the target minutes.
2. **Write a failing test first** (in `src/test/.../kNN_slug/`, alongside the shipped `Stage1Test`), then
   implement the `Starter` class until green. Talk out loud as if the interviewer is watching.
3. `./kata 01 check` — runs the current stage's tests. On green it prints your split, **unlocks the next
   stage's tests**, and reveals the next spec. On red it tells you and points you at the failing run.
4. Repeat through Stage 4. At the end you get total time + per-stage splits.

**Don't read ahead.** Later stages are `@Disabled` and the runner reveals them one at a time on purpose —
the whole skill is reacting to a requirement you didn't design for. The per-kata `README.md` does contain
all four stages if you truly need them, but resist.

## When you're done (or stuck)

- Compare against the worked reference in `solutions/kNN_slug/` — `NOTES.md` explains the **design pivot
  each stage forces** and why the naive Stage 1 breaks.
- `solutions/verify.sh 01` overlays the reference and proves it passes every stage (also a good check
  that the tests are real, not stubs).
- Read `INTERVIEWER.md` in the kata package: it's the escalation script — what a strong vs weak answer
  looks like at each stage, and the follow-up the interviewer bolts on if you finish early.

## Running tests directly (without the runner)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -Dtest='com.katas.k01_orderbook.Stage1Test' test     # one stage
mvn -Dtest='com.katas.k01_orderbook.*' test              # all enabled stages of one kata
```

To run a stage that's still `@Disabled` without the runner, either delete its `@Disabled` line or pass
`-Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'`.

## Practising like the real thing

Set a hard 90-minute timer for the whole kata. Give yourself the stage target minutes as soft splits.
If you blow the Stage 1 budget, that's the signal — usually you over-built Stage 1 for requirements that
weren't asked yet. The reference `NOTES.md` shows how small each later pivot is when Stage 1 is *just*
Stage 1.
