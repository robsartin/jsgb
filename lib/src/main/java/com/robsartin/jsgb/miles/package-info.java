/**
 * Port of {@code gb_miles}: the {@code miles} generator, an undirected graph of highway mileages
 * between 128 North American cities, plus {@code miles_distance} for reading the retained distance
 * matrix. See {@link com.robsartin.jsgb.miles.Miles} for the vertex-selection weighting, the
 * two-stage sort that decides which neighbours survive {@code maxDegree}, and the slot convention.
 */
package com.robsartin.jsgb.miles;
