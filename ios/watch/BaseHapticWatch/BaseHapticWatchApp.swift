import SwiftUI
import WatchKit

@main
struct BaseHapticWatchApp: App {
    @StateObject private var connectivity = WatchConnectivityManager.shared

    var body: some Scene {
        WindowGroup {
            WatchContentView()
                .environmentObject(connectivity)
                .environment(\.watchTeamTheme, WatchTeamThemes.theme(for: connectivity.syncedTeamName))
                .onAppear {
                    connectivity.activate()
                }
        }
    }
}

// MARK: - Main Watch View
struct WatchContentView: View {
    @EnvironmentObject private var connectivity: WatchConnectivityManager
    @Environment(\.watchTeamTheme) private var watchTheme

    @State private var isEventOverlayVisible = false
    @State private var visibleEventType: String?
    @State private var visibleEventTimestamp: Date?

    private let eventOverlayDuration: TimeInterval = 2.2
    private let eventFreshness: TimeInterval = 5.0

    var body: some View {
        ZStack {
            // Main content
            if let gameData = connectivity.gameData {
                WatchLiveGameScreen(gameData: gameData)
            } else {
                WatchNoGameScreen()
            }

            // Event overlay
            if let eventType = visibleEventType, isEventOverlayVisible {
                WatchEventOverlay(eventType: eventType)
            }

            // Watch sync prompt
            if let prompt = connectivity.watchSyncPrompt {
                WatchSyncPromptView(
                    prompt: prompt,
                    onAccept: {
                        connectivity.sendSyncResponse(gameId: prompt.gameId, accepted: true)
                        connectivity.clearSyncPrompt()
                    },
                    onDecline: {
                        connectivity.sendSyncResponse(gameId: prompt.gameId, accepted: false)
                        connectivity.clearSyncPrompt()
                    }
                )
            }
        }
        .onChange(of: connectivity.latestEventTimestamp) { timestamp in
            guard let timestamp = timestamp,
                  let eventType = connectivity.latestEventType,
                  Date().timeIntervalSince(timestamp) < eventFreshness else { return }

            visibleEventType = eventType
            visibleEventTimestamp = timestamp
            isEventOverlayVisible = true

            DispatchQueue.main.asyncAfter(deadline: .now() + eventOverlayDuration) {
                if visibleEventTimestamp == timestamp {
                    isEventOverlayVisible = false
                    visibleEventType = nil
                }
            }
        }
    }
}

// MARK: - Event Overlay
struct WatchEventOverlay: View {
    let eventType: String

    private var eventUI: (label: String, icon: String, color: Color)? {
        switch eventType.uppercased() {
        case "HIT": return ("HIT", "bolt.fill", Color(red: 34/255, green: 197/255, blue: 94/255))
        case "WALK": return ("WALK", "bolt.fill", Color(red: 74/255, green: 222/255, blue: 128/255))
        case "STEAL": return ("STEAL", "bolt.fill", Color(red: 6/255, green: 182/255, blue: 212/255))
        case "SCORE": return ("SCORE", "trophy.fill", Color(red: 234/255, green: 179/255, blue: 8/255))
        case "HOMERUN": return ("HOMERUN", "trophy.fill", Color(red: 234/255, green: 179/255, blue: 8/255))
        case "OUT": return ("OUT", "xmark.circle.fill", Color(red: 239/255, green: 68/255, blue: 68/255))
        case "DOUBLE_PLAY": return ("DOUBLE PLAY", "xmark.circle.fill", Color(red: 249/255, green: 115/255, blue: 22/255))
        case "TRIPLE_PLAY": return ("TRIPLE PLAY", "xmark.circle.fill", Color(red: 220/255, green: 38/255, blue: 38/255))
        default: return nil
        }
    }

    var body: some View {
        if let ui = eventUI {
            VStack(spacing: 4) {
                Image(systemName: ui.icon)
                    .foregroundColor(ui.color)
                    .font(.system(size: 20))
                Text(ui.label)
                    .foregroundColor(.white)
                    .font(.system(size: 12, weight: .medium))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.black.opacity(0.8))
            .cornerRadius(14)
        }
    }
}

// MARK: - Watch Sync Prompt
struct WatchSyncPromptView: View {
    let prompt: WatchConnectivityManager.WatchSyncPrompt
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.78)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Text("경기를 관람하겠습니까?")
                    .foregroundColor(.white)
                    .font(.system(size: 13))
                    .multilineTextAlignment(.center)

                let matchup = [prompt.awayTeam, prompt.homeTeam]
                    .filter { !$0.isEmpty }
                    .joined(separator: " vs ")
                if !matchup.isEmpty {
                    Text(matchup)
                        .foregroundColor(.white.opacity(0.72))
                        .font(.system(size: 12))
                }

                HStack(spacing: 8) {
                    Button("예") { onAccept() }
                        .buttonStyle(.borderedProminent)
                    Button("아니오") { onDecline() }
                        .buttonStyle(.bordered)
                }
                .padding(.top, 10)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 14)
            .background(Color(red: 26/255, green: 26/255, blue: 26/255))
            .cornerRadius(14)
            .padding(.horizontal, 12)
        }
    }
}
