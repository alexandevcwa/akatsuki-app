package com.akatsuki.android.presentation.dashboard.components.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.ListItemText
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.domain.model.ActiveSession
import com.akatsuki.android.presentation.dashboard.components.EmptyStateBox

@Composable
fun ActiveSessionsTabContent(
    sessions: List<ActiveSession>,
    onRevokeSession: (String) -> Unit,
    onRevokeAll: () -> Unit
) {
    if (sessions.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.Devices,
            title = "NINGÚN CLIENTE PC EMPAREJADO",
            subtitle = "Cuando Akatsuki Desktop valide el PIN de 6 dígitos en POST /api/v1/auth/pair, la sesión Bearer aparecerá aquí."
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListHeaderText(
                text = "SESIONES BEARER ACTIVAS (${sessions.size})",
                color = AkatsukiPalette.ZetsuBioGreen
            )
            CyberButton(
                text = "REVOCAR TODAS",
                onClick = onRevokeAll,
                variant = CyberButtonVariant.Danger,
                size = CyberButtonSize.Sm
            )
        }

        sessions.forEach { session ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberChamferSurface(
                        chamfer = 9.dp,
                        backgroundColor = AkatsukiPalette.ShadowMantle,
                        borderColor = AkatsukiPalette.ZetsuBioGreen.copy(alpha = 0.55f),
                        borderWidth = 1.5.dp
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AkatsukiPalette.ZetsuBioGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ListItemText(
                            text = session.clientName,
                            color = AkatsukiPalette.CloudWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    MonoTelemetry(
                        text = "Conectado: ${session.connectedAt}",
                        color = AkatsukiPalette.NeonTelemetry
                    )
                    MonoHash(
                        text = "Token: ${session.bearerToken.take(28)}...",
                        color = AkatsukiPalette.HiddenRainAsh,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                CyberButton(
                    text = "REVOCAR",
                    onClick = { onRevokeSession(session.bearerToken) },
                    variant = CyberButtonVariant.Danger,
                    size = CyberButtonSize.Sm
                )
            }
        }
    }
}
