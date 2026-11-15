package com.akatsuki.android.presentation.dashboard.components.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.BodyText
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.components.ListItemText
import com.akatsuki.android.core.designsystem.components.MonoHash
import com.akatsuki.android.core.designsystem.components.MonoTelemetry
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.domain.model.FileEntry
import com.akatsuki.android.presentation.dashboard.ByteFormatUtils
import com.akatsuki.android.presentation.dashboard.components.EmptyStateBox

@Composable
fun LocalStorageBrowserTabContent(
    currentPath: String,
    entries: List<FileEntry>,
    error: String?,
    onNavigateDir: (String) -> Unit,
    onNavigateParent: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Barra de ruta actual y botón para subir de nivel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .cyberChamferSurface(
                    chamfer = 9.dp,
                    backgroundColor = AkatsukiPalette.ShadowMantle,
                    borderColor = AkatsukiPalette.PanelSubtleBorder,
                    borderWidth = 1.5.dp
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ListHeaderText(text = "DIRECTORIO EXPUESTO VÍA /API/V1/FILES/LIST")
                MonoTelemetry(
                    text = currentPath,
                    color = AkatsukiPalette.NeonTelemetry
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CyberButton(
                text = "SUBIR",
                onClick = onNavigateParent,
                variant = CyberButtonVariant.Secondary,
                size = CyberButtonSize.Sm,
                leadingIcon = Icons.Default.ArrowUpward
            )
        }

        if (error != null) {
            BodyText(
                text = error,
                color = AkatsukiPalette.PaperBombAmber
            )
        } else if (entries.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.Folder,
                title = "DIRECTORIO VACÍO",
                subtitle = "No se encontraron archivos ni subcarpetas en esta ruta."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                entries.take(50).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .cyberChamferSurface(
                                chamfer = 9.dp,
                                backgroundColor = AkatsukiPalette.UchihaSlate.copy(alpha = 0.55f),
                                borderColor = AkatsukiPalette.PanelSubtleBorder,
                                borderWidth = 1.dp
                            )
                            .clickable(enabled = entry.isDir) {
                                if (entry.isDir) {
                                    onNavigateDir(entry.path)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (entry.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDir) AkatsukiPalette.AkatsukiCrimson else AkatsukiPalette.NeonTelemetry,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                ListItemText(
                                    text = entry.name,
                                    color = if (entry.isDir) AkatsukiPalette.CloudWhite else AkatsukiPalette.ShinobiSilver
                                )
                                MonoHash(
                                    text = entry.modifiedAt,
                                    color = AkatsukiPalette.HiddenRainAsh,
                                    maxLines = 1
                                )
                            }
                        }

                        MonoTelemetry(
                            text = if (entry.isDir) "DIR" else ByteFormatUtils.formatBytes(entry.sizeBytes),
                            color = if (entry.isDir) AkatsukiPalette.CrimsonHighlight else AkatsukiPalette.NeonTelemetry
                        )
                    }
                }
            }
        }
    }
}
