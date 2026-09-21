package com.robsartin.jsgb.demo;

/**
 * The C demos' {@code o}, {@code oo}, {@code ooo}, {@code oooo} comma-operator idiom: each names
 * how many memory references the operand it wraps costs, and adds that many to the running total.
 */
public final class Mems {

  private Mems() {}

  /** Running count of memory references charged by the demo currently executing. */
  public static long mems;

  /** Counts one memory reference. */
  public static void o() {
    mems += 1;
  }

  /** Counts two memory references. */
  public static void oo() {
    mems += 2;
  }

  /** Counts three memory references. */
  public static void ooo() {
    mems += 3;
  }

  /** Counts four memory references. */
  public static void oooo() {
    mems += 4;
  }
}
