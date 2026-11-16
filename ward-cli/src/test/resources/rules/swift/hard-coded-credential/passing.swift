import Foundation

struct Client {
    let apiKey = ProcessInfo.processInfo.environment["API_KEY"] ?? ""
    var password: String = ""
}

struct LoginForm {
    let tokenLabel = "access"
    let passwordHint = "At least twelve characters"
    let apiKeyHeader = "X-Api-Key"
    var secretQuestionTitle: String = "Your first school"
}
