package com.robsartin.jsgb.sort;

/**
 * The contract the C expresses as "the first field is {@code long key} and the second is {@code
 * struct ... *link}". Keys must be nonnegative and less than 2^31.
 */
public interface Sortable {
  /** The sort key: a nonnegative value less than 2^31, as the C's {@code long key} field. */
  long key();

  /** The next node in the list, or null; the C's {@code link} field. */
  Sortable link();

  /** Relinks this node; gb_linksort rewrites every node's link during sorting. */
  void setLink(Sortable next);
}
