struct Sizes {
    var base = 1

    func total(bound: Int) -> Int {
        return base + bound + missing
    }
}

func other() -> Int { return absent + 1 }
