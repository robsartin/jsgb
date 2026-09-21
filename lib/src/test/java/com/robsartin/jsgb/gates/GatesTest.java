package com.robsartin.jsgb.gates;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Graph;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatesTest {

  @Test
  @DisplayName("risc(2) has 1630 gates laid out RUN, M0..M15, P0..P9, S, N, K, V, X, R0:0..")
  void shouldLayOutRegistersWhenRiscBuilt() {
    Graph g = Gates.risc(2L);
    assertThat(g.id).isEqualTo("risc(2)");
    assertThat(g.n).isEqualTo(1630L);
    assertThat(g.m).isEqualTo(3972L);
    assertThat(g.utilTypes).isEqualTo("ZZZIIVZZZZZZZA");
    assertThat(g.vertices[0].name).isEqualTo("RUN");
    assertThat(g.vertices[0].y.I).isEqualTo(73L); // 'I'
    assertThat(g.vertices[1].name).isEqualTo("M0");
    assertThat(g.vertices[17].name).isEqualTo("P0");
    assertThat(g.vertices[27].name).isEqualTo("S");
    assertThat(g.vertices[31].name).isEqualTo("X");
    assertThat(g.vertices[32].name).isEqualTo("R0:0");
    assertThat(g.vertices[47].name).isEqualTo("R0:15");
    assertThat(g.vertices[47].y.I).isEqualTo(76L); // 'L'
    assertThat(g.vertices[63].name).isEqualTo("R1:15");
    assertThat(g.zz.A().tip.name).isEqualTo("Z598");
    assertThat(Gates.risc(0L).id).isEqualTo("risc(16)");
    assertThat(Gates.risc(0L).n).isEqualTo(3240L);
  }

  @Test
  @DisplayName("gate_eval reads the input vector, propagates, and writes the output vector")
  void shouldEvaluateWhenInputVectorGiven() {
    Graph g = Gates.risc(2L);
    StringBuilder out = new StringBuilder("stale");
    assertThat(Gates.gateEval(g, "10000000000000001", out)).isZero();
    assertThat(out.toString()).isEqualTo("0000000000000001");
    assertThat(Gates.gateEval(null, null, null)).isEqualTo(-2L);
    g.vertices[100].y.I = 'Q';
    assertThat(Gates.gateEval(g, null, null)).isEqualTo(-1L);
  }

  @Test
  @DisplayName("run_risc multiplies 3 by 4 on the 8-register machine and leaves the product in r4")
  void shouldMultiplyWhenRomIsTheTakeRiscProgram() {
    long[] rom = {
      0x2ff0, 0x1111, 0x1a30, 0x3333, 0x7f70, 0x5555, 0x0f8f, 0x3a21, 0x1a01, 0x0a12, 0x3a01,
      0x4000, 0x5000, 0x6000, 0x2a63, 0x0f95, 0x3063, 0x1061, 0x6ac1, 0x5fd1, 0x2a63, 0x039b,
      0x0843, 0x3463, 0x1561, 0x2863, 0x0c94, 0x4861, 0x6ac1, 0x2a63, 0x5a41, 0x0398, 0x6666,
      0x0fa7
    };
    rom[1] = 3;
    rom[3] = 4;
    rom[5] = 10;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream saved = Gates.out;
    Gates.out = new PrintStream(baos, true, StandardCharsets.ISO_8859_1);
    try {
      assertThat(Gates.runRisc(Gates.risc(8L), rom, 34L, 0L)).isZero();
    } finally {
      Gates.out = saved;
    }
    assertThat(baos.toString(StandardCharsets.ISO_8859_1)).isEmpty();
    assertThat(Gates.riscState)
        .containsExactly(65535, 4, 65535, 1, 12, 65535, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 3968, 65535);
  }

  @Test
  @DisplayName("prod(2,2) reduces to the 12-gate multiplier the C prints")
  void shouldBuildMultiplierWhenProdCalled() {
    Graph g = Gates.prod(2L, 2L);
    assertThat(g.id).isEqualTo("prod(2,2)");
    assertThat(g.n).isEqualTo(12L);
    assertThat(g.m).isEqualTo(16L);
    assertThat(g.vertices[0].name).isEqualTo("X0");
    assertThat(g.vertices[4].name).isEqualTo("A0:0");
    assertThat(g.vertices[8].name).isEqualTo("U1");
    assertThat(g.vertices[8].y.I).isEqualTo(94L); // '^'
    assertThat(g.zz.A().tip.name).isEqualTo("B3:2");
    StringBuilder out = new StringBuilder();
    assertThat(Gates.gateEval(g, "1111", out)).isZero();
    assertThat(out.toString()).isEqualTo("1001"); // 3 x 3 = 9, output bits high to low
    assertThat(Gates.prod(0L, 0L).id).isEqualTo("prod(2,2)");
  }

  @Test
  @DisplayName("partial_gates forces inputs at random, reports them in buf, and renames the graph")
  void shouldForceInputsWhenPartialGatesCalled() {
    StringBuilder buf = new StringBuilder("stale");
    Graph g = Gates.partialGates(Gates.prod(3L, 3L), 2L, 50000L, 1L, buf);
    assertThat(buf.toString()).isEqualTo("****");
    assertThat(g.id).isEqualTo("partial_gates(prod(3,3),2,50000,1)");
    assertThat(g.n).isEqualTo(39L);
    Graph h = Gates.partialGates(Gates.risc(0L), 1L, 43210L, 98765L, buf);
    assertThat(buf.toString()).isEqualTo("*1*1***101**010*");
    assertThat(h.id).isEqualTo("partial_gates(risc(16),1,43210,98765)");
    assertThat(h.n).isEqualTo(1702L);
    assertThat(h.m).isEqualTo(3796L);
    assertThat(h.vertices[79].name).isEqualTo("R10:10");
    assertThat(h.vertices[79].z.V().name).isEqualTo("Z898");
    assertThat(Gates.partialGates(null, 1L, 1L, 1L, null)).isNull();
    assertThat(com.robsartin.jsgb.graph.Gb.panicCode)
        .isEqualTo(com.robsartin.jsgb.graph.Gb.MISSING_OPERAND);
  }

  @Test
  @DisplayName(
      "partial_gates panics instead of throwing when reduce's own newGraph allocation fails")
  void shouldPanicWhenReduceNewGraphAllocationFails() {
    Graph g = Gates.risc(2L);
    long savedExtraN = Gb.extraN;
    // Gb.extraN is added to every gb_new_graph(n) request's size; making it huge forces reduce's
    // internal gb_new_graph(n) call (n = the small live-vertex count it just counted) to fail,
    // exactly as a colossal n would in C, without needing to actually build a colossal graph.
    Gb.extraN = Long.MAX_VALUE / 2;
    try {
      // r = g.n so partial_gates's own loop touches nothing; it still unconditionally reduces.
      assertThat(Gates.partialGates(g, g.n, 0L, 1L, null)).isNull();
      assertThat(Gb.panicCode).isEqualTo(Gb.NO_ROOM + 2);
    } finally {
      Gb.extraN = savedExtraN;
    }
  }
}
