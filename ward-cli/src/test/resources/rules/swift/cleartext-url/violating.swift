import Foundation

func endpoints() {
    let api = URL(string: "http://api.example.com/v1/items")
    let login = URL(string: "http://example.com/login?next=/home")
    _ = (api, login)
}
