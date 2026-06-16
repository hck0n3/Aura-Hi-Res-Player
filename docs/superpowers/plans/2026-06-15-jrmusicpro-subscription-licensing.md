# JR MUSIC PRO — Sistema de licencias por suscripción (Gumroad) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el gate por serial actual por demo de 3 días + suscripción mensual de Gumroad, con verificación online en cada apertura y gracia offline de 3 días.

**Architecture:** Núcleo puro (`LicenseLogic`, `GumroadVerify`) sin Android, unit-testeable con JUnit. `GumroadClient` (ktor) hace el POST a `api.gumroad.com/v2/licenses/verify`. `LicenseManager` orquesta (SharedPreferences + online). Compose `LicenseGate` decide qué pantalla mostrar según el estado.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), ktor client (engine OkHttp, ya presente), kotlinx.serialization (ya presente), JUnit.

**Spec:** `docs/superpowers/specs/2026-06-15-jrmusicpro-subscription-licensing-design.md`

## Datos clave
- `product_id` Gumroad = `wcPehkIWHRbPKR4_hZLdJQ==`
- Permalink suscripción = `https://toberto.gumroad.com/l/JR-MUSIC-PRO-OFFICIAL`
- Paquete: `iad1tya.echo.music.license` (directorio físico `app/src/main/kotlin/com/music/echo/license/`).
- Permiso INTERNET ya declarado (la app hace streaming) — **no** tocar el manifest.
- Dependencias ktor/serialization ya en `app/build.gradle.kts` — **no** añadir deps.

## Comandos (Windows, Git Bash)
Exportar JAVA_HOME antes de cada gradle (el estado del shell no persiste):
```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
```
- Tests de un paquete: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.*"`
- Compilar main (chequeo rápido): `./gradlew :app:compileUniversalFossDebugKotlin`
- APK debug: `./gradlew :app:assembleUniversalFossDebug`

## Estructura de archivos
| Archivo | Responsabilidad |
|---|---|
| `.../license/GumroadVerify.kt` (crear) | Interpretar el JSON de Gumroad → `Result` (puro) |
| `.../license/GumroadClient.kt` (crear) | POST ktor al endpoint verify |
| `.../license/LicenseLogic.kt` (reescribir) | Máquina de estados pura (demo, gracia, resolve) |
| `.../license/LicenseManager.kt` (reescribir) | SharedPreferences + orquestación online + isOnline |
| `.../license/LicenseScreens.kt` (crear) | Pantallas Compose (welcome, entry, renew, needs-connection, loading) |
| `.../license/ActivationScreen.kt` (borrar) | Reemplazado por LicenseScreens |
| `.../license/LicenseGate.kt` (crear) | Contenedor que elige pantalla según AppState |
| `MainActivity.kt` (modificar) | Usar `LicenseGate` en `setContent` |
| `app/src/test/.../license/GumroadVerifyTest.kt` (crear) | Tests del intérprete |
| `app/src/test/.../license/LicenseLogicTest.kt` (reescribir) | Tests de la máquina de estados |

Rutas físicas:
- main: `app/src/main/kotlin/com/music/echo/license/`
- test: `app/src/test/kotlin/iad1tya/echo/music/license/`

---

### Task 1: `GumroadVerify` — intérprete puro del JSON (TDD)

**Files:**
- Create: `app/src/test/kotlin/iad1tya/echo/music/license/GumroadVerifyTest.kt`
- Create: `app/src/main/kotlin/com/music/echo/license/GumroadVerify.kt`

- [ ] **Step 1: Escribir el test que falla**

Crear `app/src/test/kotlin/iad1tya/echo/music/license/GumroadVerifyTest.kt`:

```kotlin
package iad1tya.echo.music.license

import org.junit.Assert.assertEquals
import org.junit.Test

class GumroadVerifyTest {

    @Test fun activeWhenAllSubscriptionFieldsNull() {
        val body = """
            {"success":true,"purchase":{
              "subscription_cancelled_at":null,
              "subscription_failed_at":null,
              "subscription_ended_at":null}}
        """.trimIndent()
        assertEquals(GumroadVerify.Result.ACTIVE, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenCancelledHasDate() {
        val body = """{"success":true,"purchase":{"subscription_cancelled_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenFailedHasDate() {
        val body = """{"success":true,"purchase":{"subscription_failed_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun endedWhenEndedHasDate() {
        val body = """{"success":true,"purchase":{"subscription_ended_at":"2026-05-01T00:00:00Z"}}"""
        assertEquals(GumroadVerify.Result.ENDED, GumroadVerify.interpret(body))
    }

    @Test fun activeWhenSubscriptionFieldsAbsent() {
        val body = """{"success":true,"purchase":{"email":"x@y.com"}}"""
        assertEquals(GumroadVerify.Result.ACTIVE, GumroadVerify.interpret(body))
    }

    @Test fun invalidKeyWhenSuccessFalse() {
        val body = """{"success":false,"message":"That license does not exist for the provided product."}"""
        assertEquals(GumroadVerify.Result.INVALID_KEY, GumroadVerify.interpret(body))
    }

    @Test fun networkErrorWhenBodyNotJson() {
        assertEquals(GumroadVerify.Result.NETWORK_ERROR, GumroadVerify.interpret("<html>502 Bad Gateway</html>"))
    }
}
```

- [ ] **Step 2: Ejecutar el test y verificar que falla**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.GumroadVerifyTest"`
Expected: FAIL de compilación — `Unresolved reference: GumroadVerify`.

- [ ] **Step 3: Implementar `GumroadVerify`**

Crear `app/src/main/kotlin/com/music/echo/license/GumroadVerify.kt`:

```kotlin
package iad1tya.echo.music.license

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure interpreter of the Gumroad /v2/licenses/verify JSON response. No I/O, so it is unit-testable
 * with plain JUnit. A membership is ACTIVE only when the three subscription timestamps are null.
 */
object GumroadVerify {

    enum class Result { ACTIVE, ENDED, INVALID_KEY, NETWORK_ERROR }

    private val json = Json { ignoreUnknownKeys = true }

    fun interpret(body: String): Result =
        try {
            val root = json.parseToJsonElement(body).jsonObject
            val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) {
                Result.INVALID_KEY
            } else {
                val purchase = root["purchase"]?.jsonObject
                if (purchase == null) {
                    Result.ACTIVE
                } else {
                    val cancelled = purchase["subscription_cancelled_at"]?.jsonPrimitive?.contentOrNull
                    val failed = purchase["subscription_failed_at"]?.jsonPrimitive?.contentOrNull
                    val ended = purchase["subscription_ended_at"]?.jsonPrimitive?.contentOrNull
                    if (cancelled == null && failed == null && ended == null) Result.ACTIVE
                    else Result.ENDED
                }
            }
        } catch (e: Exception) {
            Result.NETWORK_ERROR
        }
}
```

Nota: `contentOrNull` devuelve `null` cuando el primitivo es `JsonNull`, así que campos `null` o ausentes cuentan como "sin fecha".

- [ ] **Step 4: Ejecutar el test y verificar que pasa**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.GumroadVerifyTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/license/GumroadVerify.kt app/src/test/kotlin/iad1tya/echo/music/license/GumroadVerifyTest.kt
git commit -m "feat(license): pure Gumroad verify-response interpreter (TDD)"
```

---

### Task 2: `GumroadClient` — POST ktor al endpoint verify

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/license/GumroadClient.kt`

- [ ] **Step 1: Implementar `GumroadClient`**

Crear `app/src/main/kotlin/com/music/echo/license/GumroadClient.kt`:

```kotlin
package iad1tya.echo.music.license

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters

/**
 * Thin ktor wrapper around Gumroad's license verification endpoint. Any network/parse failure maps to
 * [GumroadVerify.Result.NETWORK_ERROR] so the caller can fall back to the offline grace window.
 */
class GumroadClient(
    private val http: HttpClient = HttpClient(OkHttp),
) {
    suspend fun verify(productId: String, licenseKey: String): GumroadVerify.Result =
        try {
            val body = http.submitForm(
                url = VERIFY_URL,
                formParameters = Parameters.build {
                    append("product_id", productId)
                    append("license_key", licenseKey)
                    append("increment_uses_count", "false")
                },
            ).bodyAsText()
            GumroadVerify.interpret(body)
        } catch (e: Exception) {
            GumroadVerify.Result.NETWORK_ERROR
        }

    companion object {
        private const val VERIFY_URL = "https://api.gumroad.com/v2/licenses/verify"
    }
}
```

- [ ] **Step 2: Compilar main para verificar**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:compileUniversalFossDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/license/GumroadClient.kt
git commit -m "feat(license): ktor client for Gumroad license verify endpoint"
```

---

### Task 3: Swap del núcleo de licencias al modelo de suscripción

Este task reemplaza, de forma atómica, la lógica vieja (serial/perpetua) por la nueva (demo + suscripción) y conecta las pantallas. Se verifica con la suite de tests al final (compila main + corre tests).

**Files:**
- Modify (reescribir completo): `app/src/main/kotlin/com/music/echo/license/LicenseLogic.kt`
- Modify (reescribir completo): `app/src/main/kotlin/com/music/echo/license/LicenseManager.kt`
- Create: `app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt`
- Delete: `app/src/main/kotlin/com/music/echo/license/ActivationScreen.kt`
- Create: `app/src/main/kotlin/com/music/echo/license/LicenseGate.kt`
- Modify: `app/src/main/kotlin/com/music/echo/MainActivity.kt` (imports + bloque `setContent`, ~líneas 198-200 y 369-383)
- Modify (reescribir completo): `app/src/test/kotlin/iad1tya/echo/music/license/LicenseLogicTest.kt`

- [ ] **Step 1: Reescribir `LicenseLogic.kt`**

Reemplazar TODO el contenido de `app/src/main/kotlin/com/music/echo/license/LicenseLogic.kt` por:

```kotlin
package iad1tya.echo.music.license

/**
 * Pure licensing rules for JR MUSIC PRO. No Android dependencies so the whole state machine is
 * unit-testable. The app boots in a keyless 3-day demo; a Gumroad subscription is re-verified online
 * on each open, with a 3-day offline grace window.
 */
object LicenseLogic {

    /** Demo lasts 3 days from the moment the user taps "Probar gratis". */
    const val DEMO_DURATION_MS = 3L * 24 * 60 * 60 * 1000

    /** How long the app keeps working without a fresh successful online verification. */
    const val OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1000

    /** Small tolerance so timezone hops don't lock a legit user; day-scale rollbacks do. */
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 5L * 60 * 1000

    data class State(
        val subscriptionKey: String? = null,
        val lastVerifiedAt: Long = 0L,
        val demoStartedAt: Long = 0L,
        val lastSeenAt: Long = 0L,
    )

    /** Simplified result of an already-performed online verification. */
    enum class VerifyOutcome { ACTIVE, ENDED, UNVERIFIED }

    enum class AppState {
        FIRST_RUN, DEMO, DEMO_EXPIRED,
        SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, NEEDS_CONNECTION,
    }

    fun touch(state: State, now: Long): State =
        state.copy(lastSeenAt = maxOf(state.lastSeenAt, now))

    fun startDemo(state: State, now: Long): State =
        if (state.demoStartedAt > 0L) state
        else state.copy(demoStartedAt = now, lastSeenAt = maxOf(state.lastSeenAt, now))

    fun withVerifiedNow(state: State, now: Long): State =
        state.copy(lastVerifiedAt = now, lastSeenAt = maxOf(state.lastSeenAt, now))

    fun withSubscriptionKey(state: State, key: String, now: Long): State =
        state.copy(
            subscriptionKey = key,
            lastVerifiedAt = now,
            lastSeenAt = maxOf(state.lastSeenAt, now),
        )

    fun isDemoActive(state: State, now: Long): Boolean =
        state.demoStartedAt > 0L &&
            now + CLOCK_ROLLBACK_TOLERANCE_MS >= state.lastSeenAt &&
            now < state.demoStartedAt + DEMO_DURATION_MS

    fun demoDaysLeft(state: State, now: Long): Int {
        if (!isDemoActive(state, now)) return 0
        val remaining = state.demoStartedAt + DEMO_DURATION_MS - now
        val day = 24L * 60 * 60 * 1000
        return ((remaining + day - 1) / day).toInt()
    }

    private fun withinGrace(state: State, now: Long): Boolean =
        state.lastVerifiedAt > 0L &&
            now + CLOCK_ROLLBACK_TOLERANCE_MS >= state.lastSeenAt &&
            now - state.lastVerifiedAt <= OFFLINE_GRACE_MS

    /** Final decision given the (already obtained) verification outcome. */
    fun resolve(state: State, outcome: VerifyOutcome, now: Long): AppState {
        if (state.subscriptionKey != null) {
            return when (outcome) {
                VerifyOutcome.ACTIVE -> AppState.SUBSCRIPTION_ACTIVE
                VerifyOutcome.ENDED -> AppState.SUBSCRIPTION_EXPIRED
                VerifyOutcome.UNVERIFIED ->
                    if (withinGrace(state, now)) AppState.SUBSCRIPTION_ACTIVE
                    else AppState.NEEDS_CONNECTION
            }
        }
        return when {
            isDemoActive(state, now) -> AppState.DEMO
            state.demoStartedAt > 0L -> AppState.DEMO_EXPIRED
            else -> AppState.FIRST_RUN
        }
    }
}
```

- [ ] **Step 2: Reescribir `LicenseManager.kt`**

Reemplazar TODO el contenido de `app/src/main/kotlin/com/music/echo/license/LicenseManager.kt` por:

```kotlin
package iad1tya.echo.music.license

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Android-facing orchestrator. Persists [LicenseLogic.State] in SharedPreferences and performs the
 * online Gumroad verification on each app open, applying the offline grace window from [LicenseLogic].
 */
object LicenseManager {

    /** Gumroad membership product id for JR-MUSIC-PRO-OFFICIAL ANDROID. Not secret; ships in the APK. */
    const val PRODUCT_ID = "wcPehkIWHRbPKR4_hZLdJQ=="

    private const val PREFS = "jr_license"
    private const val KEY_SUB = "subscription_key"
    private const val KEY_LAST_VERIFIED = "last_verified_at"
    private const val KEY_DEMO_STARTED = "demo_started_at"
    private const val KEY_LAST_SEEN = "last_seen_at"

    private val gumroad = GumroadClient()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): LicenseLogic.State {
        val p = prefs(context)
        return LicenseLogic.State(
            subscriptionKey = p.getString(KEY_SUB, null)?.takeIf { it.isNotBlank() },
            lastVerifiedAt = p.getLong(KEY_LAST_VERIFIED, 0L),
            demoStartedAt = p.getLong(KEY_DEMO_STARTED, 0L),
            lastSeenAt = p.getLong(KEY_LAST_SEEN, 0L),
        )
    }

    private fun save(context: Context, state: LicenseLogic.State) {
        prefs(context).edit()
            .putString(KEY_SUB, state.subscriptionKey)
            .putLong(KEY_LAST_VERIFIED, state.lastVerifiedAt)
            .putLong(KEY_DEMO_STARTED, state.demoStartedAt)
            .putLong(KEY_LAST_SEEN, state.lastSeenAt)
            .apply()
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun demoDaysLeft(context: Context): Int =
        LicenseLogic.demoDaysLeft(load(context), System.currentTimeMillis())

    fun startDemo(context: Context) {
        val now = System.currentTimeMillis()
        save(context, LicenseLogic.startDemo(load(context), now))
    }

    /** Full evaluation used by the gate at startup. Performs online verification when possible. */
    suspend fun evaluate(context: Context): LicenseLogic.AppState {
        val now = System.currentTimeMillis()
        var state = LicenseLogic.touch(load(context), now)
        save(context, state)

        val key = state.subscriptionKey
            ?: return LicenseLogic.resolve(state, LicenseLogic.VerifyOutcome.UNVERIFIED, now)

        val outcome = if (isOnline(context)) {
            when (gumroad.verify(PRODUCT_ID, key)) {
                GumroadVerify.Result.ACTIVE -> {
                    state = LicenseLogic.withVerifiedNow(state, now)
                    save(context, state)
                    LicenseLogic.VerifyOutcome.ACTIVE
                }
                GumroadVerify.Result.ENDED,
                GumroadVerify.Result.INVALID_KEY -> LicenseLogic.VerifyOutcome.ENDED
                GumroadVerify.Result.NETWORK_ERROR -> LicenseLogic.VerifyOutcome.UNVERIFIED
            }
        } else {
            LicenseLogic.VerifyOutcome.UNVERIFIED
        }
        return LicenseLogic.resolve(state, outcome, now)
    }

    /** Called from the "Ya me suscribí" entry screen. Saves the key only when Gumroad confirms active. */
    suspend fun activateSubscription(context: Context, rawKey: String): GumroadVerify.Result {
        val key = rawKey.trim()
        if (key.isEmpty()) return GumroadVerify.Result.INVALID_KEY
        val result = gumroad.verify(PRODUCT_ID, key)
        if (result == GumroadVerify.Result.ACTIVE) {
            val now = System.currentTimeMillis()
            save(context, LicenseLogic.withSubscriptionKey(load(context), key, now))
        }
        return result
    }

    /** Re-checks the stored subscription (used by the renew / "ya pagué" screen). */
    suspend fun reverify(context: Context): GumroadVerify.Result {
        val key = load(context).subscriptionKey ?: return GumroadVerify.Result.INVALID_KEY
        val result = gumroad.verify(PRODUCT_ID, key)
        if (result == GumroadVerify.Result.ACTIVE) {
            val now = System.currentTimeMillis()
            save(context, LicenseLogic.withVerifiedNow(load(context), now))
        }
        return result
    }
}
```

- [ ] **Step 3: Crear `LicenseScreens.kt`**

Crear `app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt`:

```kotlin
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

private val BrandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFDE60B3), Color(0xFF9B6CFF), Color(0xFF3DA9ED)),
)
private val ScreenGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF2A1052), Color(0xFF160830), Color(0xFF0B0418)),
)
private val Accent = Color(0xFF9B6CFF)

private fun openGumroad(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUMROAD_URL))) }
}

@Composable
private fun LicenseScaffold(
    subtitle: String,
    hint: String,
    content: @Composable () -> Unit,
) {
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
                modifier = Modifier.size(110.dp).clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(brush = BrandGradient, fontWeight = FontWeight.ExtraBold)) {
                        append("JR MUSIC PLAYER PRO")
                    }
                },
                fontSize = 24.sp,
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
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun LoadingLicenseScreen() {
    MaterialTheme(colorScheme = darkColorScheme()) {
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

    LicenseScaffold(
        subtitle = "Activar suscripción",
        hint = "Pega la clave de licencia que Gumroad te envió por correo.",
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
            loading = true
            status = null
            scope.launch {
                val result = LicenseManager.activateSubscription(context, key)
                loading = false
                when (result) {
                    GumroadVerify.Result.ACTIVE -> onActivated()
                    GumroadVerify.Result.ENDED -> {
                        statusColor = Color(0xFFFFB74D)
                        status = "Esta suscripción está cancelada o vencida. Renueva el pago en Gumroad."
                    }
                    GumroadVerify.Result.INVALID_KEY -> {
                        statusColor = Color(0xFFFF6B6B)
                        status = "Clave inválida. Revisa que la copiaste completa."
                    }
                    GumroadVerify.Result.NETWORK_ERROR -> {
                        statusColor = Color(0xFFFF6B6B)
                        status = "Sin conexión. Conéctate a internet e inténtalo de nuevo."
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { openGumroad(context) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Suscribirme ($10/mes)", fontWeight = FontWeight.SemiBold) }
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
                    if (r == GumroadVerify.Result.ACTIVE) onActivated()
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
```

- [ ] **Step 4: Borrar `ActivationScreen.kt`**

```bash
git rm app/src/main/kotlin/com/music/echo/license/ActivationScreen.kt
```

- [ ] **Step 5: Crear `LicenseGate.kt`**

Crear `app/src/main/kotlin/com/music/echo/license/LicenseGate.kt`:

```kotlin
package iad1tya.echo.music.license

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.license.LicenseLogic.AppState
import kotlinx.coroutines.launch

/**
 * Wraps the whole app: evaluates the license on entry and shows the proper gate screen, or the real
 * app content when entitled (DEMO or active subscription).
 */
@Composable
fun LicenseGate(appContent: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appState by remember { mutableStateOf<AppState?>(null) }
    var showEntry by remember { mutableStateOf(false) }

    fun refresh() {
        showEntry = false
        appState = null
        scope.launch { appState = LicenseManager.evaluate(context) }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showEntry) {
        SubscriptionEntryScreen(onActivated = { refresh() }, onBack = { showEntry = false })
        return
    }

    when (val s = appState) {
        null -> LoadingLicenseScreen()
        AppState.DEMO, AppState.SUBSCRIPTION_ACTIVE -> appContent()
        AppState.FIRST_RUN -> ActivationPromptScreen(
            demoExpired = false,
            onTryDemo = { LicenseManager.startDemo(context); refresh() },
            onHaveSubscription = { showEntry = true },
        )
        AppState.DEMO_EXPIRED -> ActivationPromptScreen(
            demoExpired = true,
            onTryDemo = null,
            onHaveSubscription = { showEntry = true },
        )
        AppState.SUBSCRIPTION_EXPIRED -> RenewScreen(onActivated = { refresh() })
        AppState.NEEDS_CONNECTION -> NeedsConnectionScreen(onRetry = { refresh() })
    }
}
```

- [ ] **Step 6: Conectar `MainActivity.kt`**

En `app/src/main/kotlin/com/music/echo/MainActivity.kt`:

(a) En el bloque de imports (~líneas 198-200) reemplazar:
```kotlin
import iad1tya.echo.music.license.ActivationScreen
import iad1tya.echo.music.license.LicenseManager
```
por:
```kotlin
import iad1tya.echo.music.license.LicenseGate
```

(b) Reemplazar el bloque `setContent { ... }` (~líneas 369-383):
```kotlin
        setContent {
            var isLicensed by remember {
                mutableStateOf(LicenseManager.isLicensed(this@MainActivity))
            }
            if (!isLicensed) {
                ActivationScreen(onActivated = { isLicensed = true })
            } else {
                echomusicApp(
                    playerConnection = playerConnection,
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                )
            }
        }
```
por:
```kotlin
        setContent {
            LicenseGate {
                echomusicApp(
                    playerConnection = playerConnection,
                    database = database,
                    downloadUtil = downloadUtil,
                    syncUtils = syncUtils,
                )
            }
        }
```

- [ ] **Step 7: Reescribir `LicenseLogicTest.kt`**

Reemplazar TODO el contenido de `app/src/test/kotlin/iad1tya/echo/music/license/LicenseLogicTest.kt` por:

```kotlin
package iad1tya.echo.music.license

import iad1tya.echo.music.license.LicenseLogic.AppState
import iad1tya.echo.music.license.LicenseLogic.State
import iad1tya.echo.music.license.LicenseLogic.VerifyOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseLogicTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L

    // ---- demo ----

    @Test fun firstRunWhenNothingStarted() {
        assertEquals(AppState.FIRST_RUN, LicenseLogic.resolve(State(), VerifyOutcome.UNVERIFIED, now))
    }

    @Test fun startDemoSetsTimer() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(now, s.demoStartedAt)
        assertTrue(LicenseLogic.isDemoActive(s, now))
    }

    @Test fun startDemoIsIdempotent() {
        val s1 = LicenseLogic.startDemo(State(), now)
        val s2 = LicenseLogic.startDemo(s1, now + 10 * day)
        assertEquals(s1.demoStartedAt, s2.demoStartedAt)
    }

    @Test fun demoActiveWithinThreeDays() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(AppState.DEMO, LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now + 2 * day))
    }

    @Test fun demoExpiresAfterThreeDays() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(AppState.DEMO_EXPIRED, LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now + 3 * day))
    }

    @Test fun demoDaysLeftCountsDown() {
        val s = LicenseLogic.startDemo(State(), now)
        assertEquals(3, LicenseLogic.demoDaysLeft(s, now))
        assertEquals(1, LicenseLogic.demoDaysLeft(s, now + 2 * day + 1))
        assertEquals(0, LicenseLogic.demoDaysLeft(s, now + 3 * day))
    }

    @Test fun clockRollbackLocksDemo() {
        val started = LicenseLogic.startDemo(State(), now)
        val seen = LicenseLogic.touch(started, now + day)
        assertFalse(LicenseLogic.isDemoActive(seen, now - day))
        assertTrue(LicenseLogic.isDemoActive(seen, now + day + 1000))
    }

    // ---- subscription ----

    private fun subState(verifiedAt: Long) =
        State(subscriptionKey = "KEY", lastVerifiedAt = verifiedAt, lastSeenAt = verifiedAt)

    @Test fun activeSubscriptionEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.ACTIVE, now),
        )
    }

    @Test fun endedSubscriptionBlocks() {
        assertEquals(
            AppState.SUBSCRIPTION_EXPIRED,
            LicenseLogic.resolve(subState(now), VerifyOutcome.ENDED, now),
        )
    }

    @Test fun offlineWithinGraceEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + 2 * day),
        )
    }

    @Test fun offlineAtGraceLimitStillEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + 3 * day),
        )
    }

    @Test fun offlineBeyondGraceNeedsConnection() {
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + 3 * day + 1),
        )
    }

    @Test fun offlineWithoutAnyVerificationNeedsConnection() {
        val s = State(subscriptionKey = "KEY", lastVerifiedAt = 0L)
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now),
        )
    }

    @Test fun clockRollbackBlocksGrace() {
        val s = LicenseLogic.touch(subState(now), now + 2 * day)
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(s, VerifyOutcome.UNVERIFIED, now - day),
        )
    }
}
```

- [ ] **Step 8: Ejecutar la suite de licencias (compila main + corre tests)**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.*"`
Expected: BUILD SUCCESSFUL, todos los tests PASS (LicenseLogicTest + GumroadVerifyTest).
Si falla la compilación de main por algún símbolo viejo, revisar que no quede otra referencia a `ActivationScreen` o a las APIs viejas (`LicenseManager.isLicensed`, `LicenseLogic.activate`, `DEMO_KEY_HASH`, `PERPETUAL_KEY_HASH`): `git grep -nE "isLicensed|ActivationScreen|DEMO_KEY_HASH|PERPETUAL_KEY_HASH|\.activate\("`.

- [ ] **Step 9: Commit**

```bash
git add -A app/src/main/kotlin/com/music/echo/license app/src/test/kotlin/iad1tya/echo/music/license app/src/main/kotlin/com/music/echo/MainActivity.kt
git commit -m "feat(license): swap serial gate for Gumroad subscription + 3-day demo"
```

---

### Task 4: Build del APK y verificación manual

**Files:** ninguno (validación).

- [ ] **Step 1: Compilar el APK debug**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:assembleUniversalFossDebug`
Expected: BUILD SUCCESSFUL. APK en `app/build/outputs/apk/universalFoss/debug/`.

- [ ] **Step 2: Checklist de verificación manual (en dispositivo/emulador)**

Instalar limpio (desinstalar antes para borrar `jr_license`):
- [ ] Primer arranque → pantalla bienvenida con "Ya me suscribí" + "Probar gratis (3 días)".
- [ ] Tocar "Probar gratis" → entra a la app (estado DEMO).
- [ ] Cerrar y reabrir dentro del demo → entra directo, sin pantalla.
- [ ] "Ya me suscribí" → pantalla de pegar clave. Pegar una license key real (tras compra de prueba) → entra (SUSCRIPCION_ACTIVA).
- [ ] Modo avión, reabrir dentro de 3 días desde la última verificación → entra (gracia).
- [ ] Modo avión + saltar el reloj 4 días → pantalla "Conéctate a internet".
- [ ] Cancelar la suscripción en Gumroad, reabrir con internet → pantalla "Renovar".
- [ ] Tras reactivar el pago, "Ya pagué, reintentar" o reabrir → entra de nuevo.

> Nota: la verificación end-to-end requiere una license key real. Confirmar antes que el producto tiene activado "Generate a unique license key per sale" haciendo una compra de prueba (reembolsable).

- [ ] **Step 3 (si hubo cambios): Commit**

Solo si algún fix surgió de la verificación. Si no, no hay commit.

---

## Self-Review

**Spec coverage:**
- Estados DEMO/DEMO_EXPIRED/SUBSCRIPTION_ACTIVE/SUBSCRIPTION_EXPIRED/NEEDS_CONNECTION/FIRST_RUN → `LicenseLogic.AppState` + `resolve` (Task 3) + tests (Task 3 Step 7). ✓
- Verificación Gumroad (3 campos null) → `GumroadVerify` (Task 1). ✓
- POST con product_id/license_key/increment_uses_count=false → `GumroadClient` (Task 2). ✓
- Verificación en cada apertura + gracia offline 3 días → `LicenseManager.evaluate` + `withinGrace` (Task 3). ✓
- Demo keyless 3 días + botón "Probar gratis" → `startDemo` + `ActivationPromptScreen`/`LicenseGate` (Task 3). ✓
- "Ya me suscribí" pega license key → `SubscriptionEntryScreen` (Task 3). ✓
- Pantalla renovar + needs-connection → `RenewScreen`/`NeedsConnectionScreen` (Task 3). ✓
- Eliminar perpetua + serial viejo → reescritura LicenseLogic/LicenseManager + borrar ActivationScreen (Task 3). ✓
- Sin servidor (Opción A) → llamada directa en `GumroadClient`. ✓

**Placeholder scan:** sin TBD/TODO; todo el código está completo. ✓

**Type consistency:** `GumroadVerify.Result {ACTIVE,ENDED,INVALID_KEY,NETWORK_ERROR}` usado igual en GumroadClient/LicenseManager/SubscriptionEntryScreen. `LicenseLogic.VerifyOutcome {ACTIVE,ENDED,UNVERIFIED}` y `AppState` usados igual en LicenseManager/LicenseGate/tests. `evaluate`/`activateSubscription`/`reverify`/`startDemo`/`isOnline`/`demoDaysLeft` consistentes entre LicenseManager y sus llamadores. ✓
