package com.robsartin.jsgb.books;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import com.robsartin.jsgb.sort.LinkSort;
import com.robsartin.jsgb.sort.Sortable;

/**
 * Port of {@code gb_books}: character-encounter graphs of five novels ({@code anna.dat}, {@code
 * david.dat}, {@code homer.dat}, {@code huck.dat} and {@code jean.dat}, one chosen by {@code
 * title}). {@link #book} joins two characters with an edge for every chapter (within {@code
 * [firstChapter, lastChapter]}) in which a scene lists them together; {@link #biBook} instead adds
 * one vertex per selected chapter and joins each character to every chapter vertex for a chapter
 * that mentions them.
 *
 * <p>A character's weight is {@code inWeight} times the number of selected chapters they appear in
 * plus {@code outWeight} times the number of chapters outside the selection they appear in; the
 * {@code n} highest-weighted characters are kept (ties broken pseudo-randomly by {@code seed}), and
 * of those the {@code x} highest-weighted are then dropped, so the graph holds characters {@code
 * x+1} through {@code n} by weight rank.
 *
 * <p>Vertex slot {@code u.I} ({@code shortCode} in the C) holds the two-letter code used in the
 * data file; {@code x.I} ({@code outCount}) and {@code y.I} ({@code inCount}) hold the out-of-range
 * and in-range chapter-appearance counts; {@code z.S} ({@code desc}) holds the character's
 * description. Arc slot {@code a.I} ({@code chapNo}) holds, for a {@link #book} edge, the chapter
 * in which the pair first appeared together. {@link #chapters} and {@link #chapName} record how
 * many chapters the chosen book has and, for each selected chapter, its name (as given in the data
 * file — sometimes a plain number, sometimes a hierarchical label such as Les Mis&eacute;rables'
 * {@code "1.1.3"}).
 */
public final class Books {

  private static final int MAX_CHAPS = 360;
  private static final int MAX_CHARS = 600;
  private static final int MAX_CODE = 1296;
  private static final int MAX_CLIQUE = 30;

  /** {@code chapters}: the number of chapters in the book most recently read. */
  public static long chapters;

  /**
   * {@code chap_name}: the name of chapter {@code k} of the book most recently read, for {@code k}
   * in the selected range; element 0 is always {@code ""}, as in the C's static initialisation.
   */
  public static final String[] chapName = new String[MAX_CHAPS];

  static {
    chapName[0] = "";
  }

  private Books() {}

  /** The C {@code node} struct: one character's data file entry, linked into a sort stack. */
  private static final class Node implements Sortable {
    long key;
    Sortable link;
    long in;
    long out;
    long chap;
    Vertex vert;

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

  /**
   * {@code book(title,n,x,firstChapter,lastChapter,inWeight,outWeight,seed)}: a graph of the {@code
   * n-x} highest-weighted characters of the novel named {@code title}, joined by an edge for every
   * chapter in {@code [firstChapter, lastChapter]} in which a scene mentions both.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if a weight exceeds a million in
   * magnitude or {@code title.dat} cannot be read or is malformed.
   */
  public static Graph book(
      String title,
      long n,
      long x,
      long firstChapter,
      long lastChapter,
      long inWeight,
      long outWeight,
      long seed) {
    return bgraph(false, title, n, x, firstChapter, lastChapter, inWeight, outWeight, seed);
  }

  /**
   * {@code bi_book(title,n,x,firstChapter,lastChapter,inWeight,outWeight,seed)}: like {@link
   * #book}, but bipartite — one vertex is added per selected chapter, and a character is joined to
   * a chapter vertex whenever a scene in that chapter mentions them (characters are never joined to
   * each other). {@link Gb#markBipartite} records the character-part size in {@code uu.I}.
   */
  public static Graph biBook(
      String title,
      long n,
      long x,
      long firstChapter,
      long lastChapter,
      long inWeight,
      long outWeight,
      long seed) {
    return bgraph(true, title, n, x, firstChapter, lastChapter, inWeight, outWeight, seed);
  }

  private static Graph bgraph(
      boolean bipartite,
      String title,
      long n,
      long x,
      long firstChapter,
      long lastChapter,
      long inWeight,
      long outWeight,
      long seed) {
    Flip.initRand(seed);

    // Section 10: default and validate the parameters, then open the data file.
    if (n == 0) {
      n = MAX_CHARS;
    }
    if (firstChapter == 0) {
      firstChapter = 1;
    }
    if (lastChapter == 0) {
      lastChapter = MAX_CHAPS;
    }
    if (inWeight > 1000000 || inWeight < -1000000 || outWeight > 1000000 || outWeight < -1000000) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    String fileName = Gb.prefix(title, 6) + ".dat";
    if (GbIo.open(fileName) != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 16: first pass, the character list.
    Node[] nodeBlock = new Node[MAX_CHARS];
    Node[] xnode = new Node[MAX_CODE];
    long characters;
    {
      int idx = 0;
      Node prev = null;
      long c;
      while ((c = GbIo.number(36)) != 0) {
        if (c >= MAX_CODE || GbIo.ch() != ' ') {
          Gb.panicCode = Gb.SYNTAX_ERROR;
          Gb.troubleCode = 0;
          return null;
        }
        if (idx >= MAX_CHARS) {
          Gb.panicCode = Gb.SYNTAX_ERROR + 1;
          Gb.troubleCode = 0;
          return null;
        }
        Node node = new Node();
        node.link = prev;
        xnode[(int) c] = node;
        node.in = 0;
        node.out = 0;
        node.chap = 0;
        node.vert = null;
        nodeBlock[idx++] = node;
        prev = node;
        GbIo.newline();
      }
      characters = idx;
      GbIo.newline();
    }

    // Section 19: second pass, tallying in-range and out-of-range chapter appearances.
    long k;
    for (k = 1; k < MAX_CHAPS && !GbIo.eof(); k++) {
      String s = GbIo.string(':');
      if (s.startsWith("&")) {
        k--;
      }
      while (GbIo.ch() != '\n') {
        long c = GbIo.number(36);
        if (c >= MAX_CODE) {
          Gb.panicCode = Gb.SYNTAX_ERROR + 4;
          Gb.troubleCode = 0;
          return null;
        }
        Node p = xnode[(int) c];
        if (p == null) {
          Gb.panicCode = Gb.SYNTAX_ERROR + 5;
          Gb.troubleCode = 0;
          return null;
        }
        if (p.chap != k) {
          p.chap = k;
          if (Long.compareUnsigned(k, firstChapter) >= 0
              && Long.compareUnsigned(k, lastChapter) <= 0) {
            p.in++;
          } else {
            p.out++;
          }
        }
      }
      GbIo.newline();
    }
    if (k == MAX_CHAPS) {
      Gb.panicCode = Gb.SYNTAX_ERROR + 6;
      Gb.troubleCode = 0;
      return null;
    }
    chapters = k - 1;
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 27: clamp the counts and create the graph.
    if (Long.compareUnsigned(n, characters) > 0) {
      n = characters;
    }
    if (Long.compareUnsigned(x, n) > 0) {
      x = n;
    }
    if (Long.compareUnsigned(lastChapter, chapters) > 0) {
      lastChapter = chapters;
    }
    if (Long.compareUnsigned(firstChapter, lastChapter) > 0) {
      firstChapter = lastChapter + 1;
    }
    Graph newGraph = Gb.newGraph(n - x + (bipartite ? lastChapter - firstChapter + 1 : 0));
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.utilTypes = "IZZIISIZZZZZZZ";
    newGraph.id =
        (bipartite ? "bi_" : "")
            + "book(\""
            + title
            + "\","
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(x)
            + ","
            + Long.toUnsignedString(firstChapter)
            + ","
            + Long.toUnsignedString(lastChapter)
            + ","
            + inWeight
            + ","
            + outWeight
            + ","
            + seed
            + ")";
    long chapBase = 0;
    if (bipartite) {
      Gb.markBipartite(newGraph, n - x);
      chapBase = (n - x) - firstChapter;
    }

    // Section 28: rank characters by weight and assign the kept ones to vertices.
    for (int i = 0; i < characters; i++) {
      Node p = nodeBlock[i];
      p.key = inWeight * p.in + outWeight * p.out + 0x40000000L;
    }
    LinkSort.linksort(nodeBlock[(int) characters - 1]);
    long remaining = n;
    int nextVertex = 0;
    outer:
    for (int j = 127; j >= 0; j--) {
      for (Sortable sp = LinkSort.sorted[j]; sp != null; sp = sp.link()) {
        Node p = (Node) sp;
        if (x > 0) {
          x--;
        } else {
          p.vert = newGraph.vertices[nextVertex++];
        }
        if (--remaining == 0) {
          break outer;
        }
      }
    }

    // Section 29/17: reopen the file and read names and descriptions of the kept characters.
    if (GbIo.open(fileName) != 0) {
      Gb.panicCode = Gb.IMPOSSIBLE + 1;
      Gb.troubleCode = 0;
      return null;
    }
    {
      long c;
      while ((c = GbIo.number(36)) != 0) {
        Node node = xnode[(int) c];
        Vertex v = node.vert;
        if (v != null) {
          if (GbIo.ch() != ' ') {
            Gb.panicCode = Gb.IMPOSSIBLE;
            Gb.troubleCode = 0;
            return null;
          }
          v.name = Gb.saveString(GbIo.string(','));
          if (GbIo.ch() != ',') {
            Gb.panicCode = Gb.SYNTAX_ERROR + 2;
            Gb.troubleCode = 0;
            return null;
          }
          if (GbIo.ch() != ' ') {
            Gb.panicCode = Gb.SYNTAX_ERROR + 3;
            Gb.troubleCode = 0;
            return null;
          }
          v.z.S(Gb.saveString(GbIo.string('\n')));
          v.y.I = node.in;
          v.x.I = node.out;
          v.u.I = c;
        }
        GbIo.newline();
      }
      GbIo.newline();
    }

    if (bipartite) {
      // Section 20: one vertex per selected chapter, joined to every character it mentions.
      for (int i = 0; i < characters; i++) {
        nodeBlock[i].chap = 0;
      }
      for (k = 1; !GbIo.eof(); k++) {
        String s = GbIo.string(':');
        boolean amp = s.startsWith("&");
        if (amp) {
          k--;
        } else {
          if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
          }
          chapName[(int) k] = Gb.saveString(s);
        }
        if (Long.compareUnsigned(k, firstChapter) >= 0
            && Long.compareUnsigned(k, lastChapter) <= 0) {
          Vertex u = newGraph.vertices[(int) (chapBase + k)];
          if (!amp) {
            u.name = chapName[(int) k];
            u.z.S("");
            u.y.I = 0;
            u.x.I = 0;
          }
          while (GbIo.ch() != '\n') {
            long c = GbIo.number(36);
            Node p = xnode[(int) c];
            if (p.chap != k) {
              p.chap = k;
              Vertex v = p.vert;
              if (v != null) {
                Gb.newEdge(v, u, 1L);
                u.y.I++;
              } else {
                u.x.I++;
              }
            }
          }
        }
        GbIo.newline();
      }
    } else {
      // Section 22: every scene is a clique of characters, connected pairwise.
      for (k = 1; !GbIo.eof(); k++) {
        String s = GbIo.string(':');
        if (s.startsWith("&")) {
          k--;
        } else {
          if (s.endsWith("\n")) {
            s = s.substring(0, s.length() - 1);
          }
          chapName[(int) k] = Gb.saveString(s);
        }
        if (Long.compareUnsigned(k, firstChapter) >= 0
            && Long.compareUnsigned(k, lastChapter) <= 0) {
          char c = GbIo.ch();
          while (c != '\n') {
            Vertex[] clique = new Vertex[MAX_CLIQUE];
            int cliqueSize = 0;
            do {
              long code = GbIo.number(36);
              Vertex v = xnode[(int) code].vert;
              if (v != null) {
                if (cliqueSize == MAX_CLIQUE) {
                  throw new IllegalStateException(
                      "clique_table overflow: chapter "
                          + k
                          + " lists more than "
                          + MAX_CLIQUE
                          + " characters together");
                }
                clique[cliqueSize++] = v;
              }
              c = GbIo.ch();
            } while (c == ',');
            for (int qi = 0; qi + 1 < cliqueSize; qi++) {
              for (int ri = qi + 1; ri < cliqueSize; ri++) {
                // Section 25: connect this pair, unless they are already joined.
                Vertex u = clique[qi];
                Vertex v = clique[ri];
                boolean found = false;
                for (Arc a = u.arcs; a != null; a = a.next) {
                  if (a.tip == v) {
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  Gb.newEdge(u, v, 1L);
                  Arc a = u.index < v.index ? u.arcs : v.arcs;
                  a.a.I = k;
                  a.mate.a.I = k;
                }
              }
            }
          }
        }
        GbIo.newline();
      }
    }
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.IMPOSSIBLE + 2;
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
