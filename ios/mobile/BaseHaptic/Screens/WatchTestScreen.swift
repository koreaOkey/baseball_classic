import SwiftUI

// MARK: - Simulation Models
private struct SimGameState {
    var homeTeam = "SSG"
    var awayTeam = "KIA"
    var homeScore = 0
    var awayScore = 0
    var inning = "1회초"
    var ball = 0
    var strike = 0
    var out = 0
    var baseFirst = false
    var baseSecond = false
    var baseThird = false
    var pitcher = "김광현"
    var batter = "추신수"
}

private struct SimEvent {
    let eventType: String
    let description: String
    let stateUpdate: (SimGameState) -> SimGameState
    let delayMs: UInt64

    init(_ eventType: String, _ description: String, delayMs: UInt64 = 2000, _ stateUpdate: @escaping (SimGameState) -> SimGameState) {
        self.eventType = eventType
        self.description = description
        self.delayMs = delayMs
        self.stateUpdate = stateUpdate
    }
}

private let simulationScenario: [SimEvent] = [
    SimEvent("BALL", "1회초 초구 볼") { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크") { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼") { var s = $0; s.ball = 2; return s },
    SimEvent("HIT", "추신수 좌전 안타") { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김재환"; return s },
    SimEvent("STRIKE", "김재환에게 스트라이크") { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크") { var s = $0; s.strike = 2; return s },
    SimEvent("HOMERUN", "김재환 투런 홈런", delayMs: 3000) { var s = $0; s.homeScore += 2; s.ball = 0; s.strike = 0; s.baseFirst = false; s.batter = "최정"; return s },
    SimEvent("SCORE", "SSG 2점 리드") { $0 },
    SimEvent("BALL", "최정에게 볼") { var s = $0; s.ball = 1; return s },
    SimEvent("OUT", "최정 뜬공 아웃") { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "최지훈"; return s },
    SimEvent("STRIKE", "최지훈에게 스트라이크") { var s = $0; s.strike = 1; return s },
    SimEvent("HIT", "최지훈 중전 안타") { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "박성한"; return s },
    SimEvent("OUT", "박성한 1루 땅볼 아웃") { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "이재원"; return s },
    SimEvent("DOUBLE_PLAY", "이재원 2루수 병살타") { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.baseFirst = false; s.baseSecond = false; s.baseThird = false; s.inning = "1회말"; s.pitcher = "김건우"; s.batter = "나성범"; return s },
    SimEvent("STRIKE", "나성범에게 스트라이크") { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼") { var s = $0; s.ball = 1; return s },
    SimEvent("HIT", "나성범 우전 안타") { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김선빈"; return s },
    SimEvent("HOMERUN", "김선빈 역전 투런 홈런", delayMs: 3000) { var s = $0; s.awayScore += 2; s.ball = 0; s.strike = 0; s.baseFirst = false; s.batter = "최형우"; return s },
    SimEvent("SCORE", "KIA 역전") { var s = $0; s.inning = "1회말"; return s }
]

// MARK: - WatchTestScreen
struct WatchTestScreen: View {
    let selectedTeam: Team
    let onBack: () -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var gameState = SimGameState()
    @State private var logMessages: [String] = []
    @State private var isSimulating = false
    @State private var simIndex = 0
    @State private var simTask: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                }
                Text("워치 테스트")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(AppColors.gray950)

            ScrollView {
                LazyVStack(spacing: 12) {
                    scoreCard
                    autoSimulationCard
                    manualEventCard
                    logCard
                    Spacer().frame(height: 80)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
        }
        .background(AppColors.gray950)
        .onDisappear {
            simTask?.cancel()
        }
    }

    // MARK: - Score Card
    private var scoreCard: some View {
        VStack(spacing: 8) {
            Text(gameState.inning)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(teamTheme.primary)

            HStack {
                Spacer()
                VStack {
                    Text(gameState.homeTeam)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text("\(gameState.homeScore)")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                }
                Spacer()
                Text(":")
                    .font(.system(size: 28))
                    .foregroundColor(AppColors.gray500)
                Spacer()
                VStack {
                    Text(gameState.awayTeam)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text("\(gameState.awayScore)")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                }
                Spacer()
            }

            HStack(spacing: 16) {
                Text("B \(gameState.ball)").font(.system(size: 13)).foregroundColor(Color(hex: 0x4ADE80))
                Text("S \(gameState.strike)").font(.system(size: 13)).foregroundColor(Color(hex: 0xFACC15))
                Text("O \(gameState.out)").font(.system(size: 13)).foregroundColor(Color(hex: 0xF87171))
            }

            let bases = [gameState.baseFirst ? "1루" : nil, gameState.baseSecond ? "2루" : nil, gameState.baseThird ? "3루" : nil].compactMap { $0 }
            Text(bases.isEmpty ? "주자 없음" : "주자: \(bases.joined(separator: ", "))")
                .font(.system(size: 12)).foregroundColor(AppColors.gray400)
            Text("투수: \(gameState.pitcher)  타자: \(gameState.batter)")
                .font(.system(size: 12)).foregroundColor(AppColors.gray400)
        }
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(16)
    }

    // MARK: - Auto Simulation
    private var autoSimulationCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("자동 시뮬레이션")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AppColors.gray300)

            HStack(spacing: 8) {
                Button {
                    startSimulation()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill").font(.system(size: 14))
                        Text("시작")
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(isSimulating ? AppColors.gray700 : teamTheme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .disabled(isSimulating)

                Button {
                    stopSimulation()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "stop.fill").font(.system(size: 14))
                        Text("중단")
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.clear)
                    .foregroundColor(isSimulating ? Color(hex: 0xF87171) : AppColors.gray500)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(isSimulating ? Color(hex: 0xF87171) : AppColors.gray600, lineWidth: 1))
                    .cornerRadius(8)
                }
                .disabled(!isSimulating)
            }

            if isSimulating {
                ProgressView(value: Double(simIndex + 1), total: Double(simulationScenario.count))
                    .tint(teamTheme.primary)
            }
        }
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(12)
    }

    // MARK: - Manual Events
    private var manualEventCard: some View {
        let events: [(String, String, Color)] = [
            ("HOMERUN", "홈런", teamTheme.primary),
            ("HIT", "안타", Color(hex: 0x22C55E)),
            ("WALK", "볼넷", Color(hex: 0x4ADE80)),
            ("STEAL", "도루", Color(hex: 0x3B82F6)),
            ("SCORE", "득점", Color(hex: 0xEAB308)),
            ("DOUBLE_PLAY", "병살", Color(hex: 0xF97316)),
            ("TRIPLE_PLAY", "삼중살", Color(hex: 0xF87171)),
            ("OUT", "아웃", Color(hex: 0xEF4444)),
            ("STRIKE", "스트라이크", Color(hex: 0xF97316)),
            ("BALL", "볼", Color(hex: 0x3B82F6)),
            ("VICTORY", "승리", Color(hex: 0xEAB308))
        ]

        return VStack(alignment: .leading, spacing: 8) {
            Text("수동 이벤트 전송")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AppColors.gray300)

            let rows = stride(from: 0, to: events.count, by: 2).map { Array(events[$0..<min($0 + 2, events.count)]) }
            ForEach(0..<rows.count, id: \.self) { rowIndex in
                HStack(spacing: 8) {
                    ForEach(0..<rows[rowIndex].count, id: \.self) { colIndex in
                        let event = rows[rowIndex][colIndex]
                        Button {
                            addLog("[\(event.0)] 수동 전송")
                            sendCurrentState(eventType: event.0)
                        } label: {
                            Text(event.1)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(event.2)
                                .cornerRadius(10)
                        }
                    }
                    if rows[rowIndex].count < 2 {
                        Spacer().frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(12)
    }

    // MARK: - Log Card
    private var logCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("전송 로그")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(AppColors.gray300)
                Spacer()
                Button("지우기") { logMessages = [] }
                    .font(.system(size: 12))
                    .foregroundColor(AppColors.gray500)
            }

            if logMessages.isEmpty {
                Text("이벤트를 전송하면 여기에 표시됩니다.")
                    .font(.system(size: 13))
                    .foregroundColor(AppColors.gray600)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            } else {
                ForEach(0..<logMessages.count, id: \.self) { i in
                    Text(logMessages[i])
                        .font(.system(size: 13))
                        .foregroundColor(AppColors.gray400)
                        .padding(.vertical, 2)
                }
            }
        }
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(12)
    }

    // MARK: - Actions
    private func addLog(_ msg: String) {
        logMessages = ([msg] + logMessages).prefix(30).map { $0 }
    }

    private func sendCurrentState(eventType: String?) {
        WatchGameSyncManager.shared.sendGameData(
            gameId: "test_001",
            homeTeam: gameState.homeTeam,
            awayTeam: gameState.awayTeam,
            homeScore: gameState.homeScore,
            awayScore: gameState.awayScore,
            inning: gameState.inning,
            ball: gameState.ball,
            strike: gameState.strike,
            out: gameState.out,
            baseFirst: gameState.baseFirst,
            baseSecond: gameState.baseSecond,
            baseThird: gameState.baseThird,
            pitcher: gameState.pitcher,
            batter: gameState.batter,
            myTeam: selectedTeam.rawValue,
            eventType: eventType
        )
    }

    private func startSimulation() {
        guard !isSimulating else { return }
        isSimulating = true
        simIndex = 0
        gameState = SimGameState()
        logMessages = []
        addLog("자동 시뮬레이션 시작")

        simTask = Task {
            for i in simulationScenario.indices {
                guard !Task.isCancelled, isSimulating else { break }
                simIndex = i
                let event = simulationScenario[i]
                gameState = event.stateUpdate(gameState)
                addLog("[\(event.eventType)] \(event.description)")
                sendCurrentState(eventType: event.eventType)
                try? await Task.sleep(nanoseconds: event.delayMs * 1_000_000)
            }
            isSimulating = false
            addLog("자동 시뮬레이션 종료")
        }
    }

    private func stopSimulation() {
        isSimulating = false
        simTask?.cancel()
        addLog("자동 시뮬레이션 중단")
    }
}
