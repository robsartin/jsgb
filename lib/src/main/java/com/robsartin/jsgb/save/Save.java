package com.robsartin.jsgb.save;

import com.robsartin.jsgb.graph.Arc;
import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import com.robsartin.jsgb.graph.Util;
import com.robsartin.jsgb.graph.Vertex;
import com.robsartin.jsgb.io.GbIo;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of {@code gb_save}: writes a graph to a portable ASCII file and reads one back. The file
 * numbers vertices and arcs by their position in the graph's storage blocks, so a graph restored
 * from a file has the same structure, though string identity is not preserved.
 *
 * <p>Boolean vertex slots: a {@code V}-typed slot with {@code ref == null} and {@code I == 1} (the
 * {@code gb_gates} constant ONE) is written as {@code 1} and read back the same way.
 */
public final class Save {

  /** A util-type character outside {@code ZIVSA}; the offending type was corrected to {@code Z}. */
  public static final long BAD_TYPE_CODE = 0x1;

  /** A string or id longer than {@link #MAX_SV_STRING} or {@link #MAX_SV_ID} was truncated. */
  public static final long STRING_TOO_LONG = 0x2;

  /** A {@code V} or {@code A} slot pointed outside the graph's own storage blocks. */
  public static final long ADDR_NOT_IN_DATA_AREA = 0x4;

  /** A {@code V} or {@code A} slot pointed into a block save_graph classified differently. */
  public static final long ADDR_IN_MIXED_BLOCK = 0x8;

  /** A string held a byte outside the GraphBase alphabet; it was written as {@code '?'}. */
  public static final long BAD_STRING_CHAR = 0x10;

  /** A slot hidden by a {@code Z} util type actually held nonzero data, which was dropped. */
  public static final long IGNORED_DATA = 0x20;

  /** Longest string {@code save_graph} writes without truncation. */
  public static final int MAX_SV_STRING = 4095;

  /** Longest graph id {@code save_graph} writes without truncation. */
  public static final int MAX_SV_ID = 154;

  private static final int UNK = 0;
  private static final int ARK = 1;
  private static final int VRT = 2;
  private static final int MXT = 3;

  /** A storage block of the graph being saved (the C {@code block_rep}). */
  private static final class Block {
    final Object[] slots; // Vertex[] or Arc[]
    int cat = UNK;
    long offset;
    boolean expl;

    Block(Object[] slots) {
      this.slots = slots;
    }
  }

  /** Where an object lives: its block and slot index. */
  private record Where(Block block, int index) {}

  // ---- state shared by save_graph's helpers (C statics) ----------------------------------
  private static long anomalies;
  private static List<Block> blocks;
  private static IdentityHashMap<Object, Where> where;
  private static boolean commaExpected;
  private static final StringBuilder buffer = new StringBuilder(); // the current output line
  private static String itemBuf; // the C item_buf
  private static long magic;
  private static OutputStream saveFile;

  // ---- state shared by restore_graph's helpers ---------------------------------------------
  private static Vertex[] verts;
  private static int lastVert;
  private static Arc[] arcs;
  private static int lastArc;

  private Save() {}

  // ======================================================================================
  // save_graph
  // ======================================================================================

  /** {@code save_graph(g, f)}: writes {@code g} to file {@code f}; returns the anomaly bits. */
  public static long saveGraph(Graph g, String f) {
    if (g == null || g.vertices == null) {
      return -1;
    }
    anomalies = 0;
    collectBlocks(g); // section 23
    classifyAll(g); // section 27
    long[] counts = assignOffsets(g); // sections 32, 33
    long n = counts[0];
    long m = counts[1];
    try (OutputStream out = Files.newOutputStream(Path.of(f))) {
      saveFile = out;
      buffer.setLength(0);
      magic = 0;
      // section 38: header line, not checksummed
      StringBuilder head = new StringBuilder("* GraphBase graph (util_types ");
      for (int i = 0; i < 14; i++) {
        char c = g.utilTypes.charAt(i);
        head.append(c == 'Z' || c == 'I' || c == 'V' || c == 'S' || c == 'A' ? c : 'Z');
      }
      head.append(',').append(n).append("V,").append(m).append("A)\n");
      write(head.toString());
      // section 41: the graph record
      prepareString(g.id);
      if (g.id.length() > MAX_SV_ID) {
        itemBuf = itemBuf.substring(0, MAX_SV_ID + 1) + "\"";
        anomalies |= STRING_TOO_LONG;
      }
      moveItem();
      commaExpected = true;
      translate(g.n, null, 'I');
      translate(g.m, null, 'I');
      translate(g.uu, g.utilTypes.charAt(8));
      translate(g.vv, g.utilTypes.charAt(9));
      translate(g.ww, g.utilTypes.charAt(10));
      translate(g.xx, g.utilTypes.charAt(11));
      translate(g.yy, g.utilTypes.charAt(12));
      translate(g.zz, g.utilTypes.charAt(13));
      flushout();
      // section 42/43: vertices, the main block first, then the other vertex blocks
      write("* Vertices\n");
      for (Block b : blocks) {
        if (b.cat == VRT && b.offset == 0) {
          writeVertices(g, b);
        }
      }
      for (Block b : blocks) {
        if (b.cat == VRT && b.offset != 0) {
          writeVertices(g, b);
        }
      }
      // section 44: arcs
      write("* Arcs\n");
      for (Block b : blocks) {
        if (b.cat == ARK) {
          for (Object o : b.slots) {
            Arc a = (Arc) o;
            commaExpected = false;
            translate(0, a.tip, 'V');
            translate(0, a.next, 'A');
            translate(a.len, null, 'I');
            translate(a.a, g.utilTypes.charAt(6));
            translate(a.b, g.utilTypes.charAt(7));
            flushout();
          }
        }
      }
      write("* Checksum " + magic + "\n"); // section 45
      writeWarnings(); // section 46
    } catch (IOException e) {
      return -2;
    } finally {
      saveFile = null;
      blocks = null;
      where = null;
    }
    return anomalies;
  }

  private static void writeVertices(Graph g, Block b) throws IOException {
    for (Object o : b.slots) {
      Vertex v = (Vertex) o;
      commaExpected = false;
      translate(0, v.name, 'S');
      translate(0, v.arcs, 'A');
      translate(v.u, g.utilTypes.charAt(0));
      translate(v.v, g.utilTypes.charAt(1));
      translate(v.w, g.utilTypes.charAt(2));
      translate(v.x, g.utilTypes.charAt(3));
      translate(v.y, g.utilTypes.charAt(4));
      translate(v.z, g.utilTypes.charAt(5));
      flushout();
    }
  }

  /** Section 23: the graph's vertex and arc blocks in allocation order, indexed by object. */
  private static void collectBlocks(Graph g) {
    blocks = new ArrayList<>();
    where = new IdentityHashMap<>();
    register(new Block(g.vertices));
    for (Vertex[] vb : g.extraVertexBlocks()) {
      register(new Block(vb));
    }
    for (Arc[] ab : g.arcBlocks()) {
      register(new Block(ab));
    }
  }

  private static void register(Block b) {
    blocks.add(b);
    for (int i = 0; i < b.slots.length; i++) {
      where.put(b.slots[i], new Where(b, i));
    }
  }

  /** Section 27: mark blocks reachable through typed pointers, transitively. */
  private static void classifyAll(Graph g) {
    if (g.vertices.length > 0) {
      lookup(g.vertices[0], 0, 'V'); // lookup(g->vertices, 'V')
    }
    lookup(g.uu, g.utilTypes.charAt(8));
    lookup(g.vv, g.utilTypes.charAt(9));
    lookup(g.ww, g.utilTypes.charAt(10));
    lookup(g.xx, g.utilTypes.charAt(11));
    lookup(g.yy, g.utilTypes.charAt(12));
    lookup(g.zz, g.utilTypes.charAt(13));
    boolean activity;
    do {
      activity = false;
      for (Block b : blocks) {
        if (b.cat == VRT && !b.expl) {
          for (Object o : b.slots) { // section 28
            if (b.cat != VRT) {
              break;
            }
            Vertex v = (Vertex) o;
            lookup(v.arcs, 0, 'A');
            lookup(v.u, g.utilTypes.charAt(0));
            lookup(v.v, g.utilTypes.charAt(1));
            lookup(v.w, g.utilTypes.charAt(2));
            lookup(v.x, g.utilTypes.charAt(3));
            lookup(v.y, g.utilTypes.charAt(4));
            lookup(v.z, g.utilTypes.charAt(5));
          }
        } else if (b.cat == ARK && !b.expl) {
          for (Object o : b.slots) { // section 29
            if (b.cat != ARK) {
              break;
            }
            Arc a = (Arc) o;
            lookup(a.tip, 0, 'V');
            lookup(a.next, 0, 'A');
            lookup(a.a, g.utilTypes.charAt(6));
            lookup(a.b, g.utilTypes.charAt(7));
          }
        } else {
          continue;
        }
        b.expl = true;
        activity = true;
      }
    } while (activity);
  }

  private static void lookup(Util u, char t) {
    lookup(u.ref, u.I, t);
  }

  /** Sections 25/26: classify the block that {@code ref} points into, if any. */
  private static void lookup(Object ref, long i, char t) {
    int tcat;
    switch (t) {
      case 'V' -> {
        if (ref == null && i == 1) {
          return; // the boolean ONE
        }
        tcat = VRT;
      }
      case 'A' -> tcat = ARK;
      default -> {
        return;
      }
    }
    if (ref == null) {
      return;
    }
    Where w = where.get(ref);
    if (w == null) {
      return; // not in the data area; translate() reports it
    }
    if (w.block().cat == UNK) {
      w.block().cat = tcat;
    } else if (w.block().cat != tcat) {
      w.block().cat = MXT;
    }
  }

  /** Sections 32/33: number the vertices (main block first) and the arcs; returns {n, m}. */
  private static long[] assignOffsets(Graph g) {
    long n = g.vertices.length;
    long m = 0;
    for (Block b : blocks) {
      if (b.cat == VRT) {
        if (b.slots != g.vertices) {
          b.offset = n;
          n += b.slots.length;
        }
      } else if (b.cat == ARK) {
        b.offset = m;
        m += b.slots.length;
      }
    }
    return new long[] {n, m};
  }

  private static void translate(Util u, char t) throws IOException {
    translate(u.I, u.ref, t);
  }

  /** Sections 39/40: append one field to the current line. */
  private static void translate(long i, Object ref, char t) throws IOException {
    if (commaExpected) {
      buffer.append(',');
    } else {
      commaExpected = true;
    }
    int tcat;
    switch (t) {
      case 'I' -> {
        itemBuf = Long.toString(i);
        moveItem();
        return;
      }
      case 'S' -> {
        prepareString((String) ref);
        moveItem();
        return;
      }
      case 'V' -> {
        if (ref == null && i == 1) {
          itemBuf = "1";
          moveItem();
          return;
        }
        tcat = VRT;
      }
      case 'A' -> tcat = ARK;
      case 'Z' -> {
        buffer.setLength(buffer.length() - 1); // the C's buf_ptr--
        if (i != 0 || ref != null) {
          anomalies |= IGNORED_DATA;
        }
        return;
      }
      default -> {
        anomalies |= BAD_TYPE_CODE;
        buffer.setLength(buffer.length() - 1);
        if (i != 0 || ref != null) {
          anomalies |= IGNORED_DATA;
        }
        return;
      }
    }
    itemBuf = "0";
    if (ref != null) {
      Where w = where.get(ref);
      if (w == null) {
        anomalies |= ADDR_NOT_IN_DATA_AREA;
      } else if (w.block().cat != tcat) {
        anomalies |= ADDR_IN_MIXED_BLOCK;
      } else {
        itemBuf = t + Long.toString(w.block().offset + w.index());
      }
    }
    moveItem();
  }

  /** Section 36: quote a string, replacing characters the file format cannot hold. */
  private static void prepareString(String s) {
    StringBuilder b = new StringBuilder("\"");
    if (s != null) {
      byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
      int k = 0;
      for (; k < bytes.length && k < MAX_SV_STRING; k++) {
        int c = bytes[k] & 0xff;
        if (c == '"' || c == '\n' || c == '\\' || GbIo.imapOrd(c) == GbIo.UNEXPECTED_CHAR) {
          anomalies |= BAD_STRING_CHAR;
          b.append('?');
        } else {
          b.append((char) c);
        }
      }
      if (k < bytes.length) {
        anomalies |= STRING_TOO_LONG;
      }
    }
    b.append('"');
    itemBuf = b.toString();
  }

  /** Section 37: append {@code itemBuf} to the line, wrapping with a backslash past column 78. */
  private static void moveItem() throws IOException {
    int l = itemBuf.length();
    if (buffer.length() + l > 78) {
      if (l <= 78) {
        flushout();
      } else {
        if (buffer.length() > 77) {
          flushout();
        }
        int p = 0;
        do {
          while (buffer.length() < 78) {
            buffer.append(itemBuf.charAt(p++));
            l--;
          }
          buffer.append('\\');
          flushout();
        } while (l > 78);
        buffer.append(itemBuf, p, itemBuf.length());
        return;
      }
    }
    buffer.append(itemBuf);
  }

  /** Section 35: end the line, fold it into the checksum, write it. */
  private static void flushout() throws IOException {
    buffer.append('\n');
    String line = buffer.toString();
    magic = GbIo.newChecksum(line.getBytes(StandardCharsets.ISO_8859_1), magic);
    write(line);
    buffer.setLength(0);
  }

  private static void write(String s) throws IOException {
    saveFile.write(s.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Section 46. */
  private static void writeWarnings() throws IOException {
    if (anomalies == 0) {
      return;
    }
    write("> WARNING: I had trouble making this file from the given graph!\n");
    if ((anomalies & BAD_TYPE_CODE) != 0) {
      write(">> The original util_types had to be corrected.\n");
    }
    if ((anomalies & IGNORED_DATA) != 0) {
      write(">> Some data suppressed by Z format was actually nonzero.\n");
    }
    if ((anomalies & STRING_TOO_LONG) != 0) {
      write(">> At least one long string had to be truncated.\n");
    }
    if ((anomalies & BAD_STRING_CHAR) != 0) {
      write(">> At least one string character had to be changed to '?'.\n");
    }
    if ((anomalies & ADDR_NOT_IN_DATA_AREA) != 0) {
      write(">> At least one pointer led out of the data area.\n");
    }
    if ((anomalies & ADDR_IN_MIXED_BLOCK) != 0) {
      write(">> At least one data block had an illegal mixture of records.\n");
    }
    if ((anomalies & (ADDR_NOT_IN_DATA_AREA + ADDR_IN_MIXED_BLOCK)) != 0) {
      write(">>  (Pointers to improper data have been changed to 0.)\n");
    }
    write("> You should be able to read this file with restore_graph,\n");
    write("> but the graph you get won't be exactly like the original.\n");
  }

  // ======================================================================================
  // restore_graph
  // ======================================================================================

  private static final Pattern HEADER =
      Pattern.compile(
          "^\\* GraphBase graph \\(util_types ([ZIVSA]{1,14}),\\s*([-+]?\\d+)V,\\s*([-+]?\\d+)A");
  private static final Pattern CHECKSUM = Pattern.compile("^\\* Checksum\\s*([-+]?\\d+)");

  /** {@code restore_graph(f)}: reads a graph written by {@link #saveGraph}; null on failure. */
  public static Graph restoreGraph(String f) {
    Graph g = null;
    try {
      // section 5
      GbIo.rawOpen(f);
      if (GbIo.ioErrors != 0) {
        return panic(g, Gb.EARLY_DATA_FAULT);
      }
      String types;
      int n;
      int m;
      while (true) {
        String s = GbIo.string(')');
        Matcher hm = HEADER.matcher(s);
        if (hm.find() && hm.group(1).length() == 14) {
          types = hm.group(1);
          n = Integer.parseInt(hm.group(2));
          m = Integer.parseInt(hm.group(3));
          break;
        }
        if (s.isEmpty() || s.charAt(0) != '*') {
          return panic(g, Gb.SYNTAX_ERROR);
        }
      }
      // section 6
      g = Gb.newGraph(0L);
      if (g == null) {
        return panic(null, Gb.NO_ROOM);
      }
      Gb.restoreStorage(g, n, m);
      verts = g.vertices;
      lastVert = n;
      arcs = g.arcBlocks().get(0);
      lastArc = m;
      g.utilTypes = types;
      GbIo.newline();
      if (GbIo.ch() != '"') {
        return panic(g, Gb.SYNTAX_ERROR + 1);
      }
      String id = GbIo.string('"');
      if (id.length() >= 2 && id.endsWith("\\\n")) {
        GbIo.newline();
        id = id.substring(0, id.length() - 2) + GbIo.string('"');
      }
      g.id = id;
      if (GbIo.ch() != '"') {
        return panic(g, Gb.SYNTAX_ERROR + 2);
      }
      // section 15
      Gb.panicCode = 0;
      commaExpected = true;
      Util tmp = new Util();
      if (fillField(tmp, 'I') != 0) {
        return sorry(g);
      }
      g.n = tmp.I;
      if (fillField(tmp, 'I') != 0) {
        return sorry(g);
      }
      g.m = tmp.I;
      if (fillField(g.uu, types.charAt(8)) != 0
          || fillField(g.vv, types.charAt(9)) != 0
          || fillField(g.ww, types.charAt(10)) != 0
          || fillField(g.xx, types.charAt(11)) != 0
          || fillField(g.yy, types.charAt(12)) != 0
          || fillField(g.zz, types.charAt(13)) != 0
          || finishRecord() != 0) {
        return sorry(g);
      }
      // section 16
      if (!GbIo.string('\n').equals("* Vertices")) {
        return panic(g, Gb.SYNTAX_ERROR + 3);
      }
      GbIo.newline();
      for (int k = 0; k < lastVert; k++) {
        Vertex v = verts[k];
        if (fillField(tmp, 'S') != 0) {
          return sorry(g);
        }
        v.name = tmp.S();
        if (fillField(tmp, 'A') != 0) {
          return sorry(g);
        }
        v.arcs = tmp.A();
        if (fillField(v.u, types.charAt(0)) != 0
            || fillField(v.v, types.charAt(1)) != 0
            || fillField(v.w, types.charAt(2)) != 0
            || fillField(v.x, types.charAt(3)) != 0
            || fillField(v.y, types.charAt(4)) != 0
            || fillField(v.z, types.charAt(5)) != 0
            || finishRecord() != 0) {
          return sorry(g);
        }
      }
      // section 17
      if (!GbIo.string('\n').equals("* Arcs")) {
        return panic(g, Gb.SYNTAX_ERROR + 4);
      }
      GbIo.newline();
      for (int k = 0; k < lastArc; k++) {
        Arc a = arcs[k];
        if (fillField(tmp, 'V') != 0) {
          return sorry(g);
        }
        a.tip = tmp.V();
        if (fillField(tmp, 'A') != 0) {
          return sorry(g);
        }
        a.next = tmp.A();
        if (fillField(tmp, 'I') != 0) {
          return sorry(g);
        }
        a.len = tmp.I;
        if (fillField(a.a, types.charAt(6)) != 0
            || fillField(a.b, types.charAt(7)) != 0
            || finishRecord() != 0) {
          return sorry(g);
        }
      }
      // section 18
      Matcher cm = CHECKSUM.matcher(GbIo.string('\n'));
      if (!cm.find()) {
        return panic(g, Gb.SYNTAX_ERROR + 5);
      }
      long s = Long.parseLong(cm.group(1));
      if (GbIo.rawClose() != s && s >= 0) {
        return panic(g, Gb.LATE_DATA_FAULT);
      }
      pairArcsByPosition(g); // ADR 0020
      return g;
    } finally {
      verts = null;
      arcs = null;
    }
  }

  private static Graph panic(Graph g, long code) {
    Gb.panicCode = code;
    return sorry(g);
  }

  private static Graph sorry(Graph g) {
    GbIo.rawClose();
    Gb.recycle(g);
    return null;
  }

  /** Section 14. */
  private static long finishRecord() {
    if (GbIo.ch() != '\n') {
      return Gb.panicCode = Gb.SYNTAX_ERROR - 8;
    }
    GbIo.newline();
    commaExpected = false;
    return 0;
  }

  /** Sections 7, 9–12: read one field of type {@code t} into {@code l}; nonzero on failure. */
  private static long fillField(Util l, char t) {
    if (t != 'Z' && commaExpected) {
      if (GbIo.ch() != ',') {
        return Gb.panicCode = Gb.SYNTAX_ERROR - 1;
      }
      if (GbIo.ch() == '\n') {
        GbIo.newline();
      } else {
        GbIo.backup();
      }
    } else {
      commaExpected = true;
    }
    char c = GbIo.ch();
    switch (t) {
      case 'I' -> {
        if (c == '-') {
          l.I = -GbIo.number(10);
        } else {
          GbIo.backup();
          l.I = GbIo.number(10);
        }
      }
      case 'V' -> {
        if (c == 'V') {
          long k = GbIo.number(10);
          if (k >= lastVert || k < 0) {
            Gb.panicCode = Gb.SYNTAX_ERROR - 2;
          } else {
            l.ref = verts[(int) k];
          }
        } else if (c == '0' || c == '1') {
          l.I = c - '0';
          l.ref = null;
        } else {
          Gb.panicCode = Gb.SYNTAX_ERROR - 3;
        }
      }
      case 'S' -> {
        if (c != '"') {
          Gb.panicCode = Gb.SYNTAX_ERROR - 6;
        } else {
          String s = GbIo.string('"');
          while (s.length() >= 2 && s.length() <= MAX_SV_STRING + 2 && s.endsWith("\\\n")) {
            GbIo.newline();
            s = s.substring(0, s.length() - 2) + GbIo.string('"');
          }
          if (GbIo.ch() != '"') {
            Gb.panicCode = Gb.SYNTAX_ERROR - 7;
          } else if (s.isEmpty()) {
            l.ref = Gb.NULL_STRING;
          } else {
            l.ref = Gb.saveString(s);
          }
        }
      }
      case 'A' -> {
        if (c == 'A') {
          long k = GbIo.number(10);
          if (k >= lastArc || k < 0) {
            Gb.panicCode = Gb.SYNTAX_ERROR - 4;
          } else {
            l.ref = arcs[(int) k];
          }
        } else if (c == '0') {
          l.ref = null;
        } else {
          Gb.panicCode = Gb.SYNTAX_ERROR - 5;
        }
      }
      default -> GbIo.backup();
    }
    return Gb.panicCode;
  }

  /**
   * ADR 0020: give restored arcs the mates the C's positional rule implies. For an arc {@code a}
   * from {@code u} to {@code v} at slot {@code i}, the inverse is slot {@code i+1} iff {@code u <
   * v} or {@code a->next == a+1}, else slot {@code i-1}; slots outside the block get no mate.
   */
  private static void pairArcsByPosition(Graph g) {
    Arc[] block = g.arcBlocks().get(0);
    for (int i = 0; i < g.vertices.length; i++) {
      Vertex u = g.vertices[i];
      for (Arc a = u.arcs; a != null; a = a.next) {
        Vertex v = a.tip;
        int j = a.index;
        boolean forward =
            (v != null && u.index < v.index) || (j + 1 < block.length && a.next == block[j + 1]);
        int k = forward ? j + 1 : j - 1;
        a.mate = k >= 0 && k < block.length ? block[k] : null;
      }
    }
  }
}
