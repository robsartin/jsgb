package com.robsartin.jsgb.flip;

/**
 * Port of {@code gb_flip}: Knuth's portable subtractive lagged-Fibonacci pseudo-random number
 * generator, {@code a[n] = (a[n-55] - a[n-24]) mod 2^31}.
 *
 * <p>State is global, as in C. Every SGB generator that takes a {@code seed} calls {@link
 * #initRand(long)}; the sequence of values it then draws is defined bit for bit by this class.
 */
public final class Flip {

  private static final long TWO_TO_THE_31 = 0x80000000L;

  /** The C array {@code A[56]}; {@code A[0] = -1} is the sentinel that triggers a refill. */
  private static final long[] A = new long[56];

  /** Index form of the C pointer {@code gb_fptr}. */
  private static int fptr;

  static {
    reset();
  }

  private Flip() {}

  /** Restores the generator to its just-loaded state (unseeded). Tests only. */
  static void reset() {
    java.util.Arrays.fill(A, 0L);
    A[0] = -1;
    fptr = 0;
  }

  /** Reads {@code A[i]}. Tests only. */
  static long a(int i) {
    return A[i];
  }

  private static long modDiff(long x, long y) {
    return (x - y) & 0x7fffffffL;
  }

  /** {@code gb_next_rand()}: the next value in [0, 2^31). */
  public static long nextRand() {
    return A[fptr] >= 0 ? A[fptr--] : flipCycle();
  }

  /** {@code gb_flip_cycle()}: computes 55 more values and returns the first of them. */
  public static long flipCycle() {
    int ii;
    int jj;
    for (ii = 1, jj = 32; jj <= 55; ii++, jj++) {
      A[ii] = modDiff(A[ii], A[jj]);
    }
    for (jj = 1; ii <= 55; ii++, jj++) {
      A[ii] = modDiff(A[ii], A[jj]);
    }
    fptr = 54;
    return A[55];
  }

  /** {@code gb_init_rand(seed)}: seeds the generator. Any {@code long} is a valid seed. */
  public static void initRand(long seed) {
    seedArray(seed);
    for (int k = 0; k < 5; k++) {
      flipCycle();
    }
  }

  /** The seeding loop of {@code gb_init_rand} without the five warm-up cycles. Tests only. */
  static void seedArray(long seed) {
    long prev = seed;
    long next = 1;
    seed = prev = modDiff(prev, 0);
    A[55] = prev;
    for (int i = 21; i != 0; i = (i + 21) % 55) {
      A[i] = next;
      next = modDiff(prev, next);
      if ((seed & 1) != 0) {
        seed = 0x40000000L + (seed >> 1);
      } else {
        seed >>= 1;
      }
      next = modDiff(next, seed);
      prev = A[i];
    }
  }

  /** {@code gb_unif_rand(m)}: a uniform value in [0, m), by rejection, for 0 < m < 2^31. */
  public static long unifRand(long m) {
    long t = TWO_TO_THE_31 - (TWO_TO_THE_31 % m);
    long r;
    do {
      r = nextRand();
    } while (t <= r);
    return r % m;
  }
}
