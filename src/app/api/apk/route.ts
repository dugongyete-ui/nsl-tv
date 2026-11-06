import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";

export const dynamic = "force-dynamic";

/** Kandidat lokasi APK rilis — dipakai yang berisi file terbaru. */
const DIRS = [
  "/home/z/my-project/download",
  "/home/z/my-project/android/app/build/outputs/apk/release",
];

function findLatestApk(): { file: string; full: string; stat: fs.Stats } | null {
  const candidates: { file: string; full: string; stat: fs.Stats }[] = [];
  for (const dir of DIRS) {
    try {
      for (const f of fs.readdirSync(dir)) {
        if (!f.endsWith("-release.apk")) continue;
        const full = path.join(dir, f);
        const stat = fs.statSync(full);
        candidates.push({ file: f, full, stat });
      }
    } catch {
      // folder tidak ada → lanjut
    }
  }
  if (candidates.length === 0) return null;
  // urutkan berdasarkan nama versi (v1.0.2 > v1.0.1) lalu mtime
  candidates.sort((a, b) => {
    const va = a.file.match(/v(\d+)\.(\d+)\.(\d+)/);
    const vb = b.file.match(/v(\d+)\.(\d+)\.(\d+)/);
    if (va && vb) {
      const na = (+va[1] << 16) | (+va[2] << 8) | +va[3];
      const nb = (+vb[1] << 16) | (+vb[2] << 8) | +vb[3];
      if (na !== nb) return nb - na; // terbaru dulu
    }
    return b.stat.mtimeMs - a.stat.mtimeMs;
  });
  return candidates[0];
}

/** GET /api/apk?info=1 — metadata APK terbaru (versi, ukuran, waktu). */
export async function GET(req: Request) {
  const url = new URL(req.url);
  try {
    const found = findLatestApk();
    if (!found) {
      return NextResponse.json({ error: "APK belum tersedia" }, { status: 404 });
    }

    if (url.searchParams.get("info")) {
      const m = found.file.match(/v(\d+\.\d+\.\d+)/);
      return NextResponse.json(
        {
          version: m ? m[1] : "1.0.5",
          filename: found.file,
          size: found.stat.size,
          updatedAt: found.stat.mtime.toISOString(),
        },
        { headers: { "Cache-Control": "no-store" } }
      );
    }

    const data = fs.readFileSync(found.full);
    return new Response(
      new Uint8Array(data),
      {
        headers: {
          "Content-Type": "application/vnd.android.package-archive",
          "Content-Length": String(found.stat.size),
          "Content-Disposition": `attachment; filename="${found.file}"`,
          "Cache-Control": "no-store",
        },
      }
    );
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : "gagal" },
      { status: 500 }
    );
  }
}
