package com.robsartin.jsgb.miles;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import com.robsartin.jsgb.sort.LinkSort;
import com.robsartin.jsgb.sort.Sortable;

/**
 * Port of {@code gb_miles}: an undirected graph based on highway-mileage data between 128 North
 * American cities read from {@code miles.dat}, plus {@code miles_distance} for reading back the
 * retained distance matrix.
 *
 * <p>Each vertex corresponds to one of the 128 cities. A city's weight is {@code northWeight * (lat
 * - 2672) + westWeight * (lon - 7180) + popWeight * (pop - 2521)}; the {@code n} cities with the
 * largest weight (ties broken pseudo-randomly per {@code seed}) become the graph's vertices, sorted
 * into decreasing-weight order by {@link LinkSort#linksort}. Edges join every pair of vertices
 * whose distance is known, unless {@code maxDistance} or {@code maxDegree} prune them: a second
 * {@link LinkSort#linksort}, run once per surviving city over its own candidate neighbours, decides
 * — by walking {@link LinkSort#sorted}[0] — which of that city's shortest edges fall within {@code
 * maxDegree}.
 *
 * <p>Vertex slot {@code w.I} ({@code people} in the C) holds the city's 1980 population; {@code
 * x.I} ({@code xCoord}) and {@code y.I} ({@code yCoord}) hold coordinates derived from longitude
 * and latitude; {@code z.I} ({@code indexNo}) holds the city's index (0 to 127) in {@code
 * miles.dat}, reverse-alphabetical order.
 *
 * <p>The distance matrix and the 128 city records are kept in static state that outlives the call,
 * exactly as the C's file-scope {@code distance} and {@code node_block} do, so {@link
 * #milesDistance} can be called after {@link #miles} returns. Unlike the C, this state survives
 * even after other generators run, since nothing here frees it early.
 */
public final class Miles {

  /**
   * {@code MAX_N}: the number of cities in {@code miles.dat}, and the default/maximum for {@code
   * n}.
   */
  public static final int MAX_N = 128;

  private static final long MIN_LAT = 2672;
  private static final long MAX_LAT = 5042;
  private static final long MIN_LON = 7180;
  private static final long MAX_LON = 12312;
  private static final long MIN_POP = 2521;
  private static final long MAX_POP = 875538;

  /** The 128 city records read by the most recent {@link #miles} call, indexed by {@code kk}. */
  private static Node[] nodes = new Node[MAX_N];

  /** {@code distance}: the {@code MAX_N * MAX_N} distance matrix; see {@link #d}. */
  private static long[] distance = new long[MAX_N * MAX_N];

  private Miles() {}

  /**
   * The C {@code node} struct: a city record, sorted first by weight and then by candidate edge
   * length.
   */
  private static final class Node implements Sortable {
    long key;
    Sortable link;
    long kk;
    long lat;
    long lon;
    long pop;
    String name;

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
   * {@code d(j,k)}: the distance between the cities whose original indices are {@code j} and {@code
   * k}.
   */
  private static long d(int j, int k) {
    return distance[MAX_N * j + k];
  }

  private static void setD(int j, int k, long value) {
    distance[MAX_N * j + k] = value;
  }

  /**
   * {@code miles(n,north_weight,west_weight,pop_weight,max_distance,max_degree,seed)}: a graph of
   * the {@code min(n,MAX_N)} cities ({@code MAX_N} if {@code n} is 0) with the largest weight
   * {@code northWeight * lat + westWeight * lon + popWeight * pop}, with an edge between every pair
   * of selected cities whose distance is known, unless {@code maxDistance} excludes edges longer
   * than that many miles or {@code maxDegree} keeps only each city's shortest {@code maxDegree}
   * edges.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if any weight's magnitude is too large
   * ({@code |northWeight|, |westWeight| <= 100000}, {@code |popWeight| <= 100}), if {@code
   * miles.dat} cannot be opened or is malformed, or if graph allocation fails.
   */
  public static Graph miles(
      long n,
      long northWeight,
      long westWeight,
      long popWeight,
      long maxDistance,
      long maxDegree,
      long seed) {
    Flip.initRand(seed);

    // Section 7: check that the parameters are valid.
    if (n == 0 || n > MAX_N) {
      n = MAX_N;
    }
    if (maxDegree == 0 || maxDegree >= n) {
      maxDegree = n - 1;
    }
    if (northWeight > 100000
        || westWeight > 100000
        || popWeight > 100
        || northWeight < -100000
        || westWeight < -100000
        || popWeight < -100) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 8: set up a graph with n vertices.
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "miles("
            + Long.toUnsignedString(n)
            + ","
            + northWeight
            + ","
            + westWeight
            + ","
            + popWeight
            + ","
            + Long.toUnsignedString(maxDistance)
            + ","
            + Long.toUnsignedString(maxDegree)
            + ","
            + seed
            + ")";
    newGraph.utilTypes = "ZZIIIIZZZZZZZZ";

    // Sections 11-13: read miles.dat and compute city weights.
    nodes = new Node[MAX_N];
    for (int i = 0; i < MAX_N; i++) {
      nodes[i] = new Node();
    }
    distance = new long[MAX_N * MAX_N];

    if (GbIo.open("miles.dat") != 0) {
      Gb.panicCode = Gb.EARLY_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    for (int k = MAX_N - 1; k >= 0; k--) {
      Node p = nodes[k];
      p.kk = k;
      if (k > 0) {
        p.link = nodes[k - 1];
      }
      p.name = GbIo.string('[');
      if (GbIo.ch() != '[') {
        Gb.panicCode = Gb.SYNTAX_ERROR;
        Gb.troubleCode = 0;
        return null;
      }
      p.lat = GbIo.number(10);
      if (p.lat < MIN_LAT || p.lat > MAX_LAT || GbIo.ch() != ',') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 1;
        Gb.troubleCode = 0;
        return null;
      }
      p.lon = GbIo.number(10);
      if (p.lon < MIN_LON || p.lon > MAX_LON || GbIo.ch() != ']') {
        Gb.panicCode = Gb.SYNTAX_ERROR + 2;
        Gb.troubleCode = 0;
        return null;
      }
      p.pop = GbIo.number(10);
      if (p.pop < MIN_POP || p.pop > MAX_POP) {
        Gb.panicCode = Gb.SYNTAX_ERROR + 3;
        Gb.troubleCode = 0;
        return null;
      }
      p.key =
          northWeight * (p.lat - MIN_LAT)
              + westWeight * (p.lon - MIN_LON)
              + popWeight * (p.pop - MIN_POP)
              + 0x40000000L;
      for (int j = k + 1; j < MAX_N; j++) {
        if (GbIo.ch() != ' ') {
          GbIo.newline();
        }
        long dist = GbIo.number(10);
        setD(j, k, dist);
        setD(k, j, dist);
      }
      GbIo.newline();
    }
    if (GbIo.close() != 0) {
      Gb.panicCode = Gb.LATE_DATA_FAULT;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 15: determine the n cities to use in the graph.
    LinkSort.linksort(nodes[MAX_N - 1]);
    long vIndex = 0;
    for (int j = MAX_N - 1; j >= 0; j--) {
      for (Sortable s = LinkSort.sorted[j]; s != null; s = s.link()) {
        Node p = (Node) s;
        if (vIndex < n) {
          Vertex v = newGraph.vertices[(int) vIndex];
          v.x.I = MAX_LON - p.lon;
          v.y.I = p.lat - MIN_LAT;
          v.y.I += v.y.I >> 1;
          v.z.I = p.kk;
          v.w.I = p.pop;
          v.name = Gb.saveString(p.name);
          vIndex++;
        } else {
          p.pop = 0; // this city is not being used
        }
      }
    }

    // Sections 17-19: put the appropriate edges into the graph.
    if (maxDistance > 0 || maxDegree > 0) {
      pruneUnwantedEdges(maxDistance, maxDegree);
    }
    for (int ui = 0; ui < n; ui++) {
      Vertex u = newGraph.vertices[ui];
      long j = u.z.I;
      for (int vi = ui + 1; vi < n; vi++) {
        Vertex v = newGraph.vertices[vi];
        long k = v.z.I;
        if (d((int) j, (int) k) > 0 && d((int) k, (int) j) > 0) {
          Gb.newEdge(u, v, d((int) j, (int) k));
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
   * Section 18: makes the distance negative for every edge beyond {@code maxDistance} or, for a
   * given city, beyond its {@code maxDegree} shortest surviving edges.
   */
  private static void pruneUnwantedEdges(long maxDistance, long maxDegree) {
    if (maxDegree == 0) {
      maxDegree = MAX_N;
    }
    if (maxDistance == 0) {
      maxDistance = 30000;
    }
    for (Node p : nodes) {
      if (p.pop != 0) { // this city not deleted
        blankOutUndesiredEdges(p, p.kk, maxDistance, maxDegree);
      }
    }
  }

  /**
   * Section 19: reuses the node records' key and link fields (their {@code pop} field is left
   * alone) to sort city {@code k}'s candidate neighbours by complementary distance, then negates
   * the distance entries of any that fall beyond {@code maxDegree}.
   */
  private static void blankOutUndesiredEdges(Node p, long k, long maxDistance, long maxDegree) {
    Sortable s = null; // list of nodes containing edges from city k
    for (Node q : nodes) {
      if (q.pop != 0 && q != p) { // another city not deleted
        long dist = d((int) k, (int) q.kk); // distance from p to q
        if (dist > maxDistance) {
          setD((int) k, (int) q.kk, -dist);
        } else {
          q.key = maxDistance - dist;
          q.link = s;
          s = q;
        }
      }
    }
    LinkSort.linksort(s);
    // now all the surviving edges from p are in the list sorted[0]
    long count = 0; // counts how many edges have been accepted
    for (Sortable t = LinkSort.sorted[0]; t != null; t = t.link()) {
      Node q = (Node) t;
      count++;
      if (count > maxDegree) {
        setD((int) k, (int) q.kk, -d((int) k, (int) q.kk));
      }
    }
  }

  /**
   * {@code miles_distance(u,v)}: the distance, in miles, between {@code u} and {@code v} in the
   * most recently built {@link #miles} graph, reading the retained distance matrix by the cities'
   * {@code z.I} ({@code indexNo}) slots. May be negative if the edge was suppressed by {@code
   * maxDistance} or {@code maxDegree}; the two directions can even disagree, when {@code maxDegree}
   * suppressed the edge from one endpoint's side but not the other's.
   */
  public static long milesDistance(Vertex u, Vertex v) {
    return d((int) u.z.I, (int) v.z.I);
  }
}
