package com.twitter.common.scala.objectbridge.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * XXX
 */
@Target(PACKAGE)
@Retention(CLASS)
public @interface BridgeDefinitions {

  /**
   * XXX
   */
  ObjectBridge[] value();
}
