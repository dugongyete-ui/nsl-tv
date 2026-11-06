import { NextRequest } from "next/server";
import { decodeHeaders, encodeHeaders, withDefaultUa } from "@/lib/upstream";

export const dynamic = "force-dynamic";

/**
 * GET /api/stream?u=<url>&hh=<b64headers>
 * Relay stream TV: melewati segmen byte-per-byte, & menulis-ulang
 * manifest HLS/DASH agar semua URL turunannya tetap lewat relay ini
 * (menembus CORS + menyuntikkan header anti-hotlink per saluran).
 */

function proxify(absUrl: string, headers: Record<string, string>): string {
  const hh = encodeHeaders(headers);
  // $ pada template DASH tidak boleh ter-encode agar dashjs tetap mensubstitusi token
  const u = encodeURIComponent(absUrl).replace(/%24/g, "$");
  return `/api/stream?u=${u}&hh=${hh}`;
}

function rewriteM3u8(text: string, base: string, headers: Record<string, string>): string {
  const out: string[] = [];
  for (const raw of text.split("\n")) {
    const line = raw.trimEnd();
    if (line.startsWith("#")) {
      // tulis-ulang semua atribut URI="..." (EXT-X-KEY, EXT-X-MEDIA, dsb.)
      const replaced = line.replace(/URI="([^"]+)"/g, (_m, uri: string) => {
        if (/^(data:|blob:)/i.test(uri)) return _m;
        try {
          const abs = new URL(uri, base).href;
          return `URI="${proxify(abs, headers)}"`;
        } catch {
          return _m;
        }
      });
      out.push(replaced);
    } else if (line.trim() === "") {
      out.push(line);
    } else {
      try {
        const abs = new URL(line.trim(), base).href;
        out.push(proxify(abs, headers));
      } catch {
        out.push(line);
      }
    }
  }
  return out.join("\n");
}

function rewriteMpd(text: string, base: string, headers: Record<string, string>): string {
  // 1. atribut URL-ish: media / initialization / index / xlink:href
  let out = text.replace(
    /\s(media|initialization|index|xlink:href)="([^"]*)"/g,
    (m, attr: string, val: string) => {
      if (!val || /^(#|data:|blob:|urn:)/i.test(val)) return m;
      try {
        const abs = new URL(val, base).href;
        return ` ${attr}="${proxify(abs, headers)}"`;
      } catch {
        return m;
      }
    }
  );
  // 2. <BaseURL>teks</BaseURL>
  out = out.replace(/<BaseURL>([^<]*)<\/BaseURL>/g, (m, val: string) => {
    if (!val.trim() || /^(#|data:|blob:|urn:|https?:)/i.test(val) === false && val.includes("/api/stream?")) return m;
    try {
      const abs = new URL(val.trim(), base).href;
      return `<BaseURL>${proxify(abs, headers)}</BaseURL>`;
    } catch {
      return m;
    }
  });
  return out;
}

function sniffManifest(text: string): "m3u8" | "mpd" | null {
  const t = text.trimStart();
  if (t.startsWith("#EXTM3U")) return "m3u8";
  if (t.startsWith("<MPD") || (/^<\?xml/i.test(t) && t.includes("<MPD"))) return "mpd";
  return null;
}

export async function GET(req: NextRequest) {
  const sp = req.nextUrl.searchParams;
  const url = sp.get("u");
  const headers = withDefaultUa(decodeHeaders(sp.get("hh")));
  if (!url) {
    return new Response("parameter u wajib", { status: 400 });
  }

  const range = req.headers.get("range");
  const fwd: Record<string, string> = { ...headers };
  if (range) fwd["range"] = range;

  try {
    const up = await fetch(url, {
      headers: fwd,
      redirect: "follow",
      cache: "no-store",
      signal: AbortSignal.timeout(20000),
    });

    const ct = (up.headers.get("content-type") || "").toLowerCase();
    const looksManifest = /mpegurl|dash\+xml|mpd/i.test(ct);

    if (looksManifest) {
      const text = await up.text();
      const kind = sniffManifest(text);
      const base = up.url || url;
      if (kind === "m3u8") {
        return new Response(rewriteM3u8(text, base, headers), {
          status: up.status,
          headers: {
            "Content-Type": "application/vnd.apple.mpegurl",
            "Cache-Control": "no-store",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
      if (kind === "mpd") {
        return new Response(rewriteMpd(text, base, headers), {
          status: up.status,
          headers: {
            "Content-Type": "application/dash+xml",
            "Cache-Control": "no-store",
            "Access-Control-Allow-Origin": "*",
          },
        });
      }
      // klaim manifest tapi bukan playlist — teruskan apa adanya (jarang)
      return new Response(text, { status: up.status, headers: passthroughHeaders(up) });
    }

    // segmen / key / init mp4 → teruskan byte-stream
    const h = passthroughHeaders(up);
    h.set("Access-Control-Allow-Origin", "*");
    return new Response(up.body, { status: up.status, headers: h });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "gagal relay";
    return new Response(`Relay error: ${msg}`, { status: 502 });
  }
}

function passthroughHeaders(up: Response): Headers {
  const h = new Headers();
  const ct = up.headers.get("content-type");
  if (ct) h.set("Content-Type", ct);
  const ar = up.headers.get("accept-ranges");
  if (ar) h.set("Accept-Ranges", ar);
  const cl = up.headers.get("content-length");
  if (cl) h.set("Content-Length", cl);
  const cr = up.headers.get("content-range");
  if (cr) h.set("Content-Range", cr);
  h.set("Cache-Control", "no-store");
  return h;
}
