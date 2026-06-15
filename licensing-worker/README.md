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
