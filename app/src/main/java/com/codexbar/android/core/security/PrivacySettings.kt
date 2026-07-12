package com.codexbar.android.core.security

data class PrivacySettings(
    val screenPrivacyEnabled: Boolean = true,
    val lockScreenRedactionEnabled: Boolean = true,
    val notificationRedactionEnabled: Boolean = false,
    val widgetRedactionEnabled: Boolean = false
)
