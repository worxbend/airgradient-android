package dev.worxbend.airgradient.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUrlNormalizerTest {
    @Test
    fun `empty URL is unconfigured`() {
        assertEquals(
            DeviceUrlNormalizationResult.Unconfigured,
            DeviceUrlNormalizer.normalize("   "),
        )
    }

    @Test
    fun `bare host gains HTTP scheme`() {
        assertEquals(
            DeviceUrlNormalizationResult.Normalized("http://192.168.1.201"),
            DeviceUrlNormalizer.normalize("192.168.1.201"),
        )
    }

    @Test
    fun `path query fragment and trailing slash are stripped`() {
        assertEquals(
            DeviceUrlNormalizationResult.Normalized("http://192.168.1.201:80"),
            DeviceUrlNormalizer.normalize("http://192.168.1.201:80/foo?x=1#fragment"),
        )
    }

    @Test
    fun `HTTPS URL is accepted`() {
        assertEquals(
            DeviceUrlNormalizationResult.Normalized("https://airgradient.local:8443"),
            DeviceUrlNormalizer.normalize("https://airgradient.local:8443/path"),
        )
    }

    @Test
    fun `unsupported scheme missing host and whitespace host are invalid`() {
        assertTrue(DeviceUrlNormalizer.normalize("ftp://airgradient.local") is DeviceUrlNormalizationResult.Invalid)
        assertTrue(DeviceUrlNormalizer.normalize("http://") is DeviceUrlNormalizationResult.Invalid)
        assertTrue(DeviceUrlNormalizer.normalize("http://air gradient.local") is DeviceUrlNormalizationResult.Invalid)
    }
}
