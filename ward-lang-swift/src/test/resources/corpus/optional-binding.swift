func describe(_ value: Int?) -> String {
    if let number = value, number > 0 {
        return "positive \(number)"
    }
    guard let fallback = value else {
        return "none"
    }
    return "fallback \(fallback)"
}
