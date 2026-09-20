package com.robsartin.jsgb.words;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import com.robsartin.jsgb.sort.LinkSort;
import com.robsartin.jsgb.sort.Sortable;
import java.util.function.Consumer;

/**
 * Port of {@code gb_words}: a graph whose vertices are the qualifying five-letter words of {@code
 * words.dat} and whose edges join two words that differ in exactly one letter position. For
 * example, {@code "words"} is adjacent to {@code "cords"}, {@code "wards"}, {@code "woods"}, {@code
 * "worms"} and {@code "wordy"}.
 *
 * <p>Each word in {@code words.dat} carries a common/advanced/unusual classification and seven
 * frequency counts from different corpora; {@link #words} turns those into a weight via {@code
 * wtVector} and keeps only words whose weight is at least {@code wtThreshold}. Vertex slot {@code
 * u.I} ({@code weight} in the C) holds the word's weight; arc slot {@code a.I} ({@code loc} in the
 * C) holds the position, 0 to 4, where the two endpoints' words differ.
 *
 * <p>{@link #words} leaves behind five hash tables, indexed by which letter position is suppressed,
 * that {@link #findWord} consults to locate a word or enumerate its neighbours. A second call to
 * {@link #words} replaces those tables, so {@link #findWord} always answers for the most recently
 * built graph.
 */
public final class Words {

  /**
   * {@code max_c}: the largest frequency count {@code C_j} actually present in {@code words.dat}.
   */
  private static final long[] MAX_C = {15194, 3560, 4467, 460, 6976, 756, 362};

  /** {@code default_wt_vector}: used when {@code words} is called with a {@code null} vector. */
  private static final long[] DEFAULT_WT_VECTOR = {100, 10, 4, 2, 2, 1, 1, 1, 1};

  /** {@code hash_prime}: a prime larger than the total number of words. */
  private static final int HASH_PRIME = 6997;

  /**
   * {@code nodes_per_block}: how many word-weight nodes the C allocates per block. The Java port
   * allocates each node individually, so this constant exists only to document where the C's number
   * came from.
   */
  private static final int NODES_PER_BLOCK = 111;

  /**
   * {@code htab}: five hash tables, one per suppressed letter position, left behind by the most
   * recent {@link #words} call for {@link #findWord} to consult.
   */
  private static Vertex[][] htab;

  private Words() {}

  /**
   * Clears the hash tables left behind by {@link #words}, as if it had never been called. Tests
   * only.
   */
  static void reset() {
    htab = null;
  }

  /** The C {@code node} struct: a qualifying word and its weight, linked into a sort stack. */
  private static final class Node implements Sortable {
    long key;
    Sortable link;
    String wd;

    @Override
    public long key() {
      return key;
    }

    @Override
    public Sortable link() {
      return link;
    }

    @Override
    public void setLink(Sortable next) {
      link = next;
    }
  }

  /** {@code flabs(x)}: {@code x} as a nonnegative {@code double}. */
  private static double flabs(long x) {
    return x >= 0 ? (double) x : -((double) x);
  }

  /** {@code iabs(x)}: {@code x}'s absolute value, computed in {@code long} arithmetic. */
  private static long iabs(long x) {
    return x >= 0 ? x : -x;
  }

  /**
   * {@code words(n,wt_vector,wt_threshold,seed)}: builds a graph of at most {@code n} five-letter
   * words (all qualifying words if {@code n = 0}), chosen by decreasing weight and, among ties,
   * pseudo-randomly per {@code seed}. A word's weight is {@code wtVector[0]} if it is common,
   * {@code wtVector[1]} if advanced, 0 if unusual, plus {@code c1*wtVector[2] + ... +
   * c7*wtVector[8]} for its seven frequency counts; {@code wtVector = null} selects the default
   * weights. Only words whose weight is at least {@code wtThreshold} qualify.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if {@code wtVector} is too large (see the
   * class Javadoc's slot list) or if {@code words.dat} cannot be read.
   */
  public static Graph words(long n, long[] wtVector, long wtThreshold, long seed) {
    Flip.initRand(seed);

    boolean usedDefault = wtVector == null;
    long[] wv = usedDefault ? DEFAULT_WT_VECTOR : wtVector;
    if (!usedDefault) {
      double flacc = Math.max(flabs(wv[0]), flabs(wv[1]));
      for (int i = 0; i < 7; i++) {
        flacc += MAX_C[i] * flabs(wv[i + 2]);
      }
      if (flacc >= (double) 0x60000000L) {
        Gb.panicCode = Gb.VERY_BAD_SPECS;
        Gb.troubleCode = 0;
        return null;
      }
      long acc = Math.max(iabs(wv[0]), iabs(wv[1]));
      for (int i = 0; i < 7; i++) {
        acc += MAX_C[i] * iabs(wv[i + 2]);
      }
      if (acc >= 0x40000000L) {
        Gb.panicCode = Gb.BAD_SPECS;
        Gb.troubleCode = 0;
        return null;
      }
    }

    if (GbIo.open("words.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    Node stackPtr = null;
    long nn = 0;
    do {
      char[] wordChars = new char[5];
      for (int j = 0; j < 5; j++) {
        wordChars[j] = GbIo.ch();
      }
      String word = new String(wordChars);
      long wt;
      switch (GbIo.ch()) {
        case '*':
          wt = wv[0];
          break;
        case '+':
          wt = wv[1];
          break;
        case ' ':
        case '\n':
          wt = 0;
          break;
        default:
          Gb.panicCode = Gb.SYNTAX_ERROR;
          Gb.troubleCode = 0;
          return null;
      }
      int i = 0;
      long c;
      do {
        if (i == 7) {
          Gb.panicCode = Gb.SYNTAX_ERROR + 1;
          Gb.troubleCode = 0;
          return null;
        }
        c = GbIo.number(10);
        if (c > MAX_C[i]) {
          Gb.panicCode = Gb.SYNTAX_ERROR + 2;
          Gb.troubleCode = 0;
          return null;
        }
        wt += c * wv[i + 2];
        i++;
      } while (GbIo.ch() == ',');
      if (wt >= wtThreshold) {
        Node node = new Node();
        node.key = wt + 0x40000000L;
        node.link = stackPtr;
        node.wd = word;
        stackPtr = node;
        nn++;
      }
      GbIo.newline();
    } while (!GbIo.eof());
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    LinkSort.linksort(stackPtr);
    if (n == 0 || Long.compareUnsigned(nn, n) < 0) {
      n = nn;
    }
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    if (usedDefault) {
      newGraph.id = "words(" + Long.toUnsignedString(n) + ",0," + wtThreshold + "," + seed + ")";
    } else {
      newGraph.id =
          "words("
              + Long.toUnsignedString(n)
              + ",{"
              + wv[0]
              + ","
              + wv[1]
              + ","
              + wv[2]
              + ","
              + wv[3]
              + ","
              + wv[4]
              + ","
              + wv[5]
              + ","
              + wv[6]
              + ","
              + wv[7]
              + ","
              + wv[8]
              + "},"
              + wtThreshold
              + ","
              + seed
              + ")";
    }
    newGraph.utilTypes = "IZZZZZIZZZZZZZ";
    htab = new Vertex[5][HASH_PRIME];
    if (Gb.troubleCode == 0 && n > 0) {
      long remaining = n;
      int idx = 0;
      outer:
      for (int j = 127; j >= 0; j--) {
        for (Sortable p = LinkSort.sorted[j]; p != null; p = p.link()) {
          Node node = (Node) p;
          Vertex cur = newGraph.vertices[idx++];
          cur.name = node.wd;
          cur.u.I = node.key - 0x40000000L;
          addEdges(cur);
          if (--remaining == 0) {
            break outer;
          }
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
   * {@code find_word(q,f)}: the vertex of the most recent {@link #words} graph named exactly {@code
   * q}, if there is one. Otherwise, if {@code f} is not {@code null}, calls {@code f} on every
   * vertex whose word matches {@code q} in all but one letter position (in table order 0 to 4, and
   * probe order within each table), then returns {@code null}.
   *
   * @throws IllegalArgumentException if {@code q} has fewer than five characters
   */
  public static Vertex findWord(String q, Consumer<Vertex> f) {
    if (htab == null) {
      throw new IllegalStateException("find_word needs a words graph: call words first");
    }
    if (q.length() < 5) {
      throw new IllegalArgumentException("find_word requires a five-letter word: \"" + q + "\"");
    }
    long raw = rawHash(q);
    int h0 = probeStart(raw, q, 0);
    while (htab[0][h0] != null) {
      Vertex r = htab[0][h0];
      if (r.name.charAt(0) == q.charAt(0) && matchExcept(q, r.name, 0)) {
        return r;
      }
      h0 = hdown(h0);
    }
    if (f != null) {
      for (int k = 0; k < 5; k++) {
        int h = probeStart(raw, q, k);
        while (htab[k][h] != null) {
          Vertex r = htab[k][h];
          if (matchExcept(q, r.name, k)) {
            f.accept(r);
          }
          h = hdown(h);
        }
      }
    }
    return null;
  }

  /**
   * Section 29: hashes {@code cur}'s name into all five tables, creating an edge (and recording
   * {@code loc}, in both arcs, per the class Javadoc) to every earlier word that differs from it in
   * exactly one position.
   */
  private static void addEdges(Vertex cur) {
    String q = cur.name;
    long raw = rawHash(q);
    for (int k = 0; k < 5; k++) {
      int h = probeStart(raw, q, k);
      while (htab[k][h] != null) {
        Vertex r = htab[k][h];
        if (matchExcept(q, r.name, k)) {
          Gb.newEdge(cur, r, 1L);
          cur.arcs.a.I = k;
          cur.arcs.mate.a.I = k;
        }
        h = hdown(h);
      }
      htab[k][h] = cur;
    }
  }

  /** {@code raw_hash}: the five-letter hash code of {@code w}, before remaindering. */
  private static long rawHash(String w) {
    long raw = w.charAt(0);
    for (int i = 1; i < 5; i++) {
      raw = (raw << 5) + w.charAt(i);
    }
    return raw;
  }

  /**
   * The initial probe address in table {@code k} for the hash of {@code w} with position {@code k}
   * suppressed.
   */
  private static int probeStart(long raw, String w, int k) {
    int shift = 5 * (4 - k);
    return (int) ((raw - ((long) w.charAt(k) << shift)) % HASH_PRIME);
  }

  /** {@code hdown(k)}: one step down in a hash table, wrapping from 0 to {@code hash_prime - 1}. */
  private static int hdown(int h) {
    return h == 0 ? HASH_PRIME - 1 : h - 1;
  }

  /** {@code match}: whether {@code a} and {@code b} agree in every position except {@code k}. */
  private static boolean matchExcept(String a, String b, int k) {
    for (int i = 0; i < 5; i++) {
      if (i != k && a.charAt(i) != b.charAt(i)) {
        return false;
      }
    }
    return true;
  }
}
