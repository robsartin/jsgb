package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.gates.Gates;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code multiply}: builds an {@code m}-by-{@code n} bit binary multiplier gate graph (or,
 * with a seed, one with the multiplier's bits pre-forced to a random constant), then repeatedly
 * reads decimal numbers from the user, evaluates the circuit, and prints the product.
 *
 * <p>{@code dp} ({@code depth}'s per-vertex working value) is {@code u.I}; {@link #depth} skips
 * boolean output-arc tips ({@code null} or {@link Gb#ONE}).
 */
public final class Multiply {

  private Multiply() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** {@code decimal_to_binary}'s result: the low {@code n} bits (MSB first) and any leftover. */
  record DecimalToBinary(String bits, boolean overflow) {}

  /** Runs the program: parses {@code m n [seed]}, builds the circuit, then loops on numbers. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    if (args.length < 2 || args.length > 3) {
      err.print("Usage: multiply m n [seed]\n");
      return -2;
    }
    Long mVal = Scan.scan(args[0], "");
    Long nVal = Scan.scan(args[1], "");
    if (mVal == null || nVal == null) {
      err.print("Usage: multiply m n [seed]\n");
      return -2;
    }
    long m = mVal < 0 ? -mVal : mVal;
    long n = nVal < 0 ? -nVal : nVal;
    long seed = -1;
    if (args.length == 3) {
      Long seedVal = Scan.scan(args[2], "");
      if (seedVal != null) {
        seed = seedVal < 0 ? -seedVal : seedVal;
      }
    }

    if (m < 2) {
      m = 2;
    }
    if (n < 2) {
      n = 2;
    }
    if (m > 999 || n > 999) {
      out.print("Sorry, I'm set up only for precision less than 1000 bits.\n");
      return -1;
    }

    Graph g = Gates.prod(m, n);
    if (g == null) {
      String reason =
          Gb.panicCode == Gb.NO_ROOM
              ? "the gates"
              : Gb.panicCode == Gb.ALLOC_FAULT ? "the wires" : "local optimization";
      out.print(
          String.format(
              Locale.ROOT,
              "Sorry, I couldn't generate the graph (not enough memory for %s)!\n",
              reason));
      return -3;
    }

    String fixedY = null;
    if (seed < 0) {
      out.print(
          String.format(
              Locale.ROOT,
              "Here I am, ready to multiply %d-bit numbers by %d-bit numbers.\n",
              m,
              n));
    } else {
      StringBuilder forcedBits = new StringBuilder();
      g = Gates.partialGates(g, m, 0L, seed, forcedBits);
      if (g != null) {
        String reversedForced = forcedBits.reverse().toString();
        fixedY = binaryToDecimal(reversedForced);
        if (fixedY.equals("0")) {
          out.print(
              String.format(
                  Locale.ROOT,
                  "Please try another seed value; %d makes the answer zero!\n",
                  (int) seed));
          return -5;
        }
        out.print(
            String.format(
                Locale.ROOT, "OK, I'm ready to multiply any %d-bit number by %s.\n", m, fixedY));
      } else {
        out.print(
            String.format(
                Locale.ROOT,
                "Sorry, I couldn't process the graph (trouble code %d)!\n",
                Gb.panicCode));
        return -9;
      }
    }
    out.print(
        String.format(
            Locale.ROOT,
            "(I'm simulating a logic circuit with %d gates, depth %d.)\n",
            g.n,
            depth(g)));

    while (true) {
      String xDigits = promptForDigits(in, out, "\nNumber, please? ");
      if (xDigits == null) {
        return 0;
      }
      String yDigits;
      if (seed < 0) {
        yDigits = promptForDigits(in, out, "Another? ");
        if (yDigits == null) {
          return 0;
        }
      } else {
        yDigits = fixedY;
      }

      DecimalToBinary xBits = decimalToBinary(xDigits, m);
      if (xBits.overflow()) {
        out.print(String.format(Locale.ROOT, "(Sorry, %s has more than %d bits.)\n", xDigits, m));
        continue;
      }
      String inputVec = reverseString(xBits.bits());
      if (seed < 0) {
        DecimalToBinary yBits = decimalToBinary(yDigits, n);
        if (yBits.overflow()) {
          out.print(String.format(Locale.ROOT, "(Sorry, %s has more than %d bits.)\n", yDigits, n));
          continue;
        }
        inputVec = inputVec + reverseString(yBits.bits());
      }

      StringBuilder outBits = new StringBuilder();
      if (Gates.gateEval(g, inputVec, outBits) < 0) {
        out.print("??? An internal error occurred!");
        return 666;
      }
      String z = binaryToDecimal(outBits.toString());
      out.print(
          String.format(
              Locale.ROOT,
              "%sx%s=%s%s.\n",
              xDigits,
              yDigits,
              xDigits.length() + yDigits.length() > 35 ? "\n " : "",
              z));
    }
  }

  /**
   * Sections 7/8's shared prompt-and-validate loop: prints {@code promptText}, reads a line, skips
   * leading zeros, and returns the remaining digit string. Returns {@code null} on EOF or a
   * completely blank line (the caller should then return 0, matching the C's {@code break} out of
   * the outer session loop). Retries {@code promptText} (not the other prompt) on a non-digit
   * character before the newline or a digit run longer than 301 characters.
   */
  private static String promptForDigits(CStdin in, PrintStream out, String promptText) {
    while (true) {
      out.print(promptText);
      String line = in.fgets(999);
      if (line == null) {
        return null;
      }
      int p = 0;
      while (p < line.length() && line.charAt(p) == '0') {
        p++;
      }
      if (p < line.length() && line.charAt(p) == '\n') {
        if (p > 0) {
          p--;
        } else {
          return null;
        }
      }
      int q = p;
      while (q < line.length() && line.charAt(q) >= '0' && line.charAt(q) <= '9') {
        q++;
      }
      if (q >= line.length() || line.charAt(q) != '\n') {
        out.print("Excuse me... I'm looking for a nonnegative sequence of decimal digits.");
        continue;
      }
      String digits = line.substring(p, q);
      if (digits.length() > 301) {
        out.print("Sorry, that's too big.");
        continue;
      }
      return digits;
    }
  }

  /**
   * {@code decimal_to_binary(x,s,n)}: the low {@code n} bits of decimal digit string {@code
   * decimal}, computed by repeated halving, returned MSB first (the conventional reading order);
   * {@link DecimalToBinary#overflow()} is set when a nonzero remainder is left after {@code n}
   * halvings (the number needs more than {@code n} bits).
   */
  static DecimalToBinary decimalToBinary(String decimal, long n) {
    StringBuilder lsbFirst = new StringBuilder();
    String x = decimal;
    for (long k = 0; k < n; k++) {
      DivBy2 r = divideByTwo(x);
      lsbFirst.append((char) ('0' + r.remainderBit()));
      x = r.quotient();
    }
    return new DecimalToBinary(lsbFirst.reverse().toString(), !x.equals("0"));
  }

  /** One decimal-string halving: the quotient (no leading zero, {@code "0"} for zero) and bit. */
  private record DivBy2(String quotient, int remainderBit) {}

  private static DivBy2 divideByTwo(String decimal) {
    if (decimal.equals("0")) {
      return new DivBy2("0", 0);
    }
    StringBuilder q = new StringBuilder();
    int carry = 0;
    for (int i = 0; i < decimal.length(); i++) {
      int d = carry * 10 + (decimal.charAt(i) - '0');
      q.append((char) ('0' + d / 2));
      carry = d % 2;
    }
    int start = 0;
    while (start < q.length() - 1 && q.charAt(start) == '0') {
      start++;
    }
    return new DivBy2(q.substring(start), carry);
  }

  /**
   * Sections 9 and 12's shared binary-to-decimal doubling: {@code bitsMsbFirst} processed from the
   * most significant bit to the least, doubling a decimal digit string and adding each bit.
   */
  static String binaryToDecimal(String bitsMsbFirst) {
    String y = "0";
    for (int i = 0; i < bitsMsbFirst.length(); i++) {
      int bit = bitsMsbFirst.charAt(i) - '0';
      y = doubleAndAddBit(y, bit);
    }
    return y;
  }

  private static String doubleAndAddBit(String decimal, int bit) {
    StringBuilder result = new StringBuilder();
    int carry = bit;
    for (int i = decimal.length() - 1; i >= 0; i--) {
      int d = (decimal.charAt(i) - '0') * 2 + carry;
      result.append((char) ('0' + d % 10));
      carry = d / 10;
    }
    if (carry > 0) {
      result.append((char) ('0' + carry));
    }
    return result.reverse().toString();
  }

  private static String reverseString(String s) {
    return new StringBuilder(s).reverse().toString();
  }

  /** {@code is_boolean(v)}'s null-or-{@link Gb#ONE} test, as used by {@link #depth}. */
  private static boolean isBooleanTip(Vertex v) {
    return v == null || v == Gb.ONE;
  }

  /**
   * {@code depth(g)}: the longest path, in gate delays, from any input to any output. Section 13's
   * per-vertex pass stores each vertex's depth in {@code dp} ({@code u.I}): 0 for an input, latch
   * or constant, else one more than the deepest arc tip's depth. Section 14/15 then takes the
   * deepest non-boolean output.
   */
  static long depth(Graph g) {
    if (g == null) {
      return -1;
    }
    for (int i = 0; i < g.n; i++) {
      Vertex v = g.vertices[i];
      switch ((int) v.y.I) {
        case 'I':
        case 'L':
        case 'C':
          v.u.I = 0;
          break;
        default:
          long d = 0;
          for (Arc a = v.arcs; a != null; a = a.next) {
            if (a.tip.u.I > d) {
              d = a.tip.u.I;
            }
          }
          v.u.I = 1 + d;
      }
    }
    long d = 0;
    for (Arc a = g.zz.A(); a != null; a = a.next) {
      if (!isBooleanTip(a.tip) && a.tip.u.I > d) {
        d = a.tip.u.I;
      }
    }
    return d;
  }
}
