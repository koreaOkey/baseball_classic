import Foundation
import Supabase

@MainActor
class ThemeRepository {
    static let shared = ThemeRepository()

    private let client = SupabaseClientProvider.client

    // MARK: - Fetch (로그인 시 복원)

    /// 유저가 잠금해제한 테마 ID 목록을 서버에서 가져온다.
    func fetchUnlockedThemeIds() async throws -> Set<String> {
        let userId = try await currentUserId()

        struct PurchaseRow: Decodable {
            let theme_id: String
        }

        let rows: [PurchaseRow] = try await client.database
            .from("user_theme_purchases")
            .select("theme_id")
            .eq("user_id", value: userId.uuidString)
            .eq("status", value: "PAID")
            .execute()
            .value

        var ids = Set(rows.map(\.theme_id))
        ids.insert("default") // 기본형은 항상 포함
        return ids
    }

    /// 유저가 현재 적용 중인 테마 ID와 응원팀을 서버에서 가져온다.
    func fetchUserSettings() async throws -> (activeThemeId: String?, selectedTeam: String?) {
        let userId = try await currentUserId()

        struct SettingsRow: Decodable {
            let active_theme_id: String?
            let selected_team: String?
        }

        let rows: [SettingsRow] = try await client.database
            .from("user_settings")
            .select("active_theme_id, selected_team")
            .eq("user_id", value: userId.uuidString)
            .execute()
            .value

        let row = rows.first
        return (row?.active_theme_id, row?.selected_team)
    }

    // MARK: - Save (잠금해제 / 적용)

    /// 테마 잠금해제 이력을 서버에 저장한다.
    func saveUnlock(themeId: String) async throws {
        let userId = try await currentUserId()

        struct InsertRow: Encodable {
            let user_id: String
            let theme_id: String
            let order_id: String
            let status: String
        }

        let row = InsertRow(
            user_id: userId.uuidString,
            theme_id: themeId,
            order_id: "ad_reward_\(userId.uuidString)_\(themeId)_\(Int(Date().timeIntervalSince1970))",
            status: "PAID"
        )

        try await client.database
            .from("user_theme_purchases")
            .insert(row)
            .execute()
    }

    /// 현재 적용 중인 테마를 서버에 저장한다.
    func saveActiveTheme(themeId: String?) async throws {
        let userId = try await currentUserId()

        struct UpsertRow: Encodable {
            let user_id: String
            let active_theme_id: String?
        }

        let row = UpsertRow(
            user_id: userId.uuidString,
            active_theme_id: themeId
        )

        try await client.database
            .from("user_settings")
            .upsert(row)
            .execute()
    }

    /// 응원팀을 서버에 저장한다.
    func saveSelectedTeam(_ team: String) async throws {
        let userId = try await currentUserId()

        struct UpsertRow: Encodable {
            let user_id: String
            let selected_team: String
        }

        let row = UpsertRow(
            user_id: userId.uuidString,
            selected_team: team
        )

        try await client.database
            .from("user_settings")
            .upsert(row)
            .execute()
    }

    // MARK: - Helper

    private func currentUserId() async throws -> UUID {
        let session = try await client.auth.session
        return session.user.id
    }
}
