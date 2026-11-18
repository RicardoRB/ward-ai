interface I {
}
interface J extends I {
}
interface K {
}
class Passing implements I, K {
}
class Other implements J {
}
class Builder implements org.example.Builder<String> {
}
