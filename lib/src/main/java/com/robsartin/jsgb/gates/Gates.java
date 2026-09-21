package com.robsartin.jsgb.gates;

import com.robsartin.jsgb.flip.Flip;
import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Vertex;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Port of {@code gb_gates}'s builder, evaluator, printer and simulator: {@link #risc} builds
 * Knuth's 16-bit RISC machine as a gate graph with {@code regs} general registers; {@link
 * #gateEval} propagates a bit vector through a gate graph's inputs, latches, constants, copies and
 * logic gates (types {@code I}, {@code L}, {@code C}, {@code =}, {@code &}, {@code |}, {@code ^},
 * {@code ~}); {@link #printGates} lists a graph's gates and outputs in the C's {@code print_gates}
 * format; {@link #runRisc} drives a RISC gate graph through a ROM, optionally tracing registers.
 *
 * <p>A gate vertex's slots: {@code val} ({@code x.I}) is its current 0/1 output; {@code typ}
 * ({@code y.I}) is its gate-type character; {@code alt} ({@code z.V()}) is a latch's or copy's
 * referenced vertex; {@code bit} ({@code z.I}) is a constant's 0/1 value (the same slot as {@code
 * alt}, since the two never apply to the same vertex); {@code bar} ({@code w.V()}) is a gate's
 * already-built complement, memoized by {@link #comp}. A graph's output arcs hang off {@code outs}
 * ({@code g.zz.A()}); each output arc's {@code tip} is either a gate vertex or the boolean sentinel
 * {@link Gb#ONE} (or {@code null}), meaning a constant 1 or 0 output.
 *
 * <p>Vertex numbering is significant: every vertex is created in the exact textual order of the C
 * generator, so names (and hence {@link #printGates}'s and {@code print_sample}'s output) line up
 * with the oracle byte for byte. The shared builder state ({@link #verts}, {@link #nextVert},
 * {@link #prefix}, {@link #count}) plays the role of the C's file-scope {@code next_vert}, {@code
 * prefix} and {@code count}; {@link #prod} and {@link #partialGates} reuse that same state and
 * these helpers.
 */
public final class Gates {

  /** {@code AND}: a gate whose value is the logical AND of its arcs' tips. */
  private static final char AND = '&';

  /** {@code OR}: a gate whose value is the logical OR of its arcs' tips. */
  private static final char OR = '|';

  /** {@code NOT}: a gate whose value is the complement of its single arc's tip. */
  private static final char NOT = '~';

  /**
   * {@code XOR}: a gate whose value is the logical XOR of its arcs' tips; {@link #risc} never
   * builds one directly (only via {@link #makeXor}'s AND/OR/NOT expansion), but {@link #prod}
   * builds XOR gates directly (sections 73 and 74).
   */
  private static final char XOR = '^';

  /** {@code DELAY}: the arc length {@link #make2}..{@link #make5} give every gate input. */
  private static final long DELAY = 100L;

  /**
   * {@code util_types} every {@link #risc}, {@link #prod} and {@link #reduce} output is stamped
   * with.
   */
  private static final String UTIL_TYPES = "ZZZIIVZZZZZZZA";

  /** The C's {@code next_vert}'s target array: the graph currently being built. */
  private static Vertex[] verts;

  /** The C's {@code next_vert}: the index of the next unused slot in {@link #verts}. */
  private static int nextVert;

  /** The C's {@code prefix}: the name prefix {@link #newVert} gives its next few vertices. */
  private static String prefix;

  /** The C's {@code count}: the suffix {@link #newVert} appends to {@link #prefix}, or negative. */
  private static long count;

  /**
   * {@code risc_state[18]}: filled by {@link #runRisc} on every call — registers 0-15, then the
   * packed program counter and condition flags, then the final memory address.
   */
  public static final long[] riscState = new long[18];

  /** Where {@link #printGates} and {@link #runRisc} print; ISO-8859-1, like the C. */
  public static PrintStream out = new PrintStream(System.out, true, StandardCharsets.ISO_8859_1);

  private Gates() {}

  /**
   * {@code is_boolean(v)}: true for the sentinel {@link Gb#ONE} or for {@code null}, the C's {@code
   * (unsigned long) v <= 1} test on a {@code Vertex*} that might be {@code NULL} or the constant
   * {@code (Vertex*) 1}. {@link Gb#isBoolean} covers only the {@link Gb#ONE} half.
   */
  private static boolean isBooleanOrNull(Vertex v) {
    return v == null || v == Gb.ONE;
  }

  /** {@code the_boolean(v)}: 1 for {@link Gb#ONE}, 0 for {@code null}. */
  private static long theBoolean(Vertex v) {
    return v == Gb.ONE ? 1 : 0;
  }

  /** {@code tip_value(v)}: {@link #theBoolean} if {@code v} is boolean, else its current value. */
  private static long tipValue(Vertex v) {
    return isBooleanOrNull(v) ? theBoolean(v) : v.x.I;
  }

  /** {@code start_prefix(s)}: {@link #prefix} = {@code s}, {@link #count} reset to 0. */
  private static void startPrefix(String s) {
    prefix = s;
    count = 0;
  }

  /**
   * {@code numeric_prefix(a,b)}: {@link #prefix} = {@code "<a><b>:"}, {@link #count} reset to 0.
   */
  private static void numericPrefix(char a, long b) {
    prefix = "" + a + b + ":";
    count = 0;
  }

  /**
   * {@code new_vert(t)}: the next vertex of {@link #verts}, named {@link #prefix} (plus {@link
   * #count}, then incremented, unless {@link #count} is negative) and typed {@code t}.
   */
  private static Vertex newVert(char t) {
    Vertex v = verts[nextVert++];
    v.name = count < 0 ? prefix : prefix + count;
    if (count >= 0) {
      count++;
    }
    v.y.I = t;
    return v;
  }

  /** {@code first_of(n,t)}: {@code n} new vertices of type {@code t}; returns the first's index. */
  private static int firstOf(int n, char t) {
    int first = nextVert;
    for (int k = 0; k < n; k++) {
      newVert(t);
    }
    return first;
  }

  private static Vertex make2(char t, Vertex v1, Vertex v2) {
    Vertex v = newVert(t);
    Gb.newArc(v, v1, DELAY);
    Gb.newArc(v, v2, DELAY);
    return v;
  }

  private static Vertex make3(char t, Vertex v1, Vertex v2, Vertex v3) {
    Vertex v = newVert(t);
    Gb.newArc(v, v1, DELAY);
    Gb.newArc(v, v2, DELAY);
    Gb.newArc(v, v3, DELAY);
    return v;
  }

  private static Vertex make4(char t, Vertex v1, Vertex v2, Vertex v3, Vertex v4) {
    Vertex v = newVert(t);
    Gb.newArc(v, v1, DELAY);
    Gb.newArc(v, v2, DELAY);
    Gb.newArc(v, v3, DELAY);
    Gb.newArc(v, v4, DELAY);
    return v;
  }

  private static Vertex make5(char t, Vertex v1, Vertex v2, Vertex v3, Vertex v4, Vertex v5) {
    Vertex v = newVert(t);
    Gb.newArc(v, v1, DELAY);
    Gb.newArc(v, v2, DELAY);
    Gb.newArc(v, v3, DELAY);
    Gb.newArc(v, v4, DELAY);
    Gb.newArc(v, v5, DELAY);
    return v;
  }

  /**
   * {@code comp(v)}: {@code v}'s complement, building and memoizing (in both directions) a fresh
   * NOT gate the first time it's asked for.
   */
  private static Vertex comp(Vertex v) {
    if (v.w.V() != null) {
      return v.w.V();
    }
    Vertex u = verts[nextVert++];
    u.w.V(v);
    v.w.V(u);
    u.name = v.name + "~";
    u.y.I = NOT;
    Gb.newArc(u, v, 1);
    return u;
  }

  /** {@code make_xor(u,v)}: {@code u XOR v}, built from AND/OR/NOT gates. */
  private static Vertex makeXor(Vertex u, Vertex v) {
    Vertex t1 = make2(AND, u, comp(v));
    Vertex t2 = make2(AND, comp(u), v);
    return make2(OR, t1, t2);
  }

  /** {@code even_comp(s,v)}: {@code v} if {@code s} is odd, else {@link #comp}({@code v}). */
  private static Vertex evenComp(long s, Vertex v) {
    return (s & 1) != 0 ? v : comp(v);
  }

  /** {@code latchit(u,latch)}: makes {@code latch}'s alt gate {@code u AND runBit}. */
  private static void latchit(Vertex u, Vertex latch, Vertex runBit) {
    latch.z.V(make2(AND, u, runBit));
  }

  /**
   * {@code make_adder(n,x,y,z,carry,add)}: an {@code n}-bit ripple adder/subtractor of {@code x}
   * and {@code y} (or {@code x} minus {@code y} when {@code add} is 0) into {@code z[0..n-1]}, with
   * the final carry in {@code z[n]}. If {@code carry} is {@code null}, bit 0 has no incoming carry
   * (a plain half-adder); otherwise every bit, including bit 0, is a full adder against it.
   */
  private static void makeAdder(
      long n, Vertex[] x, Vertex[] y, Vertex[] z, Vertex carry, long add) {
    long k;
    if (carry == null) {
      z[0] = makeXor(x[0], y[0]);
      carry = make2(AND, evenComp(add, x[0]), y[0]);
      k = 1;
    } else {
      k = 0;
    }
    for (; k < n; k++) {
      int ki = (int) k;
      comp(x[ki]);
      comp(y[ki]);
      comp(carry);
      Vertex t1 = make3(AND, x[ki], comp(y[ki]), comp(carry));
      Vertex t2 = make3(AND, comp(x[ki]), y[ki], comp(carry));
      Vertex t3 = make3(AND, comp(x[ki]), comp(y[ki]), carry);
      Vertex t4 = make3(AND, x[ki], y[ki], carry);
      z[ki] = make4(OR, t1, t2, t3, t4);
      Vertex c1 = make2(AND, evenComp(add, x[ki]), y[ki]);
      Vertex c2 = make2(AND, evenComp(add, x[ki]), carry);
      Vertex c3 = make2(AND, y[ki], carry);
      carry = make3(OR, c1, c2, c3);
    }
    z[(int) n] = carry;
  }

  /**
   * {@code risc(regs)}: Knuth's 16-bit RISC machine as a gate graph, with {@code regs} general
   * registers (clamped to the range 2..16, defaulting to 16 outside it, as unsigned). The graph has
   * {@code 1400 + 115*regs} vertices, laid out (see the class documentation for the slot roles):
   * vertex 0 is {@code RUN}; 1..16 are {@code M0..M15} (the instruction word/immediate memory);
   * 17.. 26 are {@code P0..P9} (the program counter latches); 27 is {@code S} (sign), 28 {@code N}
   * (nonzero), 29 {@code K} (carry), 30 {@code V} (overflow), 31 {@code X} (the "extra word"
   * latch); then, for each register {@code r} from 0 to {@code regs-1}, sixteen bits {@code
   * Rr:0..Rr:15} (bit 15 of register {@code r} at {@code 32 + 16*regs + 15}). Returns {@code null}
   * and sets {@link Gb#panicCode} if the underlying {@link Gb#newGraph} allocation fails.
   */
  public static Graph risc(long regs) {
    if (Long.compareUnsigned(regs, 2) < 0 || Long.compareUnsigned(regs, 16) > 0) {
      regs = 16;
    }
    Graph g = Gb.newGraph(1400 + 115 * regs);
    if (g == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    g.id = "risc(" + Long.toUnsignedString(regs) + ")";
    g.utilTypes = UTIL_TYPES;
    verts = g.vertices;
    nextVert = 0;
    int intRegs = (int) regs;

    // Section 19: RUN, M0..M15, P0..P9, S, N, K, V, X, and regs registers of 16 bits each.
    prefix = "RUN";
    count = -1;
    Vertex runBit = newVert('I');
    startPrefix("M");
    Vertex[] mem = new Vertex[16];
    for (int k = 0; k < 16; k++) {
      mem[k] = newVert('I');
    }
    startPrefix("P");
    int prog = firstOf(10, 'L');
    prefix = "S";
    count = -1;
    Vertex sign = newVert('L');
    prefix = "N";
    Vertex nonzero = newVert('L');
    prefix = "K";
    Vertex carry = newVert('L');
    prefix = "V";
    Vertex overflow = newVert('L');
    prefix = "X";
    Vertex extra = newVert('L');
    int[] reg = new int[intRegs];
    for (int r = 0; r < intRegs; r++) {
      numericPrefix('R', r);
      reg[r] = firstOf(16, 'L');
    }

    // Section 21: addressing-mode decode (D prefix).
    startPrefix("D");
    Vertex imm;
    Vertex rel;
    Vertex dir;
    Vertex ind;
    Vertex op;
    Vertex cond;
    {
      Vertex t1 = comp(extra);
      Vertex t2 = comp(mem[4]);
      Vertex t3 = comp(mem[5]);
      imm = make3(AND, t1, t2, t3);
    }
    {
      Vertex t1 = comp(extra);
      Vertex t2 = mem[4];
      Vertex t3 = comp(mem[5]);
      rel = make3(AND, t1, t2, t3);
    }
    {
      Vertex t1 = comp(extra);
      Vertex t2 = comp(mem[4]);
      Vertex t3 = mem[5];
      dir = make3(AND, t1, t2, t3);
    }
    {
      Vertex t1 = comp(extra);
      Vertex t2 = mem[4];
      Vertex t3 = mem[5];
      ind = make3(AND, t1, t2, t3);
    }
    {
      Vertex t1 = make2(AND, extra, verts[prog]);
      Vertex t2 = make2(AND, comp(extra), mem[6]);
      op = make2(OR, t1, t2);
    }
    {
      Vertex t1 = make2(AND, extra, verts[prog + 1]);
      Vertex t2 = make2(AND, comp(extra), mem[7]);
      cond = make2(OR, t1, t2);
    }
    Vertex[] mod = new Vertex[4];
    Vertex[] dest = new Vertex[4];
    for (int k = 0; k < 4; k++) {
      Vertex t1 = make2(AND, extra, verts[prog + 2 + k]);
      Vertex t2 = make2(AND, comp(extra), mem[8 + k]);
      mod[k] = make2(OR, t1, t2);
      Vertex t3 = make2(AND, extra, verts[prog + 6 + k]);
      Vertex t4 = make2(AND, comp(extra), mem[12 + k]);
      dest[k] = make2(OR, t3, t4);
    }

    // Section 22: F prefix (dest_match, old_dest, old_src, register increment, source select).
    startPrefix("F");
    // Section 23: dest_match[r], old_dest[k].
    Vertex[] destMatch = new Vertex[16];
    for (int r = 0; r < intRegs; r++) {
      Vertex t1 = evenComp(r, dest[0]);
      Vertex t2 = evenComp(r >> 1, dest[1]);
      Vertex t3 = evenComp(r >> 2, dest[2]);
      Vertex t4 = evenComp(r >> 3, dest[3]);
      destMatch[r] = make4(AND, t1, t2, t3, t4);
    }
    Vertex[] tmp = new Vertex[16];
    Vertex[] oldDest = new Vertex[16];
    for (int k = 0; k < 16; k++) {
      for (int r = 0; r < intRegs; r++) {
        tmp[r] = make2(AND, destMatch[r], verts[reg[r] + k]);
      }
      oldDest[k] = newVert(OR);
      for (int r = 0; r < intRegs; r++) {
        Gb.newArc(oldDest[k], tmp[r], DELAY);
      }
    }
    // Section 24: old_src[k].
    Vertex[] oldSrc = new Vertex[16];
    for (int k = 0; k < 16; k++) {
      for (int r = 0; r < intRegs; r++) {
        Vertex t1 = verts[reg[r] + k];
        Vertex t2 = evenComp(r, mem[0]);
        Vertex t3 = evenComp(r >> 1, mem[1]);
        Vertex t4 = evenComp(r >> 2, mem[2]);
        Vertex t5 = evenComp(r >> 3, mem[3]);
        tmp[r] = make5(AND, t1, t2, t3, t4, t5);
      }
      oldSrc[k] = newVert(OR);
      for (int r = 0; r < intRegs; r++) {
        Gb.newArc(oldSrc[k], tmp[r], DELAY);
      }
    }
    // Section 39: inc_dest[0..15], the incremented destination address.
    Vertex[] incDest = new Vertex[16];
    makeAdder(4, oldDest, mem, incDest, null, 1);
    Vertex up = make2(AND, incDest[4], comp(mem[3]));
    Vertex down = make2(AND, comp(incDest[4]), mem[3]);
    for (int k = 4; ; k++) {
      comp(up);
      comp(down);
      Vertex t1 = make2(AND, comp(oldDest[k]), up);
      Vertex t2 = make2(AND, comp(oldDest[k]), down);
      Vertex t3 = make3(AND, oldDest[k], comp(up), comp(down));
      incDest[k] = make3(OR, t1, t2, t3);
      if (k < 15) {
        up = make2(AND, up, oldDest[k]);
        down = make2(AND, down, comp(oldDest[k]));
      } else {
        break;
      }
    }
    // Section 22 (continued): source[0..15].
    Vertex[] source = new Vertex[16];
    for (int k = 0; k < 16; k++) {
      Vertex t1 = make2(AND, imm, mem[k < 4 ? k : 3]);
      Vertex t2 = make2(AND, rel, incDest[k]);
      Vertex t3 = make2(AND, dir, oldSrc[k]);
      Vertex t4 = make2(AND, extra, mem[k]);
      source[k] = make4(OR, t1, t2, t3, t4);
    }

    // Section 26: L prefix (log[k], the logical-op result).
    startPrefix("L");
    Vertex[] log = new Vertex[16];
    for (int k = 0; k < 16; k++) {
      Vertex t1 = make3(AND, mod[0], comp(oldDest[k]), comp(source[k]));
      Vertex t2 = make3(AND, mod[1], comp(oldDest[k]), source[k]);
      Vertex t3 = make3(AND, mod[2], oldDest[k], comp(source[k]));
      Vertex t4 = make3(AND, mod[3], oldDest[k], source[k]);
      log[k] = make4(OR, t1, t2, t3, t4);
    }

    // Section 27: C prefix (change, the condition-satisfied signal).
    startPrefix("C");
    {
      Vertex t1 = make3(AND, mod[0], comp(sign), comp(nonzero));
      Vertex t2 = make3(AND, mod[1], comp(sign), nonzero);
      Vertex t3 = make3(AND, mod[2], sign, comp(nonzero));
      Vertex t4 = make3(AND, mod[3], sign, nonzero);
      tmp[0] = make4(OR, t1, t2, t3, t4);
    }
    {
      Vertex t1 = make3(AND, mod[0], comp(carry), comp(overflow));
      Vertex t2 = make3(AND, mod[1], comp(carry), overflow);
      Vertex t3 = make3(AND, mod[2], carry, comp(overflow));
      Vertex t4 = make3(AND, mod[3], carry, overflow);
      tmp[1] = make4(OR, t1, t2, t3, t4);
    }
    Vertex change;
    {
      Vertex t1 = comp(cond);
      Vertex t2 = make2(AND, tmp[0], comp(op));
      Vertex t3 = make2(AND, tmp[1], op);
      change = make3(OR, t1, t2, t3);
    }

    // Section 41: A prefix (shift[k], sum[k], diff[k], the arithmetic-op results).
    startPrefix("A");
    // Section 42: shift[0..17].
    Vertex[] shift = new Vertex[18];
    for (int k = 0; k < 16; k++) {
      Vertex t1 =
          k == 0
              ? make4(AND, source[15], mod[0], comp(mod[1]), comp(mod[2]))
              : make3(AND, source[k - 1], comp(mod[1]), comp(mod[2]));
      Vertex t2 =
          k < 4
              ? make4(AND, source[k + 12], mod[0], mod[1], comp(mod[2]))
              : make3(AND, source[k - 4], mod[1], comp(mod[2]));
      Vertex t3 =
          k == 15
              ? make4(AND, source[15], comp(mod[0]), comp(mod[1]), mod[2])
              : make3(AND, source[k + 1], comp(mod[1]), mod[2]);
      Vertex t4 =
          k > 11
              ? make4(AND, source[15], comp(mod[0]), mod[1], mod[2])
              : make3(AND, source[k + 4], mod[1], mod[2]);
      shift[k] = make4(OR, t1, t2, t3, t4);
    }
    {
      Vertex t1 = make2(AND, comp(mod[2]), source[15]);
      Vertex t2 = make3(AND, comp(mod[2]), mod[1], make3(OR, source[14], source[13], source[12]));
      Vertex t3 = make3(AND, mod[2], comp(mod[1]), source[0]);
      Vertex t4 = make3(AND, mod[2], mod[1], source[3]);
      shift[16] = make4(OR, t1, t2, t3, t4);
    }
    {
      Vertex t1 = make3(AND, comp(mod[2]), comp(mod[1]), makeXor(source[15], source[14]));
      Vertex t2 =
          make4(
              AND,
              comp(mod[2]),
              mod[1],
              make5(OR, source[15], source[14], source[13], source[12], source[11]),
              make5(
                  OR,
                  comp(source[15]),
                  comp(source[14]),
                  comp(source[13]),
                  comp(source[12]),
                  comp(source[11])));
      Vertex t3 = make3(AND, mod[2], mod[1], make3(OR, source[0], source[1], source[2]));
      shift[17] = make3(OR, t1, t2, t3);
    }
    // Section 41 (continued): sum[0..17], diff[0..17].
    Vertex[] sum = new Vertex[18];
    Vertex[] diff = new Vertex[18];
    makeAdder(16, oldDest, source, sum, make2(AND, carry, mod[0]), 1);
    makeAdder(16, oldDest, source, diff, make2(AND, carry, mod[0]), 0);
    {
      Vertex t1 = make3(AND, oldDest[15], source[15], comp(sum[15]));
      Vertex t2 = make3(AND, comp(oldDest[15]), comp(source[15]), sum[15]);
      sum[17] = make2(OR, t1, t2);
    }
    {
      Vertex t1 = make3(AND, oldDest[15], comp(source[15]), comp(diff[15]));
      Vertex t2 = make3(AND, comp(oldDest[15]), source[15], diff[15]);
      diff[17] = make2(OR, t1, t2);
    }

    // Section 29: Z prefix (result[k], the latches' next values, and the output arcs).
    startPrefix("Z");
    // Section 30: next_loc[k], next_next_loc[k].
    Vertex[] nextLoc = new Vertex[16];
    Vertex[] nextNextLoc = new Vertex[16];
    nextLoc[0] = comp(verts[reg[0]]);
    nextNextLoc[0] = verts[reg[0]];
    nextLoc[1] = makeXor(verts[reg[0] + 1], verts[reg[0]]);
    nextNextLoc[1] = comp(verts[reg[0] + 1]);
    {
      Vertex t5 = verts[reg[0] + 1];
      for (int k = 2; k < 16; ) {
        nextLoc[k] = makeXor(verts[reg[0] + k], make2(AND, verts[reg[0]], t5));
        nextNextLoc[k] = makeXor(verts[reg[0] + k], t5);
        t5 = make2(AND, t5, verts[reg[0] + k]);
        k++;
      }
    }
    // Section 31: jump, result[0..17].
    Vertex jump = make5(AND, op, mod[0], mod[1], mod[2], mod[3]);
    Vertex[] result = new Vertex[18];
    for (int k = 0; k < 16; k++) {
      {
        Vertex t1 = make2(AND, comp(op), log[k]);
        Vertex t2 = make2(AND, jump, nextLoc[k]);
        Vertex t3 = make3(AND, op, comp(mod[3]), shift[k]);
        Vertex t4 = make5(AND, op, mod[3], comp(mod[2]), comp(mod[1]), sum[k]);
        Vertex t5 = make5(AND, op, mod[3], comp(mod[2]), mod[1], diff[k]);
        result[k] = make5(OR, t1, t2, t3, t4, t5);
      }
      {
        Vertex t1 = make3(AND, cond, change, source[k]);
        Vertex t2 = make2(AND, comp(cond), result[k]);
        result[k] = make2(OR, t1, t2);
      }
    }
    for (int k = 16; k < 18; k++) {
      Vertex t1 = make3(AND, op, comp(mod[3]), shift[k]);
      Vertex t2 = make5(AND, op, mod[3], comp(mod[2]), comp(mod[1]), sum[k]);
      Vertex t3 = make5(AND, op, mod[3], comp(mod[2]), mod[1], diff[k]);
      result[k] = make3(OR, t1, t2, t3);
    }
    // Section 34: writing the result back into every register but 0.
    {
      Vertex t5 = make2(AND, change, comp(ind));
      for (int r = 1; r < intRegs; r++) {
        Vertex t4 = make2(AND, t5, destMatch[r]);
        for (int k = 0; k < 16; k++) {
          Vertex a1 = make2(AND, t4, result[k]);
          Vertex a2 = make2(AND, comp(t4), verts[reg[r] + k]);
          Vertex t3 = make2(OR, a1, a2);
          latchit(t3, verts[reg[r] + k], runBit);
        }
      }
    }
    // Section 35: the sign, nonzero, overflow and carry flag latches.
    {
      Vertex t5;
      {
        Vertex t1 = make2(AND, sign, cond);
        Vertex t2 = make2(AND, sign, jump);
        Vertex t3 = make2(AND, sign, ind);
        Vertex t4 = make4(AND, result[15], comp(cond), comp(jump), comp(ind));
        t5 = make4(OR, t1, t2, t3, t4);
      }
      latchit(t5, sign, runBit);
      {
        Vertex t1 = make4(OR, result[0], result[1], result[2], result[3]);
        Vertex t2 = make4(OR, result[4], result[5], result[6], result[7]);
        Vertex t3 = make4(OR, result[8], result[9], result[10], result[11]);
        Vertex t4 =
            make4(
                OR,
                result[12],
                result[13],
                result[14],
                make5(AND, make2(OR, nonzero, sign), op, mod[0], comp(mod[2]), mod[3]));
        t5 = make4(OR, t1, t2, t3, t4);
      }
      {
        Vertex t1 = make2(AND, nonzero, cond);
        Vertex t2 = make2(AND, nonzero, jump);
        Vertex t3 = make2(AND, nonzero, ind);
        Vertex t4 = make4(AND, t5, comp(cond), comp(jump), comp(ind));
        t5 = make4(OR, t1, t2, t3, t4);
      }
      latchit(t5, nonzero, runBit);
      {
        Vertex t1 = make2(AND, overflow, cond);
        Vertex t2 = make2(AND, overflow, jump);
        Vertex t3 = make2(AND, overflow, comp(op));
        Vertex t4 = make2(AND, overflow, ind);
        Vertex t5b = make5(AND, result[17], comp(cond), comp(jump), comp(ind), op);
        t5 = make5(OR, t1, t2, t3, t4, t5b);
      }
      latchit(t5, overflow, runBit);
      {
        Vertex t1 = make2(AND, carry, cond);
        Vertex t2 = make2(AND, carry, jump);
        Vertex t3 = make2(AND, carry, comp(op));
        Vertex t4 = make2(AND, carry, ind);
        Vertex t5b = make5(AND, result[16], comp(cond), comp(jump), comp(ind), op);
        t5 = make5(OR, t1, t2, t3, t4, t5b);
      }
      latchit(t5, carry, runBit);
    }
    // Section 32: the program-counter latches, the "extra word" latch, nzs, nzd.
    for (int k = 0; k < 10; k++) {
      latchit(mem[k + 6], verts[prog + k], runBit);
    }
    Vertex nextra;
    {
      Vertex t1 = make2(AND, ind, comp(cond));
      Vertex t2 = make2(AND, ind, change);
      nextra = make2(OR, t1, t2);
    }
    latchit(nextra, extra, runBit);
    Vertex nzs = make4(OR, mem[0], mem[1], mem[2], mem[3]);
    Vertex nzd = make4(OR, dest[0], dest[1], dest[2], dest[3]);
    // Section 36: register 0 (the program counter register) and the output arcs.
    Vertex skip = make2(AND, cond, comp(change));
    Vertex hop = make2(AND, comp(cond), jump);
    Vertex normal;
    {
      Vertex t1 = make2(AND, skip, comp(ind));
      Vertex t2 = make2(AND, skip, nzs);
      Vertex t3 = make3(AND, comp(skip), ind, comp(nzs));
      Vertex t4 = make3(AND, comp(skip), comp(hop), nzd);
      normal = make4(OR, t1, t2, t3, t4);
    }
    Vertex special = make3(AND, comp(skip), ind, nzs);
    for (int k = 0; k < 16; k++) {
      Vertex t5;
      {
        Vertex t1 = make2(AND, normal, nextLoc[k]);
        Vertex t2 = make4(AND, skip, ind, comp(nzs), nextNextLoc[k]);
        Vertex t3 = make3(AND, hop, comp(ind), source[k]);
        Vertex t4 = make5(AND, comp(skip), comp(hop), comp(ind), comp(nzd), result[k]);
        t5 = make4(OR, t1, t2, t3, t4);
      }
      Vertex t4;
      {
        Vertex a1 = make2(AND, special, verts[reg[0] + k]);
        Vertex a2 = make2(AND, comp(special), t5);
        t4 = make2(OR, a1, a2);
      }
      latchit(t4, verts[reg[0] + k], runBit);
      {
        Vertex a1 = make2(AND, special, oldSrc[k]);
        Vertex a2 = make2(AND, comp(special), t5);
        t4 = make2(OR, a1, a2);
      }
      Arc a = Gb.virginArc();
      a.tip = make2(AND, t4, runBit);
      a.next = g.zz.A();
      g.zz.A(a);
    }

    if (nextVert != g.n) {
      Gb.panicCode = Gb.IMPOSSIBLE;
      Gb.troubleCode = 0;
      return null;
    }
    if (Gb.troubleCode != 0) {
      Gb.recycle(g);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return g;
  }

  /**
   * {@code gate_eval(g,inVec,outVec)}: loads {@code inVec} (if non-null) into {@code g}'s first
   * vertices' values, one character per vertex ({@code '0'} or {@code '1'}), then evaluates every
   * later vertex in index order (an input is left as it stands; a latch takes its {@code alt}'s
   * value; {@code &}/{@code |}/{@code ^} combine their arcs' tips; {@code ~} complements its single
   * arc's tip). If {@code outVec} is non-null, it is cleared and filled with one character per
   * output arc. Returns {@code -2} if {@code g} is {@code null}, {@code -1} if some vertex has an
   * unrecognized type, else 0.
   */
  public static long gateEval(Graph g, String inVec, StringBuilder outVec) {
    if (g == null) {
      return -2;
    }
    Vertex[] gv = g.vertices;
    int vi = 0;
    if (inVec != null) {
      for (int i = 0; i < inVec.length() && vi < g.n; ) {
        gv[vi++].x.I = inVec.charAt(i++) - '0';
      }
    }
    for (; vi < g.n; vi++) {
      Vertex v = gv[vi];
      long t;
      switch ((int) v.y.I) {
        case 'I':
          continue;
        case 'L':
          t = v.z.V().x.I;
          break;
        case AND:
          t = 1;
          for (Arc a = v.arcs; a != null; a = a.next) {
            t &= a.tip.x.I;
          }
          break;
        case OR:
          t = 0;
          for (Arc a = v.arcs; a != null; a = a.next) {
            t |= a.tip.x.I;
          }
          break;
        case XOR:
          t = 0;
          for (Arc a = v.arcs; a != null; a = a.next) {
            t ^= a.tip.x.I;
          }
          break;
        case NOT:
          t = 1 - v.arcs.tip.x.I;
          break;
        default:
          return -1;
      }
      v.x.I = t;
    }
    if (outVec != null) {
      outVec.setLength(0);
      for (Arc a = g.zz.A(); a != null; a = a.next) {
        outVec.append((char) ('0' + tipValue(a.tip)));
      }
    }
    return 0;
  }

  /** {@code pr_gate(v)}: one line of {@link #printGates}' output for a single vertex. */
  private static void prGate(Vertex v) {
    out.print(v.name + " = ");
    switch ((int) v.y.I) {
      case 'I':
        out.print("input");
        break;
      case 'L':
        out.print("latch");
        if (v.z.V() != null) {
          out.print("ed " + v.z.V().name);
        }
        break;
      case NOT:
        out.print("~ ");
        break;
      case 'C':
        out.print("constant " + v.z.I);
        break;
      case '=':
        out.print("copy of " + v.z.V().name);
        break;
      default:
        break;
    }
    for (Arc a = v.arcs; a != null; a = a.next) {
      if (a != v.arcs) {
        out.print(" " + (char) v.y.I + " ");
      }
      out.print(a.tip.name);
    }
    out.print("\n");
  }

  /**
   * {@code print_gates(g)}: on {@link #out}, one line per vertex describing its type and inputs,
   * then one {@code "Output "} line per output arc (its tip's name, or {@code 0}/{@code 1} for a
   * boolean tip).
   */
  public static void printGates(Graph g) {
    for (int i = 0; i < g.n; i++) {
      prGate(g.vertices[i]);
    }
    for (Arc a = g.zz.A(); a != null; a = a.next) {
      if (isBooleanOrNull(a.tip)) {
        out.print("Output " + theBoolean(a.tip) + "\n");
      } else {
        out.print("Output " + a.tip.name + "\n");
      }
    }
  }

  /**
   * The value latched into the {@code bits}-bit field ending at (and including) vertex {@code top}.
   */
  private static long latchedField(Graph g, int top, int bits) {
    long m = 0;
    Vertex v = g.vertices[top];
    for (int k = 0; k < bits; k++) {
      m = 2 * m + v.z.V().x.I;
      v = g.vertices[v.index - 1];
    }
    return m;
  }

  /**
   * {@code run_risc(g,rom,size,traceRegs)}: runs the RISC gate graph {@code g} (as built by {@link
   * #risc}) fetching instructions from {@code rom[0..size-1]} until the program counter runs off
   * the end of {@code rom} (unsigned comparison), then fills {@link #riscState}. If {@code
   * traceRegs} is nonzero, prints a header and one line per instruction executed, showing that many
   * registers, the program counter and flags, and the fetched instruction word (or {@code ????} at
   * the terminating address), on {@link #out}. Returns whatever {@link #gateEval} returned if
   * negative, else 0.
   */
  public static long runRisc(Graph g, long[] rom, long size, long traceRegs) {
    if (traceRegs != 0) {
      for (long r = 0; Long.compareUnsigned(r, traceRegs) < 0; r++) {
        out.print(String.format(Locale.ROOT, " r%-2d ", r));
      }
      out.print(" P XSNKV MEM\n");
    }
    long r = gateEval(g, "0", null);
    if (r < 0) {
      return r;
    }
    g.vertices[0].x.I = 1;
    long l;
    while (true) {
      l = 0;
      for (Arc a = g.zz.A(); a != null; a = a.next) {
        l = 2 * l + a.tip.x.I;
      }
      if (traceRegs != 0) {
        for (long rr = 0; Long.compareUnsigned(rr, traceRegs) < 0; rr++) {
          Vertex v = g.vertices[(int) (16 * rr + 47)];
          long m = 0;
          if (v.y.I == 'L') {
            m = latchedField(g, v.index, 16);
          }
          out.print(String.format(Locale.ROOT, "%04x ", m));
        }
        long m = latchedField(g, 26, 10);
        boolean x = g.vertices[31].z.V().x.I != 0;
        boolean s = g.vertices[27].z.V().x.I != 0;
        boolean n = g.vertices[28].z.V().x.I != 0;
        boolean c = g.vertices[29].z.V().x.I != 0;
        boolean o = g.vertices[30].z.V().x.I != 0;
        out.print(
            String.format(
                Locale.ROOT,
                "%03x%c%c%c%c%c ",
                m << 2,
                x ? 'X' : '.',
                s ? 'S' : '.',
                n ? 'N' : '.',
                c ? 'K' : '.',
                o ? 'V' : '.'));
        if (Long.compareUnsigned(l, size) >= 0) {
          out.print("????\n");
        } else {
          out.print(String.format(Locale.ROOT, "%04x\n", rom[(int) l]));
        }
      }
      if (Long.compareUnsigned(l, size) >= 0) {
        break;
      }
      long m = rom[(int) l];
      for (int vi = 1; vi <= 16; vi++, m >>>= 1) {
        g.vertices[vi].x.I = m & 1;
      }
      gateEval(g, null, null);
    }
    if (traceRegs != 0) {
      out.print(String.format(Locale.ROOT, "Execution terminated with memory address %04x.\n", l));
    }
    for (int rr = 0; rr < 16; rr++) {
      Vertex v = g.vertices[16 * rr + 47];
      long m = 0;
      if (v.y.I == 'L') {
        m = latchedField(g, v.index, 16);
      }
      riscState[rr] = m;
    }
    long m = latchedField(g, 26, 10);
    m = 4 * m + g.vertices[31].z.V().x.I;
    m = 2 * m + g.vertices[27].z.V().x.I;
    m = 2 * m + g.vertices[28].z.V().x.I;
    m = 2 * m + g.vertices[29].z.V().x.I;
    m = 2 * m + g.vertices[30].z.V().x.I;
    riscState[16] = m;
    riscState[17] = l;
    return 0;
  }

  /**
   * The outcome of {@link #reduce}'s per-vertex switch (section 53 of {@code gb_gates.w}), applied
   * afterward: {@code MAKE_1}/{@code MAKE_0} set {@link Vertex#z}'s {@code I} (the constant's bit)
   * to 1 or 0 and fall into {@code MAKE_CONSTANT}; {@code MAKE_CONSTANT} sets the vertex's type to
   * {@code C} and clears its arcs (the case code has already set the bit); {@code MAKE_EQ} sets the
   * type to {@code =} and clears its arcs (the case code has already set {@code alt}); {@code
   * BREAK} leaves the vertex as is; {@code DONE} is {@code BREAK} but additionally skips resetting
   * {@code bar} to {@code null} (the {@code NOT} case just memoized a fresh complement pair there).
   */
  private enum ReduceTail {
    BREAK,
    MAKE_EQ,
    MAKE_1,
    MAKE_0,
    MAKE_CONSTANT,
    DONE
  }

  /**
   * Free list of XOR arcs bypassed by {@link #reduceXor} ({@code avail_arc}, threaded through
   * {@link Arc#a}) and the cursor into the aux NOT-vertex pool ({@code next_vert}/{@code
   * max_next_vert} in {@code reduce}'s section 59), filled seven at a time from {@link
   * Gb#allocAuxVertices}. Both are local to one {@link #reduce} call but shared across every vertex
   * it visits, so they live in one mutable holder rather than static fields.
   */
  private static final class XorPool {
    Arc availArc;
    Vertex[] auxBlock;
    int auxNext;
    int auxMax;
  }

  /**
   * {@code test_single_arg}: common tail of the {@code AND}/{@code OR}/{@code XOR} cases once no
   * further simplification applies — a single remaining arc collapses the vertex to a copy of that
   * arc's tip, otherwise the vertex (with its already-simplified arc list) is left alone.
   */
  private static ReduceTail testSingleArg(Vertex v) {
    if (v.arcs.next != null) {
      return ReduceTail.BREAK;
    }
    v.z.V(v.arcs.tip);
    return ReduceTail.MAKE_EQ;
  }

  /**
   * Section 55 of {@code reduce}: the {@code AND} case. Walks {@code v}'s arcs, resolving {@code =}
   * copies, bypassing (unlinking) arcs that are constant 1 or duplicate an earlier arc, and folding
   * to constant 0 on a constant-0 or self-contradicting (arc vs. its memoized complement) input.
   */
  private static ReduceTail reduceAnd(Vertex v) {
    Arc aa = null;
    for (Arc a = v.arcs; a != null; a = a.next) {
      Vertex u = a.tip;
      if (u.y.I == '=') {
        u = u.z.V();
        a.tip = u;
      }
      boolean bypass;
      if (u.y.I == 'C') {
        if (u.z.I == 0) {
          return ReduceTail.MAKE_0;
        }
        bypass = true;
      } else {
        bypass = false;
        for (Arc b = v.arcs; b != a; b = b.next) {
          if (b.tip == u) {
            bypass = true;
            break;
          }
          if (b.tip == u.w.V()) {
            return ReduceTail.MAKE_0;
          }
        }
      }
      if (bypass) {
        if (aa != null) {
          aa.next = a.next;
        } else {
          v.arcs = a.next;
        }
      } else {
        aa = a;
      }
    }
    if (v.arcs == null) {
      return ReduceTail.MAKE_1;
    }
    return testSingleArg(v);
  }

  /**
   * Section 56 of {@code reduce}: the {@code OR} case, the AND/OR dual of {@link #reduceAnd} —
   * bypasses constant-0 and duplicate arcs, folds to constant 1 on a constant-1 or
   * self-contradicting input.
   */
  private static ReduceTail reduceOr(Vertex v) {
    Arc aa = null;
    for (Arc a = v.arcs; a != null; a = a.next) {
      Vertex u = a.tip;
      if (u.y.I == '=') {
        u = u.z.V();
        a.tip = u;
      }
      boolean bypass;
      if (u.y.I == 'C') {
        if (u.z.I != 0) {
          return ReduceTail.MAKE_1;
        }
        bypass = true;
      } else {
        bypass = false;
        for (Arc b = v.arcs; b != a; b = b.next) {
          if (b.tip == u) {
            bypass = true;
            break;
          }
          if (b.tip == u.w.V()) {
            return ReduceTail.MAKE_1;
          }
        }
      }
      if (bypass) {
        if (aa != null) {
          aa.next = a.next;
        } else {
          v.arcs = a.next;
        }
      } else {
        aa = a;
      }
    }
    if (v.arcs == null) {
      return ReduceTail.MAKE_0;
    }
    return testSingleArg(v);
  }

  /**
   * Sections 57-59 of {@code reduce}: the {@code XOR} case. A constant-1 input, or a pair of arcs
   * whose tips are memoized complements, flips a running parity {@code cmp}; a duplicate pair of
   * arcs cancels outright. Every folded-away arc is pushed onto {@code pool.availArc} (arcs' {@link
   * Arc#a} field is reused as this free list's link, per the C's {@code avail_arc}). If the parity
   * ends up odd, one remaining arc's tip is complemented (reusing an already-memoized complement
   * when one of the tips has one, else building a fresh NOT gate from {@code pool}'s aux vertices
   * and a freed arc) to absorb it, transcribing the C's {@code aa}/{@code bb} bookkeeping
   * literally.
   */
  private static ReduceTail reduceXor(Vertex v, XorPool pool) {
    long cmp = 0;
    Arc aa = null;
    for (Arc a = v.arcs; a != null; a = a.next) {
      Vertex u = a.tip;
      if (u.y.I == '=') {
        u = u.z.V();
        a.tip = u;
      }
      if (u.y.I == 'C') {
        if (u.z.I != 0) {
          cmp = 1 - cmp;
        }
      } else {
        Arc bb = null;
        boolean matched = false;
        for (Arc b = v.arcs; b != a; b = b.next) {
          boolean hit = b.tip == u;
          if (!hit && b.tip == u.w.V()) {
            cmp = 1 - cmp;
            hit = true;
          }
          if (hit) {
            if (bb != null) {
              bb.next = b.next;
            } else {
              v.arcs = b.next;
            }
            matched = true;
            break;
          }
          bb = b;
        }
        if (!matched) {
          aa = a;
          continue;
        }
      }
      // bypass_xor: unlink `a` itself and push it onto the free list.
      if (aa != null) {
        aa.next = a.next;
      } else {
        v.arcs = a.next;
      }
      a.a.A(pool.availArc);
      pool.availArc = a;
    }
    if (v.arcs == null) {
      v.z.I = cmp;
      return ReduceTail.MAKE_CONSTANT;
    }
    if (cmp != 0) {
      Arc a = v.arcs;
      Vertex u;
      while (true) {
        u = a.tip;
        if (u.w.V() != null) {
          break;
        }
        if (a.next == null) {
          if (pool.auxNext == pool.auxMax) {
            pool.auxBlock = Gb.allocAuxVertices(7);
            pool.auxNext = 0;
            pool.auxMax = 7;
          }
          Vertex nv = pool.auxBlock[pool.auxNext++];
          nv.y.I = NOT;
          nv.name = u.name + "~";
          nv.arcs = pool.availArc;
          pool.availArc.tip = u;
          pool.availArc = pool.availArc.a.A();
          nv.arcs.next = null;
          nv.w.V(u);
          nv.x.V(u.x.V());
          u.x.V(nv);
          u.w.V(nv);
          break;
        }
        a = a.next;
      }
      a.tip = u.w.V();
    }
    return testSingleArg(v);
  }

  /**
   * {@code reduce(g)}: the constant-propagation and duplicate-elimination pass every graph built by
   * {@link #prod} or trimmed by {@link #partialGates} goes through. Repeatedly simplifies every
   * vertex (folding constants, copies, contradictions and repeated inputs, per {@link ReduceTail})
   * until a pass finds no new constants; latches whose {@code alt} became a copy or a constant are
   * folded too. The vertices still reachable from an output are then compacted into a fresh graph
   * (recycling {@code g}), preserving relative order and inserting a one-arc {@code OR} buffer
   * ahead of any latch whose new target would otherwise land before the latch itself. Returns
   * {@code null} and sets {@link Gb#panicCode} to {@link Gb#MISSING_OPERAND} if {@code g} is {@code
   * null}.
   */
  private static Graph reduce(Graph g) {
    if (g == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    // See ADR 0021's amendment: the C's sentinel is one-past-the-end of vertices, borrowed from
    // gb_new_graph's extra_n headroom; the Java sentinel is never guaranteed that slot (extra_n
    // may be 0, and a restored graph has none at all), so it is a synthetic vertex instead, built
    // the same way Gb.ONE is: an unregistered vertex from Gb.allocAuxVertices, only ever compared
    // and stored, never read through.
    Vertex sentinel = Gb.allocAuxVertices(1)[0];
    XorPool pool = new XorPool();
    long n = 0;
    while (true) {
      Vertex latchPtr = null;
      for (int vi = 0; vi < g.n; vi++) {
        Vertex v = g.vertices[vi];
        ReduceTail tail;
        switch ((int) v.y.I) {
          case 'L':
            v.v.V(latchPtr);
            latchPtr = v;
            tail = ReduceTail.BREAK;
            break;
          case 'I':
          case 'C':
            tail = ReduceTail.BREAK;
            break;
          case '=':
            {
              Vertex u = v.z.V();
              if (u.y.I == '=') {
                v.z.V(u.z.V());
                tail = ReduceTail.BREAK;
              } else if (u.y.I == 'C') {
                v.z.I = u.z.I;
                tail = ReduceTail.MAKE_CONSTANT;
              } else {
                tail = ReduceTail.BREAK;
              }
              break;
            }
          case NOT:
            {
              Vertex u = v.arcs.tip;
              if (u.y.I == '=') {
                u = u.z.V();
                v.arcs.tip = u;
              }
              if (u.y.I == 'C') {
                v.z.I = 1 - u.z.I;
                tail = ReduceTail.MAKE_CONSTANT;
              } else if (u.w.V() != null) {
                v.z.V(u.w.V());
                tail = ReduceTail.MAKE_EQ;
              } else {
                u.w.V(v);
                v.w.V(u);
                tail = ReduceTail.DONE;
              }
              break;
            }
          case AND:
            tail = reduceAnd(v);
            break;
          case OR:
            tail = reduceOr(v);
            break;
          case XOR:
            tail = reduceXor(v, pool);
            break;
          default:
            tail = ReduceTail.BREAK;
        }
        switch (tail) {
          case MAKE_1:
            v.z.I = 1;
            v.y.I = 'C';
            v.arcs = null;
            break;
          case MAKE_0:
            v.z.I = 0;
            v.y.I = 'C';
            v.arcs = null;
            break;
          case MAKE_CONSTANT:
            v.y.I = 'C';
            v.arcs = null;
            break;
          case MAKE_EQ:
            v.y.I = '=';
            v.arcs = null;
            break;
          case BREAK:
          case DONE:
            break;
        }
        if (tail != ReduceTail.DONE) {
          v.w.V(null);
        }
        v.x.V(vi + 1 == g.n ? sentinel : g.vertices[vi + 1]);
      }
      boolean noConstantsYet = true;
      for (Vertex v = latchPtr; v != null; v = v.v.V()) {
        Vertex u = v.z.V();
        if (u.y.I == '=') {
          v.z.V(u.z.V());
        } else if (u.y.I == 'C') {
          v.y.I = 'C';
          v.z.I = u.z.I;
          noConstantsYet = false;
        }
      }
      if (noConstantsYet) {
        break;
      }
    }
    // Section 60-61: count and mark (via the shared bar/lnk slot) every vertex still reachable
    // from an output, walking the foo chain (main array order, with aux NOT vertices spliced in).
    for (Vertex v = g.vertices[0]; v != sentinel; v = v.x.V()) {
      v.w.V(null);
    }
    for (Arc a = g.zz.A(); a != null; a = a.next) {
      Vertex v = a.tip;
      if (isBooleanOrNull(v)) {
        continue;
      }
      if (v.y.I == '=') {
        v = v.z.V();
        a.tip = v;
      }
      if (v.y.I == 'C') {
        a.tip = v.z.I == 1 ? Gb.ONE : null;
        continue;
      }
      if (v.w.V() == null) {
        v.w.V(sentinel);
        do {
          n++;
          Arc b = v.arcs;
          if (v.y.I == 'L') {
            Vertex u = v.z.V();
            // See ADR 0021's amendment for why comparing Vertex.index here is well-defined only
            // under an invariant, not in general.
            if (u.index < v.index) {
              n++;
            }
            if (u.w.V() == null) {
              u.w.V(v.w.V());
              v = u;
            } else {
              v = v.w.V();
            }
          } else {
            v = v.w.V();
          }
          for (; b != null; b = b.next) {
            Vertex u = b.tip;
            if (u.w.V() == null) {
              u.w.V(v);
              v = u;
            }
          }
        } while (v != sentinel);
      }
    }
    // Section 62-65: compact the reachable vertices into a fresh graph, preserving relative order.
    Graph ng = Gb.newGraph(n);
    if (ng == null) {
      Gb.recycle(g);
      Gb.panicCode = Gb.NO_ROOM + 2;
      Gb.troubleCode = 0;
      return null;
    }
    ng.id = g.id;
    ng.utilTypes = "ZZZIIVZZZZZZZA";
    int next = 0;
    Vertex latchChain = null;
    for (Vertex v = g.vertices[0]; v != sentinel; v = v.x.V()) {
      if (v.w.V() != null) {
        Vertex u = ng.vertices[next++];
        v.w.V(u);
        u.name = v.name;
        u.y.I = v.y.I;
        if (v.y.I == 'L') {
          u.z.V(latchChain);
          latchChain = v;
        }
        Arc prev = null;
        Arc cur = v.arcs;
        while (cur != null) {
          Arc nxt = cur.next;
          cur.next = prev;
          prev = cur;
          cur = nxt;
        }
        v.arcs = prev;
        for (Arc a = v.arcs; a != null; a = a.next) {
          Gb.newArc(u, a.tip.w.V(), a.len);
        }
      }
    }
    while (latchChain != null) {
      Vertex u = latchChain.w.V();
      Vertex v = u.z.V();
      u.z.V(latchChain.z.V().w.V());
      latchChain = v;
      if (u.z.V().index < u.index) {
        Vertex target = u.z.V();
        Vertex buffer = ng.vertices[next++];
        u.z.V(buffer);
        buffer.name = target.name + ">" + u.name;
        buffer.y.I = OR;
        Gb.newArc(buffer, target, DELAY);
        Gb.newArc(buffer, target, DELAY);
      }
    }
    Arc prevOut = null;
    Arc curOut = g.zz.A();
    while (curOut != null) {
      Arc nxt = curOut.next;
      curOut.next = prevOut;
      prevOut = curOut;
      curOut = nxt;
    }
    g.zz.A(prevOut);
    for (Arc a = g.zz.A(); a != null; a = a.next) {
      Arc b = Gb.virginArc();
      b.tip = isBooleanOrNull(a.tip) ? a.tip : a.tip.w.V();
      b.next = ng.zz.A();
      ng.zz.A(b);
    }
    Gb.recycle(g);
    return ng;
  }

  /** {@code a_pos(j)}: {@link #prod}'s row index for column {@code j} of its adder network. */
  private static long aPos(long j, long m) {
    return j < m ? j + 1 : m + 5 * ((j - m) >> 1) + 3 + (((j - m) & 1) << 1);
  }

  /**
   * {@code prod(m,n)}: an {@code m}-by-{@code n} unsigned binary multiplier (clamped to at least 2
   * bits each way, as unsigned), built as a Wallace-style carry-save adder tree of partial products
   * and always run through {@link #reduce} before being returned. Returns {@code null} and sets
   * {@link Gb#panicCode} to {@link Gb#NO_ROOM} if the underlying {@link Gb#newGraph} allocation
   * fails, or to {@link Gb#ALLOC_FAULT} if building overflows the graph's storage.
   */
  public static Graph prod(long m, long n) {
    if (Long.compareUnsigned(m, 2) < 0) {
      m = 2;
    }
    if (Long.compareUnsigned(n, 2) < 0) {
      n = 2;
    }
    long mpn = m + n;
    long f = 4;
    long j = 3;
    long k = 5;
    while (Long.compareUnsigned(k, mpn) < 0) {
      k = k + j;
      j = k - j;
      f++;
    }
    Graph g = Gb.newGraph((6 * m - 7 + 3 * f) * mpn);
    if (g == null) {
      Gb.panicCode = Gb.NO_ROOM;
      Gb.troubleCode = 0;
      return null;
    }
    g.id = "prod(" + Long.toUnsignedString(m) + "," + Long.toUnsignedString(n) + ")";
    g.utilTypes = UTIL_TYPES;

    long[] flog = new long[(int) mpn + 1];
    long[] down = new long[(int) mpn + 1];
    long[] anc = new long[(int) f + 1];
    Vertex[] w = new Vertex[(int) mpn];
    Vertex[] c = new Vertex[(int) (f * mpn)];

    verts = g.vertices;
    nextVert = 0;
    startPrefix("X");
    int x = firstOf((int) m, 'I');
    startPrefix("Y");
    int y = firstOf((int) n, 'I');

    // Section 72: the m*n AND partial-product matrix, padded with 0 constants each row.
    for (long jj = 0; jj < m; jj++) {
      numericPrefix('A', jj);
      for (long kk = 0; kk < jj; kk++) {
        newVert('C').z.I = 0;
      }
      for (long kk = 0; kk < n; kk++) {
        make2(AND, verts[x + (int) jj], verts[y + (int) kk]);
      }
      for (long kk = jj + n; kk < mpn; kk++) {
        newVert('C').z.I = 0;
      }
    }
    // Section 73: the carry-save reduction rows (P/Q/R half-adders), two rows shorter than m.
    for (long jj = 0; jj < m - 2; jj++) {
      int alpha = (int) (aPos(3 * jj, m) * mpn);
      int beta = (int) (aPos(3 * jj + 1, m) * mpn);
      numericPrefix('P', jj);
      for (long kk = 0; kk < mpn; kk++) {
        make2(XOR, verts[alpha + (int) kk], verts[beta + (int) kk]);
      }
      numericPrefix('Q', jj);
      for (long kk = 0; kk < mpn; kk++) {
        make2(AND, verts[alpha + (int) kk], verts[beta + (int) kk]);
      }
      alpha = nextVert - 2 * (int) mpn;
      beta = (int) (aPos(3 * jj + 2, m) * mpn);
      numericPrefix('A', m + 2 * jj);
      for (long kk = 0; kk < mpn; kk++) {
        make2(XOR, verts[alpha + (int) kk], verts[beta + (int) kk]);
      }
      numericPrefix('R', jj);
      for (long kk = 0; kk < mpn; kk++) {
        make2(AND, verts[alpha + (int) kk], verts[beta + (int) kk]);
      }
      alpha = nextVert - 3 * (int) mpn;
      beta = nextVert - (int) mpn;
      numericPrefix('A', m + 2 * jj + 1);
      newVert('C').z.I = 0;
      for (long kk = 0; kk < mpn - 1; kk++) {
        make2(OR, verts[alpha + (int) kk], verts[beta + (int) kk]);
      }
    }
    // Section 74: the final row pair's XOR/AND (sum/carry) outputs, U and V.
    int alpha74 = (int) (aPos(3 * m - 6, m) * mpn);
    int beta74 = (int) (aPos(3 * m - 5, m) * mpn);
    startPrefix("U");
    for (long kk = 0; kk < mpn; kk++) {
      make2(XOR, verts[alpha74 + (int) kk], verts[beta74 + (int) kk]);
    }
    startPrefix("V");
    for (long kk = 0; kk < mpn; kk++) {
      make2(AND, verts[alpha74 + (int) kk], verts[beta74 + (int) kk]);
    }
    // Section 76: flog[l]/down[l], tables of a Fibonacci-like "ancestor" structure used by the
    // final ripple-carry stage below to find each column's earlier partial sums in O(log) steps.
    flog[1] = 0;
    flog[2] = 2;
    down[1] = 0;
    down[2] = 1;
    long fi = 3;
    long fj = 2;
    long fk = 3;
    for (long ll = 3; ll <= mpn; ll++) {
      if (ll > fk) {
        fk = fk + fj;
        fj = fk - fj;
        fi++;
      }
      flog[(int) ll] = fi;
      down[(int) ll] = ll - fk + fj;
    }
    // Section 78-82: the W chain, one final ripple-carry adder tying every U/V column together.
    int vv = nextVert - (int) mpn;
    int uu = vv - (int) mpn;
    startPrefix("W");
    Vertex w0 = newVert('C');
    w0.z.I = 0;
    w[0] = w0;
    Vertex w1 = newVert('=');
    w1.z.V(verts[vv]);
    w[1] = w1;
    for (long kk = 2; kk < mpn; kk++) {
      int l = 0;
      long anceJ = kk;
      while (true) {
        anc[l] = anceJ;
        if (anceJ == 2) {
          break;
        }
        l++;
        anceJ = down[(int) anceJ];
      }
      long ii = 1;
      Vertex cc = verts[vv + (int) kk - 1];
      Vertex dd = verts[uu + (int) kk - 1];
      Vertex v = null;
      long ff = 0;
      while (true) {
        long jVal = anc[l];
        v = verts[nextVert++];
        v.name = "B" + kk + ":" + jVal;
        v.y.I = AND;
        Gb.newArc(v, dd, DELAY);
        ff = flog[(int) (jVal - ii)];
        Gb.newArc(
            v,
            ff > 0 ? c[(int) (kk - ii + (ff - 2) * mpn)] : verts[vv + (int) (kk - ii) - 1],
            DELAY);
        if (l != 0) {
          v = verts[nextVert++];
          v.name = "C" + kk + ":" + jVal;
          v.y.I = OR;
        } else {
          v = newVert(OR);
        }
        Gb.newArc(v, cc, DELAY);
        Gb.newArc(v, verts[nextVert - 2], DELAY);
        if (flog[(int) jVal] < flog[(int) jVal + 1]) {
          c[(int) (kk + (flog[(int) jVal] - 2) * mpn)] = v;
        }
        if (l == 0) {
          break;
        }
        cc = v;
        v = verts[nextVert++];
        v.name = "D" + kk + ":" + jVal;
        v.y.I = AND;
        Gb.newArc(v, dd, DELAY);
        Gb.newArc(
            v,
            ff > 0
                ? verts[c[(int) (kk - ii + (ff - 2) * mpn)].index + 1]
                : verts[uu + (int) (kk - ii) - 1],
            DELAY);
        dd = v;
        ii = jVal;
        l--;
      }
      w[(int) kk] = v;
    }
    // Section 83: the m+n output XOR gates (each column's running sum against its U bit).
    startPrefix("Z");
    for (long kk = 0; kk < mpn; kk++) {
      Arc a = Gb.virginArc();
      a.tip = make2(XOR, verts[uu + (int) kk], w[(int) kk]);
      a.next = g.zz.A();
      g.zz.A(a);
    }
    g.n = nextVert;
    if (Gb.troubleCode != 0) {
      Gb.recycle(g);
      Gb.panicCode = Gb.ALLOC_FAULT;
      Gb.troubleCode = 0;
      return null;
    }
    return reduce(g);
  }

  /**
   * {@code partial_gates(g,r,prob,seed,buf)}: seeds {@link Flip} from {@code seed}, then, for every
   * input vertex from index {@code r} (unsigned) on, forces it to a random constant with
   * probability {@code 1 - prob/2^31} (unsigned {@code prob}), else leaves it an input; stops early
   * at the first non-input, non-constant, non-copy vertex. If {@code buf} is non-null, it is
   * cleared and receives one character per input vertex visited: the forced bit, or {@code '*'} if
   * left alone. The result is always run through {@link #reduce}, and (unless that fails) renamed
   * {@code partial_gates(<g.id>,r,prob,seed)} (the old id truncated with {@code "..."} past 54
   * characters). Returns {@code null} and sets {@link Gb#panicCode} to {@link Gb#MISSING_OPERAND}
   * if {@code g} is {@code null}.
   */
  public static Graph partialGates(Graph g, long r, long prob, long seed, StringBuilder buf) {
    if (g == null) {
      Gb.panicCode = Gb.MISSING_OPERAND;
      Gb.troubleCode = 0;
      return null;
    }
    Flip.initRand(seed);
    if (buf != null) {
      buf.setLength(0);
    }
    for (long vi = r; Long.compareUnsigned(vi, g.n) < 0; vi++) {
      Vertex v = g.vertices[(int) vi];
      switch ((int) v.y.I) {
        case 'C':
        case '=':
          continue;
        case 'I':
          if (Long.compareUnsigned(Flip.nextRand() >> 15, prob) >= 0) {
            v.y.I = 'C';
            v.z.I = Flip.nextRand() >> 30;
            if (buf != null) {
              buf.append((char) ('0' + v.z.I));
            }
          } else if (buf != null) {
            buf.append('*');
          }
          break;
        default:
          vi = g.n; // stop the loop, matching the C's `goto done`.
      }
      if (vi == g.n) {
        break;
      }
    }
    String oldId = g.id;
    g = reduce(g);
    if (g != null) {
      String s = oldId;
      if (s.length() > 54) {
        s = s.substring(0, 51) + "...";
      }
      g.id =
          "partial_gates("
              + s
              + ","
              + Long.toUnsignedString(r)
              + ","
              + Long.toUnsignedString(prob)
              + ","
              + seed
              + ")";
    }
    return g;
  }
}
