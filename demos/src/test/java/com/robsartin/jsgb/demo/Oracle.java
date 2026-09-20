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
 * Reads the increment-2 C oracle, {@code oracle_inc2.out}: a sequence of cases, each headed by a
 * line {@code ==name} or {@code ==name=returnvalue}, followed by exactly what {@code print_sample}
 * printed for that case. Also captures a printer's output the same way the C harness produced it.
 */
final class Oracle {

  private static final Map<String, String> BODIES = new LinkedHashMap<>();
  private static final Map<String, Long> RETURNS = new LinkedHashMap<>();

  static {
    String content = readResource("/oracle/inc2/oracle_inc2.out");
    String[] lines = content.split("\n");
    String currentName = null;
    StringBuilder body = null;
    for (String line : lines) {
      if (line.startsWith("==")) {
        if (currentName != null) {
          BODIES.put(currentName, body.toString());
        }
        String header = line.substring(2);
        int eq = header.indexOf('=');
        if (eq >= 0) {
          currentName = header.substring(0, eq);
          RETURNS.put(currentName, Long.parseLong(header.substring(eq + 1)));
        } else {
          currentName = header;
        }
        body = new StringBuilder();
      } else if (currentName != null) {
        body.append(line).append("\n");
      }
    }
    if (currentName != null) {
      BODIES.put(currentName, body.toString());
    }
  }

  private Oracle() {}

  /** The text {@code print_sample} produced for the named case, exactly as recorded. */
  static String inc2(String name) {
    String body = BODIES.get(name);
    if (body == null) {
      throw new IllegalArgumentException("no oracle_inc2.out case named " + name);
    }
    return body;
  }

  /** The return value recorded in the {@code ==name=value} header for the named case. */
  static long inc2Return(String name) {
    Long value = RETURNS.get(name);
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
