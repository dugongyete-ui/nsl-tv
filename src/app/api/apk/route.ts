import { NextResponse } from "next/server";
import fs from "fs";
import path from "path";

export const dynamic = "force-dynamic";

/** GET /api/apk — unduh APK rilis NSL TV terbaru. */
export async function GET() {
  const dir = "/home/z/nsl-tv/out";
  try {
    const files = fs
      .readdirSync(dir)
      .filter((f) => f.endsWith("-release.apk"))
      .sort();
    if (files.length === 0) {
      return NextResponse.json({ error: "APK belum tersedia" }, { status: 404 });
    }
    const file = files[files.length - 1];
    const full = path.join(dir, file);
    const stat = fs.statSync(full);
    const stream = fs.createReadStream(full) as unknown as ReadableStream;
    return new Response(stream, {
      headers: {
        "Content-Type": "application/vnd.android.package-archive",
        "Content-Length": String(stat.size),
        "Content-Disposition": `attachment; filename="${file}"`,
        "Cache-Control": "no-store",
      },
    });
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : "gagal" },
      { status: 500 }
    );
  }
}
