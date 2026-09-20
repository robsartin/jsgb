package com.robsartin.jsgb.sort;

import com.robsartin.jsgb.flip.Flip;

/**
 * Port of {@code gb_linksort}: a six-pass radix sort of a linked list into 256 buckets, with two
 * random passes first so that equal keys come out in random order. After sorting, read {@link
 * #sorted}[127] down to {@link #sorted}[0] for decreasing key order. The random generator must be
 * seeded first.
 */
public final class LinkSort {

  /** {@code gb_sorted}: bucket {@code j} holds keys in {@code [j * 2^24, (j + 1) * 2^24)}. */
  public static final Sortable[] sorted = new Sortable[256];

  private static final Sortable[] alt = new Sortable[256];

  private LinkSort() {}

  /** Clears both bucket arrays. Tests only. */
  static void reset() {
    java.util.Arrays.fill(sorted, null);
    java.util.Arrays.fill(alt, null);
  }

  private static void clear(Sortable[] buckets) {
    java.util.Arrays.fill(buckets, null);
  }

  private static void push(Sortable[] target, int k, Sortable p) {
    p.setLink(target[k]);
    target[k] = p;
  }

  /** {@code gb_linksort(l)}: sorts the list starting at {@code l} into {@link #sorted}. */
  public static void linksort(Sortable l) {
    Sortable p;
    Sortable q;
    // Pass 1: random distribution into alt.
    clear(alt);
    for (p = l; p != null; p = q) {
      int k = (int) (Flip.nextRand() >> 23);
      q = p.link();
      push(alt, k, p);
    }
    // Pass 2: random distribution from alt (255 down) into sorted.
    clear(sorted);
    for (int b = 255; b >= 0; b--) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) (Flip.nextRand() >> 23);
        q = p.link();
        push(sorted, k, p);
      }
    }
    // Pass 3: low byte, from sorted (255 down) into alt.
    clear(alt);
    for (int b = 255; b >= 0; b--) {
      for (p = sorted[b]; p != null; p = q) {
        int k = (int) (p.key() & 0xff);
        q = p.link();
        push(alt, k, p);
      }
    }
    // Pass 4: second byte, from alt (0 up) into sorted.
    clear(sorted);
    for (int b = 0; b < 256; b++) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 8) & 0xff);
        q = p.link();
        push(sorted, k, p);
      }
    }
    // Pass 5: third byte, from sorted (255 down) into alt.
    clear(alt);
    for (int b = 255; b >= 0; b--) {
      for (p = sorted[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 16) & 0xff);
        q = p.link();
        push(alt, k, p);
      }
    }
    // Pass 6: high byte, from alt (0 up) into sorted.
    clear(sorted);
    for (int b = 0; b < 256; b++) {
      for (p = alt[b]; p != null; p = q) {
        int k = (int) ((p.key() >> 24) & 0xff);
        q = p.link();
        push(sorted, k, p);
      }
    }
  }
}
