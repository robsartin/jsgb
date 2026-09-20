package com.robsartin.jsgb.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the C oracle files {@code oracle_inc2.out} and {@code oracle_inc3a.out}: each is a sequence
 * of cases, headed by a line {@code ==name} or {@code ==name=returnvalue}, followed by exactly what
 * {@code print_sample} (or, for a few inc3a cases, a custom printer) printed. Also captures a
 * printer's output the same way the C harnesses produced it.
 */
final class Oracle {

  /** One oracle file's parsed cases: bodies by name, and any recorded {@code ==name=value}. */
  private record OracleFile(Map<String, String> bodies, Map<String, Long> returns) {}

  private static final OracleFile INC2 = load("/oracle/inc2/oracle_inc2.out");
  private static final OracleFile INC3A = load("/oracle/inc3a/oracle_inc3a.out");

  private Oracle() {}

  /** The text {@code print_sample} produced for the named increment-2 case, exactly as recorded. */
  static String inc2(String name) {
    return body(INC2, name, "oracle_inc2.out");
  }

  /**
   * The return value recorded in the {@code ==name=value} header for the named increment-2 case.
   */
  static long inc2Return(String name) {
    return returnValue(INC2, name);
  }

  /** The text printed for the named increment-3a case, exactly as recorded. */
  static String inc3a(String name) {
    return body(INC3A, name, "oracle_inc3a.out");
  }

  /**
   * The return value recorded in the {@code ==name=value} header for the named increment-3a case.
   */
  static long inc3aReturn(String name) {
    return returnValue(INC3A, name);
  }

  private static String body(OracleFile file, String name, String fileLabel) {
    String body = file.bodies().get(name);
    if (body == null) {
      throw new IllegalArgumentException("no " + fileLabel + " case named " + name);
    }
    return body;
  }

  private static long returnValue(OracleFile file, String name) {
    Long value = file.returns().get(name);
    if (value == null) {
      throw new IllegalArgumentException("no return value recorded for " + name);
    }
    return value;
  }

  /**
   * Runs {@code printer} against a fresh ISO-8859-1 {@link PrintStream} and returns what it wrote.
   */
  static String capture(Consumer<PrintStream> printer) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true, StandardCharsets.ISO_8859_1);
    printer.accept(ps);
    return baos.toString(StandardCharsets.ISO_8859_1);
  }

  /**
   * Parses one {@code ==name}/{@code ==name=value}-delimited oracle file, keyed by resource path.
   */
  private static OracleFile load(String resource) {
    Map<String, String> bodies = new LinkedHashMap<>();
    Map<String, Long> returns = new LinkedHashMap<>();
    String content = readResource(resource);
    String[] lines = content.split("\n");
    String currentName = null;
    StringBuilder body = null;
    for (String line : lines) {
      if (line.startsWith("==")) {
        if (currentName != null) {
          bodies.put(currentName, body.toString());
        }
        String header = line.substring(2);
        int eq = header.indexOf('=');
        if (eq >= 0) {
          currentName = header.substring(0, eq);
          returns.put(currentName, Long.parseLong(header.substring(eq + 1)));
        } else {
          currentName = header;
        }
        body = new StringBuilder();
      } else if (currentName != null) {
        body.append(line).append("\n");
      }
    }
    if (currentName != null) {
      bodies.put(currentName, body.toString());
    }
    return new OracleFile(bodies, returns);
  }

  private static String readResource(String path) {
    try (InputStream in = Oracle.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing classpath resource " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
