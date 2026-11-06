import { NextRequest, NextResponse } from "next/server";
import { decodeHeaders, withDefaultUa } from "@/lib/upstream";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Step = { label: string; url: string; status: number | null; note?: string; ok?: boolean };

/**
 * GET /api/check?u=<url>&hh=<b64headers>
 * Uji rantai stream server-side: manifest → variant → segmen pertama.
 */
export async function GET(req: NextRequest) {
  const url = req.nextUrl.searchParams.get("u");
  const headers = withDefaultUa(decodeHeaders(req.nextUrl.searchParams.get("hh")));
  if (!url) {
    return NextResponse.json({ ok: false, error: "parameter u wajib" }, { status: 400 });
  }

  const steps: Step[] = [];
  const ctrl = new AbortController();
  const kill = setTimeout(() => ctrl.abort(), 15000);
  try {
    // 1. manifest
    const r1 = await fetch(url, {
      headers,
      redirect: "follow",
      signal: ctrl.signal,
      cache: "no-store",
    });
    const text = await r1.text();
    const isM3u8 = text.includes("#EXTM3U");
    const isMpd = /^\s*<\?xml[\s\S]*<MPD/i.test(text) || text.trimStart().startsWith("<MPD");
    const manifestOk = r1.ok && (isM3u8 || isMpd);
    steps.push({
      label: "Manifest",
      url,
      status: r1.status,
      ok: manifestOk,
      note: manifestOk ? (isM3u8 ? "HLS playlist" : "DASH MPD") : text.slice(0, 90),
    });
    if (!manifestOk) {
      return NextResponse.json({ ok: false, steps, verdict: "MANIFEST_GAGAL" });
    }

    if (isM3u8) {
      // 2. variant pertama
      const base = finalUrl(r1, url);
      const lines = text.split("\n").map((l) => l.trim());
      let variantLine: string | null = null;
      for (let i = 0; i < lines.length; i++) {
        const l = lines[i];
        if (!l || l.startsWith("#")) continue;
        variantLine = l;
        break;
      }
      if (!variantLine) {
        // media playlist langsung → segmen
        const segRes = await probeSegment(lines, base, headers, steps);
        clearTimeout(kill);
        return NextResponse.json({ ok: segRes, steps, verdict: segRes ? "OK" : "SEGMEN_GAGAL" });
      }
      // URI="..." di tag EXT-X-MEDIA dsb
      if (!variantLine) { /* unreachable */ }
      const vUrl = new URL(stripQueryMaybe(variantLine), base).href;
      const r2 = await fetch(vUrl, {
        headers,
        redirect: "follow",
        signal: ctrl.signal,
        cache: "no-store",
      });
      const vText = await r2.text();
      const vOk = r2.ok && vText.includes("#EXTM3U");
      steps.push({
        label: "Variant",
        url: vUrl,
        status: r2.status,
        ok: vOk,
        note: vOk ? "playlist varian" : vText.slice(0, 90),
      });
      if (!vOk) {
        clearTimeout(kill);
        return NextResponse.json({ ok: false, steps, verdict: "VARIANT_GAGAL" });
      }
      const vLines = vText.split("\n").map((l) => l.trim());
      const segRes = await probeSegment(vLines, finalUrl(r2, vUrl), headers, steps);
      clearTimeout(kill);
      return NextResponse.json({ ok: segRes, steps, verdict: segRes ? "OK" : "SEGMEN_GAGAL" });
    }

    // MPD: cukup manifest terbaca (segmen via template akan diuji pemutar web)
    clearTimeout(kill);
    return NextResponse.json({ ok: true, steps, verdict: "OK_DASH" });
  } catch (e) {
    clearTimeout(kill);
    const msg = e instanceof Error ? e.message : "error";
    steps.push({ label: "Jaringan", url, status: null, note: msg, ok: false });
    return NextResponse.json({ ok: false, steps, verdict: "TIMEOUT_ATAU_JARINGAN" });
  }
}

async function probeSegment(
  lines: string[],
  base: string,
  headers: Record<string, string>,
  steps: Step[]
): Promise<boolean> {
  let seg: string | null = null;
  for (const l of lines) {
    if (!l || l.startsWith("#")) continue;
    seg = l;
    break;
  }
  if (!seg) {
    steps.push({ label: "Segmen", url: base, status: null, note: "tidak ada segmen", ok: false });
    return false;
  }
  const segUrl = new URL(seg, base).href;
  try {
    const r = await fetch(segUrl, {
      headers,
      redirect: "follow",
      signal: AbortSignal.timeout(10000),
      cache: "no-store",
    });
    let bytes = 0;
    if (r.ok && r.body) {
      const reader = r.body.getReader();
      const chunk = await reader.read();
      bytes = chunk.value ? chunk.value.byteLength : 0;
      try { await reader.cancel(); } catch { /* tutup */ }
    }
    steps.push({
      label: "Segmen",
      url: segUrl,
      status: r.status,
      ok: r.ok && bytes > 0,
      note: r.ok ? `${bytes} byte terbaca` : "gagal",
    });
    return r.ok && bytes > 0;
  } catch (e) {
    steps.push({
      label: "Segmen",
      url: segUrl,
      status: null,
      note: e instanceof Error ? e.message : "error",
      ok: false,
    });
    return false;
  }
}

function finalUrl(res: Response, fallback: string): string {
  try {
    return res.url || fallback;
  } catch {
    return fallback;
  }
}

function stripQueryMaybe(l: string): string {
  // baris playlist bisa berupa "url?query" — biarkan utuh; URL() menangani
  return l;
}
