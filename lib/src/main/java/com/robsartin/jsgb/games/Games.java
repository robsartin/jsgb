package com.robsartin.jsgb.games;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import com.robsartin.jsgb.sort.LinkSort;
import com.robsartin.jsgb.sort.Sortable;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code gb_games}: {@code games.dat}, the 1990 college football season. {@link #games}
 * builds a graph of {@code n} teams (the highest-weighted by a combination of their final AP and
 * UPI poll rankings), with an arc for each side of every game played between two selected teams on
 * a day in {@code [firstDay, lastDay]}.
 *
 * <p>Each pair of arcs for one game is created back to back by {@link Gb#newArc}, home first (never
 * by {@link Gb#newEdge}, so {@link Arc#mate} is left null); {@link Arc#len} carries the scoring
 * team's own score, {@code a.I} ({@code venue}) is {@link #HOME}, {@link #NEUTRAL} or {@link
 * #AWAY}, and {@code b.I} ({@code date}) is the day of the season the game was played.
 *
 * <p>Vertex slot {@code u.I} ({@code ap}) and {@code v.I} ({@code upi}) each pack a team's final
 * (high 16 bits) and pre-bowl (low 16 bits) poll rank, 0 if unranked; {@code x.S} ({@code abbr}) is
 * the data file's abbreviation, {@code y.S} ({@code nickname}) the mascot, and {@code z.S} ({@code
 * conference}) the conference name, or {@code null} for an independent.
 */
public final class Games {

  private static final int MAX_N = 120;
  private static final int MAX_DAY = 128;
  private static final int MAX_WEIGHT = 131072;
  private static final int HASH_PRIME = 1009;

  private static final long HOME = 1;
  private static final long NEUTRAL = 2;
  private static final long AWAY = 3;

  // Section 13: the largest a0/u0/a1/u1 count seen across the data file's history.
  private static final long MA0 = 1451;
  private static final long MU0 = 666;
  private static final long MA1 = 1475;
  private static final long MU1 = 847;

  private Games() {}

  /**
   * The C {@code node} struct: one team's data file entry, linked into a sort stack and a hash
   * chain.
   */
  private static final class Node implements Sortable {
    long key;
    Sortable link;
    String name;
    String nick;
    String abb;
    long a0;
    long u0;
    long a1;
    long u1;

    /** The stored conference name, or {@code null} for an independent. */
    String conf;

    Node hashLink;
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
   * {@code games(n,ap0Weight,upi0Weight,ap1Weight,upi1Weight,firstDay,lastDay,seed)}: a graph of
   * the {@code n} highest-weighted of the season's 120 teams, weight being {@code ap0Weight} times
   * the team's final AP rank plus {@code upi0Weight} times its final UPI rank plus {@code
   * ap1Weight}/{@code upi1Weight} times its pre-bowl AP/UPI ranks (each rank counted as 0 if the
   * team went unranked), joined by two arcs — one per team, home team's arc first — for every game
   * both teams played on a day in {@code [firstDay, lastDay]}.
   *
   * <p>{@code n} is unsigned; 0 or a value over 120 becomes 120. {@code firstDay} below 0 becomes
   * 0; {@code lastDay} 0 or over 128 becomes 128. Returns {@code null} and sets {@link
   * Gb#panicCode} if a weight exceeds 131072 in magnitude, {@code games.dat} cannot be read, or the
   * file is malformed.
   */
  public static Graph games(
      long n,
      long ap0Weight,
      long upi0Weight,
      long ap1Weight,
      long upi1Weight,
      long firstDay,
      long lastDay,
      long seed) {
    Flip.initRand(seed);

    // Section 9: default and validate the parameters.
    if (n == 0 || Long.compareUnsigned(n, MAX_N) > 0) {
      n = MAX_N;
    }
    if (ap0Weight > MAX_WEIGHT
        || ap0Weight < -MAX_WEIGHT
        || upi0Weight > MAX_WEIGHT
        || upi0Weight < -MAX_WEIGHT
        || ap1Weight > MAX_WEIGHT
        || ap1Weight < -MAX_WEIGHT
        || upi1Weight > MAX_WEIGHT
        || upi1Weight < -MAX_WEIGHT) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (firstDay < 0) {
      firstDay = 0;
    }
    if (lastDay == 0 || lastDay > MAX_DAY) {
      lastDay = MAX_DAY;
    }

    // Section 10: create the graph and its identification.
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "games("
            + Long.toUnsignedString(n)
            + ","
            + ap0Weight
            + ","
            + upi0Weight
            + ","
            + ap1Weight
            + ","
            + upi1Weight
            + ","
            + firstDay
            + ","
            + lastDay
            + ","
            + seed
            + ")";
    newGraph.utilTypes = "IIZSSSIIZZZZZZ";

    // Section 14: working storage and the data file.
    Node[] nodeBlock = new Node[MAX_N];
    Node[] hashBlock = new Node[HASH_PRIME];
    List<String> confBlock = new ArrayList<>();
    if (GbIo.open("games.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    for (int k = 0; k < MAX_N; k++) {
      // Section 15: one team's abbreviation, name, nickname, conference and poll counts.
      Node p = new Node();
      nodeBlock[k] = p;
      if (k > 0) {
        p.link = nodeBlock[k - 1];
      }
      p.abb = GbIo.string(' ');
      if (p.abb.length() > 5 || GbIo.ch() != ' ') {
        Gb.panicCode = Gb.SYNTAX_ERROR;
        Gb.troubleCode = 0;
        return null;
      }
      // Section 16: hash the abbreviation in.
      long h = 0;
      for (int i = 0; i < p.abb.length(); i++) {
        h = (h + h + p.abb.charAt(i)) % HASH_PRIME;
      }
      p.hashLink = hashBlock[(int) h];
      hashBlock[(int) h] = p;

      p.name = GbIo.string('(');
      if (p.name.length() > 23 || GbIo.ch() != '(') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 1;
        Gb.troubleCode = 0;
        return null;
      }
      p.nick = GbIo.string(')');
      if (p.nick.length() > 21 || GbIo.ch() != ')') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 2;
        Gb.troubleCode = 0;
        return null;
      }
      // Section 17: the conference, or independent (conf stays null).
      String conf = GbIo.string(';');
      if (GbIo.ch() != ';') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 3;
        Gb.troubleCode = 0;
        return null;
      }
      if (!conf.equals("Independent")) {
        int j = confBlock.indexOf(conf);
        if (j < 0) {
          confBlock.add(conf);
          j = confBlock.size() - 1;
        }
        p.conf = confBlock.get(j);
      }
      // Section 18: the AP/UPI counts and the sort key.
      p.a0 = GbIo.number(10);
      if (p.a0 > MA0 || GbIo.ch() != ',') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 4;
        Gb.troubleCode = 0;
        return null;
      }
      p.u0 = GbIo.number(10);
      if (p.u0 > MU0 || GbIo.ch() != ';') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 5;
        Gb.troubleCode = 0;
        return null;
      }
      p.a1 = GbIo.number(10);
      if (p.a1 > MA1 || GbIo.ch() != ',') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 6;
        Gb.troubleCode = 0;
        return null;
      }
      p.u1 = GbIo.number(10);
      if (p.u1 > MU1 || GbIo.ch() != '\n') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 7;
        Gb.troubleCode = 0;
        return null;
      }
      p.key =
          ap0Weight * p.a0 + upi0Weight * p.u0 + ap1Weight * p.a1 + upi1Weight * p.u1 + 0x40000000L;
      GbIo.newline();
    }

    // Section 19: rank by weight and assign the top n teams to vertices.
    LinkSort.linksort(nodeBlock[MAX_N - 1]);
    int vi = 0;
    for (int j = 127; j >= 0; j--) {
      for (Sortable sp = LinkSort.sorted[j]; sp != null; sp = sp.link()) {
        Node p = (Node) sp;
        if (vi < n) {
          // Section 20: fill in this team's vertex.
          Vertex v = newGraph.vertices[vi++];
          v.u.I = (p.a0 << 16) + p.a1;
          v.v.I = (p.u0 << 16) + p.u1;
          v.x.S(Gb.saveString(p.abb));
          v.y.S(Gb.saveString(p.nick));
          v.z.S(p.conf);
          v.name = Gb.saveString(p.name);
          p.vert = v;
        } else {
          p.abb = ""; // so team_lookup never matches a dropped team
        }
      }
    }

    // Section 21: the season's games.
    Vertex u;
    Vertex v;
    long today = 0;
    long su;
    long sv;
    long ven;
    while (!GbIo.eof()) {
      if (GbIo.ch() == '>') {
        // Section 22: a date line; execution falls through to the game line that follows.
        char c = GbIo.ch();
        long d =
            switch (c) {
              case 'A' -> -26;
              case 'S' -> 5;
              case 'O' -> 35;
              case 'N' -> 66;
              case 'D' -> 96;
              case 'J' -> 127;
              default -> 1000;
            };
        d += GbIo.number(10);
        if (d < 0 || d > MAX_DAY) {
          Gb.panicCode = Gb.SYNTAX_ERROR - 1;
          Gb.troubleCode = 0;
          return null;
        }
        today = d;
        GbIo.newline();
      } else {
        GbIo.backup();
      }
      u = teamLookup(hashBlock);
      su = GbIo.number(10);
      ven = GbIo.ch();
      if (ven == '@') {
        ven = HOME;
      } else if (ven == ',') {
        ven = NEUTRAL;
      } else {
        Gb.panicCode = Gb.SYNTAX_ERROR + 8;
        Gb.troubleCode = 0;
        return null;
      }
      v = teamLookup(hashBlock);
      sv = GbIo.number(10);
      if (GbIo.ch() != '\n') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 9;
        Gb.troubleCode = 0;
        return null;
      }
      if (u != null && v != null && today >= firstDay && today <= lastDay) {
        // Section 24: record the game as a pair of consecutive arcs.
        if (u.index > v.index) {
          Vertex w = u;
          u = v;
          v = w;
          long sw = su;
          su = sv;
          sv = sw;
          ven = HOME + AWAY - ven;
        }
        Gb.newArc(u, v, su);
        Gb.newArc(v, u, sv);
        Arc a = u.arcs;
        if (v.arcs.index != a.index + 1) {
          Gb.panicCode = Gb.IMPOSSIBLE + 9;
          Gb.troubleCode = 0;
          return null;
        }
        a.a.I = ven;
        v.arcs.a.I = HOME + AWAY - ven;
        a.b.I = today;
        v.arcs.b.I = today;
      }
      GbIo.newline();
    }

    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
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

  /**
   * {@code team_lookup()}: reads an abbreviation up to (but not including) the digit that starts
   * the following score, and returns the vertex of the team it names, or {@code null} if none of
   * the season's teams (or none kept in the graph) has that abbreviation.
   *
   * <p>{@link GbIo#digit} consumes the next byte and returns non-negative when it is a digit, but
   * returns -1 without consuming it otherwise; this loop relies on both halves of that contract,
   * reading and hashing one byte at a time until a digit is found, then backing up over the digit
   * that {@link GbIo#digit} already consumed so the caller can read the full score.
   */
  private static Vertex teamLookup(Node[] hashBlock) {
    StringBuilder sb = new StringBuilder();
    long h = 0;
    while (GbIo.digit(10) < 0) {
      char c = GbIo.ch();
      h = (h + h + c) % HASH_PRIME;
      sb.append(c);
    }
    GbIo.backup();
    String abb = sb.toString();
    for (Node p = hashBlock[(int) h]; p != null; p = p.hashLink) {
      if (p.abb.equals(abb)) {
        return p.vert;
      }
    }
    return null;
  }
}
