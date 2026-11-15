package com.akatsuki.android.presentation.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.components.BodyText
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette

@Composable
fun StoragePermissionAlertPanel(
    onGrantPermissionClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    CyberPanel(
        title = "ACCESO AL ALMACENAMIENTO REQUERIDO",
        subtitle = "PERMISO MANAGE_EXTERNAL_STORAGE (/STORAGE/EMULATED/0)",
        headerIcon = Icons.Default.WarningAmber,
        accentColor = AkatsukiPalette.PaperBombAmber,
        borderColor = AkatsukiPalette.PaperBombAmber.copy(alpha = 0.65f)
    ) {
        BodyText(
            text = "Para que el cliente Akatsuki Desktop pueda explorar, descargar y subir archivos en /storage/emulated/0, Android 11+ requiere autorizar el acceso completo a archivos para este nodo."
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CyberButton(
                text = "AUTORIZAR ACCESO A ARCHIVOS",
                onClick = onGrantPermissionClick,
                variant = CyberButtonVariant.Primary,
                size = CyberButtonSize.Md,
                modifier = Modifier.weight(1f)
            )
            CyberButton(
                text = "VERIFICAR",
                onClick = onRefreshClick,
                variant = CyberButtonVariant.Secondary,
                size = CyberButtonSize.Md
            )
        }
    }
}
