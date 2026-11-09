func f<Seq: Sequence>(s: Seq) -> [Seq.Element] {
    s.map({ $0 })
}
func g(array: [Int]) -> [Int] {
    array.map { $0 }
}
let myInts = [1, 2, 3].map { return $0 }
struct Generator: Sequence, IteratorProtocol {
    func next() -> Int? { nil }
}
let array = Generator().map { i in i }
