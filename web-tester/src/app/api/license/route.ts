import { NextRequest } from "next/server";
import { decodeHeaders, withDefaultUa } from "@/lib/upstream";

export const dynamic = "force-dynamic";

/**
 * POST /api/license?u=<licenseUrl>&hh=<b64headers>
 * Relay permintaan kunci DRM (Widevine EME dari dashjs) ke server lisensi.
 * Body biner diteruskan utuh; respons diteruskan apa adanya.
 */
export async function POST(req: NextRequest) {
  const sp = req.nextUrl.searchParams;
  const url = sp.get("u");
  const extra = withDefaultUa(decodeHeaders(sp.get("hh")));
  if (!url) return new Response("parameter u wajib", { status: 400 });

  try {
    const body = await req.arrayBuffer();
    const headers: Record<string, string> = {
      ...extra,
      "Content-Type":
        req.headers.get("content-type") || "application/octet-stream",
    };
    // header DRM kustom dari server (X-DRM-Header dsb.) sudah ada di extra
    const up = await fetch(url, {
      method: "POST",
      headers,
      body: body.byteLength > 0 ? body : new Uint8Array([0x08, 0x04]),
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    const h = new Headers();
    const ct = up.headers.get("content-type");
    if (ct) h.set("Content-Type", ct);
    h.set("Access-Control-Allow-Origin", "*");
    h.set("Cache-Control", "no-store");
    return new Response(up.body, { status: up.status, headers: h });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "gagal relay lisensi";
    return new Response(`License relay error: ${msg}`, { status: 502 });
  }
}
