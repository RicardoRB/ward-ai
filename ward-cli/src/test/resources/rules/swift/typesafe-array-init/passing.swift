enum MyError: Error {}
let myResult: Result<String, MyError> = .success("")
let result: Result<Any, MyError> = myResult.map { $0 }
struct IntArray {
    let elements = [1, 2, 3]
    func map<T>(_ transformer: (Int) throws -> T) rethrows -> [T] {
        try elements.map(transformer)
    }
}
let ints = IntArray()
let intsCopy = ints.map { $0 }
