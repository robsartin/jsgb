/**
 * GB_FLIP: portable pseudo-random numbers.
 *
 * <p>SGB needs random numbers that are identical on every machine, so it uses its own generator
 * rather than the platform's. The values are 31-bit, the period is enormous, and the
 * lagged-Fibonacci recurrence needs only subtraction. Every generator module seeds it through
 * {@link com.robsartin.jsgb.flip.Flip#initRand(long)} before drawing, which is what makes a graph
 * such as {@code words(100, wt, 0, 69)} the same graph everywhere.
 */
package com.robsartin.jsgb.flip;
