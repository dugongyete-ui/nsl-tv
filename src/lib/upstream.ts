import crypto from "crypto";

/**
 * Klien upstream Dopamine TV untuk uji coba stream NSL TV.
 * Meniru skema signature + header aplikasi Android (Api.java).
 */

export const UPSTREAM_BASE = "https://dopaminetv.mikrofast.my.id";
const SECRET = "DopamineTV_SecKey_2026_X9#";
export const APP_UA = "DopamineTV/1.0.7 (Android; ARM) Kodi/21.0";

export const TEST_DEVICE_ID = "a1b2c3d4e5f60718";

export function signedHeaders(): Record<string, string> {
  const ts = Date.now().toString();
  const sig = crypto
    .createHash("sha256")
    .update(`${TEST_DEVICE_ID}:${ts}:${SECRET}`)
    .digest("hex");
  return {
    "User-Agent": APP_UA,
    "X-Device-Id": TEST_DEVICE_ID,
    "X-Device-Model": "NSL TV Web Tester",
    "X-Device-Type": "Smartphone",
    "X-Device-Android": "Web (API 34)",
    "X-App-Version": "1.0.7",
    "X-App-Timestamp": ts,
    "X-App-Signature": sig,
    "X-App-Capabilities": "",
    Accept: "*/*",
  };
}

/** Encode headers ke param URL (base64url JSON). */
export function encodeHeaders(h: Record<string, string>): string {
  return Buffer.from(JSON.stringify(h ?? {}), "utf8")
    .toString("base64url");
}

export function decodeHeaders(hh: string | null): Record<string, string> {
  if (!hh) return {};
  try {
    const obj = JSON.parse(Buffer.from(hh, "base64url").toString("utf8"));
    if (obj && typeof obj === "object" && !Array.isArray(obj)) {
      const out: Record<string, string> = {};
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === "string" && k.length > 0) out[k] = v;
      }
      return out;
    }
    return {};
  } catch {
    return {};
  }
}

/** Gabungkan headers upstream; jika tak ada UA → pakai UA aplikasi. */
export function withDefaultUa(h: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  let hasUa = false;
  for (const [k, v] of Object.entries(h)) {
    if (k.toLowerCase() === "user-agent") {
      if (v.trim()) out["user-agent"] = v;
      hasUa = true;
    } else if (v.trim()) {
      out[k] = v;
    }
  }
  if (!hasUa) out["user-agent"] = APP_UA;
  return out;
}

// ---- Cache channels (60 detik) ----

type Cached = { at: number; body: unknown };
let channelsCache: Cached | null = null;

export async function fetchChannels(): Promise<unknown> {
  if (channelsCache && Date.now() - channelsCache.at < 60_000) {
    return channelsCache.body;
  }
  const res = await fetch(`${UPSTREAM_BASE}/api/v1/channels`, {
    headers: signedHeaders(),
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Upstream ${res.status}`);
  }
  const body = await res.json();
  channelsCache = { at: Date.now(), body };
  return body;
}
