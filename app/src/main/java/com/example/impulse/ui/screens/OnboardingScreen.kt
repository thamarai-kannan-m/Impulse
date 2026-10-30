package com.example.impulse.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.impulse.ui.SettingsViewModel
import com.example.impulse.ui.components.LoopCard
import com.example.impulse.ui.loopViewModel
import com.example.impulse.ui.theme.Gradients
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class Page(val emoji: List<String>, val title: String, val body: String, val brush: Brush)

private val pages = listOf(
    Page(listOf("🍚", "🥛", "🧺", "🍅"), "Never run out", "Log what you buy once. LifeLoop estimates when it'll run out and nudges you before it does.", Gradients.Sunrise),
    Page(listOf("👕", "🧹", "💡", "🍽️"), "Chores on autopilot", "Weekly laundry, monthly bills, the dishes still waiting — scheduled, tracked, never duplicated.", Gradients.Ocean),
    Page(listOf("🏃", "💧", "📚", "🧘"), "Habits that stick", "Streaks, gentle reminders and weekly insights — without a guilt-inducing backlog.", Gradients.Berry),
)

@Composable
fun OnboardingScreen() {
    val vm = loopViewModel { SettingsViewModel(it) }
    val pager = rememberPagerState { pages.size + 2 }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var sample by rememberSaveable { mutableStateOf(true) }
    var notifAsked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAsked = true }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LifeLoop", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < pages.size + 1) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.size + 1) } }) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage()
                in 1..pages.size -> FeaturePage(pages[page - 1])
                else -> SetupPage(
                    name = name, onName = { name = it }, sample = sample, onSample = { sample = it },
                    notifAsked = notifAsked,
                    onAskNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notifAsked = true
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size + 2) { i ->
                    val sel = pager.currentPage == i
                    val w by animateDpAsState(if (sel) 26.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotc")
                    Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == pages.size + 1
            Button(
                onClick = {
                    if (last) vm.completeOnboarding(name, sample)
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                contentPadding = ButtonDefaults.ContentPadding,
                shape = CircleShape,
                modifier = Modifier.height(52.dp),
            ) {
                Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val t = rememberInfiniteTransition(label = "loop")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "angle")
    val pulse by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                val sw = 18.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF5B4FE0), Color(0xFF0EA5A0), Color(0xFFFF7A59), Color(0xFFF062A6), Color(0xFF5B4FE0))),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(sw, sw), size = androidx.compose.ui.geometry.Size(size.width - 2 * sw, size.height - 2 * sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            val orbit = listOf("🍚", "👕", "💧", "🧹", "🥛")
            orbit.forEachIndexed { i, e ->
                val a = Math.toRadians((angle * -0.6 + i * 72).toDouble())
                Text(
                    e, fontSize = 26.sp,
                    modifier = Modifier.align(Alignment.Center).offset(x = (cos(a) * 112).toFloat().dp, y = (sin(a) * 112).toFloat().dp),
                )
            }
            Box(
                Modifier.size(110.dp * pulse).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text("∞", fontSize = 58.sp, color = primary, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(36.dp))
        Text("Welcome to LifeLoop", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Everything in life, right on time.\nGroceries, chores, routines and habits — in one calm place.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeaturePage(p: Page) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(250.dp).clip(RoundedCornerShape(64.dp)).background(p.brush), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row { Text(p.emoji[0], fontSize = 64.sp); Spacer(Modifier.width(18.dp)); Text(p.emoji[1], fontSize = 64.sp) }
                Spacer(Modifier.height(10.dp))
                Row { Text(p.emoji[2], fontSize = 64.sp); Spacer(Modifier.width(18.dp)); Text(p.emoji[3], fontSize = 64.sp) }
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(p.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SetupPage(
    name: String, onName: (String) -> Unit,
    sample: Boolean, onSample: (Boolean) -> Unit,
    notifAsked: Boolean, onAskNotifications: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Let's set you up", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Text("Takes ten seconds. You can change everything later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = onName, label = { Text("What should we call you?") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(16.dp))
        LoopCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏡", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Start with an example home", style = MaterialTheme.typography.titleMedium)
                    Text("Rice, milk, weekly laundry, habits… explore everything, delete anytime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(sample, onSample)
            }
        }
        Spacer(Modifier.height(12.dp))
        LoopCard(onClick = onAskNotifications) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (notifAsked) "Notifications set" else "Allow reminders", style = MaterialTheme.typography.titleMedium)
                    Text("Low-stock, expiry and chore reminders work offline, right on your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!notifAsked) Text("Allow", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                else Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
