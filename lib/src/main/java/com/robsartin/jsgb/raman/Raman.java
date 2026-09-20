package com.robsartin.jsgb.raman;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;

/**
 * Port of {@code gb_raman}: the {@code raman} subroutine, which builds a family of "Ramanujan
 * graphs" based on a theory developed by Alexander Lubotzky, Ralph Phillips, and Peter Sarnak
 * (<i>Combinatorica</i> 8 (1988), 261-277).
 *
 * <p>Ramanujan graphs are connected, undirected, degree-regular graphs whose adjacency-matrix
 * eigenvalues are either &plusmn;k (the degree) or bounded in absolute value by 2&radic;(k-1). The
 * particular examples built here come from interesting properties of quaternions with integer
 * coefficients.
 *
 * <p>{@link #raman(long, long, long, long)} constructs an undirected graph in which every vertex
 * has degree {@code p + 1} (fewer, at some vertices, when {@code reduce} is nonzero). Parameters
 * {@code p} and {@code q} must be distinct primes with {@code q} odd, {@code 3 <= q <= 46337}. If
 * {@code p = 2}, {@code q} must additionally satisfy {@code q mod 8} in {1,3} and {@code q mod 13}
 * in {1,3,4,9,10,12}. The number of vertices is {@code q + 1} for type 1, {@code q(q+1)/2} for type
 * 2, {@code (q-1)q(q+1)/2} for type 3, or {@code (q-1)q(q+1)} for type 4; type 3 requires {@code p}
 * to be a quadratic residue mod {@code q}, type 4 requires it not to be. Passing {@code type = 0}
 * picks the largest permissible type (3 or 4), and the chosen type appears in the graph's {@code
 * id}. Types 3 and 4 additionally require {@code q <= 1289}. All edges have length 1.
 *
 * <p>Slot usage: for type 1, vertex slot {@code x.I} holds the point's serial number ({@code q} for
 * the point named {@code "INF"}); for type 2, {@code x.I} and {@code y.I} hold the two points of
 * the pair. For types 3 and 4, {@code x.I} and {@code y.I} hold the first row of the vertex's
 * projective matrix and {@code z.I} holds the ratio of the second row's elements ({@code q}
 * representing &infin;). Arc slot {@code a.I} ({@code ref} in the C) holds the number, 0 to {@code
 * p}, of the generating permutation that produced the arc.
 */
public final class Raman {

  private Raman() {}

  // Section 6: three views of one q_sqr/q_sqrt/q_inv array, kept as separate arrays here.
  private static long[] qSqr;
  private static long[] qSqrt;
  private static long[] qInv;

  // Section 15: scratch space to format a vertex name. Not needed in Java; String.format suffices,
  // kept only as a documentation anchor for the C's name_buf.

  /**
   * The C {@code quaternion} struct: coefficients {@code a0..a3} and the index of its conjugate.
   */
  private static final class Quaternion {
    long a0;
    long a1;
    long a2;
    long a3;
    long bar;
  }

  // Section 20/22: the p+2 generating quaternions and how many have been found so far.
  private static Quaternion[] gen;
  private static long genCount;
  private static long maxGenCount;

  /**
   * {@code deposit(a,b,c,d)}: records the quaternion {@code a+bi+cj+dk} and, unless {@code a = 0},
   * its conjugate {@code a-bi-cj-dk} as a pair of mutually inverse generators.
   */
  private static void deposit(long a, long b, long c, long d) {
    if (genCount >= maxGenCount) {
      genCount = maxGenCount + 1;
    } else {
      int gc = (int) genCount;
      gen[gc].a0 = a;
      gen[gc + 1].a0 = a;
      gen[gc].a1 = b;
      gen[gc + 1].a1 = -b;
      gen[gc].a2 = c;
      gen[gc + 1].a2 = -c;
      gen[gc].a3 = d;
      gen[gc + 1].a3 = -d;
      if (a != 0) {
        gen[gc].bar = genCount + 1;
        gen[gc + 1].bar = genCount;
        genCount += 2;
      } else {
        gen[gc].bar = genCount;
        genCount++;
      }
    }
  }

  /**
   * {@code lin_frac(a,k)}: the image of {@code a} (with {@code q} representing &infin;) under the
   * linear fractional transformation defined by the matrix of {@code gen[k]}.
   */
  private static long linFrac(long a, long k) {
    long q = qInv[0];
    int kk = (int) k;
    long a00 = gen[kk].a0;
    long a01 = gen[kk].a1;
    long a10 = gen[kk].a2;
    long a11 = gen[kk].a3;
    long num;
    long den;
    if (a == q) {
      num = a00;
      den = a10;
    } else {
      num = (a00 * a + a01) % q;
      den = (a10 * a + a11) % q;
    }
    if (den == 0) {
      return q;
    }
    return (num * qInv[(int) den]) % q;
  }

  /**
   * {@code raman(p,q,type,reduce)}: builds a Ramanujan graph of degree {@code p + 1}. Returns
   * {@code null} and sets {@link Gb#panicCode} if the parameters are invalid; see the class Javadoc
   * for the parameter constraints.
   */
  public static Graph raman(long p, long q, long type, long reduce) {
    Graph newGraph;
    long a;
    long aa;
    long b;
    long bb;
    long c;
    long cc;
    long d;
    long dd;
    long k;
    long n;
    long nFactor;

    // Section 7: validate q and p, then build the quadratic-residue tables.
    if (q < 3 || q > 46337) {
      Gb.panicCode = Gb.VERY_BAD_SPECS;
      Gb.troubleCode = 0;
      return null;
    }
    if (p < 2) {
      Gb.panicCode = Gb.VERY_BAD_SPECS + 1;
      Gb.troubleCode = 0;
      return null;
    }
    int qi = (int) q;
    qSqr = new long[qi];
    qSqrt = new long[qi];
    qInv = new long[qi];
    // Section 8: q_sqr[a] = a*a mod q; q_sqrt is its (partial) inverse; q_inv[a*a] is marked seen.
    for (a = 1; a < q; a++) {
      qSqrt[(int) a] = -1;
    }
    for (a = 1, aa = 1; a < q; aa = (aa + a + a + 1) % q, a++) {
      qSqr[(int) a] = aa;
      qSqrt[(int) aa] = q - a;
      qInv[(int) aa] = -1;
    }
    // Section 10: find a primitive root a of q, marking q_inv[b] for every power b of a.
    for (a = 2; ; a++) {
      if (qInv[(int) a] == 0) {
        for (b = a, k = 1; b != 1 && k < q; aa = b, b = (a * b) % q, k++) {
          qInv[(int) b] = -1;
        }
        if (k >= q) {
          Gb.panicCode = Gb.BAD_SPECS + 1;
          Gb.troubleCode = 0;
          return null;
        }
        if (k == q - 1) {
          break;
        }
      }
    }
    // Section 11: fill in q_inv properly now that a primitive root (a, with inverse aa) is known.
    for (b = a, bb = aa; b != bb; b = (a * b) % q, bb = (aa * bb) % q) {
      qInv[(int) b] = bb;
      qInv[(int) bb] = b;
    }
    qInv[1] = 1;
    qInv[(int) b] = b;
    qInv[0] = q;

    // Section 12: resolve type (if 0) and compute n, the vertex count.
    if (p == 2) {
      if (qSqrt[(int) (13 % q)] < 0 || qSqrt[(int) (q - 2)] < 0) {
        Gb.panicCode = Gb.BAD_SPECS + 2;
        Gb.troubleCode = 0;
        return null;
      }
    }
    if ((a = p % q) == 0) {
      Gb.panicCode = Gb.BAD_SPECS + 3;
      Gb.troubleCode = 0;
      return null;
    }
    if (type == 0) {
      type = qSqrt[(int) a] > 0 ? 3 : 4;
    }
    nFactor = type == 3 ? (q - 1) / 2 : q - 1;
    switch ((int) type) {
      case 1:
        n = q + 1;
        break;
      case 2:
        n = q * (q + 1) / 2;
        break;
      default:
        if ((qSqrt[(int) a] > 0 && type != 3) || (qSqrt[(int) a] < 0 && type != 4)) {
          Gb.panicCode = Gb.BAD_SPECS + 4;
          Gb.troubleCode = 0;
          return null;
        }
        if (q > 1289) {
          Gb.panicCode = Gb.BAD_SPECS + 5;
          Gb.troubleCode = 0;
          return null;
        }
        n = nFactor * q * (q + 1);
        break;
    }
    if (p >= 0x3fffffffL / n) {
      Gb.panicCode = Gb.BAD_SPECS + 6;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 13: create the graph and label its vertices.
    newGraph = Gb.newGraph(n);
    if (newGraph == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    newGraph.id =
        "raman("
            + p
            + ","
            + q
            + ","
            + Long.toUnsignedString(type)
            + ","
            + Long.toUnsignedString(reduce)
            + ")";
    newGraph.utilTypes = "ZZZIIZIZZZZZZZ";
    Vertex[] verts = newGraph.vertices;
    int vi = 0;
    switch ((int) type) {
      case 1:
        // Section 14: label with 0, 1, ..., q-1, INF.
        newGraph.utilTypes =
            newGraph.utilTypes.substring(0, 4) + 'Z' + newGraph.utilTypes.substring(5);
        for (a = 0; a < q; a++) {
          verts[vi].name = Long.toString(a);
          verts[vi].x.I = a;
          vi++;
        }
        verts[vi].name = "INF";
        verts[vi].x.I = q;
        vi++;
        break;
      case 2:
        // Section 16: label with unordered pairs {a, aa}, aa possibly INF.
        for (a = 0; a < q; a++) {
          for (aa = a + 1; aa <= q; aa++) {
            verts[vi].name = aa == q ? "{" + a + ",INF}" : "{" + a + "," + aa + "}";
            verts[vi].x.I = a;
            verts[vi].y.I = aa;
            vi++;
          }
        }
        break;
      default:
        // Section 17: label with projective matrix rows (x,y;0,1) or (x,y;1,z).
        newGraph.utilTypes =
            newGraph.utilTypes.substring(0, 5) + 'I' + newGraph.utilTypes.substring(6);
        for (c = 0; c <= q; c++) {
          for (b = 0; b < q; b++) {
            for (a = 1; a <= nFactor; a++) {
              verts[vi].z.I = c;
              if (c == q) {
                verts[vi].y.I = b;
                verts[vi].x.I = type == 3 ? qSqr[(int) a] : a;
                verts[vi].name = "(" + verts[vi].x.I + "," + b + ";0,1)";
              } else {
                verts[vi].x.I = b;
                verts[vi].y.I = (b * c + q - (type == 3 ? qSqr[(int) a] : a)) % q;
                verts[vi].name = "(" + b + "," + verts[vi].y.I + ";1," + c + ")";
              }
              vi++;
            }
          }
        }
        break;
    }

    // Section 19: find the p+1 generating permutations, as quaternions.
    gen = new Quaternion[(int) (p + 2)];
    for (int i = 0; i < gen.length; i++) {
      gen[i] = new Quaternion();
    }
    genCount = 0;
    maxGenCount = p + 1;
    if (p == 2) {
      // Section 25: p = 2 uses three special hand-picked generators.
      long s = qSqrt[(int) (q - 2)];
      long t = (qSqrt[(int) (13 % q)] * s) % q;
      gen[0].a0 = 1;
      gen[0].a1 = 0;
      gen[0].a2 = 0;
      gen[0].a3 = q - 1;
      gen[0].bar = 0;
      gen[1].a0 = gen[2].a3 = (2 + s) % q;
      gen[1].a1 = gen[1].a2 = t;
      gen[2].a1 = gen[2].a2 = q - t;
      gen[1].a3 = gen[2].a0 = (q + 2 - s) % q;
      gen[1].bar = 2;
      gen[2].bar = 1;
      genCount = 3;
    } else {
      // Section 21: enumerate quaternions of norm p as sums of four squares, in canonical form.
      long sa;
      long sb;
      long pp = (p >> 1) & 1;
      for (a = 1 - pp, sa = p - a; sa > 0; sa -= (a + 1) << 2, a += 2) {
        for (b = pp, sb = sa - b, bb = sb - b - b;
            bb >= 0;
            bb -= 12 * (b + 1), sb -= (b + 1) << 2, b += 2) {
          for (c = b, cc = bb; cc >= 0; cc -= (c + 1) << 3, c += 2) {
            for (d = c, aa = cc; aa >= 0; aa -= (d + 1) << 2, d += 2) {
              if (aa == 0) {
                // Section 23: deposit this solution and all its sign/order variants.
                deposit(a, b, c, d);
                if (b != 0) {
                  deposit(a, -b, c, d);
                  deposit(a, -b, -c, d);
                }
                if (c != 0) {
                  deposit(a, b, -c, d);
                }
                if (b < c) {
                  deposit(a, c, b, d);
                  deposit(a, -c, b, d);
                  deposit(a, c, d, b);
                  deposit(a, -c, d, b);
                  if (b != 0) {
                    deposit(a, c, -b, d);
                    deposit(a, -c, -b, d);
                    deposit(a, c, d, -b);
                    deposit(a, -c, d, -b);
                  }
                }
                if (c < d) {
                  deposit(a, b, d, c);
                  deposit(a, d, b, c);
                  if (b != 0) {
                    deposit(a, -b, d, c);
                    deposit(a, -b, d, -c);
                    deposit(a, d, -b, c);
                    deposit(a, d, -b, -c);
                  }
                  if (c != 0) {
                    deposit(a, b, d, -c);
                    deposit(a, d, b, -c);
                  }
                  if (b < c) {
                    deposit(a, d, c, b);
                    deposit(a, d, -c, b);
                    if (b != 0) {
                      deposit(a, d, c, -b);
                      deposit(a, d, -c, -b);
                    }
                  }
                }
              }
            }
          }
        }
      }
      // Section 24: convert each quaternion to the corresponding 2x2 matrix mod q.
      long g;
      long h;
      long a00;
      long a01;
      long a10;
      long a11;
      for (k = q - 1; qSqrt[(int) k] < 0; k--) {
        // find k with a known square root
      }
      g = qSqrt[(int) k];
      h = qSqrt[(int) (q - 1 - k)];
      for (k = p; k >= 0; k--) {
        int ki = (int) k;
        a00 = (gen[ki].a0 + g * gen[ki].a1 + h * gen[ki].a3) % q;
        if (a00 < 0) {
          a00 += q;
        }
        a11 = (gen[ki].a0 - g * gen[ki].a1 - h * gen[ki].a3) % q;
        if (a11 < 0) {
          a11 += q;
        }
        a01 = (gen[ki].a2 + g * gen[ki].a3 - h * gen[ki].a1) % q;
        if (a01 < 0) {
          a01 += q;
        }
        a10 = (-gen[ki].a2 + g * gen[ki].a3 - h * gen[ki].a1) % q;
        if (a10 < 0) {
          a10 += q;
        }
        gen[ki].a0 = a00;
        gen[ki].a1 = a01;
        gen[ki].a2 = a10;
        gen[ki].a3 = a11;
      }
    }
    if (genCount != maxGenCount) {
      Gb.recycle(newGraph);
      Gb.panicCode = Gb.BAD_SPECS + 7;
      Gb.troubleCode = 0;
      return null;
    }

    // Section 26: append the edges, one generator pair at a time.
    int ni = (int) n;
    for (k = p; k >= 0; k--) {
      long kk = gen[(int) k].bar;
      if (kk <= k) {
        vertexLoop:
        for (int vidx = 0; vidx < ni; vidx++) {
          Vertex v = verts[vidx];
          Vertex u;
          // Section 27: compute the image, u, of v under the permutation defined by gen[k].
          if (type < 3) {
            // Section 31: types 1 and 2 use linear fractional transformations.
            if (type == 1) {
              u = verts[(int) linFrac(v.x.I, k)];
            } else {
              a = linFrac(v.x.I, k);
              aa = linFrac(v.y.I, k);
              u =
                  verts[
                      (int)
                          (a < aa
                              ? (a * (2 * q - 1 - a)) / 2 + aa - 1
                              : (aa * (2 * q - 1 - aa)) / 2 + a - 1)];
            }
          } else {
            // Types 3 and 4: multiply v's matrix by gen[k]'s matrix, reduce, then find the index.
            long a00 = gen[(int) k].a0;
            long a01 = gen[(int) k].a1;
            long a10 = gen[(int) k].a2;
            long a11 = gen[(int) k].a3;
            a = v.x.I;
            b = v.y.I;
            if (v.z.I == q) {
              c = 0;
              d = 1;
            } else {
              c = 1;
              d = v.z.I;
            }
            // Section 28: (a b; c d) times (a00 a01; a10 a11).
            aa = (a * a00 + b * a10) % q;
            bb = (a * a01 + b * a11) % q;
            cc = (c * a00 + d * a10) % q;
            dd = (c * a01 + d * a11) % q;
            a = cc != 0 ? qInv[(int) cc] : qInv[(int) dd];
            d = (a * dd) % q;
            c = (a * cc) % q;
            b = (a * bb) % q;
            a = (a * aa) % q;
            // Section 29: normalize to (a,b;0,1) or (a,b;1,z) and find the vertex index.
            if (c == 0) {
              d = q;
              aa = a;
            } else {
              aa = (a * d - b) % q;
              if (aa < 0) {
                aa += q;
              }
              b = a;
            }
            u = verts[(int) ((d * q + b) * nFactor + (type == 3 ? qSqrt[(int) aa] : aa) - 1)];
          }
          if (u == v) {
            if (reduce == 0) {
              Gb.newEdge(v, v, 1L);
              v.arcs.a.I = kk;
              v.arcs.mate.a.I = k;
            }
          } else {
            if (u.arcs != null && u.arcs.a.I == kk) {
              continue vertexLoop;
            }
            if (reduce != 0) {
              boolean already = false;
              for (Arc ap = v.arcs; ap != null; ap = ap.next) {
                if (ap.tip == u) {
                  already = true;
                  break;
                }
              }
              if (already) {
                continue vertexLoop;
              }
            }
            Gb.newEdge(v, u, 1L);
            v.arcs.a.I = k;
            u.arcs.a.I = kk;
            Arc ap = v.arcs.next;
            if (ap != null && ap.a.I == kk) {
              v.arcs.next = ap.next;
              ap.next = v.arcs;
              v.arcs = ap;
            }
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
}
