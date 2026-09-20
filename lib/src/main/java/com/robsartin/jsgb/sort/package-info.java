/**
 * Port of {@code gb_sort}: a radix sort of linked lists into 256 weight buckets.
 *
 * <p>Stanford GraphBase generators build graphs by repeatedly taking the heaviest remaining vertex
 * from a list, and ties are common because weights are often small integers; sorting by weight with
 * the first two passes drawn from the random stream, rather than from the key, lets those ties
 * settle in the same reproducible order the C library produces from the same seed. Callers adapt
 * their record type to {@link com.robsartin.jsgb.sort.Sortable} wherever the C overlaid its {@code
 * node}'s {@code key} and {@code link} fields directly onto a domain struct.
 */
package com.robsartin.jsgb.sort;
