import Foundation

func remember(_ password: String, _ token: String) {
    UserDefaults.standard.set(password, forKey: "userPassword")
    UserDefaults.standard.set(token, forKey: "authToken")
}
