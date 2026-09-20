package com.robsartin.jsgb.plane;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.miles.Miles;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * Port of {@code gb_plane}: {@link #plane} and {@link #planeMiles} construct undirected planar
 * graphs, using {@link #delaunay}, a general-purpose Delaunay triangulation of a set of points, by
 * the incremental algorithm of Guibas, Knuth and Sharir ({@code Axioms and Hulls}, Lecture Notes in
 * Computer Science 606, Springer-Verlag, 1992).
 *
 * <p>{@link #plane} scatters {@code n} points uniformly in a rectangle; {@link #planeMiles} uses
 * the cities and coordinates of {@link Miles#miles}. Both triangulate their points, then discard
 * each Delaunay edge with probability {@code prob / 65536} and give each surviving edge a length:
 * the Euclidean distance (times 2^10, rounded) for {@code plane}, the highway mileage for {@code
 * planeMiles}. If {@code extend != 0}, an extra vertex named {@code "INF"}, the point at infinity,
 * is added, joined by edges of length {@link #INFTY} to every vertex on the convex hull.
 *
 * <p>{@link #delaunay} itself knows nothing of {@code plane} or {@code planeMiles}: given a graph
 * whose vertices carry coordinates in utility fields {@code x.I} ({@code x_coord}), {@code y.I}
 * ({@code y_coord}), and a unique tie-breaking ID in {@code z.I} ({@code z_coord}), it calls a
 * supplied callback once for every edge of the triangulation, with either endpoint {@code null}
 * standing for the point at infinity. Existing edges of the graph, if any, are ignored.
 *
 * <p>The triangulation's arcs live in one array, {@code pool}; as in the C, an arc's mate (the arc
 * running the other way along the same edge) is found by mirroring its index: {@code
 * pool[pool.length - 1 - a.idx]}, replacing the C's {@code min_arc + (max_arc - a)} pointer
 * arithmetic. The search structure that locates the triangle containing a new point is a dag of
 * branch nodes; a node is terminal (points at one arc of a triangle) when its {@code u} field is
 * {@code null}, and becomes an internal branch (a comparison against line {@code uv}) when {@code
 * u} and {@code v} are later filled in — mirroring the C's {@code terminal_node} macro, which
 * overlays the same field.
 */
public final class Plane {

  /**
   * {@code INFTY}: the length assigned to an edge joining a finite vertex to the point at infinity.
   */
  public static final long INFTY = 0x10000000L;

  /** {@code gprob}: copy of the {@code prob} parameter, read by the edge callbacks. */
  private static long gprob;

  /**
   * {@code inf_vertex}: the vertex $\infty$, or {@code null} if none, read by the edge callbacks.
   */
  private static Vertex infVertex;

  private Plane() {}

  /** The C {@code arc} struct: one directed half of a triangulation edge. */
  private static final class DArc {
    Vertex vert;
    DArc next;
    DNode inst;
    final int idx;

    DArc(int idx) {
      this.idx = idx;
    }
  }

  /**
   * The C {@code node} struct: a branch node of the triangle-location dag, or, when {@code u} is
   * {@code null}, a terminal node whose {@code arc} field (the C's overlaid {@code v}) points to
   * one arc of the triangle it represents.
   */
  private static final class DNode {
    Vertex u;
    Vertex v;
    DArc arc;
    DNode l;
    DNode r;
  }

  /** {@code mate(a,b)}: the other arc of {@code a}'s edge, found by mirroring its pool index. */
  private static DArc mate(DArc[] pool, DArc a) {
    return pool[pool.length - 1 - a.idx];
  }

  /**
   * {@code new_node(x)} plus {@code terminal_node(x,p)}: a fresh terminal node pointing at {@code
   * p}.
   */
  private static DNode terminalNode(DArc p) {
    DNode x = new DNode();
    x.arc = p;
    return x;
  }

  /**
   * {@code delaunay(g,f)}: calls {@code f.accept(u, v)} once for every edge {@code uv} of the
   * Delaunay triangulation of {@code g}'s vertices, with a {@code null} endpoint standing for the
   * point at infinity. {@code g}'s vertices must carry coordinates in {@code x.I} and {@code y.I},
   * nonnegative and less than 2^14, and a unique ID in {@code z.I}; existing edges of {@code g}, if
   * any, are ignored.
   */
  public static void delaunay(Graph g, BiConsumer<Vertex, Vertex> f) {
    // Section 34: no edges unless there are at least 2 vertices.
    if (g.n < 2) {
      return;
    }

    // Section 27: the array of 6n-6 arc records, indexed 0 to 6n-7; an arc's mate is at the
    // mirrored index. Section 26/30/32: the rest of delaunay's local variables.
    int poolSize = (int) (6 * g.n - 6);
    DArc[] pool = new DArc[poolSize];
    for (int i = 0; i < poolSize; i++) {
      pool[i] = new DArc(i);
    }
    int nextArc = 0; // the first unused index in pool

    DNode rootNode = new DNode();
    Vertex p;
    Vertex q;
    Vertex r;
    Vertex s;
    Vertex t;
    Vertex tp;
    Vertex tpp;
    Vertex u = g.vertices[0];
    Vertex v = g.vertices[1];
    DArc a;
    DArc aa;
    DArc b;
    DArc c;
    DArc d;
    DArc e;
    DNode x;
    DNode y;
    DNode yp;
    DNode ypp;

    // Section 33: make two "triangles" for u, v, and infinity.
    rootNode.u = u;
    rootNode.v = v;
    a = pool[nextArc];
    x = terminalNode(pool[a.idx + 1]);
    rootNode.l = x;
    a.vert = v;
    a.next = pool[a.idx + 1];
    a.inst = x;
    pool[a.idx + 1].next = pool[a.idx + 2];
    pool[a.idx + 1].inst = x;
    // pool[a.idx + 1].vert is left null, representing infinity.
    pool[a.idx + 2].vert = u;
    pool[a.idx + 2].next = a;
    pool[a.idx + 2].inst = x;
    b = mate(pool, a);
    x = terminalNode(pool[b.idx - 2]);
    rootNode.r = x;
    b.vert = u;
    b.next = pool[b.idx - 2];
    b.inst = x;
    pool[b.idx - 2].next = pool[b.idx - 1];
    pool[b.idx - 2].inst = x;
    // pool[b.idx - 2].vert is left null, representing infinity.
    pool[b.idx - 1].vert = v;
    pool[b.idx - 1].next = b;
    pool[b.idx - 1].inst = x;
    nextArc += 3;

    for (int pi = 2; pi < g.n; pi++) {
      p = g.vertices[pi];

      // Section 35: find an arc a on the boundary of the triangle containing p.
      x = rootNode;
      do {
        if (ccw(x.u, x.v, p)) {
          x = x.l;
        } else {
          x = x.r;
        }
      } while (x.u != null);
      a = x.arc;

      // Section 36: divide the triangle left of a into three triangles surrounding p.
      b = a.next;
      c = b.next;
      q = a.vert;
      r = b.vert;
      s = c.vert;

      // Section 37: create new terminal nodes y, yp, ypp, and new arcs pointing to them.
      yp = terminalNode(a);
      ypp = terminalNode(pool[nextArc]);
      y = terminalNode(c);
      c.inst = y;
      a.inst = yp;
      b.inst = ypp;
      e = mate(pool, pool[nextArc]);
      a.next = e;
      b.next = pool[e.idx - 1];
      c.next = pool[e.idx - 2];
      pool[nextArc].vert = q;
      pool[nextArc].next = b;
      pool[nextArc].inst = ypp;
      pool[nextArc + 1].vert = r;
      pool[nextArc + 1].next = c;
      pool[nextArc + 1].inst = y;
      pool[nextArc + 2].vert = s;
      pool[nextArc + 2].next = a;
      pool[nextArc + 2].inst = yp;
      e.vert = p;
      pool[e.idx - 1].vert = p;
      pool[e.idx - 2].vert = p;
      e.next = pool[nextArc + 2];
      pool[e.idx - 1].next = pool[nextArc];
      pool[e.idx - 2].next = pool[nextArc + 1];
      e.inst = yp;
      pool[e.idx - 1].inst = ypp;
      pool[e.idx - 2].inst = y;
      nextArc += 3;

      if (q == null) {
        // Section 38: compile instructions to update the convex hull.
        x.u = r;
        x.v = p;
        x.l = ypp;
        DNode xp = new DNode();
        xp.u = s;
        xp.v = p;
        xp.l = y;
        xp.r = yp;
        x.r = xp;
        aa = mate(pool, a);
        d = aa.next;
        t = d.vert;
        while (t != r && ccw(p, s, t)) {
          DNode xpp = terminalNode(d);
          xp.r = d.inst;
          xp = d.inst;
          xp.u = t;
          xp.v = p;
          xp.l = xpp;
          xp.r = yp;
          flip(a, aa, d, s, null, t, p, xpp, yp);
          a = aa.next;
          aa = mate(pool, a);
          d = aa.next;
          s = t;
          t = d.vert;
          yp.arc = a; // yp->v = (Vertex*) a;
        }
        xp = terminalNode(d.next);
        x = d.inst;
        x.u = s;
        x.v = p;
        x.l = xp;
        x.r = yp;
        d.inst = xp;
        d.next.inst = xp;
        d.next.next.inst = xp;
        r = s; // this value of r shortens the exploration step that follows
      } else {
        x.u = r;
        x.v = p;
        DNode xp = new DNode();
        xp.u = q;
        xp.v = p;
        xp.l = yp;
        xp.r = ypp;
        x.l = xp;
        xp = new DNode();
        xp.u = s;
        xp.v = p;
        xp.l = y;
        xp.r = yp;
        x.r = xp;
      }

      // Section 39: explore the triangles surrounding p, flipping neighbors until all triangles
      // that should touch p are found.
      while (true) {
        d = mate(pool, c);
        e = d.next;
        t = d.vert;
        tp = c.vert;
        tpp = e.vert;
        if (tpp != null && incircle(tpp, tp, t, p)) {
          DNode xp = terminalNode(e);
          DNode xpp = terminalNode(d);
          x = c.inst;
          x.u = tpp;
          x.v = p;
          x.l = xp;
          x.r = xpp;
          x = d.inst;
          x.u = tpp;
          x.v = p;
          x.l = xp;
          x.r = xpp;
          flip(c, d, e, t, tp, tpp, p, xp, xpp);
          c = e;
        } else if (tp == r) {
          break;
        } else {
          aa = mate(pool, c.next);
          c = aa.next;
        }
      }
    }

    // Section 28: call f(u,v) for each Delaunay edge uv.
    int maxIdx = poolSize - 1;
    for (int i = 0; i < nextArc; i++) {
      f.accept(pool[i].vert, pool[maxIdx - i].vert);
    }
  }

  /**
   * {@code flip(c,d,e,t,tp,tpp,p,xp,xpp)}: replaces triangles $tt'p$ and $t't tp''$ (to the left
   * and right of arc {@code c}) by $ptt''$ and $t''t'p$, corresponding to terminal nodes {@code xp}
   * and {@code xpp}. {@code t} and {@code tp} are unused, as in the C.
   */
  private static void flip(
      DArc c, DArc d, DArc e, Vertex t, Vertex tp, Vertex tpp, Vertex p, DNode xp, DNode xpp) {
    DArc ep = e.next;
    DArc cp = c.next;
    DArc cpp = cp.next;
    e.next = c;
    c.next = cpp;
    cpp.next = e;
    e.inst = xp;
    c.inst = xp;
    cpp.inst = xp;
    c.vert = p;
    d.next = ep;
    ep.next = cp;
    cp.next = d;
    d.inst = xpp;
    ep.inst = xpp;
    cp.inst = xpp;
    d.vert = tpp;
  }

  /**
   * {@code ccw(u,v,w)}: true if and only if {@code u}, {@code v}, {@code w} have a counterclockwise
   * orientation; ties (collinear points) are broken by ID number, then lexicographically by
   * coordinates.
   */
  private static boolean ccw(Vertex u, Vertex v, Vertex w) {
    long wx = w.x.I;
    long wy = w.y.I;
    long det = (u.x.I - wx) * (v.y.I - wy) - (u.y.I - wy) * (v.x.I - wx);
    if (det == 0) {
      det = 1;
      Vertex t;
      if (u.z.I > v.z.I) {
        t = u;
        u = v;
        v = t;
        det = -det;
      }
      if (v.z.I > w.z.I) {
        t = v;
        v = w;
        w = t;
        det = -det;
      }
      if (u.z.I > v.z.I) {
        t = u;
        u = v;
        v = t;
        det = -det;
      }
      if (u.x.I > v.x.I
          || (u.x.I == v.x.I
              && (u.y.I > v.y.I
                  || (u.y.I == v.y.I && (w.x.I > u.x.I || (w.x.I == u.x.I && w.y.I >= u.y.I)))))) {
        det = -det;
      }
    }
    return det > 0;
  }

  /**
   * {@code incircle(t,u,v,w)}: true if and only if {@code t} lies outside the circle through {@code
   * u}, {@code v}, {@code w}, assuming {@code ccw(u,v,w)} holds; ties (cocircular points) are
   * broken by sorting on ID number, then by the {@code ff}/{@code gg}/{@code hh}/{@code jj}
   * perturbation cascade.
   */
  private static boolean incircle(Vertex t, Vertex u, Vertex v, Vertex w) {
    long wx = w.x.I;
    long wy = w.y.I;
    long tx = t.x.I - wx;
    long ty = t.y.I - wy;
    long ux = u.x.I - wx;
    long uy = u.y.I - wy;
    long vx = v.x.I - wx;
    long vy = v.y.I - wy;
    long det =
        signTest(
            tx * uy - ty * ux,
            ux * vy - uy * vx,
            vx * ty - vy * tx,
            vx * vx + vy * vy,
            tx * tx + ty * ty,
            ux * ux + uy * uy);
    if (det == 0) {
      // Section 22: sort (t,u,v,w) by ID number.
      det = 1;
      Vertex s;
      if (t.z.I > u.z.I) {
        s = t;
        t = u;
        u = s;
        det = -det;
      }
      if (v.z.I > w.z.I) {
        s = v;
        v = w;
        w = s;
        det = -det;
      }
      if (t.z.I > v.z.I) {
        s = t;
        t = v;
        v = s;
        det = -det;
      }
      if (u.z.I > w.z.I) {
        s = u;
        u = w;
        w = s;
        det = -det;
      }
      if (u.z.I > v.z.I) {
        s = u;
        u = v;
        v = s;
        det = -det;
      }
      // Section 23: remove incircle degeneracy.
      if (degenerate(t, u, v, w)) {
        det = -det;
      }
    }
    return det > 0;
  }

  /**
   * Section 23's twelve-way cascade: the first nonzero of {@code ff(t,u,v,w)}, {@code gg(t,u,v,w)},
   * {@code ff(u,t,w,v)}, {@code gg(u,t,w,v)}, {@code ff(v,w,t,u)}, {@code gg(v,w,t,u)}, {@code
   * hh(t,u,v,w)}, {@code jj(t,u,v,w)}, {@code hh(v,t,u,w)}, {@code jj(v,t,u,w)}, {@code
   * jj(t,w,u,v)}, evaluated lazily in that order; {@code true} if it is negative, {@code false} if
   * it is positive or every term is zero.
   */
  private static boolean degenerate(Vertex t, Vertex u, Vertex v, Vertex w) {
    return firstNonzeroIsNegative(
        () -> ff(t, u, v, w),
        () -> gg(t, u, v, w),
        () -> ff(u, t, w, v),
        () -> gg(u, t, w, v),
        () -> ff(v, w, t, u),
        () -> gg(v, w, t, u),
        () -> hh(t, u, v, w),
        () -> jj(t, u, v, w),
        () -> hh(v, t, u, w),
        () -> jj(v, t, u, w),
        () -> jj(t, w, u, v));
  }

  private static boolean firstNonzeroIsNegative(LongSupplier... terms) {
    for (LongSupplier term : terms) {
      long dd = term.getAsLong();
      if (dd < 0) {
        return true;
      }
      if (dd != 0) {
        return false;
      }
    }
    return false;
  }

  private static long ff(Vertex t, Vertex u, Vertex v, Vertex w) {
    long wx = w.x.I;
    long wy = w.y.I;
    long tx = t.x.I - wx;
    long ty = t.y.I - wy;
    long ux = u.x.I - wx;
    long uy = u.y.I - wy;
    long vx = v.x.I - wx;
    long vy = v.y.I - wy;
    return signTest(
        ux - tx, vx - ux, tx - vx, vx * vx + vy * vy, tx * tx + ty * ty, ux * ux + uy * uy);
  }

  private static long gg(Vertex t, Vertex u, Vertex v, Vertex w) {
    long wx = w.x.I;
    long wy = w.y.I;
    long tx = t.x.I - wx;
    long ty = t.y.I - wy;
    long ux = u.x.I - wx;
    long uy = u.y.I - wy;
    long vx = v.x.I - wx;
    long vy = v.y.I - wy;
    return signTest(
        uy - ty, vy - uy, ty - vy, vx * vx + vy * vy, tx * tx + ty * ty, ux * ux + uy * uy);
  }

  private static long hh(Vertex t, Vertex u, Vertex v, Vertex w) {
    return (u.x.I - t.x.I) * (v.y.I - w.y.I);
  }

  private static long jj(Vertex t, Vertex u, Vertex v, Vertex w) {
    long vx = v.x.I;
    long wy = w.y.I;
    return (u.x.I - vx) * (u.x.I - vx)
        + (u.y.I - wy) * (u.y.I - wy)
        - (t.x.I - vx) * (t.x.I - vx)
        - (t.y.I - wy) * (t.y.I - wy);
  }

  /**
   * {@code sign_test(x1,x2,x3,y1,y2,y3)}: a value with the same sign as {@code x1*y1 + x2*y2 +
   * x3*y3}, computed exactly via a redundant base-2^14 representation, for {@code -2^29 < x1,x2,x3
   * < 2^29} and {@code 0 <= y1,y2,y3 < 2^29}.
   */
  private static long signTest(long x1, long x2, long x3, long y1, long y2, long y3) {
    long s1;
    long s2;
    long s3;
    long a;
    long b;
    long c;
    long t;

    // Section 16: determine the signs of the terms.
    if (x1 == 0 || y1 == 0) {
      s1 = 0;
    } else if (x1 > 0) {
      s1 = 1;
    } else {
      x1 = -x1;
      s1 = -1;
    }
    if (x2 == 0 || y2 == 0) {
      s2 = 0;
    } else if (x2 > 0) {
      s2 = 1;
    } else {
      x2 = -x2;
      s2 = -1;
    }
    if (x3 == 0 || y3 == 0) {
      s3 = 0;
    } else if (x3 > 0) {
      s3 = 1;
    } else {
      x3 = -x3;
      s3 = -1;
    }

    // Section 17: if the answer is obvious, return it; otherwise arrange things so that x3*y3 has
    // the opposite sign to x1*y1 + x2*y2.
    if ((s1 >= 0 && s2 >= 0 && s3 >= 0) || (s1 <= 0 && s2 <= 0 && s3 <= 0)) {
      return s1 + s2 + s3;
    }
    if (s3 == 0 || s3 == s1) {
      t = s3;
      s3 = s2;
      s2 = t;
      t = x3;
      x3 = x2;
      x2 = t;
      t = y3;
      y3 = y2;
      y2 = t;
    } else if (s3 == s2) {
      t = s3;
      s3 = s1;
      s1 = t;
      t = x3;
      x3 = x1;
      x1 = t;
      t = y3;
      y3 = y1;
      y1 = t;
    }

    // Section 18: compute a redundant representation 2^28*a + 2^14*b + c of x1*y1+x2*y2+x3*y3,
    // everything multiplied by -s3.
    long lx;
    long rx;
    long ly;
    long ry;
    lx = x1 / 0x4000;
    rx = x1 % 0x4000;
    ly = y1 / 0x4000;
    ry = y1 % 0x4000;
    a = lx * ly;
    b = lx * ry + ly * rx;
    c = rx * ry;
    lx = x2 / 0x4000;
    rx = x2 % 0x4000;
    ly = y2 / 0x4000;
    ry = y2 % 0x4000;
    a += lx * ly;
    b += lx * ry + ly * rx;
    c += rx * ry;
    lx = x3 / 0x4000;
    rx = x3 % 0x4000;
    ly = y3 / 0x4000;
    ry = y3 % 0x4000;
    a -= lx * ly;
    b -= lx * ry + ly * rx;
    c -= rx * ry;

    // Section 19: return the sign of the redundant representation. The C's "goto ez" becomes a
    // break out of this labeled block.
    ez:
    {
      if (a == 0) {
        break ez;
      }
      if (a < 0) {
        a = -a;
        b = -b;
        c = -c;
        s3 = -s3;
      }
      while (c < 0) {
        a--;
        c += 0x10000000;
        if (a == 0) {
          break ez;
        }
      }
      if (b >= 0) {
        return -s3;
      }
      b = -b;
      a -= b / 0x4000;
      if (a > 0) {
        return -s3;
      }
      if (a <= -2) {
        return s3;
      }
      return -s3 * ((a * 0x4000 - b % 0x4000) * 0x4000 + c);
    }
    if (b >= 0x8000) {
      return -s3;
    }
    if (b <= -0x8000) {
      return s3;
    }
    return -s3 * (b * 0x4000 + c);
  }

  /**
   * {@code int_sqrt(x)}: the nearest integer to 2^10 times the square root of the nonnegative
   * integer {@code x} (the bit-serial square root of {@code gb_flip.w}'s companion module).
   */
  private static long intSqrt(long x) {
    long y;
    long m;
    long q = 2;
    long k;
    if (x <= 0) {
      return 0;
    }
    for (k = 25, m = 0x20000000L; x < m; k--, m >>= 2) {
      // find the range
    }
    if (x >= m + m) {
      y = 1;
    } else {
      y = 0;
    }
    do {
      if ((x & m) != 0) {
        y += y + 1;
      } else {
        y += y;
      }
      m >>= 1;
      if ((x & m) != 0) {
        y += y - q + 1;
      } else {
        y += y - q;
      }
      q += q;
      if (y > q) {
        y -= q;
        q += 2;
      } else if (y <= 0) {
        q -= 2;
        y += q;
      }
      m >>= 1;
      k--;
    } while (k != 0);
    return q >> 1;
  }

  /**
   * {@code new_euclid_edge(u,v)}: the {@link #delaunay} callback used by {@link #plane}. Draws one
   * random number on every call, before testing {@code u} and {@code v}; rejects the edge with
   * probability {@link #gprob}{@code / 65536}, otherwise adds it with its Euclidean length, or, if
   * one endpoint is the point at infinity, with length {@link #INFTY} to {@link #infVertex}.
   */
  private static void newEuclidEdge(Vertex u, Vertex v) {
    if ((Flip.nextRand() >> 15) >= gprob) {
      if (u != null) {
        if (v != null) {
          long dx = u.x.I - v.x.I;
          long dy = u.y.I - v.y.I;
          Gb.newEdge(u, v, intSqrt(dx * dx + dy * dy));
        } else if (infVertex != null) {
          Gb.newEdge(u, infVertex, INFTY);
        }
      } else if (infVertex != null) {
        Gb.newEdge(infVertex, v, INFTY);
      }
    }
  }

  /**
   * {@code new_mile_edge(u,v)}: the {@link #delaunay} callback used by {@link #planeMiles}, as
   * {@link #newEuclidEdge} but with the mileage between {@code u} and {@code v} (negated by {@link
   * Miles#miles} back to positive) in place of the Euclidean length.
   */
  private static void newMileEdge(Vertex u, Vertex v) {
    if ((Flip.nextRand() >> 15) >= gprob) {
      if (u != null) {
        if (v != null) {
          Gb.newEdge(u, v, -Miles.milesDistance(u, v));
        } else if (infVertex != null) {
          Gb.newEdge(u, infVertex, INFTY);
        }
      } else if (infVertex != null) {
        Gb.newEdge(infVertex, v, INFTY);
      }
    }
  }

  /**
   * {@code plane(n,x_range,y_range,extend,prob,seed)}: a planar graph of {@code n} points with
   * integer coordinates uniformly distributed in {@code [0,x_range) x [0,y_range)} (a zero range
   * defaults to 16384; both must be at most 16384), triangulated by {@link #delaunay} and pruned by
   * {@code prob}. If {@code extend != 0}, the graph gets one extra vertex, {@code "INF"} at {@code
   * (-1,-1)}, joined to the convex hull by edges of length {@link #INFTY}.
   *
   * <p>Slots: {@code x.I}, {@code y.I} hold the coordinates; {@code z.I} holds a random ID number,
   * unique per vertex, used to break ties in the triangulation.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} if {@code x_range} or {@code y_range}
   * exceeds 16384, if {@code n < 2}, or if graph allocation fails.
   *
   * <p>As in the C, a failure that occurs after {@code extend != 0} has already incremented {@link
   * Gb#extraN} leaves {@link Gb#extraN} incremented; nothing on that path decrements it back.
   */
  public static Graph plane(long n, long xRange, long yRange, long extend, long prob, long seed) {
    Flip.initRand(seed);
    if (Long.compareUnsigned(xRange, 16384) > 0 || Long.compareUnsigned(yRange, 16384) > 0) {
      Gb.panicCode = Gb.BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (Long.compareUnsigned(n, 2) < 0) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (xRange == 0) {
      xRange = 16384;
    }
    if (yRange == 0) {
      yRange = 16384;
    }

    // Section 6: set up a graph with n uniformly distributed vertices.
    if (extend != 0) {
      Gb.extraN++;
    }
    Graph newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "plane("
            + Long.toUnsignedString(n)
            + ","
            + Long.toUnsignedString(xRange)
            + ","
            + Long.toUnsignedString(yRange)
            + ","
            + Long.toUnsignedString(extend)
            + ","
            + Long.toUnsignedString(prob)
            + ","
            + seed
            + ")";
    newGraph.utilTypes = "ZZZIIIZZZZZZZZ";
    for (long k = 0; k < n; k++) {
      Vertex vk = newGraph.vertices[(int) k];
      vk.x.I = Flip.unifRand(xRange);
      vk.y.I = Flip.unifRand(yRange);
      vk.z.I = (Flip.nextRand() / n) * n + k;
      vk.name = Gb.saveString(Long.toString(k));
    }
    if (extend != 0) {
      Vertex v = newGraph.vertices[(int) n];
      v.name = Gb.saveString("INF");
      v.x.I = -1;
      v.y.I = -1;
      v.z.I = -1;
      Gb.extraN--;
    }

    // Section 11: compute the Delaunay triangulation and add its edges.
    gprob = prob;
    infVertex = extend != 0 ? newGraph.vertices[(int) n] : null;
    delaunay(newGraph, Plane::newEuclidEdge);

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    if (extend != 0) {
      newGraph.n++;
    }
    return newGraph;
  }

  /**
   * {@code plane_miles(n,north_weight,west_weight,pop_weight,extend,prob,seed)}: a planar graph on
   * the {@code min(n,128)} cities of {@link Miles#miles}, triangulated by {@link #delaunay} and
   * pruned by {@code prob}, with each surviving edge's length equal to the highway mileage between
   * its cities. As with {@link #plane}, {@code extend != 0} adds a point at infinity, joined to the
   * convex hull by edges of length {@link #INFTY}.
   *
   * <p>Keeps {@link Miles#miles}'s slots: {@code w.I} the city's population, {@code x.I}/{@code
   * y.I} its coordinates, {@code z.I} its index into {@code miles.dat}.
   *
   * <p>Returns {@code null} and sets {@link Gb#panicCode} exactly when {@link Miles#miles} does.
   *
   * <p>As in the C, a failure that occurs after {@code extend != 0} has already incremented {@link
   * Gb#extraN} leaves {@link Gb#extraN} incremented; nothing on that path decrements it back.
   */
  public static Graph planeMiles(
      long n,
      long northWeight,
      long westWeight,
      long popWeight,
      long extend,
      long prob,
      long seed) {
    // Section 42: use miles to set up the vertices of a graph.
    if (extend != 0) {
      Gb.extraN++;
    }
    if (n == 0 || Long.compareUnsigned(n, Miles.MAX_N) > 0) {
      n = Miles.MAX_N;
    }
    Graph newGraph = Miles.miles(n, northWeight, westWeight, popWeight, 1L, 0L, seed);
    if (newGraph == null) {
      return null;
    }
    newGraph.id =
        "plane_miles("
            + Long.toUnsignedString(n)
            + ","
            + northWeight
            + ","
            + westWeight
            + ","
            + popWeight
            + ","
            + Long.toUnsignedString(extend)
            + ","
            + Long.toUnsignedString(prob)
            + ","
            + seed
            + ")";
    if (extend != 0) {
      Gb.extraN--;
    }

    // Section 43: compute the Delaunay triangulation and add its edges, with road-mile lengths.
    gprob = prob;
    if (extend != 0) {
      infVertex = newGraph.vertices[(int) newGraph.n];
      infVertex.name = Gb.saveString("INF");
      infVertex.x.I = -1;
      infVertex.y.I = -1;
      infVertex.z.I = -1;
    } else {
      infVertex = null;
    }
    delaunay(newGraph, Plane::newMileEdge);

    if (Gb.troubleCode != 0) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    // gb_free(new_graph->aux_data) has no Java equivalent.
    if (extend != 0) {
      newGraph.n++;
    }
    return newGraph;
  }
}
