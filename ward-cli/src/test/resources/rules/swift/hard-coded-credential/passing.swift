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

let host = "example.com", passwordHint = "At least twelve characters"

func login(user: String = "guest", password: String = "") {}
func sample(user: String = "guest", password: String = "example") {}
