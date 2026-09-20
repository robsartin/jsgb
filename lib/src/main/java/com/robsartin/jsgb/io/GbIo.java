package com.robsartin.jsgb.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Port of {@code gb_io}: reads the GraphBase data files in a system-independent, checksum-verified
 * way. Files are read as ISO-8859-1 bytes; the API is byte-oriented like the C.
 *
 * <p>Only one file can be open at a time; the state is global as in C.
 */
public final class GbIo {

  public static final long CANT_OPEN_FILE = 0x1;
  public static final long CANT_CLOSE_FILE = 0x2;
  public static final long BAD_FIRST_LINE = 0x4;
  public static final long BAD_SECOND_LINE = 0x8;
  public static final long BAD_THIRD_LINE = 0x10;
  public static final long BAD_FOURTH_LINE = 0x20;
  public static final long FILE_ENDED_PREMATURELY = 0x40;
  public static final long MISSING_NEWLINE = 0x80;
  public static final long WRONG_NUMBER_OF_LINES = 0x100;
  public static final long WRONG_CHECKSUM = 0x200;
  public static final long NO_FILE_OPEN = 0x400;
  public static final long BAD_LAST_LINE = 0x800;

  /** Code returned by {@link #imapOrd(int)} for a byte outside the GraphBase alphabet. */
  public static final int UNEXPECTED_CHAR = 127;

  /** Record of anomalies noted by the routines; a bitmask of the constants above. */
  public static long ioErrors;

  private static final int MAX_LINE = 80; // fgets(buffer, 81, ...)
  private static final long CHECKSUM_PRIME = (1L << 30) - 83;
  private static final byte[] IMAP =
      ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
              + "abcdefghijklmnopqrstuvwxyz_^~&@,;.:?!%#$+-*/|\\<=>()[]{}`'\" \n")
          .getBytes(StandardCharsets.ISO_8859_1);
  private static final int[] ICODE = new int[256];

  static {
    java.util.Arrays.fill(ICODE, UNEXPECTED_CHAR);
    for (int k = 0; k < IMAP.length; k++) {
      ICODE[IMAP[k] & 0xff] = k;
    }
  }

  /**
   * Current line: bytes 0..lineEnd-1 are content, byte lineEnd is '\n'; beyond is the terminator.
   */
  private static byte[] buffer = new byte[MAX_LINE + 2];

  private static int lineEnd; // index of the '\n'
  private static int curPos;
  private static InputStream curFile;
  private static long magic;
  private static long lineNo;
  private static long finalMagic;
  private static long totLines;
  private static boolean moreData;
  private static String fileName = "";

  private GbIo() {}

  /** Restores the just-loaded state. Tests only. */
  static void reset() {
    if (curFile != null) {
      try {
        curFile.close();
      } catch (IOException ignored) {
        // nothing sensible to do
      }
    }
    curFile = null;
    ioErrors = 0;
    lineEnd = 0;
    buffer[0] = '\n';
    curPos = 0;
    magic = lineNo = finalMagic = totLines = 0;
    moreData = false;
    fileName = "";
  }

  // ---- character codes ------------------------------------------------------------------

  /** {@code imap_chr(d)}: the character with GraphBase code {@code d}, or NUL if out of range. */
  public static char imapChr(long d) {
    return d < 0 || d >= IMAP.length ? '\0' : (char) (IMAP[(int) d] & 0xff);
  }

  /** {@code imap_ord(c)}: the GraphBase code of byte {@code c} (0..255), else 127. */
  public static long imapOrd(int c) {
    return c < 0 || c > 255 ? UNEXPECTED_CHAR : ICODE[c];
  }

  /** {@code new_checksum(s, old)}: folds every byte of {@code s} into the running checksum. */
  public static long newChecksum(byte[] s, long oldChecksum) {
    long a = oldChecksum;
    for (byte b : s) {
      a = (a + a + imapOrd(b & 0xff)) % CHECKSUM_PRIME;
    }
    return a;
  }

  // ---- line buffer ----------------------------------------------------------------------

  /** {@code fill_buf()}: reads the next line (at most 80 bytes), strips trailing spaces. */
  private static void fillBuf() {
    int n = 0;
    boolean gotNewline = false;
    boolean gotAny = false;
    try {
      while (n < MAX_LINE) {
        int b = curFile.read();
        if (b < 0) {
          break;
        }
        gotAny = true;
        buffer[n++] = (byte) b;
        if (b == '\n') {
          gotNewline = true;
          break;
        }
      }
    } catch (IOException e) {
      gotAny = false;
      n = 0;
    }
    if (!gotAny) {
      ioErrors |= FILE_ENDED_PREMATURELY;
      moreData = false;
      n = 0;
    }
    int p; // index one past the last content byte
    if (n == 0 || !gotNewline) {
      ioErrors |= MISSING_NEWLINE;
      p = n;
    } else {
      p = n - 1; // drop the '\n'
    }
    while (p > 0 && buffer[p - 1] == ' ') {
      p--;
    }
    buffer[p] = '\n';
    lineEnd = p;
    curPos = 0;
  }

  private static byte[] lineBytes() {
    return java.util.Arrays.copyOfRange(buffer, 0, lineEnd + 1);
  }

  private static boolean lineStartsWith(String prefix) {
    byte[] p = prefix.getBytes(StandardCharsets.ISO_8859_1);
    if (p.length > lineEnd) {
      return false;
    }
    for (int i = 0; i < p.length; i++) {
      if (buffer[i] != p[i]) {
        return false;
      }
    }
    return true;
  }

  /** {@code gb_newline()}: advances to the next line, folding it into the checksum. */
  public static void newline() {
    if (++lineNo > totLines) {
      moreData = false;
    }
    if (moreData) {
      fillBuf();
      if (buffer[0] != '*') {
        magic = newChecksum(lineBytes(), magic);
      }
    }
  }

  /** {@code gb_eof()}: true once the data has all been read. */
  public static boolean eof() {
    return !moreData;
  }

  /** {@code gb_char()}: the next byte of the line, or '\n' once the line is exhausted. */
  public static char ch() {
    if (curPos <= lineEnd) {
      return (char) (buffer[curPos++] & 0xff);
    }
    return '\n';
  }

  /** {@code gb_backup()}: steps back one byte, never before the line start. */
  public static void backup() {
    if (curPos > 0) {
      curPos--;
    }
  }

  private static long ordAtCursor() {
    return curPos <= lineEnd ? imapOrd(buffer[curPos] & 0xff) : Long.MAX_VALUE;
  }

  /** {@code gb_digit(d)}: reads one radix-{@code d} digit, or returns -1 without advancing. */
  public static long digit(int d) {
    if (ordAtCursor() < d) {
      return imapOrd(buffer[curPos++] & 0xff);
    }
    return -1;
  }

  /** {@code gb_number(d)}: reads a radix-{@code d} number, possibly empty (0). */
  public static long number(int d) {
    long a = 0;
    while (ordAtCursor() < d) {
      a = a * d + imapOrd(buffer[curPos++] & 0xff);
    }
    return a;
  }

  /** {@code gb_string(p, c)}: the bytes up to (not including) {@code c} or the end of line. */
  public static String string(char c) {
    int start = curPos;
    while (curPos <= lineEnd && (buffer[curPos] & 0xff) != c) {
      curPos++;
    }
    return new String(buffer, start, curPos - start, StandardCharsets.ISO_8859_1);
  }

  // ---- files ----------------------------------------------------------------------------

  private static InputStream locate(String f) {
    try {
      Path direct = Path.of(f);
      if (Files.isRegularFile(direct)) {
        return Files.newInputStream(direct);
      }
      String dir = System.getProperty("jsgb.data.dir");
      if (dir != null) {
        Path inDir = Path.of(dir, f);
        if (Files.isRegularFile(inDir)) {
          return Files.newInputStream(inDir);
        }
      }
    } catch (IOException | java.nio.file.InvalidPathException e) {
      return null;
    }
    return GbIo.class.getResourceAsStream("/sgb/" + f);
  }

  /** {@code gb_raw_open(f)}: opens {@code f} without header checks; sets {@link #ioErrors}. */
  public static void rawOpen(String f) {
    InputStream in = locate(f);
    curFile = in == null ? null : new java.io.BufferedInputStream(in);
    if (curFile != null) {
      ioErrors = 0;
      moreData = true;
      lineNo = magic = 0;
      totLines = 0x7fffffffL;
      fillBuf();
    } else {
      ioErrors = CANT_OPEN_FILE;
    }
  }

  /** {@code gb_open(f)}: opens a GraphBase data file, validating its header; 0 if OK. */
  public static long open(String f) {
    fileName = f.length() > 19 ? f.substring(0, 19) : f;
    rawOpen(f);
    if (curFile != null) {
      if (!lineStartsWith("* File \"" + f + "\"")) {
        return ioErrors |= BAD_FIRST_LINE;
      }
      fillBuf();
      if (buffer[0] != '*') {
        return ioErrors |= BAD_SECOND_LINE;
      }
      fillBuf();
      if (buffer[0] != '*') {
        return ioErrors |= BAD_THIRD_LINE;
      }
      fillBuf();
      if (!lineStartsWith("* (Checksum parameters ")) {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      curPos += 23;
      totLines = number(10);
      if (ch() != ',') {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      finalMagic = number(10);
      if (ch() != ')') {
        return ioErrors |= BAD_FOURTH_LINE;
      }
      newline();
    }
    return ioErrors;
  }

  /** {@code gb_close()}: verifies the trailer, line count and checksum; 0 if OK. */
  public static long close() {
    if (curFile == null) {
      return ioErrors |= NO_FILE_OPEN;
    }
    fillBuf();
    if (!lineStartsWith("* End of file \"" + fileName + "\"")) {
      ioErrors |= BAD_LAST_LINE;
    }
    moreData = false;
    lineEnd = 0;
    buffer[0] = '\n';
    try {
      curFile.close();
    } catch (IOException e) {
      curFile = null;
      return ioErrors |= CANT_CLOSE_FILE;
    }
    curFile = null;
    if (lineNo != totLines + 1) {
      return ioErrors |= WRONG_NUMBER_OF_LINES;
    }
    if (magic != finalMagic) {
      return ioErrors |= WRONG_CHECKSUM;
    }
    return ioErrors;
  }

  /** {@code gb_raw_close()}: closes without checks and returns the running checksum. */
  public static long rawClose() {
    if (curFile != null) {
      try {
        curFile.close();
      } catch (IOException ignored) {
        // the C ignores fclose's result here too
      }
      moreData = false;
      lineEnd = 0;
      buffer[0] = '\n';
      curPos = 0;
      curFile = null;
    }
    return magic;
  }
}
