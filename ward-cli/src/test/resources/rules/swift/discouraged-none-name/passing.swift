enum Choice {
    case nothing
    case some
}

func describe(_ choice: Choice?) -> Int {
    switch choice {
    case .none: return 0
    case .some: return 1
    }
}
