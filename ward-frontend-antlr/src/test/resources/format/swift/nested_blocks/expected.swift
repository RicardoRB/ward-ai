func classify(_ value: Int?) -> String {
    guard let value = value else { return "none" }
    for item in 0..<value {
        if item % 2 == 0 {
            print("even")
        } else if item > 3 {
            print("big")
        } else {
            print("odd")
        }
    }
    switch value {
    case 0:
        print("zero")
    case 1, 2:
        print("small")
    default:
        print("other")
    }
    return "done"
}
