package com.robsartin.jsgb.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GbIoTest {

  @BeforeEach
  void resetIo() {
    GbIo.reset();
  }

  @AfterEach
  void closeAnyFile() {
    GbIo.rawClose();
    System.clearProperty("jsgb.data.dir");
  }

  @Test
  @DisplayName("test_io: reads test.dat exactly as the C self-test expects")
  void shouldPassTranslatedCSelfTestWhenReadingTestDat() {
    assertThat(GbIo.open("test.dat")).isZero();

    assertThat(GbIo.number(10)).isEqualTo(123456789L);
    assertThat(GbIo.digit(16)).isEqualTo(10L);
    GbIo.backup();
    GbIo.backup();
    assertThat(GbIo.number(16)).isEqualTo(0x9ABCDEFL);
    GbIo.newline();
    assertThat(GbIo.ch()).isEqualTo('\n');
    assertThat(GbIo.ch()).isEqualTo('\n');
    assertThat(GbIo.number(60)).isZero();
    assertThat(GbIo.string('\n')).isEmpty();
    GbIo.newline();
    assertThat(GbIo.string(':')).isEqualTo("Oops");
    assertThat(GbIo.ioErrors).isZero();
    assertThat(GbIo.digit(10)).isEqualTo(-1L);
    assertThat(GbIo.ch()).isEqualTo(':');
    assertThat(GbIo.eof()).isFalse();
    GbIo.newline();
    assertThat(GbIo.eof()).isTrue();

    assertThat(GbIo.close()).isZero();
  }

  @Test
  @DisplayName("open reports cant_open_file when the file exists nowhere")
  void shouldReportCantOpenFileWhenFileMissing() {
    assertThat(GbIo.open("no-such-file.dat")).isEqualTo(GbIo.CANT_OPEN_FILE);
    assertThat(GbIo.ioErrors).isEqualTo(GbIo.CANT_OPEN_FILE);
  }

  @Test
  @DisplayName("close reports no_file_open when nothing is open")
  void shouldReportNoFileOpenWhenClosingWithoutOpen() {
    assertThat(GbIo.close()).isEqualTo(GbIo.NO_FILE_OPEN);
  }

  @Test
  @DisplayName("data files ship on the classpath and open cleanly")
  void shouldOpenWordsDatFromClasspathWhenNoDirectoryGiven() {
    assertThat(GbIo.open("words.dat")).isZero();
    // gb_io.w section 26: when the delimiter never appears, the result's last character is
    // the '\n' that fill_buf always appends to the line -- so this is "aargh\n", not "aargh".
    assertThat(GbIo.string(' ')).isEqualTo("aargh\n");
    GbIo.rawClose();
  }

  @Test
  @DisplayName("every data file closes with a clean checksum when read to the end")
  void shouldVerifyChecksumWhenEachDataFileIsReadCompletely() {
    for (String name :
        new String[] {
          "anna", "david", "econ", "games", "homer", "huck", "jean", "lisa", "miles", "roget",
          "words"
        }) {
      GbIo.reset();
      assertThat(GbIo.open(name + ".dat")).as(name).isZero();
      while (!GbIo.eof()) {
        GbIo.newline();
      }
      assertThat(GbIo.close()).as(name).isZero();
    }
  }

  @Test
  @DisplayName("jsgb.data.dir is searched before the classpath")
  void shouldReadFromDataDirWhenPropertySet() throws Exception {
    java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("jsgb-data");
    java.nio.file.Path bogus = dir.resolve("words.dat");
    java.nio.file.Files.writeString(bogus, "not a graphbase file\n", StandardCharsets.ISO_8859_1);
    System.setProperty("jsgb.data.dir", dir.toString());
    assertThat(GbIo.open("words.dat")).isEqualTo(GbIo.BAD_FIRST_LINE);
  }

  @Test
  @DisplayName("imap_ord and imap_chr are inverse over the 96-character alphabet")
  void shouldRoundTripWhenMappingEveryImapCharacter() {
    for (int d = 0; d < 96; d++) {
      char c = GbIo.imapChr(d);
      assertThat(GbIo.imapOrd(c)).as("code %d", d).isEqualTo(d);
    }
    assertThat(GbIo.imapChr(96)).isEqualTo('\0');
    assertThat(GbIo.imapChr(-1)).isEqualTo('\0');
    assertThat(GbIo.imapOrd(0x80)).isEqualTo(GbIo.UNEXPECTED_CHAR);
  }

  @Test
  @DisplayName("new_checksum folds bytes as (a + a + code) mod 1073741741")
  void shouldFoldChecksumWhenGivenBytes() {
    byte[] s = "1\n".getBytes(StandardCharsets.ISO_8859_1);
    // code('1') = 1, code('\n') = 95: ((0+0+1)*2 + 95) = 97
    assertThat(GbIo.newChecksum(s, 0L)).isEqualTo(97L);
  }
}
