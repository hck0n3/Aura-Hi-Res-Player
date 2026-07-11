# Aura Worker — ruta `/recognize` (relay de reconocimiento Shazam, self-healing)

Este documento es para pegar **a mano** en el dashboard de Cloudflare del Worker existente
`https://round-math-d64e.toberto4000.workers.dev`. **NO tocar las rutas `/verify` ni `/demo`**: el flujo
de licencias/suscripción debe quedar EXACTAMENTE igual.

## Qué resuelve

El reconocimiento de canciones tenía un **único punto de fallo**: la app manda la firma de audio
directamente a `amp.shazam.com`. Si Shazam rota ese endpoint, geo-bloquea la IP del móvil, o hay un
fallo TLS puntual en el dispositivo, el reconocimiento se cae **sin fallback**.

La app (rama `feat/0693-recog`) ahora tiene una **cascada de proveedores**
(`ShazamConfig.providerOrder`, por defecto `["direct", "relay"]`):

1. **direct** — el POST keyless actual a `amp.shazam.com` (sin cambios).
2. **relay** — un **PROBE** a `POST /recognize` de este Worker, que reenvía la MISMA firma a
   `amp.shazam.com` desde la salida (egress) de Cloudflare. Cura una rotación/bloqueo que golpea al
   móvil directamente.

Mientras la ruta `/recognize` NO esté desplegada, el probe recibe un **404 y falla rápido** (igual que
el probe de `/ai`): la app cae al comportamiento actual sin gastar reintentos. Desplegar la ruta solo
**añade** un camino de recuperación; no cambia nada más.

---

## (a) Contrato de la ruta

La app llama a `POST /recognize` con **el body Shazam completo** (el mismo que manda al endpoint
directo), sin header `Authorization`:

```json
{
  "geolocation": { "altitude": 300.0, "latitude": 12.3, "longitude": -45.6 },
  "signature":   { "samplems": 12000, "timestamp": 1731000000, "uri": "data:audio/vnd.shazam.sig;base64,..." },
  "timestamp":   1731000000,
  "timezone":    "Europe/Madrid"
}
```

El Worker debe:
1. Generar dos UUID (`uuid1` en MAYÚSCULAS, `uuid2` normal) para la URL de tag.
2. Reenviar el body **verbatim** a `https://amp.shazam.com/discovery/v5/en/US/android/-/tag/<uuid1>/<uuid2>`
   con los mismos query params y un `User-Agent` tipo Dalvik.
3. Devolver la **respuesta JSON de Shazam tal cual** (la app la parsea con el mismo modelo que el
   camino directo). Si Shazam devuelve 404 (sin match), reenviar ese 404.

## (b) Código a añadir al Worker

Añade el routing dentro del `fetch` existente (junto a `/verify` y `/demo`, **sin** modificarlas) y la
función `handleRecognize` al final del archivo:

```js
// ─────────────────────────────────────────────────────────────────────────────
// NUEVO: dentro del fetch handler existente, junto al routing actual.
// Las rutas /verify y /demo quedan EXACTAMENTE igual.
// ─────────────────────────────────────────────────────────────────────────────
//   const url = new URL(request.url);
//   if (url.pathname === '/recognize') {
//     return handleRecognize(request, env);
//   }
//   ... routing existente de /verify y /demo sin cambios ...

// ─────────────────────────────────────────────────────────────────────────────
// NUEVO: handler completo de /recognize (pegar al final del worker.js).
// No requiere ningún binding nuevo. Rate limit opcional reutilizando el KV
// existente (`LICENSES`); si tu binding se llama distinto, ajústalo o quítalo.
// ─────────────────────────────────────────────────────────────────────────────
const SHAZAM_HOST = 'amp.shazam.com';
const RECOGNIZE_DAILY_LIMIT = 60;
const SHAZAM_USER_AGENTS = [
  'Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)',
  'Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)',
  'Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)',
];

function uuid() {
  return crypto.randomUUID();
}

async function handleRecognize(request, env) {
  if (request.method !== 'POST') {
    return recogError('Method Not Allowed', 405);
  }

  // (Opcional) rate limit por IP y día natural: clave KV `recog:<ip>:<yyyymmdd>`.
  // Si no quieres límite, borra este bloque entero.
  try {
    const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
    const day = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    const rlKey = `recog:${ip}:${day}`;
    const used = parseInt((await env.LICENSES.get(rlKey)) || '0', 10);
    if (used >= RECOGNIZE_DAILY_LIMIT) {
      return recogError('Daily recognition limit reached, try again tomorrow', 429);
    }
    await env.LICENSES.put(rlKey, String(used + 1), { expirationTtl: 172800 });
  } catch (_) {
    // Si el KV no está disponible, seguimos sin límite (mejor reconocer que fallar).
  }

  let body;
  try {
    body = await request.text(); // reenviamos el body verbatim
    JSON.parse(body);            // validación mínima: debe ser JSON
  } catch {
    return recogError('Invalid JSON body', 400);
  }

  const uuid1 = uuid().toUpperCase();
  const uuid2 = uuid();
  const target =
    `https://${SHAZAM_HOST}/discovery/v5/en/US/android/-/tag/${uuid1}/${uuid2}` +
    `?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3`;

  const ua = SHAZAM_USER_AGENTS[Math.floor(Math.random() * SHAZAM_USER_AGENTS.length)];

  let upstream;
  try {
    upstream = await fetch(target, {
      method: 'POST',
      headers: {
        'User-Agent': ua,
        'Content-Language': 'en_US',
        'Content-Type': 'application/json',
      },
      body,
    });
  } catch (e) {
    // 502 → la app trata el relay como no disponible y sigue con su cascada.
    return recogError(`Shazam upstream error: ${e.message}`, 502);
  }

  // Reenviamos el status y el cuerpo de Shazam TAL CUAL (incluido un 404 sin match).
  const text = await upstream.text();
  return new Response(text, {
    status: upstream.status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function recogError(message, status) {
  return new Response(JSON.stringify({ error: { message } }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
```

Notas:
- **CORS no hace falta**: la app es nativa (Ktor/OkHttp), no un navegador.
- No hay claves ni secretos: es un proxy transparente al mismo endpoint keyless que ya usaba la app.
- El relay **no** toca la firma ni el formato del body — solo cambia el punto de salida a la red.

## (c) Pasos en el dashboard de Cloudflare

1. Dashboard → Workers & Pages → worker `round-math-d64e` → **Edit code**.
2. Pegar el `if (url.pathname === '/recognize')` dentro del `fetch` existente y
   `handleRecognize`/`recogError`/constantes al final del archivo.
3. (El KV `LICENSES` ya está bindeado por el flujo de licencias; el rate limit es opcional.)
4. **Deploy**.
5. Probar (un body de firma real; con una firma inválida Shazam responderá 4xx, es normal):
   ```
   curl -s -X POST https://round-math-d64e.toberto4000.workers.dev/recognize \
     -H "Content-Type: application/json" \
     -d '{"geolocation":{"altitude":300,"latitude":12,"longitude":-45},"signature":{"samplems":1000,"timestamp":1,"uri":"data:audio/vnd.shazam.sig;base64,AA=="},"timestamp":1,"timezone":"Europe/Madrid"}'
   ```
   Debe devolver JSON de Shazam (o un 404 "sin match"), no un 404 de "ruta no encontrada".

## (d) Config self-healing (opcional, complementaria)

Además del relay, la app lee un JSON opcional
`https://raw.githubusercontent.com/hck0n3/Aura-Hi-Res-Player/main/shazam_recognition_config.json`
(ver `RemoteRecognitionConfig`) que permite **rotar host / path / User-Agents / orden de proveedores**
sin actualizar la app. Ejemplo (todos los campos opcionales):

```json
{
  "enabled": true,
  "host": "amp.shazam.com",
  "pathTemplate": "/discovery/v5/en/US/android/-/tag/{uuid1}/{uuid2}",
  "userAgents": ["Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)"],
  "relayUrl": "https://round-math-d64e.toberto4000.workers.dev/recognize",
  "providerOrder": ["direct", "relay"]
}
```

Para **forzar el relay como primario** (p. ej. si Shazam bloquea el acceso directo), publica
`"providerOrder": ["relay", "direct"]`. `pathTemplate` DEBE conservar `{uuid1}` y `{uuid2}`.

## (e) Mientras no esté desplegado

Nada se rompe: si `/recognize` devuelve 404/405 o falla, la app trata el relay como no disponible y
sigue con el reconocimiento directo actual. Desplegar la ruta solo **añade** un camino de recuperación
para cuando el acceso directo a Shazam falle.
