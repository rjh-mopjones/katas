package org.kata.oddsconverter;

/** A fractional-odds quote (e.g. {@code 5/2}), always in lowest terms once produced by the converter. */
public record Fractional(int numerator, int denominator) {
    public Fractional {
        if (numerator <= 0) {
            throw new IllegalArgumentException("numerator must be positive: " + numerator);
        }
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive: " + denominator);
        }
    }

    /** Parses the bookmaker board format {@code "n/d"} (e.g. {@code "5/2"}). */
    public static Fractional parse(String text) {
        String[] parts = text.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected format 'numerator/denominator': " + text);
        }
        return new Fractional(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
