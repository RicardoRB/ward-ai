interface I {
}
interface J extends I {
}
class Violating implements I, J {
}
class Base implements J {
}
class Child extends Base implements J {
}
