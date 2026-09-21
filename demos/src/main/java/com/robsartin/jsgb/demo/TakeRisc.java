package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.gates.Gates;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code take_risc}: builds Knuth's 16-bit microRISC gate graph, then repeatedly reads two
 * positive numbers and reports their product (with overflow, if any) and their quotient and
 * remainder, computed by running the RISC's built-in multiply/divide routine.
 *
 * <p>The dialogue (sections 4-5) is written as an explicit state machine rather than transliterated
 * {@code goto}s, since the C's {@code goto step0}/{@code step1}/{@code step2} jump between the
 * {@code m}- and {@code n}-reading halves of the loop; see {@link Dialogue.State} for the exact
 * correspondence, including the two places where an EOF only breaks the innermost "too big" retry
 * loop rather than the whole session (the C's {@code break} there is inside a {@code while}, not an
 * {@code if}), leaving the oversized value to flow through unclamped, verbatim.
 */
public final class TakeRisc {

  private TakeRisc() {}

  /** {@code memry[34]}, section 6's ROM table, before {@code memry[1]}/{@code [3]}/{@code [5]}. */
  private static final long[] ROM = {
    0x2ff0, 0x1111, 0x1a30, 0x3333, 0x7f70, 0x5555, 0x0f8f, 0x3a21, 0x1a01, 0x0a12, 0x3a01, 0x4000,
    0x5000, 0x6000, 0x2a63, 0x0f95, 0x3063, 0x1061, 0x6ac1, 0x5fd1, 0x2a63, 0x039b, 0x0843, 0x3463,
    0x1561, 0x2863, 0x0c94, 0x4861, 0x6ac1, 0x2a63, 0x5a41, 0x0398, 0x6666, 0x0fa7
  };

  /** {@code #define mult 10}. */
  private static final long MULT = 10;

  /** {@code #define div 7}. */
  private static final long DIV = 7;

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /**
   * Runs the program: {@code trace} is nonzero when {@code args} is non-empty (the C tests {@code
   * argc > 1}).
   */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    long trace = args.length > 0 ? 8 : 0;
    Graph g = Gates.risc(8L);
    if (g == null) {
      out.print(
          String.format(
              Locale.ROOT,
              "Sorry, I couldn't generate the graph (trouble code %d)!\n",
              Gb.panicCode));
      return -1;
    }
    out.print("Welcome to the world of microRISC.\n");

    PrintStream savedGatesOut = Gates.out;
    Gates.out = out;
    try {
      return new Dialogue(in, out, g, trace).run();
    } finally {
      Gates.out = savedGatesOut;
    }
  }

  /**
   * Sections 4, 5, 7 and 8's session loop, as an explicit state machine (see the class Javadoc).
   */
  private static final class Dialogue {
    private final CStdin in;
    private final PrintStream out;
    private final Graph g;
    private final long trace;
    private final long[] memry = ROM.clone();
    private long m;
    private long n;
    private String line;

    Dialogue(CStdin in, PrintStream out, Graph g, long trace) {
      this.in = in;
      this.out = out;
      this.g = g;
      this.trace = trace;
    }

    /** The C's labels, plus two loop-condition-check states that have no label of their own. */
    private enum State {
      PROMPT_M,
      STEP0,
      STEP1,
      M_CHECK,
      M_TOO_BIG,
      PROMPT_N,
      STEP2,
      N_CHECK,
      N_TOO_BIG,
      RUN
    }

    int run() {
      State state = State.PROMPT_M;
      while (true) {
        switch (state) {
          case PROMPT_M -> {
            line = prompt("\nGimme a number: ");
            if (line == null) {
              return 0;
            }
            state = State.STEP0;
          }
          case STEP0 -> {
            Long v = Scan.scan(line, "");
            if (v == null) {
              return 0;
            }
            m = v;
            state = State.STEP1;
          }
          case STEP1 -> {
            if (m <= 0) {
              line = prompt("Excuse me, I meant a positive number: ");
              if (line == null) {
                return 0;
              }
              Long v = Scan.scan(line, "");
              if (v == null) {
                return 0;
              }
              m = v;
              if (m <= 0) {
                return 0;
              }
            }
            state = State.M_CHECK;
          }
          case M_CHECK -> state = m > 0x7fff ? State.M_TOO_BIG : State.PROMPT_N;
          case M_TOO_BIG -> {
            line = prompt("That number's too big; please try again: ");
            if (line == null) {
              state = State.PROMPT_N;
              continue;
            }
            Long v = Scan.scan(line, "");
            if (v == null) {
              state = State.STEP0;
              continue;
            }
            m = v;
            state = m <= 0 ? State.STEP1 : State.M_CHECK;
          }
          case PROMPT_N -> {
            line = prompt("OK, now gimme another: ");
            if (line == null) {
              return 0;
            }
            Long v = Scan.scan(line, "");
            if (v == null) {
              return 0;
            }
            n = v;
            state = State.STEP2;
          }
          case STEP2 -> {
            if (n <= 0) {
              line = prompt("Excuse me, I meant a positive number: ");
              if (line == null) {
                return 0;
              }
              Long v = Scan.scan(line, "");
              if (v == null) {
                return 0;
              }
              n = v;
              if (n <= 0) {
                return 0;
              }
            }
            state = State.N_CHECK;
          }
          case N_CHECK -> state = n > 0x7fff ? State.N_TOO_BIG : State.RUN;
          case N_TOO_BIG -> {
            line = prompt("That number's too big; please try again: ");
            if (line == null) {
              state = State.RUN;
              continue;
            }
            Long v = Scan.scan(line, "");
            if (v == null) {
              state = State.STEP0;
              continue;
            }
            n = v;
            state = n <= 0 ? State.STEP2 : State.N_CHECK;
          }
          case RUN -> {
            runOnce();
            state = State.PROMPT_M;
          }
        }
      }
    }

    /** Sections 7 and 8: multiply, then divide, printing both results. */
    private void runOnce() {
      memry[1] = m;
      memry[3] = n;
      memry[5] = MULT;
      Gates.runRisc(g, memry, memry.length, trace);
      long p = Gates.riscState[4];
      long overflow = Gates.riscState[16] & 1;
      out.print(
          String.format(
              Locale.ROOT,
              "The product of %d and %d is %d%s.\n",
              m,
              n,
              p,
              overflow != 0 ? " (overflow occurred)" : ""));

      memry[5] = DIV;
      Gates.runRisc(g, memry, memry.length, trace);
      long q = Gates.riscState[4];
      long r = (Gates.riscState[2] + n) & 0x7fff;
      out.print(String.format(Locale.ROOT, "The quotient is %d, and the remainder is %d.\n", q, r));
    }

    private String prompt(String s) {
      out.print(s);
      return in.fgets(99);
    }
  }
}
