# Candado de dispositivo (1 suscripción = 1 equipo) — Diseño

**Fecha:** 2026-06-15
**Estado:** Aprobado (diseño)
**Depende de:** `2026-06-15-jrmusicpro-subscription-licensing-design.md` (suscripción Gumroad ya implementada)

---

## 1. Objetivo

Hacer que cada suscripción mensual de Gumroad funcione en **un solo equipo a la vez**. Compartir una
license key entre varios equipos debe quedar bloqueado. Esto requiere una autoridad compartida que
sepa qué dispositivo reclamó cada clave → un **backend ligero** (Opción B del spec original).

Decisiones cerradas con el usuario:

- **Backend: Cloudflare Worker + KV** (plan gratuito).
- **Identificador de equipo:** `Settings.Secure.ANDROID_ID` (sobrevive reinstalaciones en el mismo
  equipo; cambia en otro equipo / factory reset). Fallback: UUID aleatorio persistido si ANDROID_ID
  es null/blank.
- **La app habla SOLO con el Worker** para el chequeo de licencia. El Worker es quien llama a Gumroad
  (autoridad del lado servidor). La app ya **no** llama a Gumroad directamente.
- **Auto-release por inactividad = 2 días.** Si el equipo dueño no verifica en 2 días, otro equipo
  puede reclamar la clave automáticamente. Sin endpoint admin.
- **Gracia offline = 1 día** (se baja de 3d a 1d). Regla obligatoria: `auto_release > gracia_offline`,
  si no, un dueño legítimamente offline sería robado por otro equipo dentro de su propia gracia.
- **Demo sigue siendo 3 días** (sin cambios).

Datos de infraestructura ya provistos por el usuario:

- Worker URL: `https://round-math-d64e.toberto4000.workers.dev` (endpoint `/verify`).
- KV namespace binding: `LICENSES`.
- Variable de entorno del Worker: `PRODUCT_ID = wcPehkIWHRbPKR4_hZLdJQ==`.

---

## 2. El Worker (Cloudflare)

Un único archivo `worker.js` (más `README` de despliegue). KV guarda, por license key, el equipo
dueño y la última vez que se le vio:

```
KV key   = <license_key>
KV value = { "device_id": "<android_id>", "last_seen": <epoch_ms> }
```

### Endpoint `POST /verify`
Body JSON: `{ "license_key": "...", "device_id": "..." }`

```
1. Validar body. Faltan campos -> 400 { status: "invalid" }.
2. Verificar con Gumroad:
     POST https://api.gumroad.com/v2/licenses/verify
       product_id = env.PRODUCT_ID, license_key, increment_uses_count = false
   - success=false                         -> { status: "invalid" }
   - success=true y purchase sin/con-null en los 3 campos
       subscription_cancelled_at/_failed_at/_ended_at:
       todos null/ausentes                 -> activa
       alguno con fecha                    -> { status: "ended" }
3. Si NO activa: devolver invalid/ended (no tocar el binding).
4. Si activa:
     binding = KV.get(license_key)
     - no existe                           -> KV.put {device_id, now}; { status: "active" }
     - binding.device_id == device_id      -> KV.put {device_id, now}; { status: "active" }
     - binding.device_id != device_id:
         now - binding.last_seen > 2 días  -> KV.put {device_id, now}; { status: "active" }  (reasignado)
         si no                             -> { status: "device_mismatch" }
```

Respuesta siempre JSON `{ "status": "active" | "ended" | "invalid" | "device_mismatch" }`, HTTP 200
(salvo 400 por body inválido). Cualquier otra ruta/método → 404.

Constantes del Worker: `INACTIVITY_MS = 2 * 24 * 60 * 60 * 1000`. Sin secreto de Gumroad (el endpoint
verify es público; basta `PRODUCT_ID`). Sin endpoint admin (auto-release cubre el traslado de equipo).

La regla "3 campos null = activa" se reimplementa en JS (equivale a lo que hacía `GumroadVerify` en
el app, que se elimina).

### Despliegue
El usuario ya creó el Worker, el KV binding `LICENSES` y la variable `PRODUCT_ID`. Implementación =
pegar `worker.js` en el editor del Worker (Edit code) y Deploy. `wrangler.toml` se incluye en el repo
para quien prefiera CLI, pero el camino oficial es el editor del dashboard.

---

## 3. Cambios en la app

### 3.1 `DeviceId` (nuevo, `license/DeviceId.kt`)
`fun get(context): String` — devuelve `Settings.Secure.ANDROID_ID`; si es null/blank/"9774d56d682e549c"
(el bug conocido de algunos emuladores) genera un UUID y lo persiste en las prefs `jr_license`
(`device_id`), devolviéndolo en llamadas siguientes.

### 3.2 `LicenseBackendClient` (nuevo, `license/LicenseBackendClient.kt`, ktor)
```kotlin
class LicenseBackendClient(private val http: HttpClient = HttpClient(OkHttp)) {
    suspend fun verify(licenseKey: String, deviceId: String): LicenseStatus
}
enum class LicenseStatus { ACTIVE, ENDED, INVALID_KEY, DEVICE_MISMATCH, NETWORK_ERROR }
```
- `POST https://round-math-d64e.toberto4000.workers.dev/verify` con JSON `{license_key, device_id}`.
- Parsea `{status}` → enum. Cualquier excepción/timeout/HTTP!=200 → `NETWORK_ERROR`.
- Incluye un pequeño parser JSON puro `LicenseStatusParser.parse(body): LicenseStatus` (unit-testable).

### 3.3 `LicenseLogic` (modificar)
- `OFFLINE_GRACE_MS` pasa de 3 días a **1 día**. (`DEMO_DURATION_MS` sigue 3 días.)
- `VerifyOutcome` añade `DEVICE_MISMATCH`.
- `AppState` añade `DEVICE_BLOCKED`.
- `resolve`: con `subscriptionKey != null` y outcome `DEVICE_MISMATCH` → `DEVICE_BLOCKED`.
  El resto igual (ACTIVE→SUBSCRIPTION_ACTIVE, ENDED→SUBSCRIPTION_EXPIRED, UNVERIFIED→grace/NEEDS_CONNECTION).

### 3.4 `LicenseManager` (modificar)
- Reemplaza `GumroadClient` por `LicenseBackendClient`.
- En `evaluate` / `activateSubscription` / `reverify`: obtiene `deviceId = DeviceId.get(context)` y llama
  `backend.verify(key, deviceId)`. Mapea `LicenseStatus` → `VerifyOutcome`:
  `ACTIVE→ACTIVE` (guarda lastVerifiedAt), `ENDED/INVALID_KEY→ENDED`, `DEVICE_MISMATCH→DEVICE_MISMATCH`,
  `NETWORK_ERROR→UNVERIFIED`.
- `activateSubscription` devuelve el `LicenseStatus` crudo para que la pantalla muestre el mensaje
  correcto (incluido `DEVICE_MISMATCH`). Solo guarda la clave si `ACTIVE`.

### 3.5 UI
- Nueva `DeviceBlockedScreen(onRetry)`: "Esta suscripción ya está activa en otro equipo. Suscríbete
  para este equipo, o espera unos días si dejaste de usar el otro." + botón Reintentar.
- `SubscriptionEntryScreen`: añade rama para `DEVICE_MISMATCH` ("Esta clave ya está en uso en otro
  equipo.").
- `LicenseGate`: nuevo caso `AppState.DEVICE_BLOCKED → DeviceBlockedScreen`.

### 3.6 Eliminar del app
- `license/GumroadClient.kt` y `license/GumroadVerify.kt` (la lógica Gumroad vive ahora en el Worker).
- `GumroadVerifyTest.kt` se elimina (su cobertura se traslada a las pruebas manuales del Worker con curl).

---

## 4. Flujo y casos

- **Activar en equipo nuevo (clave sin usar):** Worker reclama → ACTIVE → entra.
- **Misma clave en 2º equipo (1º activo):** Worker → DEVICE_MISMATCH → DEVICE_BLOCKED.
- **Reinstalar en el mismo equipo:** mismo ANDROID_ID → ACTIVE.
- **Cambiar de equipo (dejé de usar el viejo):** tras 2 días sin que el viejo verifique, el nuevo
  reclama solo.
- **Sin internet:** dentro de 1 día desde la última verificación OK → entra (gracia); pasado 1 día →
  NEEDS_CONNECTION. (1d < 2d release ⇒ el dueño se bloquea por gracia antes de que se libere a otros.)
- **Worker caído / error de red:** `NETWORK_ERROR` → rama offline (gracia). No bloquea por una caída.

---

## 5. Testing

- `LicenseLogicTest`: añadir `deviceMismatchBlocks` (subscriptionKey + DEVICE_MISMATCH → DEVICE_BLOCKED)
  y ajustar los tests de gracia al nuevo límite de **1 día**.
- `LicenseStatusParser`: tests JUnit puros (active/ended/invalid/device_mismatch/cuerpo basura→NETWORK_ERROR).
- Worker: pruebas manuales con `curl` (clave falsa → invalid; clave real 1er equipo → active; misma
  clave 2º device_id → device_mismatch; repetir con device_id original → active). Este repo Android no
  tiene toolchain JS, así que no hay unit tests del Worker en v1.

---

## 6. Limitaciones honestas (seguridad)

- Verificación del lado cliente: un APK modificado puede saltarse el gate (no se firma la respuesta del
  Worker en v1). Mitigación futura: firmar la respuesta del Worker + Play Integrity.
- Auto-release 2d permite rotar entre equipos cada 2 días (compromiso aceptado por "reclamo rápido").
- ANDROID_ID puede repetirse en equipos clonados/rooteados raros; aceptable para v1.
- La license key del usuario viaja a un Worker propio (no a terceros) y se guarda en prefs privadas.

---

## 7. Fuera de alcance (v1)

- Firma de respuestas del Worker / Play Integrity.
- Endpoint admin de liberación manual (se usa auto-release).
- Unit tests del Worker en JS.
- Panel para ver/gestionar bindings (se usa el dashboard de Cloudflare / KV directamente).
