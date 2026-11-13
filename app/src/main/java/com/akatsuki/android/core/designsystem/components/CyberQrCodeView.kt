package com.akatsuki.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akatsuki.android.core.designsystem.shape.cyberChamferSurface
import com.akatsuki.android.core.designsystem.theme.AkatsukiPalette
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renderizador nativo en Jetpack Compose de Código QR para emparejamiento TOFU.
 * Incluye la huella SHA-256 del certificado TLS 1.3, IP, Puerto y PIN efímero.
 */
@Composable
fun CyberQrCodeView(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 188.dp,
    moduleColor: Color = AkatsukiPalette.CloudWhite,
    finderAccentColor: Color = AkatsukiPalette.RinneganCyberCyan,
    backgroundColor: Color = AkatsukiPalette.ObsidianCloak
) {
    val bitMatrix = remember(payload) {
        runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 45, 45, hints)
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .size(size)
            .cyberChamferSurface(
                chamfer = 9.dp,
                backgroundColor = backgroundColor,
                borderColor = AkatsukiPalette.RinneganCyberCyan.copy(alpha = 0.65f),
                borderWidth = 1.5.dp
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitMatrix != null) {
            Canvas(modifier = Modifier.size(size - 20.dp)) {
                val matrixWidth = bitMatrix.width
                val matrixHeight = bitMatrix.height
                val cellWidth = this.size.width / matrixWidth
                val cellHeight = this.size.height / matrixHeight

                for (x in 0 until matrixWidth) {
                    for (y in 0 until matrixHeight) {
                        if (bitMatrix.get(x, y)) {
                            val isFinderPattern = (x < 8 && y < 8) ||
                                (x >= matrixWidth - 8 && y < 8) ||
                                (x < 8 && y >= matrixHeight - 8)
                            drawRect(
                                color = if (isFinderPattern) finderAccentColor else moduleColor,
                                topLeft = Offset(x * cellWidth, y * cellHeight),
                                size = Size(cellWidth + 0.4f, cellHeight + 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
