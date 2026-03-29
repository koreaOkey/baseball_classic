import Foundation
import Supabase

enum SupabaseClientProvider {
    static let client: SupabaseClient = {
        let url = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_URL") as? String ?? "https://snrafqoqpmtoannnnwdq.supabase.co"
        let key = Bundle.main.object(forInfoDictionaryKey: "SUPABASE_ANON_KEY") as? String ?? ""
        return SupabaseClient(
            supabaseURL: URL(string: url)!,
            supabaseKey: key
        )
    }()
}
