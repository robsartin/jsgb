package com.robsartin.jsgb.dijk;

import static org.assertj.core.api.Assertions.assertThat;

import com.robsartin.jsgb.graph.Gb;
import com.robsartin.jsgb.graph.Vertex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins a behaviour {@link Dijkstra} itself never exercises (its own {@code requeue} calls always
 * pass a key {@code >= masterKey}): C section 24's {@code if (d<master_key) master_key=d;}, needed
 * by other algorithms that reuse this queue (the C cites {@code MILES_SPAN}).
 */
class Buckets128Test {

  @Test
  @DisplayName("del_min returns the true minimum after requeue lowers a key below master_key")
  void shouldLowerMasterKeyWhenRequeuedBelowCurrentMasterKey() {
    Buckets128 q = new Buckets128();
    Vertex[] vs = Gb.allocAuxVertices(2);
    Vertex a = vs[0];
    Vertex b = vs[1];
    q.initQueue(100);
    q.enqueue(a, 105);
    q.enqueue(b, 200);

    q.requeue(b, 50); // below master_key=100; must lower master_key, or a is found first

    assertThat(q.delMin()).isSameAs(b);
    assertThat(b.z.I).isEqualTo(50L);
  }
}
