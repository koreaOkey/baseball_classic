import Foundation
import Supabase

enum SupabaseConfig {
    private static let productionURL = "https://snrafqoqpmtoannnnwdq.supabase.co"
    private static let productionAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNucmFmcW9xcG10b2Fubm5ud2RxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjQ5MjQsImV4cCI6MjA4NjkwMDkyNH0.ufw8jQw9K8vhn9lS_9JA7Yetdi_D_9_ZNpJeukx53bY"

    private static func infoString(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.hasPrefix("$(") { return nil }
        return trimmed
    }

    static var urlString: String {
        infoString("SUPABASE_URL") ?? productionURL
    }

    static var anonKey: String {
        infoString("SUPABASE_ANON_KEY") ?? productionAnonKey
    }
}

enum SupabaseClientProvider {
    static let client: SupabaseClient = {
        return SupabaseClient(
            supabaseURL: URL(string: SupabaseConfig.urlString)!,
            supabaseKey: SupabaseConfig.anonKey,
            options: .init(auth: .init(flowType: .implicit))
        )
    }()
}
