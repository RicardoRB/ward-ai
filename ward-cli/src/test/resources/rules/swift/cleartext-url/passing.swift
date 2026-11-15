import Foundation

func endpoints() {
    let api = URL(string: "https://api.example.com/v1/items")
    let local = URL(string: "http://localhost:8080/health")
    _ = (api, local)
}
