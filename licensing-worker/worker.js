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
