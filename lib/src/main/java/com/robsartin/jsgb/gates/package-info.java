/**
 * Port of {@code gb_gates}: gate graphs, whose vertices are logic gates (or inputs, latches,
 * constants and copies) wired together by arcs that point at their inputs, with a designated list
 * of output arcs. See {@link com.robsartin.jsgb.gates.Gates} for {@code risc}, Knuth's 16-bit RISC
 * machine generator, {@code prod}, a binary multiplier generator, {@code partial_gates}, which
 * randomly forces a graph's inputs to constants, the {@code reduce} constant-propagation pass both
 * of those run through, and the evaluator, printer and simulator built on top of any gate graph.
 */
package com.robsartin.jsgb.gates;
