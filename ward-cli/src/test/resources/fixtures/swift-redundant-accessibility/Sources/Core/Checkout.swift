struct Checkout {
    func run() -> Int {
        Cart().total() + Helper().hashValue
    }
}
