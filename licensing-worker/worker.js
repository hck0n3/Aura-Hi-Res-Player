// Aura Hi-Res Player — license + one-device-per-subscription Worker.
// Bindings: KV namespace "LICENSES". Env var: PRODUCT_ID (Gumroad product id).
const GUMROAD_VERIFY = "https://api.gumroad.com/v2/licenses/verify";
const INACTIVITY_MS = 2 * 24 * 60 * 60 * 1000; // auto-release after 2 days idle

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST") return json({ status: "invalid" }, 404);

    // Server-authoritative demo: the first demo start per device_id (ANDROID_ID) is recorded in KV
    // and returned on every later call, so clearing app data / reinstalling can't reset the 3-day
    // demo on the same device.
    if (url.pathname === "/demo") return handleDemo(request, env);

    if (url.pathname !== "/verify") return json({ status: "invalid" }, 404);

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
      // KV unavailable / corrupted entry -> degrade gracefully so the app uses offline grace.
      return json({ status: "error" });
    }
  },
};

async function handleDemo(request, env) {
  let body;
  try {
    body = await request.json();
  } catch (e) {
    return json({ status: "invalid" }, 400);
  }
  const deviceId = body && body.device_id;
  if (!deviceId) return json({ status: "invalid" }, 400);
  const start = body.start === true; // only "Probar gratis" starts a demo; opening the app just checks
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
      // Persist permanently so clearing app data / reinstalling can never reset the demo.
      await env.LICENSES.put(k, String(started));
    } else {
      return json({ status: "none", server_time: now }); // no demo on record, and not starting one
    }
    return json({ status: "ok", demo_started_at: started, server_time: now });
  } catch (e) {
    // KV down -> let the app fall back to its local/offline handling.
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
    const ended =
      p.subscription_cancelled_at || p.subscription_failed_at || p.subscription_ended_at;
    return ended ? "ended" : "active";
  } catch (e) {
    return "error";
  }
}
