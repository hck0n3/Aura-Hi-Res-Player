# Candado de dispositivo (1 suscripción = 1 equipo) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforzar que cada suscripción Gumroad funcione en un solo equipo a la vez mediante un Cloudflare Worker + KV, con auto-release por inactividad de 2 días.

**Architecture:** La app deja de llamar a Gumroad directo y llama a un Worker (`/verify`) enviando `license_key` + `device_id` (ANDROID_ID). El Worker verifica con Gumroad y mantiene en KV el binding `license_key → {device_id, last_seen}`, devolviendo `active|ended|invalid|device_mismatch`. El núcleo puro de la app (`LicenseLogic`) gana un estado `DEVICE_BLOCKED` y baja la gracia offline a 1 día.

**Tech Stack:** Cloudflare Workers (JS) + KV; Kotlin/Compose Android; ktor client; kotlinx.serialization; JUnit.

**Spec:** `docs/superpowers/specs/2026-06-15-device-lock-backend-design.md`

## Datos de infraestructura (ya configurados por el usuario)
- Worker URL: `https://round-math-d64e.toberto4000.workers.dev` (endpoint `/verify`).
- KV namespace binding en el Worker: `LICENSES`.
- Variable de entorno del Worker: `PRODUCT_ID = wcPehkIWHRbPKR4_hZLdJQ==`.
- Cuenta Cloudflare account_id: `45159dfbd118d4846f3bd534d3732358`.

## Parámetros
- Worker auto-release (inactividad): **2 días**.
- App gracia offline: **1 día** (baja de 3d). Demo sigue **3 días**.
- Regla: `auto_release (2d) > gracia_offline (1d)`.

## Comandos (Windows, Git Bash)
```bash
export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8"
```
- Tests licencia: `./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.*"`
- Compilar main: `./gradlew :app:compileUniversalFossDebugKotlin`
- APK: `./gradlew :app:assembleUniversalFossDebug`

## Estructura de archivos
| Archivo | Acción | Responsabilidad |
|---|---|---|
| `licensing-worker/worker.js` | crear | Worker Cloudflare: verify Gumroad + binding KV |
| `licensing-worker/README.md` | crear | Pasos de despliegue + pruebas curl |
| `app/.../license/LicenseStatus.kt` | crear | enum `LicenseStatus` + `LicenseStatusParser` (puro) |
| `app/.../license/DeviceId.kt` | crear | ANDROID_ID con fallback UUID |
| `app/.../license/LicenseBackendClient.kt` | crear | ktor POST al Worker |
| `app/.../license/LicenseLogic.kt` | modificar | +DEVICE_MISMATCH/DEVICE_BLOCKED, gracia 1d |
| `app/.../license/LicenseManager.kt` | reescribir | usa backend en vez de Gumroad |
| `app/.../license/LicenseScreens.kt` | modificar | LicenseStatus + DeviceBlockedScreen |
| `app/.../license/LicenseGate.kt` | modificar | caso DEVICE_BLOCKED |
| `app/.../license/GumroadClient.kt` | borrar | lógica pasa al Worker |
| `app/.../license/GumroadVerify.kt` | borrar | lógica pasa al Worker |
| `app/src/test/.../license/GumroadVerifyTest.kt` | borrar | cobertura pasa a curl del Worker |
| `app/src/test/.../license/LicenseStatusParserTest.kt` | crear | tests del parser |
| `app/src/test/.../license/LicenseLogicTest.kt` | modificar | grace 1d + deviceMismatch |

Rutas físicas: main `app/src/main/kotlin/com/music/echo/license/`, test `app/src/test/kotlin/iad1tya/echo/music/license/`.

---

### Task 1: Worker de Cloudflare

**Files:**
- Create: `licensing-worker/worker.js`
- Create: `licensing-worker/README.md`

- [ ] **Step 1: Crear `licensing-worker/worker.js`**

```javascript
// JR MUSIC PRO — license + one-device-per-subscription Worker.
// Bindings: KV namespace "LICENSES". Env var: PRODUCT_ID (Gumroad product id).
const GUMROAD_VERIFY = "https://api.gumroad.com/v2/licenses/verify";
const INACTIVITY_MS = 2 * 24 * 60 * 60 * 1000; // auto-release after 2 days idle

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/verify") {
      return json({ status: "invalid" }, 404);
    }

    let body;
    try {
      body = await request.json();
    } catch (e) {
      return json({ status: "invalid" }, 400);
    }
    const licenseKey = body && body.license_key;
    const deviceId = body && body.device_id;
    if (!licenseKey || !deviceId) return json({ status: "invalid" }, 400);

    const gum = await verifyGumroad(env.PRODUCT_ID, licenseKey);
    if (gum === "invalid") return json({ status: "invalid" });
    if (gum === "ended") return json({ status: "ended" });
    if (gum === "error") return json({ status: "error" }); // upstream down -> app uses offline grace

    // gum === "active": apply device binding
    const now = Date.now();
    const raw = await env.LICENSES.get(licenseKey);
    const binding = raw ? JSON.parse(raw) : null;
    const owned = !binding || binding.device_id === deviceId;
    const released = binding && now - binding.last_seen > INACTIVITY_MS;
    if (owned || released) {
      await env.LICENSES.put(licenseKey, JSON.stringify({ device_id: deviceId, last_seen: now }));
      return json({ status: "active" });
    }
    return json({ status: "device_mismatch" });
  },
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function verifyGumroad(productId, licenseKey) {
  try {
    const form = new URLSearchParams();
    form.set("product_id", productId);
    form.set("license_key", licenseKey);
    form.set("increment_uses_count", "false");
    const res = await fetch(GUMROAD_VERIFY, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form.toString(),
    });
    const data = await res.json();
    if (!data || data.success !== true) return "invalid";
    const p = data.purchase || {};
    const ended =
      p.subscription_cancelled_at || p.subscription_failed_at || p.subscription_ended_at;
    return ended ? "ended" : "active";
  } catch (e) {
    return "error";
  }
}
```

- [ ] **Step 2: Crear `licensing-worker/README.md`**

```markdown
# JR MUSIC PRO — License Worker

Cloudflare Worker that verifies Gumroad subscriptions and enforces one device per subscription.

## Deploy (dashboard)
1. Cloudflare dashboard → Workers & Pages → open the `round-math-d64e` Worker.
2. **Edit code** → replace the contents with `worker.js` → **Deploy**.
3. Ensure these are configured (Settings → Runtime):
   - KV namespace binding named `LICENSES`.
   - Plaintext variable `PRODUCT_ID = wcPehkIWHRbPKR4_hZLdJQ==`.

## Behaviour
`POST /verify` with JSON `{ "license_key": "...", "device_id": "..." }` →
`{ "status": "active" | "ended" | "invalid" | "device_mismatch" | "error" }`.
Binding stored in KV as `license_key -> { device_id, last_seen }`. Auto-released after 2 days idle.

## Manual test (curl)
```
# invalid key
curl -s https://round-math-d64e.toberto4000.workers.dev/verify \
  -H "content-type: application/json" \
  -d '{"license_key":"FAKE","device_id":"dev-A"}'
# -> {"status":"invalid"}

# real key, first device -> active ; same key, second device -> device_mismatch
curl -s .../verify -H "content-type: application/json" -d '{"license_key":"REAL","device_id":"dev-A"}'
curl -s .../verify -H "content-type: application/json" -d '{"license_key":"REAL","device_id":"dev-B"}'
```
```

- [ ] **Step 3: Commit**

```bash
git add licensing-worker/worker.js licensing-worker/README.md
git commit -m "feat(license): cloudflare worker for device-locked gumroad verification"
```
End the commit body with:
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>

> El despliegue real (pegar en el dashboard) y las pruebas curl se hacen en la Task 5 (manual).

---

### Task 2: `LicenseStatus` + `LicenseStatusParser` (TDD)

**Files:**
- Create test: `app/src/test/kotlin/iad1tya/echo/music/license/LicenseStatusParserTest.kt`
- Create main: `app/src/main/kotlin/com/music/echo/license/LicenseStatus.kt`

- [ ] **Step 1: Escribir el test que falla**

Crear `app/src/test/kotlin/iad1tya/echo/music/license/LicenseStatusParserTest.kt`:

```kotlin
package iad1tya.echo.music.license

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseStatusParserTest {

    @Test fun active() {
        assertEquals(LicenseStatus.ACTIVE, LicenseStatusParser.parse("""{"status":"active"}"""))
    }

    @Test fun ended() {
        assertEquals(LicenseStatus.ENDED, LicenseStatusParser.parse("""{"status":"ended"}"""))
    }

    @Test fun invalid() {
        assertEquals(LicenseStatus.INVALID_KEY, LicenseStatusParser.parse("""{"status":"invalid"}"""))
    }

    @Test fun deviceMismatch() {
        assertEquals(
            LicenseStatus.DEVICE_MISMATCH,
            LicenseStatusParser.parse("""{"status":"device_mismatch"}"""),
        )
    }

    @Test fun unknownStatusIsNetworkError() {
        assertEquals(LicenseStatus.NETWORK_ERROR, LicenseStatusParser.parse("""{"status":"error"}"""))
    }

    @Test fun garbageBodyIsNetworkError() {
        assertEquals(LicenseStatus.NETWORK_ERROR, LicenseStatusParser.parse("<html>502</html>"))
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que falla**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.LicenseStatusParserTest"`
Expected: FAIL de compilación — `Unresolved reference: LicenseStatus`.

- [ ] **Step 3: Crear `app/src/main/kotlin/com/music/echo/license/LicenseStatus.kt`**

```kotlin
package iad1tya.echo.music.license

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of a license check performed by the backend Worker. */
enum class LicenseStatus { ACTIVE, ENDED, INVALID_KEY, DEVICE_MISMATCH, NETWORK_ERROR }

/** Pure parser of the Worker's `{ "status": "..." }` JSON. Unit-testable with plain JUnit. */
object LicenseStatusParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): LicenseStatus =
        try {
            when (json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.contentOrNull) {
                "active" -> LicenseStatus.ACTIVE
                "ended" -> LicenseStatus.ENDED
                "device_mismatch" -> LicenseStatus.DEVICE_MISMATCH
                "invalid" -> LicenseStatus.INVALID_KEY
                else -> LicenseStatus.NETWORK_ERROR
            }
        } catch (e: Exception) {
            LicenseStatus.NETWORK_ERROR
        }
}
```

- [ ] **Step 4: Ejecutar y verificar que pasa**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.LicenseStatusParserTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/license/LicenseStatus.kt app/src/test/kotlin/iad1tya/echo/music/license/LicenseStatusParserTest.kt
git commit -m "feat(license): LicenseStatus enum + pure worker-response parser (TDD)"
```
End the commit body with:
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>

---

### Task 3: `DeviceId` + `LicenseBackendClient`

**Files:**
- Create: `app/src/main/kotlin/com/music/echo/license/DeviceId.kt`
- Create: `app/src/main/kotlin/com/music/echo/license/LicenseBackendClient.kt`

- [ ] **Step 1: Crear `DeviceId.kt`**

```kotlin
package iad1tya.echo.music.license

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Stable per-device identifier for the one-device-per-subscription check. Uses ANDROID_ID (survives
 * reinstalls on the same device); falls back to a persisted random UUID when ANDROID_ID is missing or
 * the known buggy emulator value. Cached in the jr_license prefs.
 */
object DeviceId {

    private const val PREFS = "jr_license"
    private const val KEY = "device_id"
    private const val ANDROID_ID_BUG = "9774d56d682e549c"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val id = if (androidId.isNullOrBlank() || androidId == ANDROID_ID_BUG) {
            UUID.randomUUID().toString()
        } else {
            androidId
        }
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
```

- [ ] **Step 2: Crear `LicenseBackendClient.kt`**

```kotlin
package iad1tya.echo.music.license

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Talks to the JR MUSIC PRO license Worker, which verifies the Gumroad subscription and enforces one
 * device per key. Any network/parse failure maps to [LicenseStatus.NETWORK_ERROR] so the caller can
 * fall back to the offline grace window.
 */
class LicenseBackendClient(
    private val http: HttpClient = HttpClient(OkHttp),
) {
    suspend fun verify(licenseKey: String, deviceId: String): LicenseStatus =
        try {
            val payload = buildJsonObject {
                put("license_key", licenseKey)
                put("device_id", deviceId)
            }.toString()
            val body = http.post(VERIFY_URL) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.bodyAsText()
            LicenseStatusParser.parse(body)
        } catch (e: Exception) {
            LicenseStatus.NETWORK_ERROR
        }

    companion object {
        private const val VERIFY_URL = "https://round-math-d64e.toberto4000.workers.dev/verify"
    }
}
```

- [ ] **Step 3: Compilar main para verificar**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:compileUniversalFossDebugKotlin`
Expected: BUILD SUCCESSFUL. Si un import ktor no resuelve (`post`/`setBody`/`contentType`), revisa cómo lo importan otros archivos: `git grep -n "io.ktor.client.request.post" app` y adapta; reporta la desviación.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/music/echo/license/DeviceId.kt app/src/main/kotlin/com/music/echo/license/LicenseBackendClient.kt
git commit -m "feat(license): device id helper + ktor client for the license worker"
```
End the commit body with:
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>

---

### Task 4: Conectar la app al Worker (swap atómico)

Reemplaza el uso directo de Gumroad por el Worker y añade el estado/​pantalla de equipo bloqueado. Se verifica con la suite de licencias (compila main + corre tests) al final.

**Files:**
- Modify: `app/src/main/kotlin/com/music/echo/license/LicenseLogic.kt`
- Modify (reescribir): `app/src/main/kotlin/com/music/echo/license/LicenseManager.kt`
- Modify: `app/src/main/kotlin/com/music/echo/license/LicenseScreens.kt`
- Modify: `app/src/main/kotlin/com/music/echo/license/LicenseGate.kt`
- Delete: `app/src/main/kotlin/com/music/echo/license/GumroadClient.kt`
- Delete: `app/src/main/kotlin/com/music/echo/license/GumroadVerify.kt`
- Delete: `app/src/test/kotlin/iad1tya/echo/music/license/GumroadVerifyTest.kt`
- Modify (reescribir): `app/src/test/kotlin/iad1tya/echo/music/license/LicenseLogicTest.kt`

- [ ] **Step 1: `LicenseLogic.kt` — bajar gracia a 1 día**

Reemplazar:
```kotlin
    /** How long the app keeps working without a fresh successful online verification. */
    const val OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1000
```
por:
```kotlin
    /** How long the app keeps working without a fresh successful online verification. */
    const val OFFLINE_GRACE_MS = 1L * 24 * 60 * 60 * 1000
```

- [ ] **Step 2: `LicenseLogic.kt` — añadir DEVICE_MISMATCH y DEVICE_BLOCKED**

Reemplazar:
```kotlin
    /** Simplified result of an already-performed online verification. */
    enum class VerifyOutcome { ACTIVE, ENDED, UNVERIFIED }

    enum class AppState {
        FIRST_RUN, DEMO, DEMO_EXPIRED,
        SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, NEEDS_CONNECTION,
    }
```
por:
```kotlin
    /** Simplified result of an already-performed online verification. */
    enum class VerifyOutcome { ACTIVE, ENDED, DEVICE_MISMATCH, UNVERIFIED }

    enum class AppState {
        FIRST_RUN, DEMO, DEMO_EXPIRED,
        SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, NEEDS_CONNECTION, DEVICE_BLOCKED,
    }
```

- [ ] **Step 3: `LicenseLogic.kt` — manejar DEVICE_MISMATCH en `resolve`**

Reemplazar:
```kotlin
            return when (outcome) {
                VerifyOutcome.ACTIVE -> AppState.SUBSCRIPTION_ACTIVE
                VerifyOutcome.ENDED -> AppState.SUBSCRIPTION_EXPIRED
                VerifyOutcome.UNVERIFIED ->
                    if (withinGrace(state, now)) AppState.SUBSCRIPTION_ACTIVE
                    else AppState.NEEDS_CONNECTION
            }
```
por:
```kotlin
            return when (outcome) {
                VerifyOutcome.ACTIVE -> AppState.SUBSCRIPTION_ACTIVE
                VerifyOutcome.ENDED -> AppState.SUBSCRIPTION_EXPIRED
                VerifyOutcome.DEVICE_MISMATCH -> AppState.DEVICE_BLOCKED
                VerifyOutcome.UNVERIFIED ->
                    if (withinGrace(state, now)) AppState.SUBSCRIPTION_ACTIVE
                    else AppState.NEEDS_CONNECTION
            }
```

- [ ] **Step 4: Reescribir `LicenseManager.kt`**

Reemplazar TODO el contenido por:

```kotlin
package iad1tya.echo.music.license

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-facing orchestrator. Persists [LicenseLogic.State] in SharedPreferences and verifies the
 * subscription online via the backend Worker (which enforces one device per key) on each app open,
 * applying the offline grace window from [LicenseLogic].
 */
object LicenseManager {

    private const val PREFS = "jr_license"
    private const val KEY_SUB = "subscription_key"
    private const val KEY_LAST_VERIFIED = "last_verified_at"
    private const val KEY_DEMO_STARTED = "demo_started_at"
    private const val KEY_LAST_SEEN = "last_seen_at"

    private val backend = LicenseBackendClient()

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

    suspend fun startDemo(context: Context) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        save(context, LicenseLogic.startDemo(load(context), now))
    }

    private fun outcomeOf(status: LicenseStatus): LicenseLogic.VerifyOutcome =
        when (status) {
            LicenseStatus.ACTIVE -> LicenseLogic.VerifyOutcome.ACTIVE
            LicenseStatus.ENDED, LicenseStatus.INVALID_KEY -> LicenseLogic.VerifyOutcome.ENDED
            LicenseStatus.DEVICE_MISMATCH -> LicenseLogic.VerifyOutcome.DEVICE_MISMATCH
            LicenseStatus.NETWORK_ERROR -> LicenseLogic.VerifyOutcome.UNVERIFIED
        }

    /** Full evaluation used by the gate at startup. Verifies online (Worker) when possible. */
    suspend fun evaluate(context: Context): LicenseLogic.AppState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var state = LicenseLogic.touch(load(context), now)
        save(context, state)

        val key = state.subscriptionKey
            ?: return@withContext LicenseLogic.resolve(state, LicenseLogic.VerifyOutcome.UNVERIFIED, now)

        val outcome = if (isOnline(context)) {
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                state = LicenseLogic.withVerifiedNow(state, now)
                save(context, state)
            }
            outcomeOf(status)
        } else {
            LicenseLogic.VerifyOutcome.UNVERIFIED
        }
        LicenseLogic.resolve(state, outcome, now)
    }

    /** Called from the "Ya me suscribí" entry screen. Saves the key only when verification is ACTIVE. */
    suspend fun activateSubscription(context: Context, rawKey: String): LicenseStatus =
        withContext(Dispatchers.IO) {
            val key = rawKey.trim()
            if (key.isEmpty()) return@withContext LicenseStatus.INVALID_KEY
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                val now = System.currentTimeMillis()
                save(context, LicenseLogic.withSubscriptionKey(load(context), key, now))
            }
            status
        }

    /** Re-checks the stored subscription (used by the renew / "ya pagué" screen). */
    suspend fun reverify(context: Context): LicenseStatus =
        withContext(Dispatchers.IO) {
            val state = load(context)
            val key = state.subscriptionKey ?: return@withContext LicenseStatus.INVALID_KEY
            val status = backend.verify(key, DeviceId.get(context))
            if (status == LicenseStatus.ACTIVE) {
                save(context, LicenseLogic.withVerifiedNow(state, System.currentTimeMillis()))
            }
            status
        }
}
```

- [ ] **Step 5: `LicenseScreens.kt` — `SubscriptionEntryScreen` usa `LicenseStatus`**

Reemplazar:
```kotlin
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
```
por:
```kotlin
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
```

- [ ] **Step 6: `LicenseScreens.kt` — `RenewScreen` usa `LicenseStatus`**

Reemplazar:
```kotlin
                    if (r == GumroadVerify.Result.ACTIVE) onActivated()
```
por:
```kotlin
                    if (r == LicenseStatus.ACTIVE) onActivated()
```

- [ ] **Step 7: `LicenseScreens.kt` — añadir `DeviceBlockedScreen`**

Añadir al final del archivo (después de `NeedsConnectionScreen`):
```kotlin

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
```

- [ ] **Step 8: `LicenseGate.kt` — caso `DEVICE_BLOCKED`**

Reemplazar:
```kotlin
        AppState.SUBSCRIPTION_EXPIRED -> RenewScreen(onActivated = { refresh() })
        AppState.NEEDS_CONNECTION -> NeedsConnectionScreen(onRetry = { refresh() })
    }
```
por:
```kotlin
        AppState.SUBSCRIPTION_EXPIRED -> RenewScreen(onActivated = { refresh() })
        AppState.NEEDS_CONNECTION -> NeedsConnectionScreen(onRetry = { refresh() })
        AppState.DEVICE_BLOCKED -> DeviceBlockedScreen(onRetry = { refresh() })
    }
```

- [ ] **Step 9: Borrar archivos Gumroad del app**

```bash
git rm app/src/main/kotlin/com/music/echo/license/GumroadClient.kt \
       app/src/main/kotlin/com/music/echo/license/GumroadVerify.kt \
       app/src/test/kotlin/iad1tya/echo/music/license/GumroadVerifyTest.kt
```

- [ ] **Step 10: Reescribir `LicenseLogicTest.kt`**

Reemplazar TODO el contenido por:

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

    @Test fun deviceMismatchBlocks() {
        assertEquals(
            AppState.DEVICE_BLOCKED,
            LicenseLogic.resolve(subState(now), VerifyOutcome.DEVICE_MISMATCH, now),
        )
    }

    @Test fun offlineWithinGraceEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day / 2),
        )
    }

    @Test fun offlineAtGraceLimitStillEnters() {
        assertEquals(
            AppState.SUBSCRIPTION_ACTIVE,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day),
        )
    }

    @Test fun offlineBeyondGraceNeedsConnection() {
        assertEquals(
            AppState.NEEDS_CONNECTION,
            LicenseLogic.resolve(subState(now), VerifyOutcome.UNVERIFIED, now + day + 1),
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

- [ ] **Step 11: Ejecutar la suite de licencias**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:testUniversalFossDebugUnitTest --tests "iad1tya.echo.music.license.*"`
Expected: BUILD SUCCESSFUL, todos PASS (LicenseLogicTest 15 + LicenseStatusParserTest 6).
Si main no compila por una referencia residual a Gumroad, busca:
`git grep -nE "GumroadVerify|GumroadClient"`
No debe quedar ninguna (los borramos). Si aparece, corrígela.

- [ ] **Step 12: Commit**

```bash
git add -A app/src/main/kotlin/com/music/echo/license app/src/test/kotlin/iad1tya/echo/music/license
git commit -m "feat(license): route verification through device-locking worker"
```
End the commit body with:
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>

---

### Task 5: Build APK + despliegue del Worker + verificación manual

**Files:** ninguno (validación).

- [ ] **Step 1: Compilar el APK debug**

Run: `export JAVA_HOME="/c/Program Files/Android/openjdk/jdk-21.0.8" && ./gradlew :app:assembleUniversalFossDebug`
Expected: BUILD SUCCESSFUL. APK en `app/build/outputs/apk/universalFoss/debug/`.

- [ ] **Step 2: Desplegar el Worker (usuario, dashboard)**

1. Cloudflare → Worker `round-math-d64e` → **Edit code** → pegar `licensing-worker/worker.js` → **Deploy**.
2. Confirmar binding KV `LICENSES` y variable `PRODUCT_ID` (Settings → Runtime).

- [ ] **Step 3: Probar el Worker con curl**

```bash
# clave falsa -> invalid
curl -s https://round-math-d64e.toberto4000.workers.dev/verify -H "content-type: application/json" -d '{"license_key":"FAKE","device_id":"dev-A"}'
# clave real, equipo A -> active
curl -s https://round-math-d64e.toberto4000.workers.dev/verify -H "content-type: application/json" -d '{"license_key":"REAL","device_id":"dev-A"}'
# misma clave, equipo B -> device_mismatch
curl -s https://round-math-d64e.toberto4000.workers.dev/verify -H "content-type: application/json" -d '{"license_key":"REAL","device_id":"dev-B"}'
# equipo A otra vez -> active
curl -s https://round-math-d64e.toberto4000.workers.dev/verify -H "content-type: application/json" -d '{"license_key":"REAL","device_id":"dev-A"}'
```
(Requiere una license key real de una compra de prueba.)

- [ ] **Step 4: Checklist de verificación en dispositivo**

- [ ] Activar suscripción con clave real en equipo 1 → entra.
- [ ] Misma clave en equipo 2 → pantalla "Suscripción en otro equipo" (DEVICE_BLOCKED).
- [ ] Reinstalar app en equipo 1 → sigue funcionando (mismo ANDROID_ID).
- [ ] Modo avión dentro de 1 día desde la última verificación → entra (gracia).
- [ ] Modo avión + saltar reloj 2 días → "Conéctate a internet".

---

## Self-Review

**Spec coverage:**
- Worker `/verify` + KV binding + auto-release 2d → Task 1 (`worker.js`). ✓
- device_id ANDROID_ID + fallback UUID → Task 3 (`DeviceId`). ✓
- App habla solo con el Worker → Task 3 (`LicenseBackendClient`) + Task 4 (LicenseManager usa backend). ✓
- LicenseStatus {ACTIVE,ENDED,INVALID_KEY,DEVICE_MISMATCH,NETWORK_ERROR} → Task 2. ✓
- AppState.DEVICE_BLOCKED + VerifyOutcome.DEVICE_MISMATCH + resolve → Task 4 Steps 2-3 + test. ✓
- Gracia offline 1 día → Task 4 Step 1 + tests ajustados. ✓
- DeviceBlockedScreen + rama device_mismatch en entry → Task 4 Steps 5,7,8. ✓
- Eliminar GumroadClient/GumroadVerify/GumroadVerifyTest del app → Task 4 Step 9. ✓
- Tests parser + LicenseLogic → Task 2 + Task 4 Step 10. ✓

**Placeholder scan:** sin TBD/TODO; todo el código completo. Las claves "REAL" en curl son marcadores explícitos para pruebas manuales del usuario (no código). ✓

**Type consistency:** `LicenseStatus` (5 valores) usado igual en LicenseStatusParser/LicenseBackendClient/LicenseManager/LicenseScreens. `LicenseLogic.VerifyOutcome` ahora {ACTIVE,ENDED,DEVICE_MISMATCH,UNVERIFIED}; `outcomeOf` mapea los 5 LicenseStatus a estos 4. `AppState.DEVICE_BLOCKED` usado en resolve/LicenseGate/DeviceBlockedScreen/test. `backend.verify(key, deviceId)` y `DeviceId.get(context)` consistentes. ✓
