# Spans

Zero-allocation string parsing with `ReadOnlySpan<char>` and `stackalloc`.

## The problem

Implement three parsing utilities that operate directly on character spans without creating heap-allocated strings. The goal is to understand stack-only memory slicing and why it matters for high-throughput code.

## Requirements

- `TryParseInt` must parse an optional `+`/`-` sign followed by decimal digits; return `false` for empty input, non-digit characters, or overflow beyond `int` range.
- `SumCsvInts` must split on commas, trim each field, and sum parseable integers; invalid/whitespace fields count as zero.
- `CountWords` must count whitespace-delimited words; consecutive spaces and leading/trailing whitespace produce no extra words.
- No intermediate `string` allocations are permitted — use span slicing throughout.

## What you implement

```csharp
public static class SpanParsing
{
    public static bool TryParseInt(ReadOnlySpan<char> s, out int value);
    public static int SumCsvInts(ReadOnlySpan<char> line);
    public static int CountWords(ReadOnlySpan<char> text);
}
```

## The real challenge

- `out` parameters prevent expression-body (`=>`) syntax — you must use block bodies for `TryParseInt`.
- Understanding `span[..commaIndex]` (range slicing) and `span.Trim()` as zero-copy operations.
- Optional: incorporate a `stackalloc Span<char>` scratch buffer in `CountWords` to demonstrate safe bounded stack allocation.

## Run

Write your own tests under `practice.tests/Spans/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Spans"
```

## Reference

`solution/Spans/` — see `SpanParsing.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/ref-struct
