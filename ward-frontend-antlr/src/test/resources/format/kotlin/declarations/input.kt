package sample

import kotlin.math.max

class Greeter(val name: String,   var count: Int = 0) : Base() {
    fun greet(  times : Int ) : String {
        count = count+times
        return "hi $name"
    }
}
data class Point(val x: Int, val y: Int)
object Registry {
    val items: MutableList<String> = mutableListOf()
}
interface Shape {
  fun area(): Double
    val sides: Int
}
fun top(a:Int,b:Int)=max(a,b)
val answer: Int   =   42
