# JR MUSIC PRO — License Worker

Cloudflare Worker that verifies Gumroad subscriptions and enforces one device per subscription.

## Deploy (dashboard)
1. Cloudflare dashboard → Workers & Pages → open the `round-math-d64e` Worker.
2. **Edit code** → replace the contents with `worker.js` → **Deploy**.
3. Ensure these are configured (Settings → Runtime):
   - KV namespace binding named `LICENSES`.
   - Plaintext variable `PRODUCT_ID = wcPehkIWHRbPKR4_hZLdJQ==`.
   - **Secret** `MASTER_KEYS` (optional) — comma-separated perpetual keys for the owner / QA team.

## Master keys (perpetual, multi-device — owner / testing)

For your own testing on any number of devices without the one-device lock or any expiry, add a
**secret** named `MASTER_KEYS` (Settings → Variables → *Encrypt*, or `wrangler secret put MASTER_KEYS`)
with one or more comma-separated keys, e.g. `KEY1,KEY2`. Any `/verify` request whose `license_key`
matches one of them returns `{"status":"active"}` immediately — **before** Gumroad, with **no device
binding and no expiry**. Enter the master key in the app's normal license-activation field.

Keep the keys in the secret **only** — never commit them to the repo, or anyone could read them and
bypass the paid subscription. The Gumroad subscription flow is unchanged for everyone else.

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
