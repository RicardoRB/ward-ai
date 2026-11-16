#if canImport(Combine) && !os(Android)
import Combine
#endif

#if targetEnvironment(simulator) || compiler(>=5.9)
let simulated = true
#else
let simulated = false
#endif

enum Failure {
    case timeout
    #if canImport(Security)
    case untrusted(host: String)
    case revoked
    #endif
    case cancelled
}

func describe(_ failure: Failure) -> String {
    switch failure {
    case .timeout: return "timeout"
    #if canImport(Security)
    case .untrusted(let host): return host
    case .revoked: return "revoked"
    #endif
    case .cancelled: return "cancelled"
    }
}
