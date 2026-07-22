// Aura Hi-Res Player — license + one-device-per-subscription Worker.
// Bindings: KV namespace "LICENSES". Env var: PRODUCT_ID (Gumroad product id).
// AÑADIDO: binding Workers AI "AI" para la ruta /ai (AI Playlists sin clave).
const GUMROAD_VERIFY = "https://api.gumroad.com/v2/licenses/verify";
const INACTIVITY_MS = 2 * 24 * 60 * 60 * 1000; // auto-release after 2 days idle
// Lista de modelos en orden de preferencia. Se prueba de arriba abajo y se usa el PRIMERO que responda:
// así, si Cloudflare deprecia uno (error 5028), el Worker cae solo al siguiente sin tocar nada.
const AI_MODELS = [
  "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
  "@cf/meta/llama-3.2-3b-instruct",
  "@cf/meta/llama-3.1-8b-instruct-fast",
  "@cf/mistralai/mistral-small-3.1-24b-instruct",
  "@cf/meta/llama-4-scout-17b-16e-instruct",
];
const AI_DAILY_LIMIT = 30;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST") return json({ status: "invalid" }, 404);

    if (url.pathname === "/demo") return handleDemo(request, env);
    if (url.pathname === "/ai") return handleAi(request, env);
    if (url.pathname !== "/verify") return json({ status: "invalid" }, 404);

    let body;
    try { body = await request.json(); } catch (e) { return json({ status: "invalid" }, 400); }
    const licenseKey = body && body.license_key;
    const deviceId = body && body.device_id;
    if (!licenseKey || !deviceId) return json({ status: "invalid" }, 400);

    const masterKeys = (env.MASTER_KEYS || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (masterKeys.includes(licenseKey)) return json({ status: "active" });

    const gum = await verifyGumroad(env.PRODUCT_ID, licenseKey);
    if (gum === "invalid") return json({ status: "invalid" });
    if (gum === "ended") return json({ status: "ended" });
    if (gum === "error") return json({ status: "error" });

    try {
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
    } catch (e) {
      return json({ status: "error" });
    }
  },
};

async function handleAi(request, env) {
  // Rate limit por IP y día: clave KV `ai:<ip>:<yyyymmdd>` (no colisiona con licencias/demo).
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const day = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const rlKey = `ai:${ip}:${day}`;
  const used = parseInt((await env.LICENSES.get(rlKey)) || "0", 10);
  if (used >= AI_DAILY_LIMIT) return json({ error: { message: "Daily AI limit reached, try again tomorrow" } }, 429);

  let body;
  try { body = await request.json(); } catch (e) { return json({ error: { message: "Invalid JSON body" } }, 400); }
  const messages = body && body.messages;
  if (!Array.isArray(messages) || messages.length === 0) return json({ error: { message: "messages[] is required" } }, 400);

  // Prueba cada modelo en orden; usa el primero que responda. Un modelo deprecado (5028) o caído lanza
  // y se pasa al siguiente. Solo si TODOS fallan devolvemos 500 (y la app cae a su fallback keyless).
  let result = null;
  let lastErr = "no model available";
  for (const model of AI_MODELS) {
    try {
      const r = await env.AI.run(model, { messages, max_tokens: body.max_tokens || 2048 });
      if (r && typeof r.response === "string") { result = r; break; }
    } catch (e) {
      lastErr = (e && e.message) ? e.message : String(e);
    }
  }
  if (!result) return json({ error: { message: "Workers AI error: " + lastErr } }, 500);

  await env.LICENSES.put(rlKey, String(used + 1), { expirationTtl: 172800 });
  return json({ choices: [{ message: { role: "assistant", content: result.response || "" } }] });
}

async function handleDemo(request, env) {
  let body;
  try { body = await request.json(); } catch (e) { return json({ status: "invalid" }, 400); }
  const deviceId = body && body.device_id;
  if (!deviceId) return json({ status: "invalid" }, 400);
  const start = body.start === true;
  try {
    const now = Date.now();
    const k = "demo:" + deviceId;
    const existing = await env.LICENSES.get(k);
    let started;
    if (existing) {
      started = parseInt(existing, 10);
      if (!Number.isFinite(started)) started = now;
    } else if (start) {
      started = now;
      await env.LICENSES.put(k, String(started));
    } else {
      return json({ status: "none", server_time: now });
    }
    return json({ status: "ok", demo_started_at: started, server_time: now });
  } catch (e) {
    return json({ status: "error" });
  }
}

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

    // Access follows the PAID period, automatically:
    //  - A cancellation alone does NOT end access — the subscriber keeps the month they already paid for.
    //  - "ended" only once subscription_ended_at / subscription_failed_at is in the PAST. Then the app
    //    cuts on its next check and resumes automatically when Gumroad reports a fresh active charge.
    const now = Date.now();
    const isPast = (t) => {
      if (!t) return false;
      const ms = typeof t === "number" ? (t < 1e12 ? t * 1000 : t) : Date.parse(t);
      return Number.isFinite(ms) ? ms <= now : true;
    };
    const ended = isPast(p.subscription_ended_at) || isPast(p.subscription_failed_at);
    return ended ? "ended" : "active";
  } catch (e) {
    return "error";
  }
}
