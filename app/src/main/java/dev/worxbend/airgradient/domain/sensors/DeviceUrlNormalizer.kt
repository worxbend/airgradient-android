package dev.worxbend.airgradient.domain.sensors

import java.net.URI

object DeviceUrlNormalizer {
    fun normalize(input: String): DeviceUrlNormalizationResult = normalizeTrimmed(input.trim())

    private fun normalizeTrimmed(trimmedInput: String): DeviceUrlNormalizationResult =
        if (trimmedInput.isEmpty()) {
            DeviceUrlNormalizationResult.Unconfigured
        } else {
            normalizeCandidate(trimmedInput)
        }

    private fun normalizeCandidate(trimmedInput: String): DeviceUrlNormalizationResult {
        val candidate =
            if (trimmedInput.contains("://")) {
                trimmedInput
            } else {
                "$DEFAULT_SCHEME://$trimmedInput"
            }

        val uri =
            runCatching { URI(candidate) }
                .getOrNull()

        return if (uri == null) {
            DeviceUrlNormalizationResult.Invalid
        } else {
            normalizeUri(uri)
        }
    }

    private fun normalizeUri(uri: URI): DeviceUrlNormalizationResult {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host

        return if (scheme !in SUPPORTED_SCHEMES || host.isNullOrBlank() || host.any(Char::isWhitespace)) {
            DeviceUrlNormalizationResult.Invalid
        } else {
            val normalized =
                URI(
                    scheme,
                    null,
                    host,
                    uri.port,
                    null,
                    null,
                    null,
                ).toString()

            DeviceUrlNormalizationResult.Normalized(normalized)
        }
    }

    private const val DEFAULT_SCHEME = "http"
    private val SUPPORTED_SCHEMES = setOf("http", "https")
}

sealed interface DeviceUrlNormalizationResult {
    data object Unconfigured : DeviceUrlNormalizationResult

    data object Invalid : DeviceUrlNormalizationResult

    data class Normalized(
        val value: String,
    ) : DeviceUrlNormalizationResult
}
