/**
 * Create object bridges for objects in this package.
 */
@BridgeDefinitions({
  @ObjectBridge(object = Palette$.class, bridge = "Palettes"),
  @ObjectBridge(object = Palette$.class, bridge = "Palettes2")
})
package com.twitter.common.scala.objectbridge.processor;

import com.twitter.common.scala.objectbridge.annotations.BridgeDefinitions;
import com.twitter.common.scala.objectbridge.annotations.ObjectBridge;
