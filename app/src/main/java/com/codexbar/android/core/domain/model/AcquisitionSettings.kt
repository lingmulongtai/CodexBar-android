package com.codexbar.android.core.domain.model

enum class AcquisitionMode { PHONE, WINDOWS }

/** Source selection is independent from saved phone credentials and pairing. */
data class AcquisitionSettings(
    val mode: AcquisitionMode = AcquisitionMode.PHONE,
    val advertisedServices: Set<AiService> = emptySet(),
    val phoneExceptions: Set<AiService> = emptySet()
) {
    val desktopServices: Set<AiService>
        get() = if (mode == AcquisitionMode.WINDOWS) advertisedServices - phoneExceptions else emptySet()

    // An unadvertised service must not silently fall back to a different phone account.
    fun routesToDesktop(service: AiService): Boolean = mode == AcquisitionMode.WINDOWS && service !in phoneExceptions

    fun hasSource(service: AiService, hasPhoneCredential: Boolean): Boolean =
        if (routesToDesktop(service)) service in advertisedServices else hasPhoneCredential

    companion object {
        fun restore(
            paired: Boolean,
            storedMode: String?,
            legacyServices: Set<AiService>,
            advertisedServices: Set<AiService>?,
            phoneExceptions: Set<AiService>?
        ): AcquisitionSettings {
            if (!paired) return AcquisitionSettings()
            if (storedMode == null) {
                return AcquisitionSettings(
                    mode = if (legacyServices.isEmpty()) AcquisitionMode.PHONE else AcquisitionMode.WINDOWS,
                    advertisedServices = legacyServices,
                    phoneExceptions = AiService.entries.toSet() - legacyServices
                )
            }
            return AcquisitionSettings(
                AcquisitionMode.entries.find { it.name == storedMode } ?: AcquisitionMode.PHONE,
                advertisedServices.orEmpty(), phoneExceptions.orEmpty()
            )
        }
    }
}
