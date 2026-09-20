package com.robsartin.jsgb.sort;

/**
 * The contract the C expresses as "the first field is {@code long key} and the second is {@code
 * struct ... *link}". Keys must be nonnegative and less than 2^31.
 */
public interface Sortable {
  long key();

  Sortable link();

  void setLink(Sortable next);
}
