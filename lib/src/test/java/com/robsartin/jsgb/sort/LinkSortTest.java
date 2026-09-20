package com.robsartin.jsgb.sort;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.flip.Flip;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinkSortTest {

  /** The simplest Sortable: the C's {@code node} struct plus an id for the oracle. */
  private static final class Node implements Sortable {
    final long key;
    final int id;
    Sortable link;

    Node(long key, int id) {
      this.key = key;
      this.id = id;
    }

    @Override
    public long key() {
      return key;
    }

    @Override
    public Sortable link() {
      return link;
    }

    @Override
    public void setLink(Sortable next) {
      link = next;
    }
  }

  @BeforeEach
  void resetState() {
    LinkSort.reset();
    Flip.initRand(1L);
  }

  private static Node chain(long... keys) {
    Node head = null;
    Node prev = null;
    for (int i = 0; i < keys.length; i++) {
      Node n = new Node(keys[i], i);
      if (prev == null) {
        head = n;
      } else {
        prev.link = n;
      }
      prev = n;
    }
    return head;
  }

  private static List<String> readAll() {
    List<String> out = new ArrayList<>();
    for (int j = 255; j >= 0; j--) {
      for (Sortable p = LinkSort.sorted[j]; p != null; p = p.link()) {
        Node n = (Node) p;
        out.add(j + ":" + n.key + "(n" + n.id + ")");
      }
    }
    return out;
  }

  @Test
  @DisplayName("gb_linksort with seed 1 reproduces the C library's bucket order, ties included")
  void shouldMatchCOrderWhenSortingTwentyKeysWithSeedOne() {
    Node head =
        chain(
            5,
            3,
            9,
            3,
            0x01000000L,
            7,
            3,
            0x7fffffffL,
            256,
            255,
            65536,
            65535,
            1,
            2,
            3,
            4,
            0x00ff00ffL,
            0x00ff00ffL,
            0x10000000L,
            6);
    LinkSort.linksort(head);
    assertThat(readAll())
        .containsExactly(
            "127:2147483647(n7)",
            "16:268435456(n18)",
            "1:16777216(n4)",
            "0:16711935(n17)",
            "0:16711935(n16)",
            "0:65536(n10)",
            "0:65535(n11)",
            "0:256(n8)",
            "0:255(n9)",
            "0:9(n2)",
            "0:7(n5)",
            "0:6(n19)",
            "0:5(n0)",
            "0:4(n15)",
            "0:3(n3)",
            "0:3(n6)",
            "0:3(n1)",
            "0:3(n14)",
            "0:2(n13)",
            "0:1(n12)");
    // The sort consumed exactly 40 random numbers (two per node); the next draw is this one.
    assertThat(Flip.nextRand()).isEqualTo(1963953515L);
  }

  @Test
  @DisplayName("sorting an empty list leaves every bucket empty")
  void shouldLeaveBucketsEmptyWhenListIsNull() {
    LinkSort.linksort(null);
    for (int j = 0; j < 256; j++) {
      assertThat(LinkSort.sorted[j]).isNull();
    }
  }

  @Test
  @DisplayName("a second sort discards the previous buckets")
  void shouldReplaceBucketsWhenSortedTwice() {
    LinkSort.linksort(chain(0x7fffffffL));
    assertThat(LinkSort.sorted[127]).isNotNull();
    LinkSort.linksort(chain(1));
    assertThat(LinkSort.sorted[127]).isNull();
    assertThat(LinkSort.sorted[0].key()).isEqualTo(1L);
  }
}
