# JR MUSIC PRO — Sistema de licencias por suscripción (Gumroad)

**Fecha:** 2026-06-15
**Estado:** Aprobado (diseño)
**Plataforma de cobro:** Gumroad — producto membership `JR-MUSIC-PRO-OFFICIAL ANDROID`

---

## 1. Objetivo

Reemplazar el sistema de activación por serial actual por un modelo de **suscripción mensual** con
**demo gratuito de 3 días**. La app arranca siempre en demo (sin pedir nada), y el usuario activa la
suscripción pegando una **license key** de Gumroad. La suscripción se **revalida online** contra
Gumroad, con re-chequeo forzado **cada 3 días** por seguridad.

Decisiones cerradas (con el usuario):

- **Arquitectura: Opción A (sin servidor).** La app llama directo a `api.gumroad.com/v2/licenses/verify`.
- **Demo: app completa durante 3 días.** Sin recortes de funciones.
- **Sin licencia perpetua.** Se elimina toda la lógica perpetua del sistema actual.
- **Revalidación online cada 3 días.** Gracia offline = 3 días exactos.
- **Pantalla de bienvenida con 2 botones** (sin perpetua): "Ya me suscribí" / "Probar gratis (3 días)".

Datos de Gumroad ya confirmados:

- `product_id = wcPehkIWHRbPKR4_hZLdJQ==`
- Membership recurrente mensual, **$10/mes**, publicado.
- Endpoint verify operativo (probado con clave falsa).
- Pendiente confirmar end-to-end con una license key real (compra de prueba).

---

## 2. Qué se elimina del sistema actual

El sistema actual (`LicenseLogic`, `LicenseManager`, `ActivationScreen`) implementa un modelo de
serial: demo gateado por clave demo (hash) + clave perpetua (hash). Se reescribe:

- **Eliminar** `PERPETUAL_KEY_HASH` y toda la rama perpetua.
- **Eliminar** `DEMO_KEY_HASH` y la activación de demo por clave. El demo pasa a ser **sin clave**
  (se inicia con un botón "Probar gratis").
- **Conservar** la guardia anti-retroceso de reloj (`lastSeenAt` / `CLOCK_ROLLBACK_TOLERANCE_MS`)
  para que el contador de 3 días del demo no se reinicie cambiando la hora del dispositivo.
- **Conservar** la persistencia en `SharedPreferences` (`jr_license`), ampliando campos.

---

## 3. Los estados de la app

Calculados al abrir (y tras activar). La app está siempre en exactamente uno:

| Estado | Condición | Resultado |
|---|---|---|
| `DEMO` | demo iniciado y `now < demoStartedAt + 3 días` | Entra completa |
| `DEMO_EXPIRED` | demo iniciado, expirado, sin clave de suscripción | Pantalla de activación |
| `SUBSCRIPTION_ACTIVE` | Gumroad confirma activa, **o** dentro de gracia offline (≤3 días desde la última verificación OK) | Entra completa |
| `SUBSCRIPTION_EXPIRED` | hay clave, Gumroad dice terminada | Pantalla "renueva tu suscripción" |
| `NEEDS_CONNECTION` | hay clave, `>3 días` sin verificar online y sin internet ahora | Pantalla de bloqueo "conéctate a internet" |
| `FIRST_RUN` | nunca inició demo y sin clave | Pantalla de bienvenida (2 botones) |

`SUBSCRIPTION_ACTIVE` por Gumroad ⇒ **active** únicamente cuando los tres campos
`subscription_cancelled_at`, `subscription_failed_at`, `subscription_ended_at` son `null`.
Si **cualquiera** tiene fecha ⇒ terminada.

---

## 4. Flujo al abrir (decisión)

Función suspend (corre en coroutine, con estado de carga / spinner mientras consulta Gumroad):

```
si existe subscriptionKey:
    si hay internet:
        r = gumroadVerify(productId, key)
        Active   -> lastVerifiedAt = now; guardar; SUBSCRIPTION_ACTIVE
        Ended    -> SUBSCRIPTION_EXPIRED
        NetError -> caer a rama offline
    rama offline (sin internet o NetError):
        now - lastVerifiedAt <= 3 días -> SUBSCRIPTION_ACTIVE (gracia)
        en caso contrario               -> NEEDS_CONNECTION
si NO existe subscriptionKey:
    demo iniciado y activo (< 3 días) -> DEMO
    demo iniciado y expirado          -> DEMO_EXPIRED
    nunca iniciado                    -> FIRST_RUN
```

Notas:

- La verificación online ocurre **en cada apertura** (cumple el requisito de seguridad). El re-chequeo
  forzado "cada 3 días" es el límite de la gracia: si pasan 3 días sin una verificación exitosa, se bloquea.
- Si la verificación online da `Active`, se actualiza `lastVerifiedAt = now`. Mientras haya internet,
  la gracia se renueva en cada apertura; el límite de 3 días solo muerde en uso prolongado sin conexión.
- Cuando el cliente renueva el pago, Gumroad devuelve los 3 campos a `null` automáticamente: en la
  siguiente apertura con internet la app pasa de `SUBSCRIPTION_EXPIRED` a `SUBSCRIPTION_ACTIVE` sola.

---

## 5. Componentes

### 5.1 `LicenseLogic` (puro, sin Android, unit-testable) — reescritura

Máquina de estados y reglas de tiempo, sin I/O. Recibe el resultado de la verificación como parámetro
para mantenerse puro y testeable.

```kotlin
object LicenseLogic {
    const val DEMO_DURATION_MS = 3L * 24 * 60 * 60 * 1000
    const val OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1000
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 5L * 60 * 1000

    data class State(
        val subscriptionKey: String? = null,
        val lastVerifiedAt: Long = 0L,
        val demoStartedAt: Long = 0L,
        val lastSeenAt: Long = 0L,
    )

    enum class VerifyOutcome { ACTIVE, ENDED, NETWORK_ERROR, NOT_CHECKED }

    enum class AppState {
        DEMO, DEMO_EXPIRED, SUBSCRIPTION_ACTIVE, SUBSCRIPTION_EXPIRED, NEEDS_CONNECTION, FIRST_RUN
    }

    fun touch(state: State, now: Long): State
    fun startDemo(state: State, now: Long): State          // setea demoStartedAt si no existe
    fun isDemoActive(state: State, now: Long): Boolean
    fun demoDaysLeft(state: State, now: Long): Int
    fun withVerifiedNow(state: State, now: Long): State     // lastVerifiedAt = now
    fun withSubscriptionKey(state: State, key: String): State

    // Decisión final dado el resultado (ya consultado fuera) de la verificación online.
    fun resolve(state: State, outcome: VerifyOutcome, online: Boolean, now: Long): AppState
}
```

`resolve` implementa exactamente la tabla de la Sección 3 / el flujo de la Sección 4.

### 5.2 `GumroadClient` (nuevo, ktor)

```kotlin
class GumroadClient(private val http: HttpClient) {
    suspend fun verify(productId: String, licenseKey: String): VerifyResult
}

sealed interface VerifyResult {
    data object Active : VerifyResult                 // success && 3 campos null
    data object Ended : VerifyResult                  // success && algún campo con fecha
    data object InvalidKey : VerifyResult             // success=false ("license does not exist")
    data object NetworkError : VerifyResult           // timeout / sin conexión / 5xx
}
```

- `POST https://api.gumroad.com/v2/licenses/verify`
- Body form-urlencoded: `product_id`, `license_key`, `increment_uses_count=false`.
- Parsea `purchase.subscription_cancelled_at`, `subscription_failed_at`, `subscription_ended_at`.
- `InvalidKey` se trata, a nivel de estado, como sin suscripción válida → cae a demo/activación según
  corresponda (no se guarda la clave inválida).
- Usa el `HttpClient` ktor ya presente en el módulo `app` (engine okhttp + content negotiation JSON).
- Timeout corto (ej. 10 s) para no colgar el arranque; en timeout ⇒ `NetworkError` ⇒ rama offline.

### 5.3 `LicenseManager` (reescritura) — orquestador Android

- Persiste `State` en `SharedPreferences("jr_license")`: `subscription_key`, `last_verified_at`,
  `demo_started_at`, `last_seen_at`.
- `suspend fun evaluate(context): AppState` — carga estado, hace `touch`, decide si hay internet,
  llama a `GumroadClient.verify` si hay clave + internet, persiste `lastVerifiedAt` en `ACTIVE`,
  y devuelve `AppState` vía `LicenseLogic.resolve`.
- `fun startDemo(context)` — inicia el contador demo (botón "Probar gratis").
- `suspend fun activateSubscription(context, key): VerifyResult` — verifica online; si `Active`,
  guarda la clave + `lastVerifiedAt`; devuelve el resultado para la UI.
- `fun demoDaysLeft(context): Int`.
- Detección de internet: `ConnectivityManager` (helper simple `isOnline(context)`).

### 5.4 UI (Compose, español, dark, branding actual)

Reusa el estilo de `ActivationScreen` (gradientes, logo `jr_logo`). Pantallas:

- **`WelcomeScreen`** — logo + 2 botones: "Ya me suscribí" → `SubscriptionEntryScreen`;
  "Probar gratis (3 días)" → `startDemo()` y entra. Solo en `FIRST_RUN`.
- **`SubscriptionEntryScreen`** — campo para pegar la license key + botón "Activar". Muestra
  spinner mientras verifica. Resultados: éxito (entra), clave inválida, suscripción terminada,
  error de red. Enlace a la página de Gumroad para suscribirse.
- **`RenewScreen`** — estado `SUBSCRIPTION_EXPIRED`: mensaje "tu suscripción venció", botón para
  abrir Gumroad y botón "Ya pagué, reintentar" (re-verifica).
- **`NeedsConnectionScreen`** — estado `NEEDS_CONNECTION`: "conéctate a internet para verificar tu
  suscripción", botón "Reintentar".
- **`ActivationScreen`** (reutilizada/renombrada) — estado `DEMO_EXPIRED`: ofrece "Ya me suscribí"
  (→ entry). Sin opción perpetua.

Un único contenedor `LicenseGate` que, según `AppState`, muestra la pantalla correcta o el contenido
de la app. Maneja el estado de carga inicial (spinner) mientras `evaluate` corre.

### 5.5 `MainActivity` — integración

Sustituir el gate binario actual:

```kotlin
setContent {
    var isLicensed by remember { mutableStateOf(LicenseManager.isLicensed(this@MainActivity)) }
    if (!isLicensed) ActivationScreen(onActivated = { isLicensed = true })
    else echomusicApp(...)
}
```

por un gate de estado con coroutine:

```kotlin
setContent {
    LicenseGate(
        appContent = { echomusicApp(...) }
    )
    // LicenseGate: LaunchedEffect -> evaluate(); muestra Loading / Welcome / Entry /
    // Renew / NeedsConnection / DemoExpired / appContent según AppState.
}
```

`product_id` se cablea como constante en el cliente: `wcPehkIWHRbPKR4_hZLdJQ==`.

---

## 6. Modelo de datos (SharedPreferences `jr_license`)

| Clave | Tipo | Uso |
|---|---|---|
| `subscription_key` | String? | License key de Gumroad guardada tras activar |
| `last_verified_at` | Long | Epoch ms de la última verificación online OK (gracia) |
| `demo_started_at` | Long | Epoch ms de inicio del demo (0 = no iniciado) |
| `last_seen_at` | Long | Mayor `now` visto, guardia anti-retroceso de reloj |

Migración: las claves viejas (`perpetual`, `demo_used`) se ignoran/limpian. Un usuario que tenía el
serial viejo simplemente verá la bienvenida (acepta como comportamiento v1).

---

## 7. Manejo de errores

- **Sin internet al abrir con clave:** rama offline → gracia 3 días o `NEEDS_CONNECTION`.
- **Timeout / 5xx Gumroad:** `NetworkError` → rama offline (no bloquear a un cliente que pagó por
  una caída de Gumroad, dentro de la gracia).
- **Clave inválida:** no se guarda; UI muestra error; el estado cae a demo/activación.
- **Reloj atrasado:** `lastSeenAt` evita reiniciar el demo o estirar la gracia hacia atrás.
- **Crash de red en arranque:** capturado; degradar a rama offline, nunca crashear el arranque.

---

## 8. Testing

- **`LicenseLogicTest`** (JVM puro, reescrito): demo activo/expirado, `demoDaysLeft`, gracia offline
  (límite exacto 3 días), `resolve` para los 6 estados con cada `VerifyOutcome` × online/offline,
  guardia anti-retroceso de reloj.
- **`GumroadClient`**: test con `MockEngine` de ktor — respuestas Active (3 null), Ended (un campo con
  fecha), InvalidKey (`success:false`), NetworkError (excepción/timeout). Parseo robusto.
- Sin test instrumentado de UI en v1 (manual): bienvenida, activar con clave real, expiración, renovación.

---

## 9. Limitación honesta (seguridad)

Verificación 100% del lado cliente (Opción A): un APK modificado podría saltarse el gate. El re-chequeo
online cada 3 días y la ofuscación normal de release suben la barrera, pero el blindaje real requeriría
Opción B (backend que firme respuestas y registre dispositivos). Aceptable para v1; migrable después.

---

## 10. Fuera de alcance (v1)

- Backend propio (Opción B), firma de respuestas, registro por dispositivo.
- Anti-abuso del demo (reinstalar/borrar datos reinicia el demo — aceptado en v1).
- Licencia perpetua (eliminada por decisión del usuario).
- Restauración de compra cross-device más allá de re-pegar la license key.
