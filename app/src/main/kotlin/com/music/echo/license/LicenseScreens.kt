package iad1tya.echo.music.license

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import iad1tya.echo.music.ui.theme.BrandAccent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

private const val GUMROAD_URL = "https://toberto.gumroad.com/l/JR-MUSIC-PRO-OFFICIAL"

// Aura dark gradient: a subtle teal tint (matching the cyan brand accent) fading to near-black.
private val ScreenGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0E2A30), Color(0xFF0A171B), Color(0xFF05070A)),
)
private val Accent = BrandAccent

// Opens the Gumroad checkout in an in-app Chrome Custom Tab (the user never leaves the app). Falls
// back to any external browser if Custom Tabs aren't available on the device.
private fun openGumroad(context: Context) {
    val uri = Uri.parse(GUMROAD_URL)
    runCatching {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

/** Returns clipboard text if it looks like a Gumroad license key (XXXXXXXX-…×4), else null. */
private fun clipboardLicenseKey(context: Context): String? {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
        ?: return null
    val text = clip.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        ?: return null
    val licenseFormat = Regex("^[A-Za-z0-9]{8}(-[A-Za-z0-9]{8}){3}$")
    return text.takeIf { licenseFormat.matches(it) }
}

@Composable
private fun LicenseScaffold(
    subtitle: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = BrandAccent, secondary = BrandAccent)) {
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
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Light)) {
                        append("AURA ")
                    }
                    withStyle(SpanStyle(color = BrandAccent, fontWeight = FontWeight.SemiBold)) {
                        append("HI-RES")
                    }
                },
                fontSize = 28.sp,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            content()
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

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun LoadingLicenseScreen() {
    MaterialTheme(colorScheme = darkColorScheme(primary = BrandAccent, secondary = BrandAccent)) {
        Column(
            modifier = Modifier.fillMaxSize().background(ScreenGradient),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
            Text("Verificando licencia…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

/** Welcome (FIRST_RUN, with demo button) or demo-expired prompt (onTryDemo = null). */
@Composable
fun ActivationPromptScreen(
    demoExpired: Boolean,
    onTryDemo: (() -> Unit)?,
    onHaveSubscription: () -> Unit,
) {
    LicenseScaffold(
        subtitle = if (demoExpired) "Tu prueba terminó" else "Bienvenido",
        hint = if (demoExpired) {
            "Tu demo de 3 días terminó. Suscríbete para seguir disfrutando de toda la música."
        } else {
            "Prueba gratis 3 días o activa tu suscripción."
        },
    ) {
        PrimaryButton("Ya me suscribí", onClick = onHaveSubscription)
        if (onTryDemo != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onTryDemo,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Probar gratis (3 días)", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
fun SubscriptionEntryScreen(
    onActivated: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusColor by remember { mutableStateOf(Color(0xFFFF6B6B)) }
    var autoTried by remember { mutableStateOf<String?>(null) }

    fun activate(k: String) {
        if (k.isBlank() || loading) return
        loading = true
        status = null
        scope.launch {
            val result = LicenseManager.activateSubscription(context, k)
            loading = false
            when (result) {
                LicenseStatus.ACTIVE -> onActivated()
                LicenseStatus.ENDED -> {
                    statusColor = Color(0xFFFFB74D)
                    status = "Esta suscripción está cancelada o vencida. Renueva el pago en Gumroad."
                }
                LicenseStatus.DEVICE_MISMATCH -> {
                    statusColor = Color(0xFFFFB74D)
                    status = "Esta clave ya está en uso en otro equipo. Usa el equipo original o espera unos días."
                }
                LicenseStatus.INVALID_KEY -> {
                    statusColor = Color(0xFFFF6B6B)
                    status = "Clave inválida. Revisa que la copiaste completa."
                }
                LicenseStatus.NETWORK_ERROR -> {
                    statusColor = Color(0xFFFF6B6B)
                    status = "Sin conexión. Conéctate a internet e inténtalo de nuevo."
                }
            }
        }
    }

    // After paying in the in-app Gumroad checkout, the user copies the license key shown on the receipt.
    // On returning here (ON_RESUME) we auto-detect it from the clipboard and activate it — no manual paste.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val detected = clipboardLicenseKey(context)
                if (detected != null && detected != autoTried && detected != key) {
                    autoTried = detected
                    key = detected
                    statusColor = Accent
                    status = "Licencia detectada, activando…"
                    activate(detected)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LicenseScaffold(
        subtitle = "Activar suscripción",
        hint = "Toca \"Suscribirme\", paga sin salir de la app y la licencia se activa sola. O pega tu clave.",
    ) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; status = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Clave de licencia") },
            placeholder = { Text("XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Spacer(Modifier.height(12.dp))
        status?.let { StatusCard(it, statusColor) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (loading) "Verificando…" else "Activar",
            enabled = !loading && key.isNotBlank(),
        ) {
            activate(key)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { openGumroad(context) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Suscribirme por $3.74/mes", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Al suscribirte recibes tu licencia para seguir disfrutando de la experiencia.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack) { Text("Volver", color = Accent) }
    }
}

@Composable
fun RenewScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LicenseScaffold(
        subtitle = "Suscripción vencida",
        hint = "Tu suscripción mensual no está activa. Renueva el pago para seguir escuchando.",
    ) {
        status?.let {
            StatusCard(it, Color(0xFFFFB74D))
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton("Renovar en Gumroad") { openGumroad(context) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                loading = true
                status = null
                scope.launch {
                    val r = LicenseManager.reverify(context)
                    loading = false
                    if (r == LicenseStatus.ACTIVE) onActivated()
                    else status = "Todavía no detectamos el pago. Si ya pagaste, espera un momento y reintenta."
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (loading) "Comprobando…" else "Ya pagué, reintentar", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun NeedsConnectionScreen(onRetry: () -> Unit) {
    LicenseScaffold(
        subtitle = "Conéctate a internet",
        hint = "Necesitamos verificar tu suscripción. Conéctate a internet para continuar.",
    ) {
        PrimaryButton("Reintentar", onClick = onRetry)
    }
}

@Composable
fun DeviceBlockedScreen(onRetry: () -> Unit) {
    val context = LocalContext.current
    LicenseScaffold(
        subtitle = "Suscripción en otro equipo",
        hint = "Esta suscripción ya está activa en otro equipo. Cada suscripción funciona en un solo " +
            "equipo a la vez. Suscríbete para este equipo, o espera unos días si dejaste de usar el otro.",
    ) {
        PrimaryButton("Reintentar", onClick = onRetry)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { openGumroad(context) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Suscribirme ($10/mes)", fontWeight = FontWeight.SemiBold) }
    }
}
