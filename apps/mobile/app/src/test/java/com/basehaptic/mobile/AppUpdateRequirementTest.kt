package com.basehaptic.mobile

import com.basehaptic.mobile.data.BackendGamesRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRequirementTest {
    @Test
    fun `minimum supported version requires update even when force update is false`() {
        val config = appConfig(
            minSupportedVersion = "1.1.7",
            latestVersion = "1.1.7",
            forceUpdate = false
        )

        assertTrue(requiresServerUpdate("1.1.6", config))
    }

    @Test
    fun `latest version only requires server update when force update is true`() {
        val optionalConfig = appConfig(
            latestVersion = "1.1.7",
            forceUpdate = false
        )
        val requiredConfig = optionalConfig.copy(forceUpdate = true)

        assertFalse(requiresServerUpdate("1.1.6", optionalConfig))
        assertTrue(requiresServerUpdate("1.1.6", requiredConfig))
    }

    @Test
    fun `current version does not require update when it satisfies server versions`() {
        val config = appConfig(
            minSupportedVersion = "1.1.7",
            latestVersion = "1.1.7",
            forceUpdate = true
        )

        assertFalse(requiresServerUpdate("1.1.7", config))
    }

    private fun appConfig(
        minSupportedVersion: String = "",
        latestVersion: String = "",
        forceUpdate: Boolean = false,
    ): BackendGamesRepository.AppConfig =
        BackendGamesRepository.AppConfig(
            platform = "android",
            minSupportedVersion = minSupportedVersion,
            latestVersion = latestVersion,
            forceUpdate = forceUpdate,
            updateTitle = "업데이트가 필요합니다",
            updateMessage = "안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요.",
            storeUrl = "market://details?id=com.basehaptic.mobile",
            notice = BackendGamesRepository.AppNotice(
                enabled = false,
                title = "",
                message = ""
            )
        )
}
