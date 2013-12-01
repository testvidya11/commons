package com.twitter.common.scala.objectbridge.processor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ObjectBridgeProcessorTest {
  @Test
  public void test() throws Exception {
    assertEquals(Palettes2.stroke(3), Palettes.stroke(42));
    assertEquals(42, Palettes.fred());
    assertEquals("green", Palettes2.color());
  }
}
