import Foundation

func remember(_ theme: String) {
    UserDefaults.standard.set(theme, forKey: "theme")
}
