package com.akatsuki.android.presentation.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akatsuki.android.R
import com.akatsuki.android.core.designsystem.components.CyberStatusBadge
import com.akatsuki.android.core.designsystem.components.DisplayXL
import com.akatsuki.android.core.designsystem.components.Heading3
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import kotlinx.coroutines.delay

/**
 * Splash Screen Táctico "Akatsuki Cyberpunk" mostrado al abrir la aplicación.
 * Presenta el emblema oficial de Akatsuki con anillos HUD pulsantes, telemetría de arranque
 * y barra de progreso biselada antes de dar paso al Centro de Mando.
 */
@Composable
fun AkatsukiSplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnim by remember { mutableStateOf(false) }
    var bootProgress by remember { mutableFloatStateOf(0.08f) }
    var bootStatusMessage by remember { mutableStateOf("INITIALIZING X.509 ECDSA P-256 KEYSTORE...") }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.78f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "splashLogoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splashContentAlpha"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = bootProgress,
        animationSpec = tween(durationMillis = 280),
        label = "splashBootProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(350L)
        bootProgress = 0.42f
        bootStatusMessage = "VERIFYING TLS 1.3 SHA-256 FINGERPRINT..."
        delay(450L)
        bootProgress = 0.78f
        bootStatusMessage = "PREPARING KTOR HTTPS & mDNS (_akatsuki._tcp.)..."
        delay(450L)
        bootProgress = 1.0f
        bootStatusMessage = "SHINOBI NODE READY // ONLINE"
        delay(320L)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AkatsukiPalette.ObsidianCloak)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CyberStatusBadge(
                label = "SHINOBI SECURE TRANSFER PROTOCOL",
                accentColor = AkatsukiPalette.RinneganCyberCyan
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Contenedor HUD central con anillos Cyberpunk y el logo oficial Akatsuki
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusOuter = (size.minDimension / 2f) * ringPulse
                    val radiusInner = (size.minDimension / 2f) * 0.78f

                    drawCircle(
                        color = AkatsukiPalette.AkatsukiCrimson.copy(alpha = 0.22f),
                        radius = radiusOuter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = AkatsukiPalette.RinneganCyberCyan.copy(alpha = 0.35f),
                        radius = radiusInner,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 168.dp, height = 122.dp)
                        .scale(logoScale)
                        .cyberChamferSurface(
                            chamfer = 14.dp,
                            backgroundColor = AkatsukiPalette.TacticalSurface,
                            borderColor = AkatsukiPalette.AkatsukiCrimson,
                            borderWidth = 1.5.dp,
                            topAccentColor = AkatsukiPalette.SharinganNeon,
                            topAccentHeight = 2.5.dp
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.akatsuki_logo),
                        contentDescription = "Akatsuki Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            DisplayXL(
                text = "AKATSUKI",
                color = AkatsukiPalette.CloudWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Heading3(
                text = "SHINOBI SECURE FILE TRANSFER // ANDROID NODE",
                color = AkatsukiPalette.CrimsonHighlight
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Barra de telemetría de arranque
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .cyberChamferSurface(
                        chamfer = 9.dp,
                        backgroundColor = AkatsukiPalette.ShadowMantle,
                        borderColor = AkatsukiPalette.PanelSubtleBorder,
                        borderWidth = 1.5.dp
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ListHeaderText(
                        text = "SYSTEM BOOT SEQUENCE",
                        color = AkatsukiPalette.ShinobiSilver
                    )
                    MonoTelemetry(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = AkatsukiPalette.NeonTelemetry
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(AkatsukiPalette.UchihaSlate)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(6.dp)
                            .background(AkatsukiPalette.AkatsukiCrimson)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(2.dp))
                    MonoTelemetry(
                        text = bootStatusMessage,
                        color = AkatsukiPalette.RinneganCyberCyan
                    )
                }
            }
        }
    }
}
