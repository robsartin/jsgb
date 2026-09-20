package com.robsartin.jsgb.lisa;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;

/**
 * Port of {@code gb_lisa}: the Mona Lisa as a 360&times;250 grey-level matrix ({@code lisa.dat}),
 * and two graphs derived from it.
 *
 * <p>{@link #lisa} reduces a rectangular window of the painting (rows {@code [m0,m1)}, columns
 * {@code [n0,n1)}) to an {@code m &times; n} matrix by area-weighted averaging, then linearly
 * rescales each element from the range {@code [d0,d1)} onto {@code [0,d]} (clamping outside that
 * range). {@link #planeLisa} merges the 4-connected regions of equal value in such a matrix into
 * vertices, adjacent when their regions touch. {@link #biLisa} instead builds a bipartite graph
 * joining a vertex per row to a vertex per column whenever the corresponding pixel passes a
 * threshold.
 *
 * <p>All nine parameters of {@link #lisa}, the nine of {@link #planeLisa}, and the first seven of
 * {@link #biLisa} are C {@code unsigned long}; every comparison between them is unsigned. {@link
 * #planeLisa} and {@link #biLisa} recover the clamped {@code m}, {@code n} (and, for {@link
 * #biLisa}, {@code m0}, {@code m1}, {@code n0}, {@code n1}) by parsing {@link #lisaId}, exactly as
 * the C's {@code sscanf} does.
 *
 * <p>{@code plane_lisa} vertex slots: {@code x.I} ({@code pixel_value}) the matrix value of the
 * region, {@code y.I} ({@code first_pixel}) the row-major index of the region's first pixel, {@code
 * z.I} ({@code last_pixel}) the index of the last pixel visited so far in the region. Graph slots
 * {@code uu.I} ({@code matrix_rows}) and {@code vv.I} ({@code matrix_cols}) record {@code m} and
 * {@code n}.
 */
public final class Lisa {

  private static final int MAX_M = 360;
  private static final int MAX_N = 250;
  private static final int MAX_D = 255;
  private static final long EL_GORDO = 0x7fffffffL;

  /**
   * {@code lisa_id}: the identification string of the matrix most recently produced by {@link
   * #lisa}, overwritten only when a call's parameters pass every validity check (a {@code
   * bad_specs} panic leaves it untouched).
   */
  public static String lisaId = "lisa(360,250,9999999999,359,360,249,250,9999999999,9999999999)";

  private Lisa() {}

  /**
   * {@code lisa(m,n,d,m0,m1,n0,n1,d0,d1)}: an {@code m &times; n} row-major matrix, each element
   * the area-weighted average grey level of the corresponding cell of an {@code m &times; n} grid
   * over the window {@code [m0,m1) &times; [n0,n1)} of the painting, then linearly rescaled from
   * {@code [d0,d1)} onto {@code [0,d]} ({@code &le; d0} maps to 0, {@code &ge; d1} maps to {@code
   * d}).
   *
   * <p>{@code m1} 0 or over {@value #MAX_M} becomes {@value #MAX_M}; {@code n1} 0 or over {@value
   * #MAX_N} becomes {@value #MAX_N}; {@code m}, {@code n} 0 default to the window's row/column
   * count; {@code d} 0 becomes {@value #MAX_D}; {@code d1} 0 defaults to {@code MAX_D * (m1-m0) *
   * (n1-n0)}.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} to {@link Gb#BAD_SPECS} + 1 ({@code m1 <=
   * m0}), + 2 ({@code n1 <= n0}), + 3 ({@code d1 <= d0}), or + 4 ({@code d1 >= 2^31}); to {@link
   * Gb#NO_ROOM} + 1 if the {@code m &times; n} matrix is too large for a Java array; to {@link
   * Gb#EARLY_DATA_FAULT} if {@code lisa.dat} cannot be opened; or to {@link Gb#LATE_DATA_FAULT} if
   * it is malformed. Every parameter is unsigned.
   */
  public static long[] lisa(
      long m, long n, long d, long m0, long m1, long n0, long n1, long d0, long d1) {
    // Section 8: default and validate the parameters, then fix lisa_id.
    if (m1 == 0 || Long.compareUnsigned(m1, MAX_M) > 0) {
      m1 = MAX_M;
    }
    if (Long.compareUnsigned(m1, m0) <= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }
    if (n1 == 0 || Long.compareUnsigned(n1, MAX_N) > 0) {
      n1 = MAX_N;
    }
    if (Long.compareUnsigned(n1, n0) <= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 2;
      Gb.troubleCode = 0;
      return null;
    }
    long capM = m1 - m0;
    long capN = n1 - n0;
    if (m == 0) {
      m = capM;
    }
    if (n == 0) {
      n = capN;
    }
    if (d == 0) {
      d = MAX_D;
    }
    if (d1 == 0) {
      d1 = (long) MAX_D * capM * capN;
    }
    if (Long.compareUnsigned(d1, d0) <= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 3;
      Gb.troubleCode = 0;
      return null;
    }
    if (Long.compareUnsigned(d1, 0x80000000L) >= 0) {
      Gb.panicCode = Gb.BAD_SPECS + 4;
      Gb.troubleCode = 0;
      return null;
    }
    long capD = d1 - d0;
    lisaId =
        "lisa("
            + Long.toUnsignedString(m)
            + ","
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(d)
            + ","
            + Long.toUnsignedString(m0)
            + ","
            + Long.toUnsignedString(m1)
            + ","
            + Long.toUnsignedString(n0)
            + ","
            + Long.toUnsignedString(n1)
            + ","
            + Long.toUnsignedString(d0)
            + ","
            + Long.toUnsignedString(d1)
            + ")";

    // Section 9: the output matrix (no_room + 1 if it cannot fit in a Java array).
    long[] matx = allocateMatrix(m, n);
    if (matx == null) {
      Gb.panicCode = Gb.NO_ROOM + 1;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 19: open the data file and skip the rows above the window.
    if (GbIo.open("lisa.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    for (long i = 0; Long.compareUnsigned(i, m0) < 0; i++) {
      for (int j = 0; j < 5; j++) {
        GbIo.newline();
      }
    }

    // Section 13: scan the window's input rows, distributing them over the m output rows.
    long[] inRow = new long[MAX_N];
    long kappa = 0;
    long kap = 0;
    int outRow = 0;
    for (long k = 0; k < m; k++) {
      for (int l = 0; l < n; l++) {
        matx[outRow + l] = 0;
      }
      long nextKap = kap + capM;
      do {
        if (kap >= kappa) {
          readInputRow(inRow);
          kappa += m;
        }
        long nk = Math.min(kappa, nextKap);
        long f = nk - kap;
        // Section 12: distribute this input row's window over the n output columns.
        long lambda = n;
        int cp = (int) n0;
        for (long l = 0, lam = 0; l < n; l++) {
          long sum = 0;
          long nextLam = lam + capN;
          do {
            if (lam >= lambda) {
              cp++;
              lambda += n;
            }
            long nl = Math.min(lambda, nextLam);
            sum += (nl - lam) * inRow[cp];
            lam = nl;
          } while (lam < nextLam);
          matx[outRow + (int) l] += f * sum;
        }
        kap = nk;
      } while (kap < nextKap);
      // Section 18: rescale each element of this row from [d0,d1) onto [0,d].
      for (int l = 0; l < n; l++, outRow++) {
        long v = matx[outRow];
        if (Long.compareUnsigned(v, d0) <= 0) {
          matx[outRow] = 0;
        } else if (Long.compareUnsigned(v, d1) >= 0) {
          matx[outRow] = d;
        } else {
          matx[outRow] = naOverB(d, v - d0, capD);
        }
      }
    }

    // Section 20: skip the rows below the window and close the file.
    for (long i = m1; Long.compareUnsigned(i, MAX_M) < 0; i++) {
      for (int j = 0; j < 5; j++) {
        GbIo.newline();
      }
    }
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return matx;
  }

  /** Section 9: {@code m * n} as an {@code int}, or {@code null} if it cannot fit an array. */
  private static long[] allocateMatrix(long m, long n) {
    long maxN = m == 0 ? -1L : Long.divideUnsigned(Integer.MAX_VALUE, m);
    if (m != 0 && Long.compareUnsigned(n, maxN) > 0) {
      return null;
    }
    return new long[(int) (m * n)];
  }

  /**
   * Section 21: reads one 250-pixel row of {@code lisa.dat}, base-85 encoded four pixels to five
   * digits (the last two pixels of the row, three digits), into {@code inRow}.
   */
  private static void readInputRow(long[] inRow) {
    int j = 15;
    int cp = 0;
    long dd;
    while (true) {
      dd = GbIo.digit(85);
      dd = dd * 85 + GbIo.digit(85);
      dd = dd * 85 + GbIo.digit(85);
      if (cp == MAX_N - 2) {
        break;
      }
      dd = dd * 85 + GbIo.digit(85);
      dd = dd * 85 + GbIo.digit(85);
      inRow[cp + 3] = dd & 0xff;
      dd = (dd >> 8) & 0xffffff;
      inRow[cp + 2] = dd & 0xff;
      dd >>= 8;
      inRow[cp + 1] = dd & 0xff;
      inRow[cp] = dd >> 8;
      if (--j == 0) {
        GbIo.newline();
        j = 15;
      }
      cp += 4;
    }
    inRow[cp + 1] = dd & 0xff;
    inRow[cp] = dd >> 8;
    GbIo.newline();
  }

  /**
   * {@code na_over_b(n,a,b)}: {@code (n*a)/b}, computed by bit-serial division when {@code n*a}
   * would overflow a signed 32-bit product (section 15/17 of {@code gb_lisa.w}, transcribed
   * verbatim). {@code n}, {@code a} and {@code b} are plain (signed) longs, as in the C.
   */
  private static long naOverB(long n, long a, long b) {
    long nmax = EL_GORDO / a;
    if (n <= nmax) {
      return (n * a) / b;
    }
    long aThresh = b - a;
    long bThresh = (b + 1) >> 1;
    long[] bit = new long[30];
    int k = 0;
    do {
      bit[k] = n & 1;
      n >>= 1;
      k++;
    } while (n > nmax);
    long r = n * a;
    long q = r / b;
    r = r - q * b;
    do {
      k--;
      q <<= 1;
      if (r < bThresh) {
        r <<= 1;
      } else {
        q++;
        long br = (b - r) << 1;
        r = b - br;
      }
      if (bit[k] != 0) {
        if (r < aThresh) {
          r += a;
        } else {
          q++;
          r -= aThresh;
        }
      }
    } while (k != 0);
    return q;
  }

  /**
   * {@code plane_lisa(m,n,d,m0,m1,n0,n1,d0,d1)}: the planar graph of the 4-connected regions of
   * equal value in {@code lisa(m,n,d,m0,m1,n0,n1,d0,d1)}, one vertex per region, an edge between
   * two regions that share a horizontal or vertical border. {@code m} and {@code n} are recovered
   * from {@link #lisaId} after the call to {@link #lisa} (as its clamped values, not necessarily
   * the arguments passed in here).
   *
   * <p>Vertex {@code name} is the region's 0-based creation order as a decimal string; {@code x.I}
   * is the region's matrix value, {@code y.I} the row-major index of its first pixel, {@code z.I}
   * the index of the last pixel visited in it. Returns {@code null} (propagating {@link #lisa}'s
   * panic) if {@code lisa} fails, or sets {@link Gb#panicCode} to {@link Gb#NO_ROOM} or {@link
   * Gb#ALLOC_FAULT} if the graph or its arcs cannot be allocated.
   */
  public static Graph planeLisa(
      long m, long n, long d, long m0, long m1, long n0, long n1, long d0, long d1) {
    long[] a = lisa(m, n, d, m0, m1, n0, n1, d0, d1);
    if (a == null) {
      return null;
    }
    long[] fields = parseLisaIdFields();
    m = fields[0];
    n = fields[1];

    // Section 28: merge 4-connected equal-valued cells via a union-find over the matrix, walked
    // bottom-to-top, right-to-left; apos is one row past the matrix at k == m and is never
    // dereferenced there.
    long[] f = new long[(int) n];
    long regs = 0;
    int apos = (int) (n * (m + 1) - 1);
    for (long k = m; k >= 0; k--) {
      for (long l = n - 1; l >= 0; l--, apos--) {
        if (k < m) {
          if (k > 0 && a[apos - (int) n] == a[apos]) {
            long j = l;
            while (f[(int) j] != j) {
              j = f[(int) j];
            }
            f[(int) j] = l;
            a[apos] = l;
          } else if (f[(int) l] == l) {
            a[apos] = -1 - a[apos];
            regs++;
          } else {
            a[apos] = f[(int) l];
          }
        }
        if (k > 0 && l < n - 1 && a[apos - (int) n] == a[apos - (int) n + 1]) {
          f[(int) (l + 1)] = l;
        }
        f[(int) l] = l;
      }
    }

    // Section 29: the graph itself.
    Graph newGraph = Gb.newGraph(regs);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id = "plane_" + lisaId;
    newGraph.utilTypes = "ZZZIIIZZIIZZZZ";
    newGraph.uu.I = m;
    newGraph.vv.I = n;

    // Section 30: walk the matrix again, creating a vertex per fresh region and joining adjacent
    // regions above and to the left.
    regs = 0;
    Vertex[] u = new Vertex[(int) n];
    apos = 0;
    long aloc = 0;
    outer:
    for (long k = 0; k < m; k++) {
      for (long l = 0; l < n; l++, apos++, aloc++) {
        Vertex w = u[(int) l];
        Vertex v;
        if (a[apos] < 0) {
          v = newGraph.vertices[(int) regs];
          v.name = Long.toString(regs);
          v.x.I = -a[apos] - 1;
          v.y.I = aloc;
          regs++;
        } else {
          v = u[(int) a[apos]];
        }
        u[(int) l] = v;
        v.z.I = aloc;
        if (Gb.troubleCode != 0) {
          break outer;
        }
        if (k > 0 && v != w) {
          adjac(v, w);
        }
        if (l > 0 && v != u[(int) l - 1]) {
          adjac(v, u[(int) l - 1]);
        }
      }
    }
    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /** {@code adjac(u,v)}: joins {@code u} and {@code v} with an edge, unless one already exists. */
  private static void adjac(Vertex u, Vertex v) {
    for (Arc arc = u.arcs; arc != null; arc = arc.next) {
      if (arc.tip == v) {
        return;
      }
    }
    Gb.newEdge(u, v, 1L);
  }

  /**
   * {@code bi_lisa(m,n,m0,m1,n0,n1,thresh,c)}: the bipartite graph on {@code m} row vertices
   * ({@code "r0"} .. {@code "rm-1"}) and {@code n} column vertices ({@code "c0"} .. {@code "cn-1"})
   * over {@code lisa(m,n,65535,m0,m1,n0,n1,0,0)}, joined by an edge whenever the corresponding
   * pixel is below {@code thresh} (when {@code c} is nonzero) or at or above it (when {@code c} is
   * zero). The edge carries the pixel value in {@code b.I} of both its arcs. {@code m}, {@code n},
   * {@code m0}, {@code m1}, {@code n0} and {@code n1} are recovered from {@link #lisaId} after the
   * call to {@link #lisa}. {@code c} is signed; every other parameter is unsigned.
   *
   * <p>Returns {@code null} (propagating {@link #lisa}'s panic) if {@code lisa} fails, or sets
   * {@link Gb#panicCode} to {@link Gb#NO_ROOM} or {@link Gb#ALLOC_FAULT} if the graph or its arcs
   * cannot be allocated. {@link Gb#markBipartite} records {@code m} in {@code uu.I}.
   */
  public static Graph biLisa(
      long m, long n, long m0, long m1, long n0, long n1, long thresh, long c) {
    long[] a = lisa(m, n, 65535L, m0, m1, n0, n1, 0L, 0L);
    if (a == null) {
      return null;
    }
    long[] fields = parseLisaIdFields();
    m = fields[0];
    n = fields[1];
    m0 = fields[3];
    m1 = fields[4];
    n0 = fields[5];
    n1 = fields[6];

    Graph newGraph = Gb.newGraph(m + n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "bi_lisa("
            + Long.toUnsignedString(m)
            + ","
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(m0)
            + ","
            + Long.toUnsignedString(m1)
            + ","
            + Long.toUnsignedString(n0)
            + ","
            + Long.toUnsignedString(n1)
            + ","
            + Long.toUnsignedString(thresh)
            + ","
            + (c != 0 ? '1' : '0')
            + ")";
    newGraph.utilTypes = newGraph.utilTypes.substring(0, 7) + "I" + newGraph.utilTypes.substring(8);
    Gb.markBipartite(newGraph, m);
    for (long k = 0; k < m; k++) {
      newGraph.vertices[(int) k].name = "r" + k;
    }
    for (long l = 0; l < n; l++) {
      newGraph.vertices[(int) (m + l)].name = "c" + l;
    }

    // Section 36: join every row to every column whose pixel passes the threshold.
    int apos = 0;
    for (long ui = 0; ui < m; ui++) {
      Vertex u = newGraph.vertices[(int) ui];
      for (long vi = m; vi < m + n; vi++, apos++) {
        Vertex v = newGraph.vertices[(int) vi];
        boolean take =
            c != 0
                ? Long.compareUnsigned(a[apos], thresh) < 0
                : Long.compareUnsigned(a[apos], thresh) >= 0;
        if (take) {
          Gb.newEdge(u, v, 1L);
          u.arcs.b.I = a[apos];
          v.arcs.b.I = a[apos];
        }
      }
    }
    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }

  /**
   * The C's {@code sscanf(lisa_id, "lisa(%lu,%lu,%lu,%lu,%lu,%lu,%lu,%lu,%lu)", ...)}: the nine
   * unsigned values embedded in {@link #lisaId}, in order.
   */
  private static long[] parseLisaIdFields() {
    long[] result = new long[9];
    String s = lisaId;
    int idx = s.indexOf('(') + 1;
    for (int i = 0; i < 9; i++) {
      int end = i == 8 ? s.indexOf(')', idx) : s.indexOf(',', idx);
      result[i] = Long.parseUnsignedLong(s.substring(idx, end));
      idx = end + 1;
    }
    return result;
  }
}
