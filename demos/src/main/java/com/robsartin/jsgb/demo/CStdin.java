package com.robsartin.jsgb.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * C's {@code fgets}/{@code getchar} idioms over one shared {@link InputStream}, so a short {@code
 * fgets} leaves the rest of the line for the next {@code getchar()} to read. Bytes are treated as
 * ISO-8859-1: every byte value from 0 to 255 round-trips to exactly one {@code char}.
 */
public final class CStdin {

  private final InputStream in;

  /** Wraps {@code in}; this class never closes it. */
  public CStdin(InputStream in) {
    this.in = in;
  }

  /**
   * {@code fgets(buffer, size, stdin)}: reads at most {@code size - 1} bytes, stopping after (and
   * keeping) a {@code '\n'}; returns the bytes read as an ISO-8859-1 string, or {@code null} when
   * EOF was hit before any byte was read. A partial final line with no trailing newline is returned
   * once; the next call then returns {@code null}. {@code size <= 1} reads nothing and returns
   * {@code ""}, matching {@code fgets} with no room for a character.
   */
  public String fgets(int size) {
    int limit = size - 1;
    if (limit <= 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    while (sb.length() < limit) {
      int b = read();
      if (b < 0) {
        break;
      }
      sb.append((char) b);
      if (b == '\n') {
        break;
      }
    }
    return sb.isEmpty() ? null : sb.toString();
  }

  /** {@code getchar()}: the next byte (0-255), or -1 at EOF. */
  public int getchar() {
    return read();
  }

  private int read() {
    try {
      return in.read();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
