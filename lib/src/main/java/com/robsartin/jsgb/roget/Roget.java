package com.robsartin.jsgb.roget;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;

/**
 * Port of {@code gb_roget}: builds a directed graph whose vertices are (a subset of) the 1022
 * categories of the 1879 edition of Roget's Thesaurus, and whose arcs are the cross-references
 * Roget gave from one category to another, plus the implicit pairing of consecutively numbered
 * categories in his scheme.
 *
 * <p>{@link #roget} first chooses and randomly permutes {@code n} of the {@code MAX_N} categories
 * (all of them, if {@code n} is 0), consuming one {@link Flip#unifRand} draw per vertex, from the
 * last vertex of the graph down to the first. It then reads {@code roget.dat} once, category by
 * category; for each selected category it adds an arc to every other selected category listed on
 * its line, unless the two category numbers differ by less than {@code minDistance}, or unless
 * {@code prob} is nonzero and a {@link Flip#nextRand} draw — one per candidate arc that passes the
 * distance test — rejects it. Vertex slot {@code catNo} ({@code u.I}) holds the category's original
 * (pre-permutation) number.
 */
public final class Roget {

  /** {@code MAX_N}: the number of categories in Roget's book. */
  private static final int MAX_N = 1022;

  private Roget() {}

  /** {@code iabs(x)}: {@code x}'s absolute value, computed in {@code long} arithmetic. */
  private static long iabs(long x) {
    return Math.abs(x);
  }

  /**
   * {@code roget(n,min_distance,prob,seed)}: a graph of {@code min(n,MAX_N)} categories ({@code
   * MAX_N} if {@code n} is 0), with an arc {@code u -> v} wherever Roget cross-referenced (or
   * implicitly paired) {@code u}'s category to {@code v}'s, {@code v} is also selected, the two
   * category numbers differ by at least {@code minDistance}, and — when {@code prob} is nonzero — a
   * draw from {@code [0, 65536)} lands at or above {@code prob}.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if {@code roget.dat} cannot be opened or
   * is malformed.
   */
  public static Graph roget(long n, long minDistance, long prob, long seed) {
    Flip.initRand(seed);
    if (n == 0 || n > MAX_N) {
      n = MAX_N;
    }

    // Section 6: set up a graph with n vertices.
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "roget("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(minDistance)
            + ","
            + Long.toUnsignedString(prob)
            + ","
            + seed
            + ")";
    newGraph.utilTypes = "IZZZZZZZZZZZZZ";

    // Section 8: determine the n categories to use in the graph.
    Vertex[] mapping = new Vertex[MAX_N + 1];
    long[] cats = new long[MAX_N];
    for (int k = 0; k < MAX_N; k++) {
      cats[k] = k + 1;
      mapping[k + 1] = null;
    }
    long k = MAX_N;
    for (long vi = n - 1; vi >= 0; vi--) {
      Vertex v = newGraph.vertices[(int) vi];
      long j = Flip.unifRand(k);
      mapping[(int) cats[(int) j]] = v;
      k--;
      cats[(int) j] = cats[(int) k];
    }

    // Section 10: input roget.dat and build the graph.
    if (GbIo.open("roget.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    long cat;
    for (cat = 1; !GbIo.eof(); cat++) {
      if (mapping[(int) cat] != null) {
        // Section 12: read the data for a selected category.
        if (GbIo.number(10) != cat) {
          Gb.panicCode = Gb.SYNTAX_ERROR;
          Gb.troubleCode = 0;
          return null;
        }
        String name = GbIo.string(':');
        if (GbIo.ch() != ':') {
          Gb.panicCode = Gb.SYNTAX_ERROR + 1;
          Gb.troubleCode = 0;
          return null;
        }
        Vertex v = mapping[(int) cat];
        v.name = Gb.saveString(name);
        v.u.I = cat;

        // Section 13: add arcs from v for every category that's both listed and selected.
        long j = GbIo.number(10);
        if (j != 0) {
          arcs:
          while (true) {
            if (j > MAX_N) {
              Gb.panicCode = Gb.SYNTAX_ERROR + 2;
              Gb.troubleCode = 0;
              return null;
            }
            if (mapping[(int) j] != null
                && iabs(j - cat) >= minDistance
                && (prob == 0 || (Flip.nextRand() >> 15) >= prob)) {
              Gb.newArc(v, mapping[(int) j], 1L);
            }
            char c = GbIo.ch();
            switch (c) {
              case '\\':
                GbIo.newline();
                if (GbIo.ch() != ' ') {
                  Gb.panicCode = Gb.SYNTAX_ERROR + 3;
                  Gb.troubleCode = 0;
                  return null;
                }
              // falls through: a continuation line resumes just like a plain space
              case ' ':
                j = GbIo.number(10);
                break;
              case '\n':
                break arcs;
              default:
                Gb.panicCode = Gb.SYNTAX_ERROR + 4;
                Gb.troubleCode = 0;
                return null;
            }
          }
        }
        GbIo.newline();
      } else {
        // Section 14: skip past the data for one unselected category.
        String s = GbIo.string('\n');
        if (!s.isEmpty() && s.charAt(s.length() - 1) == '\\') {
          GbIo.newline();
        }
        GbIo.newline();
      }
    }
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    if (cat != MAX_N + 1) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return newGraph;
  }
}
