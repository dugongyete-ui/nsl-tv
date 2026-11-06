'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Tv, Search, Download, RefreshCw, Play, CircleAlert, CircleCheck, CircleDashed,
  Radio, Satellite, Activity, Menu, X, ShieldCheck, Layers, MonitorPlay,
} from 'lucide-react';

// ============================================================
// Tipe data upstream
// ============================================================
interface TSource {
  url: string;
  manifestType?: string;
  headers?: Record<string, string> | null;
  drmConfig?: {
    type?: string;
    licenseKey?: string;
    licenseUrl?: string | null;
    headers?: Record<string, string> | null;
  } | null;
  resolutionLabel?: string;
}
interface TChannel {
  id: string;
  name: string;
  logoUrl?: string;
  groupTitle: string;
  order?: number;
  sources: TSource[];
}
interface CheckStep {
  label: string;
  url: string;
  status: number | null;
  note?: string;
  ok?: boolean;
}
interface CheckResult {
  ok: boolean;
  steps: CheckStep[];
  verdict?: string;
}
interface LogLine { t: string; msg: string; kind: 'info' | 'ok' | 'warn' | 'err' }

// ============================================================
// Util
// ============================================================
function b64urlEncode(str: string): string {
  const bytes = new TextEncoder().encode(str);
  let bin = '';
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function hh(src: TSource): string {
  return b64urlEncode(JSON.stringify(src.headers ?? {}));
}
function proxied(src: TSource): string {
  return `/api/stream?u=${encodeURIComponent(src.url).replace(/%24/g, '$')}&hh=${hh(src)}`;
}
function proxiedLicense(src: TSource, licUrl: string): string {
  return `/api/license?u=${encodeURIComponent(licUrl)}&hh=${hh(src)}`;
}
function hexToB64url(hex: string): string {
  const clean = hex.replace(/[^0-9a-fA-F]/g, '');
  const bytes = new Uint8Array(clean.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(clean.substr(i * 2, 2), 16);
  let bin = '';
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function now(): string {
  return new Date().toLocaleTimeString('id-ID', { hour12: false });
}
function typeOf(src: TSource): string {
  if (src.manifestType) return src.manifestType.toUpperCase();
  const u = (src.url || '').split('?')[0].toLowerCase();
  if (u.endsWith('.mpd')) return 'MPD';
  if (u.endsWith('.m3u8')) return 'HLS';
  return 'HLS';
}
const STATUS_META: Record<string, { label: string; cls: string }> = {
  OK: { label: 'Siap', cls: 'bg-emerald-400/15 text-emerald-300 border-emerald-400/30' },
  OK_DASH: { label: 'DASH terbaca', cls: 'bg-emerald-400/15 text-emerald-300 border-emerald-400/30' },
  MANIFEST_GAGAL: { label: 'Manifest gagal', cls: 'bg-rose-400/15 text-rose-300 border-rose-400/30' },
  VARIANT_GAGAL: { label: 'Variant 404', cls: 'bg-rose-400/15 text-rose-300 border-rose-400/30' },
  SEGMEN_GAGAL: { label: 'Segmen gagal', cls: 'bg-rose-400/15 text-rose-300 border-rose-400/30' },
  TIMEOUT_ATAU_JARINGAN: { label: 'Timeout/jaringan', cls: 'bg-rose-400/15 text-rose-300 border-rose-400/30' },
};

// ============================================================
// Halaman
// ============================================================
export default function TestPage() {
  const [channels, setChannels] = useState<TChannel[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [serverInfo, setServerInfo] = useState<{ name?: string; version?: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadErr, setLoadErr] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [cat, setCat] = useState('SEMUA');
  const [selected, setSelected] = useState<TChannel | null>(null);
  const [checks, setChecks] = useState<Record<number, CheckResult | 'loading'>>({});
  const [activeSrc, setActiveSrc] = useState<number>(-1);
  const [playing, setPlaying] = useState(false);
  const [res, setRes] = useState('');
  const [logs, setLogs] = useState<LogLine[]>([]);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [apkReady, setApkReady] = useState(true);
  const [apkInfo, setApkInfo] = useState<{ version: string; size: number } | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const hlsRef = useRef<{ destroy(): void } | null>(null);
  const dashRef = useRef<{ reset(): void; destroy(): void } | null>(null);
  const logRef = useRef<HTMLDivElement | null>(null);
  const currentIdRef = useRef<string | null>(null);

  const addLog = useCallback((msg: string, kind: LogLine['kind'] = 'info') => {
    setLogs((prev) => [...prev.slice(-120), { t: now(), msg, kind }]);
  }, []);

  useEffect(() => {
    logRef.current?.scrollTo({ top: 999999 });
  }, [logs]);

  // ---- Muat daftar saluran ----
  const loadChannels = useCallback(async () => {
    setLoading(true);
    setLoadErr(null);
    try {
      const r = await fetch('/api/channels', { cache: 'no-store' });
      const j = await r.json();
      if (!j.success) throw new Error(j.message || 'upstream error');
      const list: TChannel[] = (j.channels as TChannel[]).filter(
        (c) => c.sources && c.sources.length > 0
      );
      const cats: string[] = (j.categories as string[]).filter(
        (c) => c && !c.includes('Favorites')
      );
      setChannels(list);
      setCategories(cats);
      setServerInfo(j.server ?? null);
      addLog(`Daftar saluran dimuat: ${list.length} saluran`, 'ok');
    } catch (e) {
      setLoadErr(e instanceof Error ? e.message : 'gagal');
      addLog(`Gagal memuat saluran: ${e instanceof Error ? e.message : '?'}`, 'err');
    } finally {
      setLoading(false);
    }
  }, [addLog]);

  useEffect(() => { loadChannels(); }, [loadChannels]);
  useEffect(() => {
    fetch('/api/apk?info=1', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error('n/a'))))
      .then((j) => {
        setApkInfo({ version: j.version, size: j.size });
        setApkReady(true);
      })
      .catch(() => setApkReady(false));
  }, []);

  // ---- Uji & putar saluran terpilih ----
  const selectChannel = useCallback(async (ch: TChannel) => {
    currentIdRef.current = ch.id;
    setSelected(ch);
    setSidebarOpen(false);
    setActiveSrc(-1);
    setPlaying(false);
    setRes('');
    setChecks(Object.fromEntries(ch.sources.map((_, i) => [i, 'loading' as const])));
    setLogs([]);
    addLog(`Memilih "${ch.name}" — menguji ${ch.sources.length} sumber…`, 'info');

    // uji semua sumber paralel
    const results = await Promise.all(
      ch.sources.map(async (s): Promise<CheckResult | 'loading'> => {
        try {
          const r = await fetch(`/api/check?u=${encodeURIComponent(s.url)}&hh=${hh(s)}`, { cache: 'no-store' });
          const j = (await r.json()) as CheckResult;
          addLog(
            `Uji ${s.url.slice(0, 64)}… → ${j.verdict ?? '?'}`,
            j.ok ? 'ok' : 'warn'
          );
          return j;
        } catch (e) {
          addLog(`Uji gagal: ${e instanceof Error ? e.message : '?'}`, 'err');
          return { ok: false, steps: [], verdict: 'TIMEOUT_ATAU_JARINGAN' };
        }
      })
    );
    // hindari race: hanya terapkan bila saluran belum berganti
    if (currentIdRef.current !== ch.id) return;
    setChecks(Object.fromEntries(results.map((r, i) => [i, r])));
    const best = results.findIndex((r) => r !== 'loading' && r.ok);
    playSource(ch, best >= 0 ? best : 0);
     
  }, [addLog]);

  const destroyPlayers = useCallback(() => {
    if (hlsRef.current) { try { hlsRef.current.destroy(); } catch { /* noop */ } hlsRef.current = null; }
    if (dashRef.current) { try { dashRef.current.reset(); } catch { /* noop */ } try { dashRef.current.destroy(); } catch { /* noop */ } dashRef.current = null; }
    if (videoRef.current) {
      videoRef.current.removeAttribute('src');
      try { videoRef.current.load(); } catch { /* noop */ }
    }
  }, []);

  const playSource = useCallback(async (ch: TChannel, index: number) => {
    const src = ch.sources[index];
    if (!src) return;
    destroyPlayers();
    setActiveSrc(index);
    setPlaying(false);
    const type = typeOf(src);
    const url = proxied(src);
    const drm = src.drmConfig?.type;
    addLog(`Memutar sumber ${index + 1}/${ch.sources.length} • ${type}${drm ? ` • DRM ${drm}` : ''}`, 'info');

    const v = videoRef.current;
    if (!v) return;

    try {
      if (type === 'HLS' || type === 'TS') {
        const Hls = (await import('hls.js')).default;
        if (Hls.isSupported()) {
          const hls = new Hls({
            enableWorker: true,
            lowLatencyMode: false,
            backBufferLength: 30,
            manifestLoadingTimeOut: 15000,
            manifestLoadingMaxRetry: 2,
            levelLoadingMaxRetry: 3,
            fragLoadingMaxRetry: 4,
          });
          hlsRef.current = hls;
          hls.on(Hls.Events.ERROR, (_e, data) => {
            if (!data.fatal) return;
            addLog(`HLS error (${data.details}) — lanjut sumber berikutnya`, 'warn');
            const next = index + 1;
            if (next < ch.sources.length) playSource(ch, next);
            else addLog('Semua sumber gagal diputar', 'err');
          });
          hls.loadSource(url);
          hls.attachMedia(v);
        } else {
          v.src = url; // Safari
        }
      } else {
        // DASH
        const dashjs = (await import('dashjs')).default;
        const player = dashjs.MediaPlayer().create();
        dashRef.current = player as unknown as { reset(): void; destroy(): void };
        // proteksi DRM
        if (src.drmConfig?.type === 'WIDEVINE') {
          const lic = src.drmConfig.licenseKey || src.drmConfig.licenseUrl || '';
          if (lic.startsWith('http')) {
            player.setProtectionData({
              'com.widevine.alpha': { serverURL: proxiedLicense(src, lic) },
            });
            addLog('Widevine: lisensi via relay /api/license', 'info');
          }
        } else if (src.drmConfig?.type === 'CLEARKEY') {
          const key = src.drmConfig.licenseKey || '';
          if (/^[0-9a-fA-F]{16,64}:[0-9a-fA-F]{16,64}$/.test(key)) {
            const [kid, k] = key.split(':');
            player.setProtectionData({
              'org.w3.clearkey': { clearkeys: { [hexToB64url(kid)]: hexToB64url(k) } },
            });
            addLog('ClearKey: kunci lokal dimuat', 'ok');
          } else if (key.startsWith('http')) {
            player.setProtectionData({
              'org.w3.clearkey': { serverURL: proxiedLicense(src, key) },
            });
          }
        }
        player.on('error', (e: { error?: { message?: string } }) => {
          addLog(`DASH error: ${e?.error?.message ?? 'tidak diketahui'} — lanjut sumber berikutnya`, 'warn');
          const next = index + 1;
          if (next < ch.sources.length) playSource(ch, next);
          else addLog('Semua sumber gagal diputar', 'err');
        });
        player.updateSettings({
          streaming: {
            buffer: { fastSwitchEnabled: true },
            abr: { autoSwitchBitrate: { video: true } },
          },
        });
        player.initialize(v, url, true);
      }

      // autoplay: coba dengan suara; diblokir → bisukan lalu putar
      v.onloadedmetadata = () => {
        setRes(`${v.videoWidth}×${v.videoHeight}`);
      };
      v.onplaying = () => { setPlaying(true); };
      v.onwaiting = () => { setPlaying(false); };
      try {
        await v.play();
      } catch {
        v.muted = true;
        try { await v.play(); addLog('Autoplay dengan suara diblokir — dimulai tanpa suara', 'warn'); } catch { /* biarkan kontrol manual */ }
      }
    } catch (e) {
      addLog(`Gagal memulai pemutar: ${e instanceof Error ? e.message : '?'}`, 'err');
    }
  }, [addLog, destroyPlayers]);

  // ---- Filter daftar ----
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return channels.filter((c) => {
      const okCat = cat === 'SEMUA' || c.groupTitle === cat;
      const okQ = !q || c.name.toLowerCase().includes(q) || c.groupTitle.toLowerCase().includes(q);
      return okCat && okQ;
    });
  }, [channels, cat, query]);

  // ============================================================
  // Render
  // ============================================================
  return (
    <div className="h-dvh flex flex-col bg-[#070B1E] text-[#F5F7FF] overflow-hidden">
      {/* ===== Header ===== */}
      <header className="shrink-0 flex items-center gap-3 px-4 sm:px-6 h-14 border-b border-white/10 bg-[#05081A]/80 backdrop-blur">
        <button
          className="lg:hidden p-2 -ml-2 rounded-lg hover:bg-white/5"
          onClick={() => setSidebarOpen((v) => !v)}
          aria-label="Buka daftar saluran"
        >
          {sidebarOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#FFC93C] to-[#FF9D2E] grid place-items-center shadow-lg shadow-amber-500/20">
            <Tv className="w-4.5 h-4.5 text-[#231A00]" strokeWidth={2.4} />
          </div>
          <div className="min-w-0">
            <h1 className="text-[15px] font-bold leading-tight truncate">NSL TV · Uji Coba Stream</h1>
            <p className="text-[10.5px] text-[#7C85AD] leading-tight flex items-center gap-1.5">
              <span className={`w-1.5 h-1.5 rounded-full ${loading ? 'bg-amber-300 animate-pulse' : loadErr ? 'bg-rose-400' : 'bg-emerald-400'}`} />
              {loading ? 'Menghubungkan server…' : loadErr ? 'Server terputus' : `Server aktif${serverInfo?.name ? ` · ${serverInfo.name}` : ''} · ${channels.length} saluran`}
            </p>
          </div>
        </div>
        <div className="flex-1" />
        <a
          href="/api/apk"
          className={`hidden sm:inline-flex items-center gap-2 h-9 px-4 rounded-full text-[12.5px] font-bold transition
            ${apkReady
              ? 'bg-[#FFC93C] text-[#231A00] hover:bg-[#FFDA5C] shadow-lg shadow-amber-500/20'
              : 'bg-white/5 text-[#7C85AD] pointer-events-none'}`}
        >
          <Download className="w-4 h-4" />
          Unduh APK v{apkInfo?.version ?? '1.0.3'}
          {apkInfo?.size ? (
            <span className="opacity-70 font-semibold">· {(apkInfo.size / 1048576).toFixed(1)} MB</span>
          ) : null}
        </a>
      </header>

      {/* ===== Body ===== */}
      <div className="flex-1 flex min-h-0">
        {/* ---- Sidebar ---- */}
        <aside
          className={`fixed lg:static inset-y-14 left-0 z-40 w-80 max-w-[85vw] bg-[#0D1230] lg:bg-[#0D1230]/40 border-r border-white/10 flex flex-col
            transition-transform duration-200 lg:translate-x-0 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}`}
        >
          <div className="p-3 space-y-2.5 border-b border-white/5">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#7C85AD]" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Cari saluran…"
                className="w-full h-9 pl-9 pr-3 rounded-xl bg-white/5 border border-white/10 text-[13px] placeholder:text-[#7C85AD] outline-none focus:border-amber-300/40"
              />
            </div>
            <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-none">
              {['SEMUA', ...categories].map((c) => (
                <button
                  key={c}
                  onClick={() => setCat(c)}
                  className={`shrink-0 h-7 px-3 rounded-full text-[11px] font-semibold border transition
                    ${cat === c
                      ? 'bg-[#FFC93C] text-[#231A00] border-transparent'
                      : 'bg-white/5 text-[#B9C1E3] border-white/10 hover:bg-white/10'}`}
                >
                  {c}
                </button>
              ))}
            </div>
          </div>
          <div className="flex-1 overflow-y-auto overscroll-contain p-2 space-y-1 sidebar-scroll">
            {loading && (
              <div className="py-10 text-center text-[#7C85AD] text-xs flex flex-col items-center gap-2">
                <Satellite className="w-5 h-5 animate-pulse" /> Memuat saluran…
              </div>
            )}
            {loadErr && (
              <div className="m-2 p-3 rounded-xl bg-rose-400/10 border border-rose-400/20 text-[12px] text-rose-200 space-y-2">
                <p className="flex items-center gap-1.5 font-semibold"><CircleAlert className="w-4 h-4" /> Gagal memuat</p>
                <p className="text-rose-300/80">{loadErr}</p>
                <button onClick={loadChannels} className="inline-flex items-center gap-1.5 h-8 px-3 rounded-lg bg-white/10 hover:bg-white/15 text-[12px] font-semibold">
                  <RefreshCw className="w-3.5 h-3.5" /> Coba lagi
                </button>
              </div>
            )}
            {!loading && filtered.map((ch) => {
              const active = selected?.id === ch.id;
              return (
                <button
                  key={ch.id}
                  onClick={() => selectChannel(ch)}
                  className={`w-full flex items-center gap-2.5 p-2 rounded-xl text-left transition
                    ${active ? 'bg-amber-300/10 ring-1 ring-amber-300/30' : 'hover:bg-white/5'}`}
                >
                  <span className="w-9 h-9 rounded-lg bg-white/5 border border-white/10 grid place-items-center overflow-hidden shrink-0">
                    {ch.logoUrl ? (
                       
                      <img src={ch.logoUrl} alt="" referrerPolicy="no-referrer" loading="lazy" className="w-7 h-7 object-contain" />
                    ) : (
                      <Tv className="w-4 h-4 text-[#7C85AD]" />
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[13px] font-semibold truncate">{ch.name}</span>
                    <span className="block text-[10.5px] text-[#7C85AD] truncate">{ch.groupTitle} · {ch.sources.length} sumber</span>
                  </span>
                  {active && <Play className="w-3.5 h-3.5 text-amber-300 shrink-0" />}
                </button>
              );
            })}
            {!loading && !loadErr && filtered.length === 0 && (
              <p className="py-10 text-center text-[#7C85AD] text-xs">Tidak ada saluran cocok.</p>
            )}
          </div>

          {/* ---- Kartu unduh APK rilis ---- */}
          <div className="border-t border-white/5 p-3">
            {apkReady ? (
              <a
                href="/api/apk"
                className="flex items-center gap-3 w-full p-3 rounded-xl bg-amber-300/10 border border-amber-300/25 hover:bg-amber-300/15 transition group"
              >
                <span className="w-10 h-10 rounded-xl bg-[#FFC93C] text-[#231A00] grid place-items-center shrink-0 shadow-lg shadow-amber-500/20">
                  <Download className="w-5 h-5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-bold text-amber-200 leading-tight">
                    Unduh APK NSL TV v{apkInfo?.version ?? '1.0.3'}
                  </span>
                  <span className="block text-[10.5px] text-[#9BA4CC] leading-tight mt-0.5">
                    Rilis resmi (bukan debug){apkInfo?.size ? ` · ${(apkInfo.size / 1048576).toFixed(1)} MB` : ''} · untuk HP &amp; TV Box
                  </span>
                </span>
              </a>
            ) : (
              <div className="p-3 rounded-xl bg-white/5 border border-white/10 text-[11.5px] text-[#7C85AD] flex items-center gap-2">
                <CircleDashed className="w-4 h-4" /> APK sedang disiapkan…
              </div>
            )}
          </div>
        </aside>
        {sidebarOpen && (
          <div className="fixed inset-0 z-30 bg-black/60 lg:hidden" onClick={() => setSidebarOpen(false)} />
        )}

        {/* ---- Main ---- */}
        <main className="flex-1 min-w-0 overflow-y-auto p-4 sm:p-6 space-y-4 main-scroll">
          {!selected ? (
            <div className="h-full min-h-[60vh] grid place-items-center">
              <div className="text-center space-y-3 max-w-sm">
                <div className="mx-auto w-14 h-14 rounded-2xl bg-white/5 border border-white/10 grid place-items-center">
                  <MonitorPlay className="w-7 h-7 text-amber-300" />
                </div>
                <h2 className="text-lg font-bold">Pilih saluran di sidebar</h2>
                <p className="text-[13px] text-[#B9C1E3] leading-relaxed">
                  Setiap sumber saluran akan diuji otomatis (manifest → variant → segmen),
                  lalu diputar langsung di sini untuk memastikan stream bisa jalan.
                </p>
              </div>
            </div>
          ) : (
            <>
              {/* Player */}
              <section className="rounded-2xl overflow-hidden border border-white/10 bg-black shadow-2xl shadow-black/40">
                <video
                  ref={videoRef}
                  controls
                  playsInline
                  className="w-full aspect-video bg-black"
                />
              </section>

              {/* Baris info saluran */}
              <section className="flex flex-wrap items-center gap-2">
                <div className="flex items-center gap-2 min-w-0 mr-auto">
                  {selected.logoUrl ? (
                     
                    <img src={selected.logoUrl} alt="" referrerPolicy="no-referrer" className="w-8 h-8 object-contain" />
                  ) : null}
                  <div className="min-w-0">
                    <h2 className="text-[15px] font-bold leading-tight truncate">{selected.name}</h2>
                    <p className="text-[11px] text-[#7C85AD]">{selected.groupTitle}</p>
                  </div>
                  {playing && (
                    <span className="ml-1 inline-flex items-center gap-1 h-6 px-2 rounded-full bg-rose-500/15 border border-rose-400/30 text-rose-300 text-[10px] font-bold tracking-wide">
                      <Radio className="w-3 h-3" /> LIVE
                    </span>
                  )}
                  {res && (
                    <span className="inline-flex items-center gap-1 h-6 px-2 rounded-full bg-white/5 border border-white/10 text-[#B9C1E3] text-[10px] font-semibold">
                      {res}
                    </span>
                  )}
                </div>
                <div className="flex gap-1.5">
                  {selected.sources.map((s, i) => {
                    const c = checks[i];
                    const dot = c === 'loading'
                      ? 'bg-amber-300 animate-pulse'
                      : c && c.ok ? 'bg-emerald-400' : 'bg-rose-400';
                    const done = c !== 'loading' && c !== undefined;
                    return (
                      <button
                        key={i}
                        onClick={() => playSource(selected, i)}
                        className={`h-8 pl-2.5 pr-3 rounded-full text-[11.5px] font-bold border inline-flex items-center gap-1.5 transition
                          ${activeSrc === i
                            ? 'bg-amber-300/15 border-amber-300/40 text-amber-200'
                            : 'bg-white/5 border-white/10 text-[#B9C1E3] hover:bg-white/10'}`}
                      >
                        <span className={`w-1.5 h-1.5 rounded-full ${done ? dot : 'bg-amber-300 animate-pulse'}`} />
                        Sumber {i + 1} · {typeOf(s)}
                        {s.drmConfig?.type ? ` · ${s.drmConfig.type === 'WIDEVINE' ? 'WV' : s.drmConfig.type === 'CLEARKEY' ? 'CK' : s.drmConfig.type[0]}` : ''}
                      </button>
                    );
                  })}
                </div>
              </section>

              {/* Hasil uji per sumber */}
              <section className="grid md:grid-cols-2 xl:grid-cols-3 gap-3">
                {selected.sources.map((s, i) => {
                  const c = checks[i];
                  const meta = c !== 'loading' && c ? (STATUS_META[c.verdict ?? ''] ?? null) : null;
                  return (
                    <div key={i} className="rounded-2xl border border-white/10 bg-[#111736]/60 p-3.5 space-y-2.5">
                      <div className="flex items-center gap-2">
                        <Layers className="w-4 h-4 text-amber-300 shrink-0" />
                        <p className="text-[12.5px] font-bold mr-auto">Sumber {i + 1} — {typeOf(s)}</p>
                        {c === 'loading' ? (
                          <span className="inline-flex items-center gap-1 text-[10.5px] font-semibold text-amber-300">
                            <RefreshCw className="w-3 h-3 animate-spin" /> Menguji…
                          </span>
                        ) : meta ? (
                          <span className={`h-6 px-2 inline-flex items-center rounded-full border text-[10px] font-bold ${meta.cls}`}>
                            {meta.label}
                          </span>
                        ) : null}
                      </div>
                      {s.drmConfig?.type && (
                        <p className="inline-flex items-center gap-1.5 text-[10.5px] text-[#B9C1E3] bg-white/5 border border-white/10 rounded-full h-6 px-2">
                          <ShieldCheck className="w-3 h-3 text-amber-300" /> DRM {s.drmConfig.type}
                        </p>
                      )}
                      <div className="space-y-1">
                        {c === 'loading' && (
                          <p className="text-[11px] text-[#7C85AD] flex items-center gap-1.5 py-1">
                            <CircleDashed className="w-3.5 h-3.5 animate-spin" /> Memeriksa rantai stream…
                          </p>
                        )}
                        {c && c !== 'loading' && c.steps.map((st, k) => (
                          <div key={k} className="flex items-start gap-2 text-[11px]">
                            {st.ok ? (
                              <CircleCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-px" />
                            ) : (
                              <CircleAlert className="w-3.5 h-3.5 text-rose-400 shrink-0 mt-px" />
                            )}
                            <div className="min-w-0">
                              <p className="font-semibold text-[#F5F7FF]">
                                {st.label} <span className={st.status ? 'text-[#7C85AD] font-normal' : 'text-rose-300 font-normal'}>
                                  {st.status ?? 'ERR'}
                                </span>
                              </p>
                              {st.note && <p className="text-[10px] text-[#7C85AD] truncate">{st.note}</p>}
                            </div>
                          </div>
                        ))}
                      </div>
                      {Object.keys(s.headers ?? {}).length > 0 && (
                        <div className="pt-1 border-t border-white/5">
                          <p className="text-[10px] text-[#7C85AD] font-mono truncate">
                            {Object.entries(s.headers ?? {}).map(([k, v]) => `${k}: ${v}`).join('  ·  ')}
                          </p>
                        </div>
                      )}
                    </div>
                  );
                })}
              </section>

              {/* Log */}
              <section className="rounded-2xl border border-white/10 bg-[#05081A]/70 p-3.5">
                <div className="flex items-center gap-2 mb-2">
                  <Activity className="w-4 h-4 text-amber-300" />
                  <p className="text-[12.5px] font-bold mr-auto">Log Uji Coba</p>
                  <button
                    onClick={() => selected && playSource(selected, activeSrc >= 0 ? activeSrc : 0)}
                    className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-lg bg-white/5 hover:bg-white/10 text-[11px] font-semibold text-[#B9C1E3]"
                  >
                    <RefreshCw className="w-3 h-3" /> Putar ulang
                  </button>
                </div>
                <div ref={logRef} className="max-h-40 overflow-y-auto font-mono text-[10.5px] leading-5 space-y-0.5 log-scroll">
                  {logs.length === 0 ? (
                    <p className="text-[#7C85AD]">Belum ada aktivitas.</p>
                  ) : (
                    logs.map((l, i) => (
                      <p key={i} className="truncate">
                        <span className="text-[#7C85AD]">[{l.t}]</span>{' '}
                        <span className={
                          l.kind === 'ok' ? 'text-emerald-300'
                            : l.kind === 'warn' ? 'text-amber-300'
                              : l.kind === 'err' ? 'text-rose-300' : 'text-[#B9C1E3]'
                        }>{l.msg}</span>
                      </p>
                    ))
                  )}
                </div>
              </section>
            </>
          )}
        </main>
      </div>

      {/* ===== Footer (menempel di bawah) ===== */}
      <footer className="shrink-0 h-9 flex items-center justify-center gap-2 border-t border-white/10 bg-[#05081A]/80 text-[10.5px] text-[#7C85AD]">
        <Tv className="w-3.5 h-3.5" />
        NSL TV · Panel Uji Coba Stream — sumber bertanda hijau pasti jalan di aplikasi Android
      </footer>
    </div>
  );
}
