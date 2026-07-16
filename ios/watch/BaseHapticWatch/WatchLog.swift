import Foundation

/// DEBUG 빌드에서만 출력되는 로그 — release에서는 메시지 생성 자체를 생략
func wlog(_ msg: @autoclosure () -> String) {
    #if DEBUG
    print(msg())
    #endif
}
