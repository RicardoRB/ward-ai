func name(of info: [String: Any]?) -> String {
    let bundle = info?["Identifier"] as? String ?? "Unknown"
    let failed = info?["Error"] as? LoadError != nil
    return failed ? "" : bundle
}

func open<Parameters>(with parameters: Parameters) -> Socket where Parameters: Encodable & Sendable {
    Socket(parameters)
}

func each(_ any: [Int], some: Int) -> Int {
    for any in any where any > some {
        return any
    }
    return some
}
