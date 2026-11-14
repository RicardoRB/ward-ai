import Foundation

struct Client {
    let apiKey = ProcessInfo.processInfo.environment["API_KEY"] ?? ""
    var password: String = ""
}
