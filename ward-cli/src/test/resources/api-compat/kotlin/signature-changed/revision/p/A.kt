package p

class A {
  fun f(x: List<Int>) {}
  fun <T> g(x: T) where T : Number, T : Comparable<T> {}
}
