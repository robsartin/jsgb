package com.robsartin.jsgb.demo;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.miles.Miles;
import com.robsartin.jsgb.save.Save;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

/**
 * Port of {@code miles_span}: builds (or restores) a graph of highway mileages, then finds its
 * minimum spanning tree four different ways — Kruskal's algorithm with a radix-sort bucket queue,
 * Jarnik/Prim with a binary heap, the same with a Fibonacci heap, and Cheriton/Tarjan/Karp with
 * binomial-queue fragments — printing each algorithm's {@code mems} (memory reference) count.
 *
 * <p>Slots (the C {@code #define}s): {@code from} = {@code a.V}, {@code klink} = {@code b.A},
 * {@code clink} = {@code z.V}, {@code comp} = {@code y.V}, {@code csize}/{@code findex} = {@code
 * x.I}, {@code dist} = {@code z.I}, {@code backlink} = {@code y.V}, {@code KNOWN} = {@link Gb#ONE},
 * {@code heap_elt(i)} = {@code gv[i].u.V()} (the vertex array doubles as the binary heap), {@code
 * heap_index} = {@code v.I}, {@code newarc}/{@code pq} = {@code u.A} (a per-vertex scratch arc from
 * {@link Gb#allocAuxArcs}), {@code parent} = {@code newarc.tip}, {@code child} = {@code
 * newarc.a.V}, {@code lsib} = {@code v.V}, {@code rsib} = {@code w.V}, {@code rank_tag} = {@code
 * x.I}, {@code qchild} = {@code a.A}, {@code qsib} = {@code b.A}, {@code qcount} = {@code a.I},
 * {@code matx(j,k)} = {@code gv[j*lo_sqrt+k].z.I}, {@code matx_arc(j,k)} = {@code
 * gv[j*lo_sqrt+k].v.A}. The C overlays several of these on the same union members in different
 * phases of the program; this port keeps the same overlaps rather than giving each a distinct
 * field, since they are never live at the same time.
 */
public final class MilesSpan {

  private MilesSpan() {}

  /** The C's {@code (unsigned long) -1}: no spanning tree exists (the graph is disconnected). */
  private static final long INFINITY = -1L;

  private static final long INF = 30000;

  // ---- state shared by the priority-queue variants and cher_tar_kar (C file-scope statics) ----
  private static Graph currentGraph;
  private static Vertex[] gv;
  private static long hsize;
  private static Vertex fHeap;
  private static final Vertex[] newRoots = new Vertex[46];
  private static final Arc[] aucket = new Arc[64];
  private static final Arc[] bucket = new Arc[64];
  private static long loSqrt;
  private static long hiSqrt;
  private static long kk;
  private static final long[] distance = new long[100];
  private static final Arc[] distArc = new Arc[100];

  /** Entry point: runs with the real process streams and exits with the C's return value. */
  public static void main(String[] args) {
    System.exit(
        Jsgb.exitStatus(
            run(args, new CStdin(System.in), Jsgb.stdout(), Jsgb.stderr(), Path.of(""))));
  }

  /** Runs the program: parses arguments, then repeatedly builds a graph and spans it 4 ways. */
  public static int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir) {
    Gb.verbose = 0;
    Mems.mems = 0;
    resetStatics();

    long n = 100;
    long nWeight = 0;
    long wWeight = 0;
    long pWeight = 0;
    long d = 10;
    long s = 0;
    long r = 1;
    String fileName = null;

    for (int i = args.length - 1; i >= 0; i--) {
      String arg = args[i];
      Long value;
      if ((value = Scan.scan(arg, "-n")) != null) {
        n = value;
      } else if ((value = Scan.scan(arg, "-N")) != null) {
        nWeight = value;
      } else if ((value = Scan.scan(arg, "-W")) != null) {
        wWeight = value;
      } else if ((value = Scan.scan(arg, "-P")) != null) {
        pWeight = value;
      } else if ((value = Scan.scan(arg, "-d")) != null) {
        d = value;
      } else if ((value = Scan.scan(arg, "-r")) != null) {
        r = value;
      } else if ((value = Scan.scan(arg, "-s")) != null) {
        s = value;
      } else if (arg.equals("-v")) {
        Gb.verbose = 1;
      } else if (arg.startsWith("-g")) {
        fileName = arg.substring(2);
      } else {
        err.print("Usage: miles_span [-nN][-dN][-rN][-sN][-NN][-WN][-PN][-v][-gfoo]\n");
        return -2;
      }
    }
    if (fileName != null) {
      r = 1;
    }

    while (r-- != 0) {
      Graph g =
          fileName != null
              ? Save.restoreGraph(workDir.resolve(fileName).toString())
              : Miles.miles(n, nWeight, wWeight, pWeight, 0L, d, s);
      if (g == null || g.n <= 1) {
        err.print(
            String.format(
                Locale.ROOT, "Sorry, can't create the graph! (error code %d)\n", Gb.panicCode));
        return -1;
      }
      int bugReturn = runOneGraph(g, out);
      if (bugReturn != Integer.MIN_VALUE) {
        return bugReturn;
      }
      Gb.recycle(g);
      s++;
    }
    return 0;
  }

  private static void resetStatics() {
    currentGraph = null;
    gv = null;
    hsize = 0;
    fHeap = null;
    java.util.Arrays.fill(newRoots, null);
    java.util.Arrays.fill(aucket, null);
    java.util.Arrays.fill(bucket, null);
    loSqrt = 0;
    hiSqrt = 0;
    kk = 0;
    java.util.Arrays.fill(distance, 0);
    java.util.Arrays.fill(distArc, null);
  }

  /**
   * Section 5: runs all four algorithms on one graph. Returns {@link Integer#MIN_VALUE} to continue
   * the {@code while (r--)} loop, or one of the C's bug-detected return values.
   */
  private static int runOneGraph(Graph g, PrintStream out) {
    currentGraph = g;
    out.print(String.format(Locale.ROOT, "The graph %s has %d edges,\n", g.id, g.m / 2));
    long spLength = krusk(g, out);
    if (spLength == INFINITY) {
      out.print("  and it isn't connected.\n");
    } else {
      out.print(
          String.format(Locale.ROOT, "  and its minimum spanning tree has length %d.\n", spLength));
    }
    out.print(
        String.format(
            Locale.ROOT, " The Kruskal/radix-sort algorithm takes %d mems;\n", Mems.mems));

    if (spLength != jarPr(g, BINARY_HEAP, out)) {
      out.print(" ...oops, I've got a bug, please fix fix fix\n");
      return -4;
    }
    out.print(
        String.format(
            Locale.ROOT, " the Jarnik/Prim/binary-heap algorithm takes %d mems;\n", Mems.mems));

    Arc[] aux = Gb.allocAuxArcs((int) g.n);
    if (aux == null) {
      out.print(" and there isn't enough space to try the other methods.\n\n");
      return Integer.MIN_VALUE;
    }
    for (int i = 0; i < g.n; i++) {
      g.vertices[i].u.A(aux[i]);
    }

    if (spLength != jarPr(g, FIBONACCI_HEAP, out)) {
      out.print(" ...oops, I've got a bug, please fix fix fix\n");
      return -5;
    }
    out.print(
        String.format(
            Locale.ROOT, " the Jarnik/Prim/Fibonacci-heap algorithm takes %d mems;\n", Mems.mems));

    if (spLength != cherTarKar(g, out)) {
      if (Gb.troubleCode != 0) {
        out.print(" ...oops, I've run out of memory!\n");
      } else {
        out.print(" ...oops, I've got a bug, please fix fix fix\n");
      }
      return -3;
    }
    out.print(
        String.format(
            Locale.ROOT, " the Cheriton/Tarjan/Karp algorithm takes %d mems.\n\n", Mems.mems));
    return Integer.MIN_VALUE;
  }

  /** {@code report(u,v,l)}: one spanning-tree edge, with the running {@link Mems#mems} count. */
  private static void report(PrintStream out, Vertex u, Vertex v, long l) {
    out.print(
        String.format(
            Locale.ROOT, "  %d miles between %s and %s [%d mems]\n", l, u.name, v.name, Mems.mems));
  }

  // ======================================================================================
  // krusk: Kruskal's algorithm with a radix-sort bucket queue (sections 12, 14-18)
  // ======================================================================================

  private static long krusk(Graph g, PrintStream out) {
    Mems.mems = 0;
    long totLen = 0;
    long n;

    // section 12: sort every edge's higher-indexed arc into buckets by length.
    Mems.o();
    n = g.n;
    for (int l = 0; l < 64; l++) {
      Mems.oo();
      aucket[l] = null;
      bucket[l] = null;
    }
    Mems.o();
    for (int vi = 0; vi < n; vi++) {
      Vertex v = g.vertices[vi];
      Mems.o();
      Arc a = v.arcs;
      while (a != null) {
        Mems.o();
        if (!(a.tip.index > v.index)) {
          break;
        }
        Mems.o();
        a.a.V(v); // from = v
        Mems.o();
        long l = a.len & 0x3f;
        Mems.oo();
        a.b.A(aucket[(int) l]); // klink = aucket[l]
        Mems.o();
        aucket[(int) l] = a;
        Mems.o();
        a = a.next;
      }
    }
    for (int l = 63; l >= 0; l--) {
      Mems.o();
      Arc a = aucket[l];
      while (a != null) {
        Arc aa = a;
        Mems.o();
        a = aa.b.A(); // a = aa->klink
        Mems.o();
        long ll = aa.len >> 6;
        Mems.oo();
        aa.b.A(bucket[(int) ll]); // aa->klink = bucket[ll]
        Mems.o();
        bucket[(int) ll] = aa;
      }
    }

    if (Gb.verbose != 0) {
      out.print(
          String.format(Locale.ROOT, "   [%d mems to sort the edges into buckets]\n", Mems.mems));
    }

    // section 17: every vertex starts as its own singleton component.
    for (int vi = 0; vi < n; vi++) {
      Vertex v = g.vertices[vi];
      Mems.oo();
      v.z.V(v); // clink = v
      v.y.V(v); // comp = v
      Mems.o();
      v.x.I = 1; // csize = 1
    }
    long components = n;

    for (int l = 0; l < 64; l++) {
      Mems.o();
      Arc a = bucket[l];
      while (a != null) {
        Mems.o();
        Vertex u = a.a.V(); // from
        Mems.o();
        Vertex v = a.tip;
        // section 16
        Mems.oo();
        if (u.y.V() == v.y.V()) { // comp == comp
          Mems.o();
          a = a.b.A(); // klink
          continue;
        }
        if (Gb.verbose != 0) {
          report(out, a.a.V(), a.tip, a.len);
        }
        Mems.o();
        totLen += a.len;
        if (--components == 1) {
          return totLen;
        }
        // section 18
        u = u.y.V(); // u = u->comp
        v = v.y.V(); // v = v->comp
        Mems.oo();
        if (u.x.I < v.x.I) { // csize
          Vertex w = u;
          u = v;
          v = w;
        }
        Mems.o();
        u.x.I += v.x.I; // csize
        Mems.o();
        Vertex w = v.z.V(); // clink
        Mems.oo();
        v.z.V(u.z.V()); // v->clink = u->clink
        Mems.o();
        u.z.V(w); // u->clink = w
        while (true) {
          Mems.o();
          w.y.V(u); // w->comp = u
          if (w == v) {
            break;
          }
          Mems.o();
          w = w.z.V(); // w->clink
        }
        Mems.o();
        a = a.b.A(); // klink
      }
    }
    return INFINITY;
  }

  // ======================================================================================
  // jarPr: Jarnik/Prim, shared between the binary-heap and Fibonacci-heap variants
  // (sections 20-22)
  // ======================================================================================

  /** The C's {@code init_queue}/{@code enqueue}/{@code requeue}/{@code del_min} function group. */
  private record QueuePolicy(
      LongConsumer initQueue,
      ObjLongConsumer<Vertex> enqueue,
      ObjLongConsumer<Vertex> requeue,
      Supplier<Vertex> delMin) {}

  private static final QueuePolicy BINARY_HEAP =
      new QueuePolicy(
          MilesSpan::initHeap, MilesSpan::enqHeap, MilesSpan::reqHeap, MilesSpan::delHeap);

  private static final QueuePolicy FIBONACCI_HEAP =
      new QueuePolicy(
          MilesSpan::initFHeap, MilesSpan::enqFHeap, MilesSpan::reqFHeap, MilesSpan::delFHeap);

  private static long jarPr(Graph g, QueuePolicy q, PrintStream out) {
    Mems.mems = 0;
    long totLen = 0;

    // section 21
    Mems.oo();
    Vertex t = g.vertices[(int) g.n - 1];
    while (t.index > 0) {
      Mems.o();
      t.y.V(null); // backlink = NULL
      t = g.vertices[t.index - 1];
    }
    Mems.o();
    t.y.V(Gb.ONE); // backlink = KNOWN
    long fragmentSize = 1;
    q.initQueue().accept(0L);

    while (fragmentSize < g.n) {
      // section 22
      Mems.o();
      Arc a = t.arcs;
      while (a != null) {
        Mems.o();
        Vertex v = a.tip;
        Mems.o();
        if (v.y.V() != null) { // backlink truthy
          if (v.y.V() != Gb.ONE) { // backlink > KNOWN
            Mems.oo();
            if (a.len < v.z.I) { // dist
              Mems.o();
              v.y.V(t);
              q.requeue().accept(v, a.len);
            }
          }
        } else {
          Mems.o();
          v.y.V(t);
          Mems.o();
          q.enqueue().accept(v, a.len);
        }
        Mems.o();
        a = a.next;
      }
      t = q.delMin().get();
      if (t == null) {
        return INFINITY;
      }
      if (Gb.verbose != 0) {
        report(out, t.y.V(), t, t.z.I);
      }
      Mems.o();
      totLen += t.z.I; // dist
      Mems.o();
      t.y.V(Gb.ONE); // backlink = KNOWN
      fragmentSize++;
    }
    return totLen;
  }

  // ---- binary heap (sections 24-27) ----------------------------------------------------

  private static void initHeap(long d) {
    gv = currentGraph.vertices;
    hsize = 0;
  }

  private static void enqHeap(Vertex v, long d) {
    Mems.o();
    v.z.I = d; // dist
    long k = ++hsize;
    long j = k >> 1;
    while (j > 0) {
      Mems.oo();
      Vertex u = gv[(int) j].u.V();
      if (u.z.I <= d) {
        break;
      }
      Mems.o();
      gv[(int) k].u.V(u);
      Mems.o();
      u.v.I = k; // heap_index
      k = j;
      j = k >> 1;
    }
    Mems.o();
    gv[(int) k].u.V(v);
    Mems.o();
    v.v.I = k; // heap_index
  }

  private static void reqHeap(Vertex v, long d) {
    Mems.o();
    v.z.I = d; // dist
    Mems.o();
    long k = v.v.I; // heap_index
    long j = k >> 1;
    Vertex u;
    boolean cond;
    if (j > 0) {
      Mems.oo();
      u = gv[(int) j].u.V();
      cond = u.z.I > d;
    } else {
      u = null;
      cond = false;
    }
    if (cond) {
      do {
        Mems.o();
        gv[(int) k].u.V(u);
        Mems.o();
        u.v.I = k; // heap_index
        k = j;
        j = k >> 1;
        if (j > 0) {
          Mems.oo();
          u = gv[(int) j].u.V();
          cond = u.z.I > d;
        } else {
          cond = false;
        }
      } while (cond);
      Mems.o();
      gv[(int) k].u.V(v);
      Mems.o();
      v.v.I = k; // heap_index
    }
  }

  private static Vertex delHeap() {
    if (hsize == 0) {
      return null;
    }
    Mems.o();
    Vertex v = gv[1].u.V();
    long oldHsize = hsize;
    hsize--;
    Mems.o();
    Vertex u = gv[(int) oldHsize].u.V();
    Mems.o();
    long d = u.z.I; // dist
    long k = 1;
    long j = 2;
    while (j <= hsize) {
      Mems.oooo();
      if (gv[(int) j].u.V().z.I > gv[(int) j + 1].u.V().z.I) {
        j++;
      }
      if (gv[(int) j].u.V().z.I >= d) {
        break;
      }
      Mems.o();
      gv[(int) k].u.V(gv[(int) j].u.V());
      Mems.o();
      gv[(int) k].u.V().v.I = k; // heap_index
      k = j;
      j = k << 1;
    }
    Mems.o();
    gv[(int) k].u.V(u);
    Mems.o();
    u.v.I = k; // heap_index
    return v;
  }

  // ---- Fibonacci heap (sections 30, 33, 34, 38) ----------------------------------------

  private static void initFHeap(long d) {
    fHeap = null;
  }

  private static void enqFHeap(Vertex v, long d) {
    Mems.o();
    v.z.I = d; // dist
    Mems.o();
    v.u.A().tip = null; // parent = NULL
    Mems.o();
    v.x.I = 0; // rank_tag
    if (fHeap == null) {
      Mems.oo();
      v.w.V(v); // rsib = v
      v.v.V(v); // lsib = v
      fHeap = v;
    } else {
      Mems.o();
      Vertex u = fHeap.v.V(); // u = F_heap->lsib
      Mems.o();
      v.v.V(u); // v->lsib = u
      Mems.o();
      v.w.V(fHeap); // v->rsib = F_heap
      Mems.oo();
      u.w.V(v); // u->rsib = v
      fHeap.v.V(v); // F_heap->lsib = v
      if (fHeap.z.I > d) {
        fHeap = v;
      }
    }
  }

  private static void reqFHeap(Vertex v, long d) {
    Mems.o();
    v.z.I = d; // dist
    Mems.o();
    Vertex p = v.u.A().tip; // parent
    if (p == null) {
      if (fHeap.z.I > d) {
        fHeap = v;
      }
      return;
    }
    Mems.o();
    if (!(p.z.I > d)) {
      return;
    }
    while (true) {
      Mems.o();
      long r = p.x.I; // rank_tag
      if (r >= 4) {
        // section 35
        Mems.o();
        Vertex u = v.v.V(); // lsib
        Mems.o();
        Vertex w = v.w.V(); // rsib
        Mems.o();
        u.w.V(w); // u->rsib = w
        Mems.o();
        w.v.V(u); // w->lsib = u
        Mems.o();
        if (p.u.A().a.V() == v) { // p->child == v
          Mems.o();
          p.u.A().a.V(w); // p->child = w
        }
      }
      // section 36 (unconditional)
      Mems.o();
      v.u.A().tip = null; // parent = NULL
      Mems.o();
      Vertex u = fHeap.v.V(); // u = F_heap->lsib
      Mems.o();
      v.v.V(u); // v->lsib = u
      Mems.o();
      v.w.V(fHeap); // v->rsib = F_heap
      Mems.oo();
      u.w.V(v); // u->rsib = v
      fHeap.v.V(v); // F_heap->lsib = v
      if (fHeap.z.I > d) {
        fHeap = v;
      }
      Mems.o();
      Vertex pp = p.u.A().tip; // parent
      if (pp == null) {
        Mems.o();
        p.x.I = r - 2; // rank_tag
        break;
      }
      if ((r & 1) == 0) {
        Mems.o();
        p.x.I = r - 1; // rank_tag
        break;
      } else {
        Mems.o();
        p.x.I = r - 2; // rank_tag
      }
      v = p;
      p = pp;
    }
  }

  private static Vertex delFHeap() {
    Vertex finalV = fHeap;
    long h = -1;
    if (fHeap != null) {
      Vertex v;
      Mems.o();
      if (fHeap.x.I < 2) { // rank_tag
        Mems.o();
        v = fHeap.w.V(); // rsib
      } else {
        Mems.o();
        Vertex w = fHeap.u.A().a.V(); // child
        Mems.o();
        v = w.w.V(); // rsib
        Mems.oo();
        w.w.V(fHeap.w.V()); // w->rsib = F_heap->rsib
        for (w = v; w != fHeap.w.V(); ) {
          Mems.o();
          w.u.A().tip = null; // parent = NULL
          Mems.o();
          w = w.w.V(); // rsib
        }
      }
      while (v != fHeap) {
        Mems.o();
        Vertex w = v.w.V(); // rsib
        // section 39
        Mems.o();
        long r = v.x.I >> 1; // rank_tag
        while (true) {
          if (h < r) {
            do {
              h++;
              Mems.o();
              newRoots[(int) h] = (h == r) ? v : null;
            } while (h < r);
            break;
          }
          Mems.o();
          if (newRoots[(int) r] == null) {
            Mems.o();
            newRoots[(int) r] = v;
            break;
          }
          Vertex u = newRoots[(int) r];
          Mems.o();
          newRoots[(int) r] = null;
          Mems.oo();
          if (u.z.I < v.z.I) { // dist
            Mems.o();
            v.x.I = r << 1; // rank_tag
            Vertex tmp = u;
            u = v;
            v = tmp;
          }
          // section 40
          if (r == 0) {
            Mems.o();
            v.u.A().a.V(u); // v->child = u
            Mems.oo();
            u.v.V(u); // u->lsib = u
            u.w.V(u); // u->rsib = u
          } else {
            Mems.o();
            Vertex t = v.u.A().a.V(); // t = v->child
            Mems.oo();
            u.w.V(t.w.V()); // u->rsib = t->rsib
            Mems.o();
            u.v.V(t); // u->lsib = t
            Mems.oo();
            Vertex x = u.w.V(); // u->rsib (== old t->rsib)
            t.w.V(u); // t->rsib = u
            x.v.V(u); // u->rsib(old)->lsib = u
          }
          Mems.o();
          u.u.A().tip = v; // u->parent = v
          r++;
        }
        Mems.o();
        v.x.I = r << 1; // rank_tag
        v = w;
      }
      // section 41
      if (h < 0) {
        fHeap = null;
      } else {
        Mems.o();
        Vertex u = newRoots[(int) h];
        v = u;
        Mems.o();
        long d = u.z.I; // dist
        fHeap = u;
        for (h--; h >= 0; h--) {
          Mems.o();
          if (newRoots[(int) h] != null) {
            Vertex w = newRoots[(int) h];
            Mems.o();
            w.v.V(v); // w->lsib = v
            Mems.o();
            v.w.V(w); // v->rsib = w
            Mems.o();
            if (w.z.I < d) { // dist
              fHeap = w;
              d = w.z.I;
            }
            v = w;
          }
        }
        Mems.o();
        v.w.V(u); // v->rsib = u
        Mems.o();
        u.v.V(v); // u->lsib = v
      }
    }
    return finalV;
  }

  // ======================================================================================
  // cher_tar_kar: Cheriton/Tarjan/Karp, using per-vertex binomial-queue fragments
  // (sections 55-70)
  // ======================================================================================

  private static long cherTarKar(Graph g, PrintStream out) {
    Mems.mems = 0;
    long totLen = 0;

    // section 58
    Mems.o();
    long frags = g.n;
    hiSqrt = 1;
    while (hiSqrt * (hiSqrt + 1) <= frags) {
      hiSqrt++;
    }
    loSqrt = (hiSqrt * hiSqrt <= frags) ? hiSqrt : hiSqrt - 1;
    Vertex largeList = null;

    // section 59
    Mems.o();
    Vertex s = g.vertices[0];
    Vertex t = null;
    for (int vi = 0; vi < frags; vi++) {
      Vertex v = g.vertices[vi];
      if (vi > 0) {
        Mems.o();
        v.v.V(g.vertices[vi - 1]); // lsib = v-1
        Mems.o();
        g.vertices[vi - 1].w.V(v); // (v-1)->rsib = v
      }
      Mems.o();
      v.y.V(null); // comp = NULL
      Mems.o();
      v.x.I = 1; // csize = 1
      Mems.o();
      v.u.A().a.I = 0; // pq->qcount = 0
      Mems.o();
      Arc a = v.arcs;
      while (a != null) {
        qenque(v.u.A(), a); // v->pq
        Mems.o();
        a = a.next;
      }
      t = v;
    }

    while (frags > loSqrt) {
      // section 60
      Vertex v = s;
      Mems.o();
      s = s.w.V(); // rsib
      Arc a;
      Vertex u;
      do {
        a = qdelMin(v.u.A()); // v->pq
        if (a == null) {
          return INFINITY;
        }
        Mems.o();
        u = a.tip;
        while (true) {
          Mems.o();
          if (u.y.V() == null) { // comp
            break;
          }
          u = u.y.V();
        }
      } while (u == v);
      if (Gb.verbose != 0) {
        report(out, a.mate.tip, a.tip, a.len);
      }
      Mems.o();
      totLen += a.len;
      Mems.o();
      v.y.V(u); // comp = u
      qmerge(u.u.A(), v.u.A()); // qmerge(u->pq, v->pq)
      Mems.o();
      long oldSize = u.x.I; // csize
      Mems.o();
      long newSize = oldSize + v.x.I; // csize
      Mems.o();
      u.x.I = newSize; // csize

      // section 62
      fin:
      {
        if (oldSize >= hiSqrt) {
          if (t == v) {
            s = null;
          }
        } else if (newSize < hiSqrt) {
          if (u == t) {
            break fin;
          }
          if (u == s) {
            Mems.o();
            s = u.w.V(); // rsib
          } else {
            Mems.ooo();
            u.w.V().v.V(u.v.V()); // u->rsib->lsib = u->lsib
            Mems.o();
            u.v.V().w.V(u.w.V()); // u->lsib->rsib = u->rsib
          }
          Mems.o();
          t.w.V(u); // t->rsib = u
          Mems.o();
          u.v.V(t); // u->lsib = t
          t = u;
        } else {
          if (u == t) {
            if (u == s) {
              break fin;
            }
            Mems.o();
            t = u.v.V(); // lsib
          } else if (u == s) {
            Mems.o();
            s = u.w.V(); // rsib
          } else {
            Mems.ooo();
            u.w.V().v.V(u.v.V()); // u->rsib->lsib = u->lsib
            Mems.o();
            u.v.V().w.V(u.w.V()); // u->lsib->rsib = u->rsib
          }
          Mems.o();
          u.w.V(largeList); // u->rsib = large_list
          largeList = u;
        }
      }
      frags--;
    }

    if (Gb.verbose != 0) {
      out.print(String.format(Locale.ROOT, "    [Stage 1 has used %d mems]\n", Mems.mems));
    }

    // section 64
    gv = g.vertices;

    // section 65
    if (s == null) {
      s = largeList;
    } else {
      Mems.o();
      t.w.V(largeList); // t->rsib = large_list
    }
    {
      long k = 0;
      Vertex v = s;
      while (v != null) {
        Mems.o();
        v.x.I = k; // findex
        Mems.o();
        v = v.w.V(); // rsib
        k++;
      }
    }
    for (int vi = 0; vi < g.n; vi++) {
      Vertex v = g.vertices[vi];
      Mems.o();
      if (v.y.V() != null) { // comp
        Vertex tNode = v.y.V();
        while (true) {
          Mems.o();
          if (tNode.y.V() == null) { // comp
            break;
          }
          tNode = tNode.y.V();
        }
        Mems.o();
        long k = tNode.x.I; // findex
        Vertex walk = v;
        while (true) {
          Mems.o();
          Vertex u = walk.y.V(); // comp
          if (u == null) {
            break;
          }
          Mems.o();
          walk.y.V(null); // comp = NULL
          Mems.o();
          walk.x.I = k; // findex
          walk = u;
        }
      }
    }

    // section 66
    for (long j = 0; j < loSqrt; j++) {
      for (long k = 0; k < loSqrt; k++) {
        Mems.o();
        setMatx((int) j, (int) k, INF);
      }
    }
    kk = 0;
    while (s != null) {
      qtraverse(s.u.A(), MilesSpan::noteEdge); // s->pq
      Mems.o();
      s = s.w.V(); // rsib
      kk++;
    }

    // section 69/70
    long dVar;
    Mems.o();
    distance[0] = -1;
    dVar = INF;
    long j = 0;
    for (long k = 1; k < loSqrt; k++) {
      Mems.o();
      distance[(int) k] = matx(0, (int) k);
      distArc[(int) k] = matxArc(0, (int) k);
      if (distance[(int) k] < dVar) {
        dVar = distance[(int) k];
        j = k;
      }
    }
    while (frags > 1) {
      if (dVar == INF) {
        return INFINITY;
      }
      Mems.o();
      distance[(int) j] = -1;
      totLen += dVar;
      if (Gb.verbose != 0) {
        Arc a = distArc[(int) j];
        report(out, a.mate.tip, a.tip, a.len);
      }
      frags--;
      dVar = INF;
      for (long k = 1; k < loSqrt; k++) {
        Mems.o();
        if (distance[(int) k] >= 0) {
          Mems.o();
          if (matx((int) j, (int) k) < distance[(int) k]) {
            Mems.o();
            distance[(int) k] = matx((int) j, (int) k);
            distArc[(int) k] = matxArc((int) j, (int) k);
          }
          if (distance[(int) k] < dVar) {
            dVar = distance[(int) k];
            kk = k;
          }
        }
      }
      j = kk;
    }
    return totLen;
  }

  private static long matx(int j, int k) {
    return gv[j * (int) loSqrt + k].z.I;
  }

  private static void setMatx(int j, int k, long value) {
    gv[j * (int) loSqrt + k].z.I = value;
  }

  /**
   * {@code matx_arc(j,k)}: the shortest known edge between fragments {@code j} and {@code k}. When
   * no edge between them was ever recorded, the C reads whatever bit pattern is left in the same
   * union slot from an earlier phase (the vertex's own {@code lsib}); it is harmless there because
   * {@code matx(j,k) == INF} in that case, and the caller always tests the distance before using
   * the arc. Java's checked cast would throw on that stale content, so this returns {@code null}
   * instead — the C-equivalent of "never actually looked at."
   */
  private static Arc matxArc(int j, int k) {
    Object ref = gv[j * (int) loSqrt + k].v.ref;
    return ref instanceof Arc arc ? arc : null;
  }

  private static void setMatxArc(int j, int k, Arc a) {
    gv[j * (int) loSqrt + k].v.A(a);
  }

  /** {@code note_edge(a)}: records {@code a} in the inter-fragment distance matrix if shortest. */
  private static void noteEdge(Arc a) {
    Mems.oo();
    long k = a.tip.x.I; // findex
    if (k == kk) {
      return;
    }
    Mems.oo();
    if (a.len < matx((int) kk, (int) k)) {
      Mems.o();
      setMatx((int) kk, (int) k, a.len);
      Mems.o();
      setMatx((int) k, (int) kk, a.len);
      setMatxArc((int) kk, (int) k, a);
      setMatxArc((int) k, (int) kk, a);
    }
  }

  // ======================================================================================
  // The binomial-queue primitives shared by cher_tar_kar (sections 45, 50-54)
  // ======================================================================================

  private static void qunite(long m, Arc q, long mm, Arc qq, Arc h) {
    long k = 1;
    Arc p = h;
    while (m != 0) {
      if ((m & k) == 0) {
        if ((mm & k) != 0) {
          Mems.o();
          p.b.A(qq); // p->qsib = qq
          p = qq;
          mm -= k;
          if (mm != 0) {
            Mems.o();
            qq = qq.b.A(); // qsib
          }
        }
      } else if ((mm & k) == 0) {
        Mems.o();
        p.b.A(q); // p->qsib = q
        p = q;
        m -= k;
        if (m != 0) {
          Mems.o();
          q = q.b.A(); // qsib
        }
      } else {
        // section 46
        Arc c;
        long key;
        Arc r = null;
        Arc rr = null;
        m -= k;
        if (m != 0) {
          Mems.o();
          r = q.b.A(); // qsib
        }
        mm -= k;
        if (mm != 0) {
          Mems.o();
          rr = qq.b.A(); // qsib
        }
        // section 47
        Mems.oo();
        if (q.len < qq.len) {
          c = q;
          key = q.len;
          q = qq;
        } else {
          c = qq;
          key = qq.len;
        }
        if (k == 1) {
          Mems.o();
          c.a.A(q); // c->qchild = q
        } else {
          Mems.o();
          qq = c.a.A(); // qq = c->qchild
          Mems.o();
          c.a.A(q); // c->qchild = q
          if (k == 2) {
            Mems.o();
            q.b.A(qq); // q->qsib = qq
          } else {
            Mems.oo();
            q.b.A(qq.b.A()); // q->qsib = qq->qsib
          }
          Mems.o();
          qq.b.A(q); // qq->qsib = q
        }
        k <<= 1;
        q = r;
        qq = rr;
        while (((m | mm) & k) != 0) {
          if ((m & k) == 0) {
            // section 49
            mm -= k;
            if (mm != 0) {
              Mems.o();
              rr = qq.b.A(); // qsib
            }
            Mems.o();
            if (qq.len < key) {
              r = c;
              c = qq;
              key = qq.len;
              qq = r;
            }
            Mems.o();
            r = c.a.A(); // r = c->qchild
            Mems.o();
            c.a.A(qq); // c->qchild = qq
            if (k == 2) {
              Mems.o();
              qq.b.A(r); // qq->qsib = r
            } else {
              Mems.oo();
              qq.b.A(r.b.A()); // qq->qsib = r->qsib
            }
            Mems.o();
            r.b.A(qq); // r->qsib = qq
            qq = rr;
          } else {
            // section 48
            m -= k;
            if (m != 0) {
              Mems.o();
              r = q.b.A(); // qsib
            }
            Mems.o();
            if (q.len < key) {
              rr = c;
              c = q;
              key = q.len;
              q = rr;
            }
            Mems.o();
            rr = c.a.A(); // rr = c->qchild
            Mems.o();
            c.a.A(q); // c->qchild = q
            if (k == 2) {
              Mems.o();
              q.b.A(rr); // q->qsib = rr
            } else {
              Mems.oo();
              q.b.A(rr.b.A()); // q->qsib = rr->qsib
            }
            Mems.o();
            rr.b.A(q); // rr->qsib = q
            q = r;

            if ((mm & k) != 0) {
              Mems.o();
              p.b.A(qq); // p->qsib = qq
              p = qq;
              mm -= k;
              if (mm != 0) {
                Mems.o();
                qq = qq.b.A(); // qsib
              }
            }
          }
          k <<= 1;
        }
        Mems.o();
        p.b.A(c); // p->qsib = c
        p = c;
      }
      k <<= 1;
    }
    if (mm != 0) {
      Mems.o();
      p.b.A(qq); // p->qsib = qq
    }
  }

  private static void qenque(Arc h, Arc a) {
    Mems.o();
    long m = h.a.I; // qcount
    Mems.o();
    h.a.I = m + 1; // qcount
    if (m == 0) {
      Mems.o();
      h.b.A(a); // qsib = a
    } else {
      Mems.o();
      qunite(1L, a, m, h.b.A(), h); // h->qsib
    }
  }

  private static void qmerge(Arc h, Arc hh) {
    Mems.o();
    long mm = hh.a.I; // qcount
    if (mm != 0) {
      Mems.o();
      long m = h.a.I; // qcount
      Mems.o();
      h.a.I = m + mm; // qcount
      if (m >= mm) {
        Mems.oo();
        qunite(mm, hh.b.A(), m, h.b.A(), h);
      } else if (m == 0) {
        Mems.oo();
        h.b.A(hh.b.A()); // h->qsib = hh->qsib
      } else {
        Mems.oo();
        qunite(m, h.b.A(), mm, hh.b.A(), h);
      }
    }
  }

  private static Arc qdelMin(Arc h) {
    Mems.o();
    long m = h.a.I; // qcount
    if (m == 0) {
      return null;
    }
    Mems.o();
    h.a.I = m - 1; // qcount
    // section 53
    long mm = m & (m - 1);
    Mems.o();
    Arc q = h.b.A(); // qsib
    long k = m - mm;
    if (mm != 0) {
      Arc p = q;
      Arc qq = h;
      Mems.o();
      long key = q.len;
      do {
        long tt = mm & (mm - 1);
        Arc pp = p;
        Mems.o();
        p = p.b.A(); // qsib
        Mems.o();
        if (p.len <= key) {
          q = p;
          qq = pp;
          k = mm - tt;
          key = p.len;
        }
        mm = tt;
      } while (mm != 0);
      if (k + k <= m) {
        Mems.oo();
        qq.b.A(q.b.A()); // qq->qsib = q->qsib
      }
    }
    if (k > 2) {
      if (k + k <= m) {
        Mems.oo();
        qunite(k - 1, q.a.A().b.A(), m - k, h.b.A(), h);
      } else {
        Mems.oo();
        qunite(m - k, h.b.A(), k - 1, q.a.A().b.A(), h);
      }
    } else if (k == 2) {
      Mems.o();
      qunite(1L, q.a.A(), m - k, h.b.A(), h);
    }
    return q;
  }

  private static void qtraverse(Arc h, Consumer<Arc> visit) {
    Mems.o();
    long m = h.a.I; // qcount
    Arc p = h;
    while (m != 0) {
      Mems.o();
      p = p.b.A(); // qsib
      visit.accept(p);
      if ((m & 1) != 0) {
        m--;
      } else {
        Mems.o();
        Arc q = p.a.A(); // qchild
        if ((m & 2) != 0) {
          visit.accept(q);
        } else {
          Mems.o();
          Arc r = q.b.A(); // qsib
          if ((m & (m - 1)) != 0) {
            Mems.oo();
            q.b.A(p.b.A()); // q->qsib = p->qsib
          }
          visit.accept(r);
          p = r;
        }
        m -= 2;
      }
    }
  }
}
