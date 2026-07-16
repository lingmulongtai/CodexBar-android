package com.codexbar.android.core.network.elevenlabs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ElevenLabsDto {
    @Serializable
    data class Subscription(
        val tier: String? = null,
        val status: String? = null,
        @SerialName("character_count") val characterCount: Long,
        @SerialName("character_limit") val characterLimit: Long,
        @SerialName("voice_slots_used") val voiceSlotsUsed: Long? = null,
        @SerialName("professional_voice_slots_used") val professionalVoiceSlotsUsed: Long? = null,
        @SerialName("voice_limit") val voiceLimit: Long? = null,
        @SerialName("professional_voice_limit") val professionalVoiceLimit: Long? = null,
        @SerialName("next_character_count_reset_unix") val nextCharacterCountResetUnix: Long? = null
    )
}
