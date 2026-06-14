import Foundation
import Supabase

enum SupabaseConfig {
    private static let defaultURL = "https://egcsxoxqfcwjjcvjycry.supabase.co"
    private static let defaultAnonKey = "sb_publishable_VTKZ4I3FS3COPXSe0dryjg_VDhg0D-a"

    private static func infoString(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.hasPrefix("$(") { return nil }
        return trimmed
    }

    static var urlString: String {
        infoString("SUPABASE_URL") ?? defaultURL
    }

    static var anonKey: String {
        infoString("SUPABASE_ANON_KEY") ?? defaultAnonKey
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
