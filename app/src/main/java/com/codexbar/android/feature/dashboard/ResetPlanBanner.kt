package com.codexbar.android.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.presentation.QuotaResetPlanPresentation
import com.codexbar.android.core.presentation.ResetPlanAction
import com.codexbar.android.ui.theme.CodexBarSpacing
import com.codexbar.android.ui.theme.CodexBarStateColors

@Composable
internal fun ResetPlanBanner(
    plan: QuotaResetPlanPresentation,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val color = when (plan.action) {
        ResetPlanAction.AlmostUsed -> MaterialTheme.colorScheme.error
        ResetPlanAction.SlowDown -> CodexBarStateColors.warningColor()
        ResetPlanAction.UseNow -> MaterialTheme.colorScheme.tertiary
        ResetPlanAction.KeepPace,
        ResetPlanAction.UseByCheckpoint -> accent
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(CodexBarSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(CodexBarSpacing.medium),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CodexBarSpacing.xsmall)
            ) {
                Text(
                    text = stringResource(R.string.reset_plan_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = plan.actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${plan.deadlineLabel} · ${plan.budgetLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
