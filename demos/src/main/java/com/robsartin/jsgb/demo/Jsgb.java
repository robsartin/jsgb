package com.robsartin.jsgb.demo;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code jsgb <demo> [arguments]} launcher: dispatches to one of the registered {@link Demo}
 * implementations, translating between the process's {@code argv}/{@code stdin}/exit-status world
 * and {@link Demo#run}'s testable signature.
 */
public final class Jsgb {

  private Jsgb() {}

  /** Registered demos, keyed by their C program name, in the order they were ported. */
  public static final Map<String, Demo> DEMOS = new LinkedHashMap<>();

  static {
    DEMOS.put("queen", Queen::run);
    DEMOS.put("word_components", WordComponents::run);
    DEMOS.put("roget_components", RogetComponents::run);
    DEMOS.put("ladders", Ladders::run);
    DEMOS.put("book_components", BookComponents::run);
    DEMOS.put("econ_order", EconOrder::run);
    DEMOS.put("miles_span", MilesSpan::run);
    DEMOS.put("girth", Girth::run);
    DEMOS.put("multiply", Multiply::run);
    DEMOS.put("take_risc", TakeRisc::run);
    DEMOS.put(
        "test_sample",
        (args, in, out, err, workDir) -> {
          TestSample.run(out, workDir);
          return 0;
        });
  }

  /** {@code main}'s C-return-value-to-exit-status conversion: the low 8 bits, as the shell sees. */
  static int exitStatus(int c) {
    return c & 0xff;
  }

  /** An autoflushing ISO-8859-1 stream over {@link System#out}. */
  static PrintStream stdout() {
    return new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);
  }

  /** An autoflushing ISO-8859-1 stream over {@link System#err}. */
  static PrintStream stderr() {
    return new PrintStream(System.err, true, StandardCharsets.ISO_8859_1);
  }

  /**
   * Runs {@code args[0]}'s demo with the rest of {@code args}, or prints usage on {@code err} and
   * returns 1 when {@code args} is empty or names an unregistered demo. Factored out of {@link
   * #main} so it is testable without {@link System#exit}.
   */
  static int dispatch(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    if (args.length == 0 || !DEMOS.containsKey(args[0])) {
      err.print("Usage: jsgb <demo> [arguments]\n");
      for (String name : DEMOS.keySet()) {
        err.print("  " + name + "\n");
      }
      return 1;
    }
    String[] rest = Arrays.copyOfRange(args, 1, args.length);
    return DEMOS.get(args[0]).run(rest, in, out, err, workDir);
  }

  /** Entry point: dispatches {@code args} and exits with the demo's masked return value. */
  public static void main(String[] args) {
    System.exit(exitStatus(dispatch(args, new CStdin(System.in), stdout(), stderr(), Path.of(""))));
  }
}
