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
    // pitchCount 는 시나리오 클로저가 손대지 않고 runner 가 이벤트 종류로 자동 누적/리셋한다.
    var pitchCount = 0
}

// 시뮬레이터에서 한 구로 카운트되는 이벤트 종류
private let pitchEventTypes: Set<String> = [
    "BALL", "STRIKE", "HIT", "HOMERUN", "WALK", "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY"
]

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

// 10분+ 시뮬레이션 시나리오 (백그라운드 데이터 전송 테스트용)
// 이벤트 간 간격: 기본 5초, 주요 이벤트 7초
private let simulationScenario: [SimEvent] = [
    // --- 1회초 (SSG 공격) ---
    SimEvent("BALL", "1회초 초구 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("HIT", "추신수 좌전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김재환"; return s },
    SimEvent("STRIKE", "김재환에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("HOMERUN", "김재환 투런 홈런", delayMs: 7000) { var s = $0; s.homeScore += 2; s.ball = 0; s.strike = 0; s.baseFirst = false; s.batter = "최정"; return s },
    SimEvent("BALL", "최정에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "최정 뜬공 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "최지훈"; return s },
    SimEvent("STRIKE", "최지훈에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("HIT", "최지훈 중전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "박성한"; return s },
    SimEvent("OUT", "박성한 1루 땅볼 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "이재원"; return s },
    SimEvent("DOUBLE_PLAY", "이재원 병살타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.baseFirst = false; s.inning = "1회말"; s.pitcher = "김건우"; s.batter = "나성범"; return s },
    // --- 1회말 (KIA 공격) ---
    SimEvent("STRIKE", "나성범에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("HIT", "나성범 우전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김선빈"; return s },
    SimEvent("HOMERUN", "김선빈 역전 투런 홈런", delayMs: 7000) { var s = $0; s.awayScore += 2; s.ball = 0; s.strike = 0; s.baseFirst = false; s.batter = "최형우"; return s },
    SimEvent("BALL", "최형우에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "최형우 삼진 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "이창진"; return s },
    SimEvent("BALL", "이창진에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "이창진 땅볼 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "류지혁"; return s },
    SimEvent("STRIKE", "류지혁에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("OUT", "류지혁 삼진", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.inning = "2회초"; s.pitcher = "김광현"; s.batter = "추신수"; return s },
    // --- 2회초 (SSG 공격) ---
    SimEvent("BALL", "추신수에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 3; return s },
    SimEvent("WALK", "추신수 볼넷", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김재환"; return s },
    SimEvent("STRIKE", "김재환에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("HIT", "김재환 좌전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.baseSecond = true; s.batter = "최정"; return s },
    SimEvent("BALL", "최정에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("HIT", "최정 적시타", delayMs: 7000) { var s = $0; s.homeScore += 1; s.ball = 0; s.strike = 0; s.baseFirst = true; s.baseSecond = false; s.baseThird = true; s.batter = "최지훈"; return s },
    SimEvent("SCORE", "SSG 1점 추가", delayMs: 7000) { $0 },
    SimEvent("BALL", "최지훈에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("OUT", "최지훈 삼진", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "박성한"; return s },
    SimEvent("STRIKE", "박성한에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "박성한 플라이 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "이재원"; return s },
    SimEvent("BALL", "이재원에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "이재원 땅볼 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.baseFirst = false; s.baseThird = false; s.inning = "2회말"; s.pitcher = "김건우"; s.batter = "나성범"; return s },
    // --- 2회말 (KIA 공격) ---
    SimEvent("STRIKE", "나성범에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("OUT", "나성범 플라이 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "김선빈"; return s },
    SimEvent("BALL", "김선빈에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STEAL", "김선빈 도루 시도", delayMs: 5000) { var s = $0; s.baseSecond = true; return s },
    SimEvent("HIT", "최형우 우전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.baseSecond = true; s.batter = "이창진"; return s },
    SimEvent("STRIKE", "이창진에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("OUT", "이창진 삼진", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "류지혁"; return s },
    SimEvent("BALL", "류지혁에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("HIT", "류지혁 적시타", delayMs: 7000) { var s = $0; s.awayScore += 1; s.ball = 0; s.strike = 0; s.baseFirst = true; s.baseSecond = false; s.baseThird = false; s.batter = "박찬호"; return s },
    SimEvent("SCORE", "KIA 동점", delayMs: 7000) { $0 },
    SimEvent("BALL", "박찬호에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "박찬호 땅볼 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.baseFirst = false; s.inning = "3회초"; s.pitcher = "김광현"; s.batter = "추신수"; return s },
    // --- 3회초 (SSG 공격) ---
    SimEvent("STRIKE", "추신수에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("HIT", "추신수 중전 안타", delayMs: 7000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "김재환"; return s },
    SimEvent("BALL", "김재환에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("HIT", "김재환 2루타", delayMs: 7000) { var s = $0; s.homeScore += 1; s.ball = 0; s.strike = 0; s.baseFirst = false; s.baseSecond = true; s.baseThird = true; s.batter = "최정"; return s },
    SimEvent("SCORE", "SSG 역전", delayMs: 7000) { $0 },
    SimEvent("BALL", "최정에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 2; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 3; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 4; return s },
    SimEvent("WALK", "최정 고의사구", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.baseFirst = true; s.batter = "최지훈"; return s },
    SimEvent("STRIKE", "최지훈에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "최지훈 삼진", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "박성한"; return s },
    SimEvent("OUT", "박성한 플라이 아웃", delayMs: 5000) { var s = $0; s.out = 2; s.batter = "이재원"; return s },
    SimEvent("OUT", "이재원 땅볼 아웃", delayMs: 5000) { var s = $0; s.out = 0; s.baseFirst = false; s.baseSecond = false; s.baseThird = false; s.inning = "3회말"; s.pitcher = "김건우"; s.batter = "나성범"; return s },
    // --- 3회말 (KIA 공격) ---
    SimEvent("BALL", "나성범에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("OUT", "나성범 땅볼 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 1; s.batter = "김선빈"; return s },
    SimEvent("STRIKE", "김선빈에게 스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("BALL", "볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("OUT", "김선빈 플라이 아웃", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 2; s.batter = "최형우"; return s },
    SimEvent("BALL", "최형우에게 볼", delayMs: 5000) { var s = $0; s.ball = 1; return s },
    SimEvent("STRIKE", "스트라이크", delayMs: 5000) { var s = $0; s.strike = 1; return s },
    SimEvent("STRIKE", "연속 스트라이크", delayMs: 5000) { var s = $0; s.strike = 2; return s },
    SimEvent("OUT", "최형우 삼진", delayMs: 5000) { var s = $0; s.ball = 0; s.strike = 0; s.out = 0; s.inning = "경기 종료"; return s },
    // --- 경기 종료 ---
    SimEvent("VICTORY", "SSG 승리!", delayMs: 7000) { $0 }
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
                        .font(AppFont.h4)
                }
                Text("워치 테스트")
                    .font(AppFont.h4Bold)
                    .foregroundColor(.white)
                Spacer()
            }
            .padding(.horizontal, AppSpacing.lg)
            .padding(.vertical, AppSpacing.md)
            .background(AppColors.gray950)

            ScrollView {
                LazyVStack(spacing: AppSpacing.md) {
                    BannerAdView()
                        .frame(width: 320, height: 50)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, AppSpacing.md)

                    scoreCard
                    autoSimulationCard
                    manualEventCard
                    cheerTestCard
                    logCard
                    Spacer().frame(height: AppSpacing.bottomSafeSpacer)
                }
                .padding(.horizontal, AppSpacing.lg)
                .padding(.top, AppSpacing.md)
            }
        }
        .background(AppColors.gray950)
        .onDisappear {
            simTask?.cancel()
        }
    }

    // MARK: - Score Card
    private var scoreCard: some View {
        VStack(spacing: AppSpacing.sm) {
            Text(gameState.inning)
                .font(AppFont.bodyBold)
                .foregroundColor(teamTheme.primary)

            HStack {
                Spacer()
                VStack {
                    Text(gameState.homeTeam)
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(.white)
                    Text("\(gameState.homeScore)")
                        .font(AppFont.h1)
                        .foregroundColor(.white)
                }
                Spacer()
                Text(":")
                    .font(AppFont.h2)
                    .foregroundColor(AppColors.gray500)
                Spacer()
                VStack {
                    Text(gameState.awayTeam)
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(.white)
                    Text("\(gameState.awayScore)")
                        .font(AppFont.h1)
                        .foregroundColor(.white)
                }
                Spacer()
            }

            HStack(spacing: AppSpacing.lg) {
                Text("B \(gameState.ball)").font(AppFont.caption).foregroundColor(AppColors.green400)
                Text("S \(gameState.strike)").font(AppFont.caption).foregroundColor(AppColors.yellow400)
                Text("O \(gameState.out)").font(AppFont.caption).foregroundColor(AppColors.red400)
            }

            let bases = [gameState.baseFirst ? "1루" : nil, gameState.baseSecond ? "2루" : nil, gameState.baseThird ? "3루" : nil].compactMap { $0 }
            Text(bases.isEmpty ? "주자 없음" : "주자: \(bases.joined(separator: ", "))")
                .font(AppFont.micro).foregroundColor(AppColors.gray400)
            Text("투수: \(gameState.pitcher) · \(gameState.pitchCount)구  타자: \(gameState.batter)")
                .font(AppFont.micro).foregroundColor(AppColors.gray400)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
    }

    // MARK: - Auto Simulation
    private var autoSimulationCard: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("자동 시뮬레이션")
                .font(AppFont.bodyBold)
                .foregroundColor(AppColors.gray300)

            HStack(spacing: AppSpacing.sm) {
                Button {
                    startSimulation()
                } label: {
                    HStack(spacing: AppSpacing.xs) {
                        Image(systemName: "play.fill").font(AppFont.body)
                        Text("시작")
                    }
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.sm)
                    .background(isSimulating ? AppColors.gray700 : teamTheme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(AppRadius.sm)
                }
                .disabled(isSimulating)

                Button {
                    stopSimulation()
                } label: {
                    HStack(spacing: AppSpacing.xs) {
                        Image(systemName: "stop.fill").font(AppFont.body)
                        Text("중단")
                    }
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.sm)
                    .background(Color.clear)
                    .foregroundColor(isSimulating ? AppColors.red400 : AppColors.gray500)
                    .overlay(RoundedRectangle(cornerRadius: AppRadius.sm).stroke(isSimulating ? AppColors.red400 : AppColors.gray600, lineWidth: 1))
                    .cornerRadius(AppRadius.sm)
                }
                .disabled(!isSimulating)
            }

            if isSimulating {
                ProgressView(value: Double(simIndex + 1), total: Double(simulationScenario.count))
                    .tint(teamTheme.primary)
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }

    // MARK: - Manual Events
    private var manualEventCard: some View {
        // HOMERUN은 팀 테마 primary를 강조색으로 사용, 나머지는 AppEventColors 매핑.
        let events: [(String, String, Color)] = [
            ("HOMERUN", "홈런", teamTheme.primary),
            ("HIT", "안타", AppEventColors.color(for: "HIT")),
            ("WALK", "볼넷", AppEventColors.color(for: "WALK")),
            ("STEAL", "도루", AppEventColors.color(for: "STEAL")),
            ("SCORE", "득점", AppEventColors.color(for: "SCORE")),
            ("DOUBLE_PLAY", "병살", AppEventColors.color(for: "DOUBLE_PLAY")),
            ("TRIPLE_PLAY", "삼중살", AppEventColors.color(for: "TRIPLE_PLAY")),
            ("OUT", "아웃", AppEventColors.color(for: "OUT")),
            ("STRIKE", "스트라이크", AppEventColors.color(for: "STRIKE")),
            ("BALL", "볼", AppEventColors.color(for: "BALL")),
            ("VICTORY", "승리", AppEventColors.color(for: "VICTORY"))
        ]

        return VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("수동 이벤트 전송")
                .font(AppFont.bodyBold)
                .foregroundColor(AppColors.gray300)

            let rows = stride(from: 0, to: events.count, by: 2).map { Array(events[$0..<min($0 + 2, events.count)]) }
            ForEach(0..<rows.count, id: \.self) { rowIndex in
                HStack(spacing: AppSpacing.sm) {
                    ForEach(0..<rows[rowIndex].count, id: \.self) { colIndex in
                        let event = rows[rowIndex][colIndex]
                        Button {
                            addLog("[\(event.0)] 수동 전송")
                            sendCurrentState(eventType: event.0)
                        } label: {
                            Text(event.1)
                                .font(AppFont.bodyBold)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: AppSpacing.buttonHeight)
                                .background(event.2)
                                .cornerRadius(AppRadius.sm)
                        }
                    }
                    if rows[rowIndex].count < 2 {
                        Spacer().frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }

    // MARK: - Cheer Test
    private var cheerTestCard: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("현장 응원 테스트")
                .font(AppFont.bodyBold)
                .foregroundColor(AppColors.gray300)

            Text("워치에 풀스크린 응원 문구와 팀 컬러, 햅틱을 즉시 전송합니다.")
                .font(AppFont.caption)
                .foregroundColor(AppColors.gray500)

            Button {
                sendCheerTest()
            } label: {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "figure.baseball")
                        .font(AppFont.body)
                    Text("응원 화면 테스트")
                        .font(AppFont.bodyBold)
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: AppSpacing.buttonHeight)
                .background(selectedTeam == .none ? AppColors.blue600 : teamTheme.primary)
                .cornerRadius(AppRadius.sm)
            }
            .buttonStyle(.plain)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }

    // MARK: - Log Card
    private var logCard: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            HStack {
                Text("전송 로그")
                    .font(AppFont.bodyBold)
                    .foregroundColor(AppColors.gray300)
                Spacer()
                Button("지우기") { logMessages = [] }
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray500)
            }

            if logMessages.isEmpty {
                Text("이벤트를 전송하면 여기에 표시됩니다.")
                    .font(AppFont.caption)
                    .foregroundColor(AppColors.gray600)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, AppSpacing.lg)
            } else {
                ForEach(0..<logMessages.count, id: \.self) { i in
                    Text(logMessages[i])
                        .font(AppFont.caption)
                        .foregroundColor(AppColors.gray400)
                        .padding(.vertical, AppSpacing.xxs)
                }
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }

    // MARK: - Actions
    private func addLog(_ msg: String) {
        logMessages = ([msg] + logMessages).prefix(30).map { $0 }
    }

    private func shouldSendEvent(_ eventType: String?) -> Bool {
        guard let type = eventType?.uppercased() else { return true }
        if type == "BALL" || type == "STRIKE" {
            return UserDefaults.standard.bool(forKey: "ball_strike_haptic_enabled")
        }
        return true
    }

    private func sendCurrentState(eventType: String?) {
        let filteredEventType = shouldSendEvent(eventType) ? eventType : nil
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
            pitcherPitchCount: gameState.pitchCount,
            myTeam: selectedTeam.rawValue,
            eventType: filteredEventType
        )
    }

    private func sendCheerTest() {
        let team = selectedTeam == .none ? Team.doosan : selectedTeam
        let cheerText = "\(team.teamName) 팬들, 지금 함께 응원해요!"
        WatchThemeSyncManager.sendCheerTrigger(
            teamCode: team.rawValue,
            stadiumCode: "TEST",
            cheerText: cheerText,
            primaryColorHex: cheerPrimaryColorHex(for: team),
            hapticPatternId: "watch_test_v1",
            fireAtUnixMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        addLog("[CHEER] \(team.teamName) 응원 화면 테스트 전송")
    }

    private func cheerPrimaryColorHex(for team: Team) -> String {
        switch team {
        case .none: return "#3B82F6"
        case .doosan: return "#131230"
        case .lg: return "#C30452"
        case .kiwoom: return "#820024"
        case .samsung: return "#074CA1"
        case .lotte: return "#041E42"
        case .ssg: return "#CE0E2D"
        case .kt: return "#000000"
        case .hanwha: return "#FF6600"
        case .kia: return "#EA0029"
        case .nc: return "#315288"
        }
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
                let prevPitcher = gameState.pitcher
                var next = event.stateUpdate(gameState)
                // pitchCount: 투수가 바뀌면 0 으로 리셋, 같으면 한 구짜리 이벤트일 때만 +1
                if next.pitcher != prevPitcher {
                    next.pitchCount = 0
                } else if pitchEventTypes.contains(event.eventType) {
                    next.pitchCount = gameState.pitchCount + 1
                } else {
                    next.pitchCount = gameState.pitchCount
                }
                gameState = next
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
