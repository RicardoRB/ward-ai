class App {
    fun g(s: String, b: Base, c: Other) {







        val a = s as CharSequence
        val b2 = s as Any
        val d = b as Other
        val e = s as Unknown
        val g2 = c as Runnable
    }
}
