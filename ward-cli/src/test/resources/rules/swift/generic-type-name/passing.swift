func foo<T>() {}
func foo<T, U>(param: U) -> T {}
func foo<Element: Hashable>(e: Element) {}
struct Foo<T: Hashable> {}
