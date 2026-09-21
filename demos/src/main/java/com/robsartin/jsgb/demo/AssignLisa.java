package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.lisa.Lisa;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Port of {@code assign_lisa}: builds an {@code m}&times;{@code n} grey-level matrix from a window
 * of the Mona Lisa (via {@link Lisa#lisa}), then finds the assignment of rows to columns that
 * minimises total cost with the Hungarian algorithm, instrumented with a {@link Mems} memory
 * reference count. Can also print the matrix ({@code -p}), or write it and the solution as
 * encapsulated PostScript ({@code -P}).
 *
 * <p>{@code aa(k,l)} (the C macro {@code *(mtx+k*n+l)}) is {@link #aa}/{@link #setAa}, always
 * called with the matrix's <em>current</em> {@code n} (which changes after a transpose). The three
 * sanity checks of section 23 return &minus;6/&minus;66/&minus;666 on stderr; the C's out-of-memory
 * returns (&minus;3 for the working-storage arrays, &minus;4 for the transpose buffer) are
 * unreachable here since Java arrays either allocate or throw {@link OutOfMemoryError} rather than
 * leaving {@code Gb.troubleCode} set, so they are omitted.
 */
public final class AssignLisa {

  private static final long INF = 0x7fffffffL;

  private AssignLisa() {}

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /**
   * Runs the program: parses arguments, builds the matrix, optionally prints or writes it as
   * PostScript, then solves the assignment problem and reports the {@link Mems} count.
   */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;
    Mems.mems = 0;

    long m = 0;
    long n = 0;
    long d = 0;
    long m0 = 0;
    long m1 = 0;
    long n0 = 0;
    long n1 = 0;
    long d0 = 0;
    long d1 = 0;
    boolean complFlag = false;
    boolean heur = false;
    boolean printing = false;
    boolean postScript = false;

    for (int i = args.length - 1; i >= 0; i--) {
      String arg = args[i];
      Long value;
      if ((value = Scan.scan(arg, "m=")) != null) {
        m = value;
      } else if ((value = Scan.scan(arg, "n=")) != null) {
        n = value;
      } else if ((value = Scan.scan(arg, "d=")) != null) {
        d = value;
      } else if ((value = Scan.scan(arg, "m0=")) != null) {
        m0 = value;
      } else if ((value = Scan.scan(arg, "m1=")) != null) {
        m1 = value;
      } else if ((value = Scan.scan(arg, "n0=")) != null) {
        n0 = value;
      } else if ((value = Scan.scan(arg, "n1=")) != null) {
        n1 = value;
      } else if ((value = Scan.scan(arg, "d0=")) != null) {
        d0 = value;
      } else if ((value = Scan.scan(arg, "d1=")) != null) {
        d1 = value;
      } else if (arg.equals("-s")) {
        m0 = 94;
        m1 = 110;
        n0 = 97;
        n1 = 129;
        d1 = 100000;
      } else if (arg.equals("-e")) {
        m0 = 61;
        m1 = 80;
        n0 = 91;
        n1 = 140;
        d1 = 200000;
      } else if (arg.equals("-c")) {
        complFlag = true;
      } else if (arg.equals("-h")) {
        heur = true;
      } else if (arg.equals("-v")) {
        Gb.verbose = 1;
      } else if (arg.equals("-V")) {
        Gb.verbose = 2;
      } else if (arg.equals("-p")) {
        printing = true;
      } else if (arg.equals("-P")) {
        postScript = true;
      } else {
        err.print("Usage: assign_lisa [param=value] [-s] [-c] [-h] [-v] [-p] [-P]\n");
        return -2;
      }
    }

    long[] mtx = Lisa.lisa(m, n, d, m0, m1, n0, n1, d0, d1);
    if (mtx == null) {
      err.print(
          String.format(
              Locale.ROOT, "Sorry, can't create the matrix! (error code %d)\n", Gb.panicCode));
      return -1;
    }
    out.print(
        String.format(
            Locale.ROOT,
            "Assignment problem for %s%s\n",
            Lisa.lisaId,
            complFlag ? ", complemented" : ""));
    long[] fields = parseMnD(Lisa.lisaId);
    m = fields[0];
    n = fields[1];
    d = fields[2];
    if (m != n) {
      heur = false;
    }

    if (printing) {
      printMatrix(out, mtx, m, n, d, complFlag);
    }

    OutputStream epsOut = null;
    if (postScript) {
      try {
        epsOut = Files.newOutputStream(workDir.resolve("lisa.eps"));
      } catch (IOException e) {
        err.print("Sorry, I can't open the file `lisa.eps'!\n");
        postScript = false;
      }
      if (epsOut != null) {
        writeEpsHeaderAndImage(epsOut, mtx, m, n, d, complFlag);
      }
    }

    Mems.mems = 0;
    boolean transposed;
    if (m > n) {
      if (Gb.verbose > 1) {
        out.print("Temporarily transposing rows and columns...\n");
      }
      long[] tmtx = new long[(int) (m * n)];
      for (long k = 0; k < m; k++) {
        for (long l = 0; l < n; l++) {
          tmtx[(int) (l * m + k)] = mtx[(int) (k * n + l)];
        }
      }
      long oldM = m;
      m = n;
      n = oldM;
      mtx = tmtx;
      transposed = true;
    } else {
      transposed = false;
    }

    long[] colMate = new long[(int) m];
    long[] rowMate = new long[(int) n];
    long[] parentRow = new long[(int) n];
    long[] unchosenRow = new long[(int) m];
    long[] rowDec = new long[(int) m];
    long[] colInc = new long[(int) n];
    long[] slack = new long[(int) n];
    long[] slackRow = new long[(int) n];

    if (!complFlag) {
      for (long k = 0; k < m; k++) {
        for (long l = 0; l < n; l++) {
          setAa(mtx, n, k, l, d - aa(mtx, n, k, l));
        }
      }
    }

    if (heur) {
      runHeuristic(out, mtx, n);
    }

    solve(
        out, mtx, m, n, colMate, rowMate, parentRow, unchosenRow, rowDec, colInc, slack, slackRow);

    // Section 23: the three sanity checks.
    for (long k = 0; k < m; k++) {
      for (long l = 0; l < n; l++) {
        if (aa(mtx, n, k, l) < rowDec[(int) k] - colInc[(int) l]) {
          err.print("Oops, I made a mistake!\n");
          return -6;
        }
      }
    }
    for (long k = 0; k < m; k++) {
      long l = colMate[(int) k];
      if (l < 0 || aa(mtx, n, k, l) != rowDec[(int) k] - colInc[(int) l]) {
        err.print("Oops, I blew it!\n");
        return -66;
      }
    }
    long adjustedColumns = 0;
    for (long l = 0; l < n; l++) {
      if (colInc[(int) l] != 0) {
        adjustedColumns++;
      }
    }
    if (adjustedColumns > m) {
      err.print("Oops, I adjusted too many columns!\n");
      return -666;
    }

    if (printing) {
      out.print("The following entries produce an optimum assignment:\n");
      for (long k = 0; k < m; k++) {
        out.print(
            String.format(
                Locale.ROOT,
                " [%d,%d]\n",
                transposed ? colMate[(int) k] : k,
                transposed ? k : colMate[(int) k]));
      }
    }

    if (postScript && epsOut != null) {
      writeEpsFooter(epsOut, colMate, m, n, transposed);
    }

    out.print(
        String.format(
            Locale.ROOT,
            "Solved in %d mems%s.\n",
            Mems.mems,
            heur ? " with square-matrix heuristic" : ""));
    return 0;
  }

  /** {@code aa(k,l)}: the C macro {@code *(mtx+k*n+l)}. */
  private static long aa(long[] mtx, long n, long k, long l) {
    return mtx[(int) (k * n + l)];
  }

  /** Assigns {@code aa(k,l) = value}. */
  private static void setAa(long[] mtx, long n, long k, long l, long value) {
    mtx[(int) (k * n + l)] = value;
  }

  /** Section 6: {@code -p}'s listing of the input matrix, {@code "% 4ld"} per entry. */
  private static void printMatrix(
      PrintStream out, long[] mtx, long m, long n, long d, boolean complFlag) {
    for (long k = 0; k < m; k++) {
      StringBuilder line = new StringBuilder();
      for (long l = 0; l < n; l++) {
        long value = complFlag ? d - aa(mtx, n, k, l) : aa(mtx, n, k, l);
        line.append(String.format(Locale.ROOT, "% 4d", value));
      }
      out.print(line + "\n");
    }
  }

  /**
   * Section 30: {@code float conv = 255.0/(float)d; x = (long)(conv*(float)value);} clamped to 255
   * and hex-encoded as {@code "%02lx"}. Package-private for direct unit testing.
   */
  static String formatEpsPixel(long d, long value) {
    float conv = (float) (255.0 / (float) d);
    long x = (long) (conv * (float) value);
    return String.format(Locale.ROOT, "%02x", Math.min(x, 255));
  }

  /**
   * Sections 28/30: the EPS header and the hex-encoded image, using the matrix, {@code m}, {@code
   * n}, {@code d} and {@code compl} as they stood right after the matrix was built (before the
   * transpose and the solving complement).
   */
  private static void writeEpsHeaderAndImage(
      OutputStream epsOut, long[] mtx, long m, long n, long d, boolean complFlag) {
    PrintStream ps = new PrintStream(epsOut, false, StandardCharsets.ISO_8859_1);
    ps.print("%!PS-Adobe-3.0 EPSF-3.0\n");
    ps.print(String.format(Locale.ROOT, "%%%%BoundingBox: -1 -1 %d %d\n", n + 1, m + 1));
    ps.print(String.format(Locale.ROOT, "/buffer %d string def\n", n));
    ps.print(String.format(Locale.ROOT, "%d %d 8 [%d 0 0 -%d 0 %d]\n", n, m, n, m, m));
    ps.print("{currentfile buffer readhexstring pop} bind\n");
    ps.print(String.format(Locale.ROOT, "gsave %d %d scale image\n", n, m));
    for (long k = 0; k < m; k++) {
      for (long l = 0; l < n; l++) {
        long value = complFlag ? d - aa(mtx, n, k, l) : aa(mtx, n, k, l);
        ps.print(formatEpsPixel(d, value));
        if ((l & 0x1f) == 0x1f) {
          ps.print("\n");
        }
      }
      if ((n & 0x1f) != 0) {
        ps.print("\n");
      }
    }
    ps.print("grestore\n");
    ps.flush();
  }

  /** Section 31: the {@code bx} box definition and one box per assignment, then closes the file. */
  private static void writeEpsFooter(
      OutputStream epsOut, long[] colMate, long m, long n, boolean transposed) {
    PrintStream ps = new PrintStream(epsOut, false, StandardCharsets.ISO_8859_1);
    ps.print("/bx {moveto 0 1 rlineto 1 0 rlineto 0 -1 rlineto closepath\n");
    ps.print(" gsave .3 setlinewidth 1 setgray clip stroke");
    ps.print(" grestore stroke} bind def\n");
    ps.print(" .1 setlinewidth\n");
    for (long k = 0; k < m; k++) {
      long x = transposed ? k : colMate[(int) k];
      long y = transposed ? n - 1 - colMate[(int) k] : m - 1 - k;
      ps.print(String.format(Locale.ROOT, " %d %d bx\n", x, y));
    }
    ps.flush();
    try {
      epsOut.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The C's {@code sscanf(lisa_id, "lisa(%lu,%lu,%lu", &m, &n, &d)}: the first three unsigned
   * values embedded in {@code lisaId}.
   */
  private static long[] parseMnD(String lisaId) {
    int idx = lisaId.indexOf('(') + 1;
    long[] result = new long[3];
    for (int i = 0; i < 3; i++) {
      int end = lisaId.indexOf(',', idx);
      result[i] = Long.parseUnsignedLong(lisaId.substring(idx, end));
      idx = end + 1;
    }
    return result;
  }

  /** Section 12: the square-matrix heuristic, subtracting each column's minimum from it. */
  private static void runHeuristic(PrintStream out, long[] mtx, long n) {
    for (long l = 0; l < n; l++) {
      Mems.o();
      long s = aa(mtx, n, 0, l);
      for (long k = 1; k < n; k++) {
        Mems.o();
        if (aa(mtx, n, k, l) < s) {
          s = aa(mtx, n, k, l);
        }
      }
      if (s != 0) {
        for (long k = 0; k < n; k++) {
          Mems.oo();
          setAa(mtx, n, k, l, aa(mtx, n, k, l) - s);
        }
      }
    }
    if (Gb.verbose != 0) {
      out.print(String.format(Locale.ROOT, " The heuristic has cost %d mems.\n", Mems.mems));
    }
  }

  /** Sections 16-23 (16-22 here; 23's sanity checks stay in {@link #run}): the Hungarian search. */
  private static void solve(
      PrintStream out,
      long[] mtx,
      long m,
      long n,
      long[] colMate,
      long[] rowMate,
      long[] parentRow,
      long[] unchosenRow,
      long[] rowDec,
      long[] colInc,
      long[] slack,
      long[] slackRow) {
    // Section 16: initial matching by each row's own minimum.
    long t = 0;
    for (long l = 0; l < n; l++) {
      Mems.o();
      rowMate[(int) l] = -1;
      Mems.o();
      parentRow[(int) l] = -1;
      Mems.o();
      colInc[(int) l] = 0;
      Mems.o();
      slack[(int) l] = INF;
    }
    long k = 0;
    long l = 0;
    long j = 0;
    rowLoop:
    for (k = 0; k < m; k++) {
      Mems.o();
      long s = aa(mtx, n, k, 0);
      for (l = 1; l < n; l++) {
        Mems.o();
        if (aa(mtx, n, k, l) < s) {
          s = aa(mtx, n, k, l);
        }
      }
      Mems.o();
      rowDec[(int) k] = s;
      for (l = 0; l < n; l++) {
        Mems.o();
        if (s == aa(mtx, n, k, l)) {
          Mems.o();
          if (rowMate[(int) l] < 0) {
            Mems.o();
            colMate[(int) k] = l;
            Mems.o();
            rowMate[(int) l] = k;
            if (Gb.verbose > 1) {
              out.print(String.format(Locale.ROOT, " matching col %d==row %d\n", l, k));
            }
            continue rowLoop;
          }
        }
      }
      Mems.o();
      colMate[(int) k] = -1;
      if (Gb.verbose > 1) {
        out.print(String.format(Locale.ROOT, "  node %d: unmatched row %d\n", t, k));
      }
      Mems.o();
      unchosenRow[(int) (t++)] = k;
    }

    if (t == 0) {
      return;
    }
    long unmatched = t;
    augment:
    while (true) {
      if (Gb.verbose != 0) {
        out.print(
            String.format(Locale.ROOT, " After %d mems I've matched %d rows.\n", Mems.mems, m - t));
      }
      long q = 0;
      searchLoop:
      while (true) {
        while (q < t) {
          // Section 19: extend the alternating tree from unchosen_row[q].
          Mems.o();
          k = unchosenRow[(int) q];
          Mems.o();
          long s = rowDec[(int) k];
          for (l = 0; l < n; l++) {
            Mems.o();
            if (slack[(int) l] != 0) {
              Mems.oo();
              long del = aa(mtx, n, k, l) - s + colInc[(int) l];
              if (del < slack[(int) l]) {
                if (del == 0) {
                  Mems.o();
                  if (rowMate[(int) l] < 0) {
                    break searchLoop;
                  }
                  Mems.o();
                  slack[(int) l] = 0;
                  Mems.o();
                  parentRow[(int) l] = k;
                  if (Gb.verbose > 1) {
                    out.print(
                        String.format(
                            Locale.ROOT,
                            "  node %d: row %d==col %d--row %d\n",
                            t,
                            rowMate[(int) l],
                            l,
                            k));
                  }
                  Mems.oo();
                  unchosenRow[(int) (t++)] = rowMate[(int) l];
                } else {
                  Mems.o();
                  slack[(int) l] = del;
                  Mems.o();
                  slackRow[(int) l] = k;
                }
              }
            }
          }
          q++;
        }
        // Section 21: no augmenting path found yet; adjust the dual variables.
        long s = INF;
        for (l = 0; l < n; l++) {
          Mems.o();
          if (slack[(int) l] != 0 && slack[(int) l] < s) {
            s = slack[(int) l];
          }
        }
        for (q = 0; q < t; q++) {
          Mems.ooo();
          rowDec[(int) unchosenRow[(int) q]] += s;
        }
        for (l = 0; l < n; l++) {
          Mems.o();
          if (slack[(int) l] != 0) {
            Mems.o();
            slack[(int) l] -= s;
            if (slack[(int) l] == 0) {
              // Section 22: this column just became tight.
              Mems.o();
              k = slackRow[(int) l];
              if (Gb.verbose > 1) {
                out.print(
                    String.format(
                        Locale.ROOT,
                        " Decreasing uncovered elements by %d produces zero at [%d,%d]\n",
                        s,
                        k,
                        l));
              }
              Mems.o();
              if (rowMate[(int) l] < 0) {
                for (j = l + 1; j < n; j++) {
                  Mems.o();
                  if (slack[(int) j] == 0) {
                    Mems.oo();
                    colInc[(int) j] += s;
                  }
                }
                break searchLoop;
              } else {
                Mems.o();
                parentRow[(int) l] = k;
                if (Gb.verbose > 1) {
                  out.print(
                      String.format(
                          Locale.ROOT,
                          "  node %d: row %d==col %d--row %d\n",
                          t,
                          rowMate[(int) l],
                          l,
                          k));
                }
                Mems.oo();
                unchosenRow[(int) (t++)] = rowMate[(int) l];
              }
            }
          } else {
            Mems.oo();
            colInc[(int) l] += s;
          }
        }
      }
      // Section 20: a breakthrough was found at [k,l]; rematch the alternating path back to it.
      if (Gb.verbose != 0) {
        out.print(String.format(Locale.ROOT, " Breakthrough at node %d of %d!\n", q, t));
      }
      while (true) {
        Mems.o();
        j = colMate[(int) k];
        Mems.o();
        colMate[(int) k] = l;
        Mems.o();
        rowMate[(int) l] = k;
        if (Gb.verbose > 1) {
          out.print(String.format(Locale.ROOT, " rematching col %d==row %d\n", l, k));
        }
        if (j < 0) {
          break;
        }
        Mems.o();
        k = parentRow[(int) j];
        l = j;
      }
      if (--unmatched == 0) {
        break;
      }
      // Section 17: rebuild the unchosen-row list from the still-unmatched rows.
      t = 0;
      for (l = 0; l < n; l++) {
        Mems.o();
        parentRow[(int) l] = -1;
        Mems.o();
        slack[(int) l] = INF;
      }
      for (k = 0; k < m; k++) {
        Mems.o();
        if (colMate[(int) k] < 0) {
          if (Gb.verbose > 1) {
            out.print(String.format(Locale.ROOT, "  node %d: unmatched row %d\n", t, k));
          }
          Mems.o();
          unchosenRow[(int) (t++)] = k;
        }
      }
    }
  }
}
