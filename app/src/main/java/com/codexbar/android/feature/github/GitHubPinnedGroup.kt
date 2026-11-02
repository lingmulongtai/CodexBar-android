package com.codexbar.android.feature.github

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.GitHubWidget
import com.codexbar.android.core.network.github.GitHubRepository
import com.codexbar.android.ui.components.*

@Composable
internal fun GitHubPinnedGroup(widget: GitHubWidget, repositories: List<GitHubRepository>,
    onManage: () -> Unit, open: (String) -> Unit) {
    val repos = widget.repositoryIds.mapNotNull { id -> repositories.find { it.id == id } }
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pinned", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                CodexBarTextButton(onClick = onManage) { Text(stringResource(R.string.github_widgets_edit)) }
            }
            if (repos.isEmpty()) CodexBarOutlinedButton(onClick = onManage) {
                Text(stringResource(R.string.github_widgets_add_repository))
            }
            repos.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { repo ->
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = .06f))
                            .border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(16.dp))
                            .clickable { open(repo.htmlUrl) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(repo.fullName.substringAfter('/'), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(if (repo.isPrivate) R.string.github_dashboard_private else R.string.github_dashboard_public),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            repo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                                maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            Spacer(Modifier.weight(1f))
                            Text(listOfNotNull(repo.language, "★ ${repo.stars}").joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
