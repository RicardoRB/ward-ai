public protocol Evaluating: Sendable {
    #if !canImport(Security)
    func evaluate(_ host: String) throws
    #else
    func evaluate(_ trust: SecTrust, forHost host: String) throws
    #endif
}
