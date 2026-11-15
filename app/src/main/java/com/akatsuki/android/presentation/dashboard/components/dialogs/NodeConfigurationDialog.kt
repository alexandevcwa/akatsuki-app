package com.akatsuki.android.presentation.dashboard.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akatsuki.android.core.designsystem.components.CyberButton
import com.akatsuki.android.core.designsystem.components.CyberButtonSize
import com.akatsuki.android.core.designsystem.components.CyberButtonVariant
import com.akatsuki.android.core.designsystem.components.CyberPanel
import com.akatsuki.android.core.designsystem.components.ListHeaderText
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.akatsuki.android.core.designsystem.theme.AkatsukiTheme

@Composable
fun NodeConfigurationDialog(
    currentName: String,
    currentPort: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    var portInput by remember(currentPort) { mutableStateOf(currentPort.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        CyberPanel(
            title = "CONFIGURACIÓN",
            subtitle = "IDENTIDAD DEL DISPOSITIVO",
            headerIcon = Icons.Default.Terminal,
            accentColor = AkatsukiPalette.AkatsukiCrimson
        ) {
            ListHeaderText(text = "ALIAS DEL DISPOSITIVO (DEVICE NAME)")
            Spacer(modifier = Modifier.height(6.dp))
            CyberTextField(
                value = nameInput,
                onValueChange = { nameInput = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ListHeaderText(text = "PUERTO HTTPS (DEFECTO: 8443)")
            Spacer(modifier = Modifier.height(6.dp))
            CyberTextField(
                value = portInput,
                onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberButton(
                    text = "CANCELAR",
                    onClick = onDismiss,
                    variant = CyberButtonVariant.Ghost,
                    size = CyberButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
                CyberButton(
                    text = "GUARDAR",
                    onClick = {
                        val parsedPort = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8443
                        onSave(nameInput, parsedPort)
                    },
                    variant = CyberButtonVariant.Primary,
                    size = CyberButtonSize.Sm,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = AkatsukiPalette.ShadowMantle,
                borderColor = AkatsukiPalette.RinneganCyberCyan.copy(alpha = 0.55f),
                borderWidth = 1.5.dp
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AkatsukiTheme.typography.monoData.copy(color = AkatsukiPalette.CloudWhite),
            cursorBrush = SolidColor(AkatsukiPalette.AkatsukiCrimson),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
