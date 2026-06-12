package iad1tya.echo.music.license

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.R

private val BrandGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFDE60B3),
        Color(0xFF9B6CFF),
        Color(0xFF3DA9ED)
    )
)

private val ScreenGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2A1052),
        Color(0xFF160830),
        Color(0xFF0B0418)
    )
)

private sealed class ActivationUi {
    data object Idle : ActivationUi()
    data object Invalid : ActivationUi()
    data object DemoAlreadyUsed : ActivationUi()
    data class DemoOk(val days: Int) : ActivationUi()
    data object PerpetualOk : ActivationUi()
}

/**
 * Full-screen gate shown until the app is activated with a demo or perpetual key.
 * Self-themed (dark) because it renders before the app theme is applied.
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var ui by remember { mutableStateOf<ActivationUi>(ActivationUi.Idle) }
    val demoExpired = remember { LicenseManager.isDemoExpired(context) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenGradient)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.jr_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(brush = BrandGradient, fontWeight = FontWeight.ExtraBold)
                    ) {
                        append("JR MUSIC PLAYER PRO")
                    }
                },
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Activación requerida",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (demoExpired) {
                    "Tu demo de 3 días terminó. Introduce una clave de activación permanente."
                } else {
                    "Introduce tu clave de activación para continuar."
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it.uppercase()
                    if (ui != ActivationUi.Idle) ui = ActivationUi.Idle
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Clave de activación") },
                placeholder = { Text("XXXXXXXXXXXXXXXXXXXX") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF9B6CFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                ),
            )

            Spacer(Modifier.height(12.dp))

            when (val state = ui) {
                ActivationUi.Invalid -> StatusCard(
                    text = "Clave inválida. Revisa e inténtalo de nuevo.",
                    color = Color(0xFFFF6B6B),
                )

                ActivationUi.DemoAlreadyUsed -> StatusCard(
                    text = "Esta clave demo ya fue utilizada en este dispositivo y quedó inservible. Usa la clave de activación permanente.",
                    color = Color(0xFFFFB74D),
                )

                is ActivationUi.DemoOk -> StatusCard(
                    text = "✓ Demo activada: tienes ${state.days} días completos. No se volverá a pedir la clave hasta que terminen.",
                    color = Color(0xFF69DB7C),
                )

                ActivationUi.PerpetualOk -> StatusCard(
                    text = "✓ LICENCIA PERPETUA — el programa quedó activado para siempre.",
                    color = Color(0xFF69DB7C),
                )

                ActivationUi.Idle -> {}
            }

            Spacer(Modifier.height(16.dp))

            val activated = ui is ActivationUi.DemoOk || ui == ActivationUi.PerpetualOk
            Button(
                onClick = {
                    if (activated) {
                        onActivated()
                    } else {
                        ui = when (val result = LicenseManager.activate(context, key)) {
                            is LicenseLogic.Activation.Perpetual -> ActivationUi.PerpetualOk
                            is LicenseLogic.Activation.Demo -> ActivationUi.DemoOk(
                                LicenseManager.demoDaysLeft(context).coerceAtLeast(1)
                            )
                            is LicenseLogic.Activation.DemoAlreadyUsed -> ActivationUi.DemoAlreadyUsed
                            is LicenseLogic.Activation.Invalid -> ActivationUi.Invalid
                        }
                    }
                },
                enabled = activated || key.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9B6CFF),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.lock),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (activated) "Entrar" else "Activar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(text: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
