import Foundation

enum BackendConfiguration {
    static let userDefaultsKey = "nomnom.baseURL"
    static let defaultBaseURLString = "http://127.0.0.1:8081"

    static func loadBaseURL() -> String {
        let stored = UserDefaults.standard.string(forKey: userDefaultsKey)?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let stored, !stored.isEmpty {
            return stored
        }
        return defaultBaseURLString
    }

    static func saveBaseURL(_ value: String) {
        UserDefaults.standard.set(value, forKey: userDefaultsKey)
    }
}
