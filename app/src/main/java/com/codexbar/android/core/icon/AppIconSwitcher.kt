package com.codexbar.android.core.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.codexbar.android.MainActivity
import com.codexbar.android.core.domain.model.AppThemeStyle

/**
 * Keeps exactly one launcher entry enabled: the activity alias whose icon matches the selected
 * design. Changing launcher components while the app is in front can make some launchers close
 * the task, so callers apply it when the app leaves the foreground.
 */
object AppIconSwitcher {
    internal fun aliasFor(style: AppThemeStyle): String = when (style) {
        AppThemeStyle.MATERIAL_3 -> "LauncherExpressive"
        AppThemeStyle.LIQUID_GLASS -> "LauncherLiquidGlass"
        AppThemeStyle.INSTRUMENT -> "LauncherInstrument"
    }

    fun apply(context: Context, style: AppThemeStyle) {
        val packageManager = context.packageManager
        val target = component(context, aliasFor(style))
        // Enable the new entry first so the app is never left without a launcher icon.
        if (!isEnabled(packageManager, target, defaultEnabled = style == AppThemeStyle.MATERIAL_3)) {
            packageManager.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        AppThemeStyle.entries.filter { it != style }.forEach { other ->
            val alias = component(context, aliasFor(other))
            if (isEnabled(packageManager, alias, defaultEnabled = other == AppThemeStyle.MATERIAL_3)) {
                packageManager.setComponentEnabledSetting(
                    alias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun component(context: Context, alias: String): ComponentName {
        // Aliases are declared relative to the namespace, which debug builds keep unchanged.
        return ComponentName(context.packageName, "${MainActivity::class.java.`package`!!.name}.$alias")
    }

    private fun isEnabled(packageManager: PackageManager, component: ComponentName, defaultEnabled: Boolean): Boolean {
        return when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaultEnabled
            else -> false
        }
    }
}
