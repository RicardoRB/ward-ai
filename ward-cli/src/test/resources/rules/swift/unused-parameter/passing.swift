struct Point {
    func move(x: Int, y: Int) -> Int {
        return x + y
    }
    func ignored(_value: Int) -> Int {
        return 1
    }
}

class Shape: Drawable {
    func draw(size: Int) {}
}

struct Form {
    func headers(name: String, fileName: String? = nil, flag: Bool = false) -> String {
        var text = "name=\(name)"
        if let fileName { text += fileName }
        return text + "\(flag)"
    }
}

struct Handler {
    private func handle(transaction: Verification<Int>) {
        guard case .verified(let transaction) = transaction else {
            print("unverified: \(transaction)")
            return
        }
        print(transaction)
    }
}
