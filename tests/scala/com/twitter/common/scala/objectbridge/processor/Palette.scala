package com.twitter.common.scala.objectbridge.processor

trait PaintBrush[T] {
  val color: T
  def stroke(width: Int): T = {
    println("" + width + color)
    color
  }
}

sealed trait Palette

object Palette extends PaintBrush[String] {
  object Jake extends Palette

  val fred = 42
  val jake = apply()
  val color: String = "green"
  def apply() = new Palette {
    println("zap")
  }
  def meld(palette: Palette) = new Palette {
    println(palette)
  }
}
