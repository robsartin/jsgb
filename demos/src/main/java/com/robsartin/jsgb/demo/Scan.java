package com.robsartin.jsgb.demo;

/**
 * {@code sscanf}'s "literal prefix, then {@code %ld}" idiom, as the demos use it to parse a flag
 * like {@code -n50}: the literal prefix must match; then any run of C whitespace is skipped; then
 * an optional {@code +}/{@code -} sign; then at least one decimal digit. Anything after the digit
 * run is ignored, exactly as {@code sscanf} stops at the first non-digit.
 */
public final class Scan {

  private Scan() {}

  /**
   * Returns the integer that {@code sscanf(arg, prefix + "%ld", &x) == 1} would store in {@code x},
   * or {@code null} when there is no match (the prefix does not match, or no digit follows it). A
   * digit run too long for a {@code long} throws {@link NumberFormatException}; out of scope for
   * the demos' argument ranges.
   */
  public static Long scan(String arg, String prefix) {
    if (!arg.startsWith(prefix)) {
      return null;
    }
    int n = arg.length();
    int i = prefix.length();
    while (i < n && isCWhitespace(arg.charAt(i))) {
      i++;
    }
    int start = i;
    if (i < n && (arg.charAt(i) == '+' || arg.charAt(i) == '-')) {
      i++;
    }
    int digitsStart = i;
    while (i < n && Character.isDigit(arg.charAt(i))) {
      i++;
    }
    if (i == digitsStart) {
      return null;
    }
    return Long.parseLong(arg.substring(start, i));
  }

  private static boolean isCWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '' || c == '\f' || c == '\r';
  }
}
