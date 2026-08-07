# Money

## Approach
- Normalise once, at construction: the constructor rescales the incoming `BigDecimal` to a fixed
  `SCALE` (2 decimal places) using `RoundingMode.HALF_EVEN` — banker's rounding, the standard for
  financial arithmetic because it avoids the slight positive bias `HALF_UP` introduces over many
  roundings. From that point on the stored amount is canonical: `Money.of("2.0", "USD")` and
  `Money.of("2.00", "USD")` store the identical `BigDecimal`.
- Because the amount is already canonical, `equals`/`hashCode` are a plain field comparison
  (`amount.equals(other.amount) && currency.equals(...)`, `Objects.hash(...)`) — no `compareTo`
  needed there, unlike everywhere else in the class that touches an amount.
- `equals` never throws: amounts in different currencies are simply unequal, which is required for
  safe use as a `HashMap`/`HashSet` key (those collections call `equals` internally, including
  during resize, on pairs the caller doesn't control).
- `compareTo`, by contrast, throws `IllegalArgumentException` on a currency mismatch — there's no
  sane way to order `5 USD` against `5 GBP` without a conversion rate, so it refuses to guess. That
  makes `compareTo` a partial order: total within one currency, loud and undefined across
  currencies. Callers who need a cross-currency order must convert first.
- All fields are `final`; `plus`/`minus`/`times` return new instances, so a `Money` is safe to hand
  out, cache, or use as a map key without defensive copying.

## The real challenge
- **`BigDecimal` scale is part of its state**: `new BigDecimal("2.0")` and `new BigDecimal("2.00")` are *not* `.equals()` — they have different scale, even though `compareTo` says they're the same value. Store the amount however you like, but decide **when** you normalise it.
- **Normalise once, at construction**: rescale to a fixed number of decimal places (with a defined `RoundingMode`) in the constructor, so every stored `Money` is already canonical. Do this and `equals`/`hashCode`/`compareTo` fall out consistent for free; skip it and `2.0`/`2.00` diverge depending on which method you ask.
- **`equals` must never throw**: a `HashMap`/`HashSet` will call `equals` on arbitrary pairs internally (e.g. during resize) — cross-currency amounts must just be *unequal*, not an exception.
- **`compareTo` is allowed to throw**: unlike `equals`, there's no sane way to order `5 USD` against `5 GBP` without a conversion rate, so `compareTo` should reject a currency mismatch loudly (`IllegalArgumentException`) rather than guess. That makes it a partial order — total only within one currency.
- **Compare amounts with `compareTo`, never `BigDecimal.equals`** — even after your normalisation, reach for `compareTo`/`signum` for any value comparison inside your own methods; it's the habit that survives if the normalisation invariant is ever relaxed.

## Common mistakes & senior signal
- Deriving `equals`/`hashCode`/`compareTo` straight off a raw, unnormalized `BigDecimal` — passes a
  same-currency smoke test but fails the `2.0` vs `2.00` edge case, and a `HashSet` dedup test
  catches it immediately.
- Comparing `BigDecimal`s with `.equals()` inside your own arithmetic instead of `.compareTo()`/
  `signum()` — silently wrong even after normalisation, because the habit doesn't transfer.
- Making `equals` throw on a currency mismatch instead of returning `false` — breaks the general
  `Object.equals` contract (must be total, must never throw) and corrupts hash-based collections.
- Making `compareTo` silently order by currency code when currencies differ, instead of throwing —
  hides a real bug (comparing apples to oranges) behind a plausible-looking total order.
- Picking a `RoundingMode` without being able to justify it — an interviewer will ask "why this
  one," and "it's the finance convention, avoids bias over many roundings" (`HALF_EVEN`) is the
  answer they're listening for.

## Extensions
- `java.util.Currency` instead of a raw currency-code `String` — validates the code against
  ISO 4217 and enables locale-aware formatting.
- Allocation / splitting — dividing an amount N ways without losing or gaining a cent (the classic
  "split a bill 3 ways" rounding-remainder problem).
- `MonetaryAmount` (JSR-354, `javax.money`) — the standard library abstraction this class is a
  simplified stand-in for.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/money/`)
- Java Interview Primer: Effective Java Items 10-14 (equals/hashCode/compareTo contracts)
