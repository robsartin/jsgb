package com.robsartin.jsgb.demo;

import java.io.PrintStream;
import java.nio.file.Path;

/**
 * One SGB demo program's {@code main} body, factored out of process concerns. {@link #run} returns
 * exactly what the C {@code main} returned (negative values included); {@code args} excludes the
 * program name; {@code in} reproduces the C's {@code fgets}/{@code getchar} idioms over {@code
 * stdin}; files the C reads or writes relative to the current directory resolve against {@code
 * workDir}.
 */
@FunctionalInterface
public interface Demo {

  /** Runs the program's body once, C-process-fresh; returns the C {@code main}'s return value. */
  int run(String[] args, CStdin in, PrintStream out, PrintStream err, Path workDir);
}
