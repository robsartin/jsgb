/**
 * Port of {@code gb_io}. The data files must be read identically on every system, so {@code GB_IO}
 * defines its own 96-character alphabet, reads lines of at most 80 bytes, strips trailing blanks,
 * and keeps a running checksum that {@code close} checks against the value in the file header. The
 * API is a cursor over the current line ({@code ch}, {@code backup}, {@code digit}, {@code number},
 * {@code string}) plus {@code newline}.
 */
package com.robsartin.jsgb.io;
