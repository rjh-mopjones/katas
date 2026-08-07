# Odds Converter

## Approach
- Every value is a `BigDecimal`; `double` never appears in the arithmetic. Every comparison —
  in the implementation and in the tests — uses `compareTo`, never `equals`, because `equals` is
  scale-sensitive (`2.50` and `2.5` compare equal but aren't `.equals()`).
- Decimal results are rounded `RoundingMode.HALF_UP` to a fixed `SCALE` of 4 decimal places, applied
  consistently everywhere. That's enough precision for a decimal → fractional → decimal round trip
  to land back where it started, and it matches the display precision real sportsbooks quote.
  `HALF_UP` ("round half away from zero") is the convention bettors expect; `HALF_EVEN` would be
  defensible for accounting but surprises anyone checking a price by hand.
- `fractionalFromDecimal` recovers `numerator/denominator` from `decimalOdds - 1` by reading the
  `BigDecimal` as an exact integer ratio (`unscaledValue / 10^scale`) and dividing both terms by
  their `BigInteger.gcd`. Working from the exact unscaled representation — rather than looping over
  candidate denominators or casting through `double` — keeps the reduction exact:
  `3.50 - 1 = 2.50 = 250/100`, `gcd(250, 100) = 50`, reduces to `5/2`.
- `americanFromDecimal` treats decimal `>= 2.0` as the positive-moneyline branch
  (`(decimal - 1) * 100`), so `2.0` lands exactly on `+100` — American odds have no `-100`/`+0`
  gap; the sign flips exactly at even money.
- Every method validates its own domain at the boundary (`decimalOdds <= 1`, probability outside
  `(0, 1)`, `american == 0`) rather than relying on a shared upstream check — each conversion is an
  independent entry point.

## The real challenge
- **`BigDecimal`, never `double`, for every odds/probability value.** Odds and probabilities are fractions with no exact binary representation — `1.0/3.0` in floating point compounds drift across repeated conversions, and a bookmaker rounding a price the wrong way, at volume, is a real money leak. Compare `BigDecimal`s with `compareTo`, never `equals` — `equals` is scale-sensitive (`2.50` and `2.5` are not `.equals()`).
- **Pick and document one rounding policy.** A fixed scale (e.g. 4 decimal places) and `RoundingMode.HALF_UP` applied consistently is what lets a decimal → fractional → decimal round trip land back where it started (within a small tolerance) instead of drifting with every hop.
- **Reduce the fraction exactly, not by float-casting.** Read the `BigDecimal` as an exact integer ratio (`unscaledValue / 10^scale`) and divide both terms by their GCD — that is how `2.50` (from `3.50 - 1`) becomes `250/100` and reduces to `5/2` without ever approximating.
- **The even-money boundary is a real edge case, not a rounding accident.** Decimal `2.0`, fractional `1/1`, American `+100`, and probability `0.5` all name the same coin-flip price — get the `>= 2.0` boundary in `americanFromDecimal` wrong and even money silently reports as a negative line instead of `+100`.
- **Validate every entry point.** `decimalOdds <= 1`, `p` outside `(0, 1)`, and `american == 0` are all real user input a UI or feed could hand you — reject them at the boundary, not three calculations downstream.

## Common mistakes & senior signal
- Reaching for `double` "just for the division" anywhere in the pipeline — the signal is catching
  this instinct yourself and explaining why `BigDecimal` is non-negotiable for money-adjacent math.
- Comparing `BigDecimal`s with `.equals()` (even just in your own tests) — passes for `"2.50"` vs
  `"2.50"` but silently fails `"2.50"` vs `"2.5"`, a self-inflicted bug.
- Getting the `>= 2.0` boundary backwards in `americanFromDecimal` — even money `2.0` falls into the
  negative branch and produces a nonsensical divide-by-zero or a wrong sign.
- Reducing fractions by casting through `double` and testing candidate denominators — works for
  "nice" numbers, breaks silently for anything without an exact `double` representation.
- Validating only the "main" conversion path instead of every entry point — `decimalFromProbability`
  and `decimalFromAmerican` each have their own independent domain and need their own guard, not a
  shared check that only some call paths reach.

## Extensions
- Hong Kong / Indonesian / Malaysian odds — other regional quoting conventions used by Asian
  handicap books; each is a linear transform of decimal odds, so they slot in as more
  `xFromDecimal`/`decimalFromX` pairs.
- Vig removal — given a two-way (or n-way) market whose implied probabilities sum to `> 1`,
  normalise back to a fair book by dividing each by the sum (proportional) or solving for the
  overround (Shin's method).
- Configurable scale/rounding — thread a `java.math.MathContext` through a constructor instead of a
  fixed constant, for a caller that quotes to 2 or 3 decimal places.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/oddsconverter/`)
- Java Interview Primer: Q112 (`BigDecimal` vs `double`, rounding modes)
