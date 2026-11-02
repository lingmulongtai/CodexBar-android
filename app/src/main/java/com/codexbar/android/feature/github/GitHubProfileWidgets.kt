package com.codexbar.android.feature.github

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codexbar.android.R
import com.codexbar.android.core.network.github.*
import com.codexbar.android.ui.components.CodexBarCard
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ice = Color(0xFF6FCFFF)
private val Gold = Color(0xFFFFD36D)

@Composable
private fun ProfileWidget(title: String, content: @Composable ColumnScope.() -> Unit) {
    CodexBarCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .06f))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Ice, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun GitHubStatsWidget(calendar: GitHubContributionCalendar?) {
    ProfileWidget(stringResource(R.string.github_widgets_stats)) {
        if (calendar == null) { Text(stringResource(R.string.github_profile_unavailable)); return@ProfileWidget }
        val stats = remember(calendar) { calendar.statistics(LocalDate.now()) }
        val days = stringResource(R.string.github_stats_days)
        val values = listOf(
            Triple(stringResource(R.string.github_stats_contributions), "%,d".format(stats.total), stringResource(R.string.github_stats_last_year)),
            Triple(stringResource(R.string.github_stats_best_day), "%,d".format(stats.bestDay?.contributionCount ?: 0), stats.bestDay?.date ?: "—"),
            Triple(stringResource(R.string.github_stats_longest), "${stats.longestStreak}", days),
            Triple(stringResource(R.string.github_stats_current), "${stats.currentStreak}", days),
            Triple(stringResource(R.string.github_stats_active), "${stats.activeDays} / ${stats.days}", days),
            Triple(stringResource(R.string.github_stats_weekday), stats.busiestWeekday?.let {
                DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) } ?: "—", stringResource(R.string.github_stats_daily_average)))
        values.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (label, value, detail) -> StatTile(label, value, detail, Modifier.weight(1f)) }
        } }
        Text(stringResource(R.string.github_stats_monthly), style = MaterialTheme.typography.labelMedium)
        val months = stats.monthly
        val max = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            months.forEach { (_, value) -> Box(Modifier.weight(1f).height((value.toFloat() / max * 84).coerceAtLeast(2f).dp)
                .background(Brush.verticalGradient(listOf(Ice, Color(0xFF387BFF))), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEach { (month, _) -> Text("${month.monthValue}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun AchievementTile(name: String, status: String, detail: String, progress: Float?, color: Color,
    imageUrl: String? = null, symbol: String = "✦", modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, imageUrl) {
        value = safeGitHubBadgeImageUrl(imageUrl)?.let { withContext(Dispatchers.IO) { loadGitHubImage(it) } }
    }
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .045f))
        .border(1.dp, color.copy(alpha = .3f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).background(Brush.radialGradient(listOf(color.copy(alpha = .5f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center) {
                if (image != null) Image(image!!, null, Modifier.size(38.dp))
                else Text(symbol, color = color, style = MaterialTheme.typography.headlineSmall)
            }
            Text(status, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))) {
            if (progress != null && progress > 0) Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun GitHubAchievementsWidget(extras: GitHubProfileExtras?, calendar: GitHubContributionCalendar?) {
    ProfileWidget(stringResource(R.string.github_widgets_achievements)) {
        Text(stringResource(R.string.github_achievements_public), style = MaterialTheme.typography.labelLarge)
        val names = listOf("pull-shark" to "Pull Shark", "quickdraw" to "Quickdraw", "yolo" to "YOLO",
            "pair-extraordinaire" to "Pair Extraordinaire", "galaxy-brain" to "Galaxy Brain",
            "public-sponsor" to "Public Sponsor", "starstruck" to "Starstruck")
        val tiers = listOf("UNLOCKED", "BRONZE ×2", "SILVER ×3", "GOLD ×4")
        names.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (slug, name) ->
                val badge = extras?.badges?.find { it.slug == slug }
                val status = badge?.let { tiers[(it.tier - 1).coerceIn(0, 3)] }
                    ?: stringResource(if (extras?.badgesAvailable == true) R.string.github_achievements_not_shown else R.string.github_achievements_unavailable)
                val next = badge?.let { listOf(2, 16, 128, 1024).getOrNull(it.tier) }
                val floor = badge?.let { listOf(2, 16, 128, 1024).getOrNull(it.tier - 1) }
                val reportedCount = extras?.mergedPullRequests
                val count = reportedCount?.takeIf { floor != null && it >= floor } ?: floor
                val detail = if (slug == "pull-shark" && badge != null && count != null && next != null)
                    "$count${if (reportedCount == null || reportedCount < count) "+" else ""} / $next PRs" else stringResource(R.string.github_achievements_public)
                AchievementTile(name, status, detail, if (slug == "pull-shark" && count != null && next != null) count.toFloat()/next
                    else badge?.let { 1f }, if (badge == null) Color(0xFF9CA8BC) else if (badge.tier == 4) Gold else Ice,
                    badge?.imageUrl, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } }
        Text(stringResource(R.string.github_achievements_local), style = MaterialTheme.typography.labelLarge)
        if (calendar == null) Text(stringResource(R.string.github_profile_unavailable)) else {
            val stats = remember(calendar) { calendar.statistics(LocalDate.now()) }
            val locals = listOf(Triple("Big Bang", stats.bestDay?.contributionCount ?: 0, listOf(50,200,500,1000)),
                Triple("Streak Runner", stats.longestStreak, listOf(7,14,30,100)),
                Triple("Year in Code", stats.total, listOf(500,1000,2500,5000)),
                Triple("Daily Driver", stats.activeDays, listOf(50,100,200,300)))
            locals.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (name, count, levels) ->
                    val tier = levels.count { count >= it }
                    val target = levels.firstOrNull { count < it } ?: levels.last()
                    AchievementTile(name, if (tier == 0) stringResource(R.string.github_achievements_progress) else tiers[tier-1],
                        "%,d / %,d".format(count, target), count.toFloat()/target, if (tier >= 3) Gold else Ice,
                        symbol = if (name == "Streak Runner") "♨" else "✦", modifier = Modifier.weight(1f).fillMaxHeight())
                }
            } }
        }
    }
}

@Composable
internal fun GitHubStackWidget(extras: GitHubProfileExtras?) {
    ProfileWidget(stringResource(R.string.github_widgets_stack)) {
        if (extras?.stack.isNullOrEmpty()) Text(stringResource(R.string.github_stack_missing))
        else {
            StackIcons(extras.stack)
            if (extras.learning.isNotEmpty()) {
                Text(stringResource(R.string.github_stack_learning), style = MaterialTheme.typography.labelLarge)
                StackIcons(extras.learning)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackIcons(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { id ->
            val tech = techIcon(id)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(46.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tech.second.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                    if (tech.first != null) Icon(painterResource(tech.first!!), id, Modifier.size(26.dp), tint = tech.second)
                    else Text(id.take(3).uppercase(), style = MaterialTheme.typography.labelMedium)
                }
                Text(tech.third, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun techIcon(id: String): Triple<Int?, Color, String> = when (id) {
    "python" -> Triple(R.drawable.ic_tech_python, Color(0xFFFFD65B), "Python")
    "ts" -> Triple(R.drawable.ic_tech_ts, Color(0xFF69B5FF), "TS")
    "js" -> Triple(R.drawable.ic_tech_js, Gold, "JS")
    "kotlin" -> Triple(R.drawable.ic_tech_kotlin, Color(0xFFCF9CFF), "Kotlin")
    "rust" -> Triple(R.drawable.ic_tech_rust, Color(0xFFFFAD8C), "Rust")
    "cs" -> Triple(R.drawable.ic_tech_cs, Color(0xFFCE8FFF), "C#")
    "react" -> Triple(R.drawable.ic_tech_react, Ice, "React")
    "nextjs" -> Triple(R.drawable.ic_tech_nextjs, Color.White, "Next.js")
    "nodejs" -> Triple(R.drawable.ic_tech_nodejs, Color(0xFF98D36B), "Node.js")
    "html" -> Triple(R.drawable.ic_tech_html, Color(0xFFFF9567), "HTML")
    "css" -> Triple(R.drawable.ic_tech_css, Color(0xFF7CB4FF), "CSS")
    "tailwind" -> Triple(R.drawable.ic_tech_tailwind, Ice, "Tailwind")
    "arduino" -> Triple(R.drawable.ic_tech_arduino, Color(0xFF6CDAD5), "Arduino")
    "firebase" -> Triple(R.drawable.ic_tech_firebase, Gold, "Firebase")
    "vercel" -> Triple(R.drawable.ic_tech_vercel, Color.White, "Vercel")
    "git" -> Triple(R.drawable.ic_tech_git, Color(0xFFFF987C), "Git")
    "github" -> Triple(R.drawable.ic_tech_github, Color.White, "GitHub")
    "cpp" -> Triple(R.drawable.ic_tech_cpp, Ice, "C++")
    "java" -> Triple(R.drawable.ic_tech_java, Color(0xFFFFBC80), "Java")
    "swift" -> Triple(R.drawable.ic_tech_swift, Color(0xFFFF967D), "Swift")
    "fastapi" -> Triple(R.drawable.ic_tech_fastapi, Color(0xFF6AE5CA), "FastAPI")
    "docker" -> Triple(R.drawable.ic_tech_docker, Ice, "Docker")
    "raspberrypi" -> Triple(R.drawable.ic_tech_raspberrypi, Color(0xFFFF91BC), "RPi")
    "linux" -> Triple(R.drawable.ic_tech_linux, Gold, "Linux")
    "vscode" -> Triple(null, Ice, "VS Code")
    "matlab" -> Triple(null, Gold, "MATLAB")
    else -> Triple(null, Ice, id)
}

@Composable
internal fun GitHubPortraitWidget(profile: GitHubProfile?) {
    val safeUrl = safeGitHubAvatarUrl(profile?.avatarUrl)
    val bitmap by produceState<ImageBitmap?>(null, safeUrl) {
        value = safeUrl?.let { withContext(Dispatchers.IO) { loadGitHubImage(it) } }
    }
    val pixels = remember(bitmap) { bitmap?.toPixelMap() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE } }
    val description = stringResource(R.string.github_widgets_portrait)
    ProfileWidget(description) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f).semantics { contentDescription = description }) {
            val columns = 64; val rows = 42
            val stepX = size.width / columns; val stepY = size.height / rows
            paint.textSize = stepX * 1.1f
            drawIntoCanvas { canvas ->
                for (row in 0 until rows) for (col in 0 until columns) {
                    val color = pixels?.get((col * pixels.width / columns).coerceAtMost(pixels.width-1),
                        (row * pixels.height / rows).coerceAtMost(pixels.height-1))
                    val light = color?.let { .2126f*it.red + .7152f*it.green + .0722f*it.blue } ?: .15f
                    val glyph = " .:-=+*#%@"[(light * 9).toInt().coerceIn(0,9)]
                    paint.color = lerp(Color(0xFF5B8CFF), Ice, row.toFloat()/rows).copy(alpha = .25f + light*.75f).toArgb()
                    canvas.nativeCanvas.drawText(glyph.toString(), col * stepX, (row+1)*stepY, paint)
                }
            }
        }
        Text("@${profile?.login.orEmpty()}", style = MaterialTheme.typography.labelLarge, color = Ice)
    }
}
