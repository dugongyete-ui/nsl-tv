import { NextResponse } from "next/server";
import { fetchChannels } from "@/lib/upstream";

export const dynamic = "force-dynamic";

/** GET /api/channels — daftar saluran dari upstream (cache 60s). */
export async function GET() {
  try {
    const body = await fetchChannels();
    return NextResponse.json(body);
  } catch (e) {
    return NextResponse.json(
      { success: false, message: e instanceof Error ? e.message : "Gagal memuat saluran" },
      { status: 502 }
    );
  }
}
