'use client';

import { motion } from 'framer-motion';
import { Download, ShieldCheck, Tv, Trophy, KeyRound, PictureInPicture2, Smartphone, PackageCheck, Cpu } from 'lucide-react';

const features = [
  { icon: Tv, title: '200+ Saluran', desc: 'Nasional, sports, film, kids & event live dalam satu aplikasi.' },
  { icon: Trophy, title: 'Jadwal Pertandingan', desc: 'Tab Acara dengan hitung mundur kick-off & badge LIVE otomatis.' },
  { icon: KeyRound, title: 'DRM Widevine + ClearKey', desc: 'Mesin putar Media3 dengan multi-sumber fallback otomatis.' },
  { icon: PictureInPicture2, title: 'Picture-in-Picture', desc: 'Tonton sambil buka aplikasi lain, mode TV-ready (D-pad).' },
];

const specs = [
  { icon: PackageCheck, label: 'Ukuran', value: '3.0 MB' },
  { icon: Smartphone, label: 'Android', value: '6.0+ (HP & TV)' },
  { icon: Cpu, label: 'Arsitektur', value: 'Universal' },
  { icon: ShieldCheck, label: 'Rilis', value: 'Signed v1.0.0' },
];

const steps = [
  { n: '1', t: 'Unduh APK', d: 'Tekan tombol unduh di atas untuk mengunduh NSL-TV-v1.0.0-release.apk.' },
  { n: '2', t: 'Izinkan Instalasi', d: 'Saat diminta, izinkan "Instal aplikasi tidak dikenal" untuk browser Anda.' },
  { n: '3', t: 'Instal & Nikmati', d: 'Buka file APK, tekan Instal, lalu masuk daftar saluran langsung tersaji.' },
];

export default function Home() {
  return (
    <div className="min-h-screen bg-[#070B1E] text-[#F5F7FF] flex flex-col selection:bg-[#FFC93C] selection:text-[#231A00]">
      {/* glow background */}
      <div aria-hidden className="pointer-events-none fixed inset-0 overflow-hidden">
        <div className="absolute -top-40 left-1/2 -translate-x-1/2 h-[480px] w-[720px] rounded-full bg-[#FFC93C]/10 blur-[120px]" />
        <div className="absolute bottom-0 right-0 h-[360px] w-[520px] rounded-full bg-[#2E4FD8]/10 blur-[110px]" />
      </div>

      <main className="relative flex-1 mx-auto w-full max-w-3xl px-5 py-10 sm:py-16">
        {/* Hero */}
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: 'easeOut' }}
          className="flex flex-col items-center text-center"
        >
          <motion.img
            src="/nsl/logo-512.png"
            alt="Logo NSL TV"
            width={120}
            height={120}
            className="h-28 w-28 sm:h-32 sm:w-32 rounded-[28px] shadow-2xl shadow-black/60 ring-1 ring-white/10"
            animate={{ y: [0, -6, 0] }}
            transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
          />
          <h1 className="mt-6 text-4xl sm:text-5xl font-black tracking-tight">
            NSL <span className="text-[#FFC93C]">TV</span>
          </h1>
          <p className="mt-2 text-sm text-[#8A93B8]">Live Streaming Indonesia — ringan, halus, tanpa iklan.</p>

          <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
            <span className="rounded-full border border-[#FFC93C]/40 bg-[#FFC93C]/10 px-3 py-1 text-[11px] font-bold tracking-wider text-[#FFC93C]">v1.0.0 RILIS</span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] font-semibold text-[#B9C1E3]">3.0 MB</span>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[11px] font-semibold text-[#B9C1E3]">Android 6.0+</span>
            <span className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1 text-[11px] font-semibold text-emerald-300">HP &amp; Android TV</span>
          </div>

          <a
            href="/nsl/NSL-TV-v1.0.0-release.apk"
            download
            className="group mt-8 inline-flex min-h-[52px] items-center gap-3 rounded-2xl bg-[#FFC93C] px-8 text-base font-extrabold text-[#231A00] shadow-lg shadow-[#FFC93C]/25 transition-all duration-200 hover:bg-[#FFDA5C] hover:shadow-[#FFC93C]/40 active:scale-[0.97]"
          >
            <Download className="h-5 w-5 transition-transform duration-200 group-hover:translate-y-0.5" />
            Unduh APK NSL TV
          </a>
          <p className="mt-3 text-xs text-[#7C85AD]">NSL-TV-v1.0.0-release.apk • signed release build</p>
        </motion.section>

        {/* Features */}
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="mt-12 grid grid-cols-1 gap-3 sm:grid-cols-2"
        >
          {features.map((f) => (
            <div key={f.title} className="rounded-2xl border border-white/10 bg-white/[0.04] p-5 transition-colors duration-200 hover:bg-white/[0.07]">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#FFC93C]/15 text-[#FFC93C]">
                <f.icon className="h-5 w-5" />
              </div>
              <h3 className="mt-3 text-[15px] font-bold">{f.title}</h3>
              <p className="mt-1 text-[13px] leading-relaxed text-[#8A93B8]">{f.desc}</p>
            </div>
          ))}
        </motion.section>

        {/* Specs */}
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4"
        >
          {specs.map((s) => (
            <div key={s.label} className="rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-4 text-center">
              <s.icon className="mx-auto h-4 w-4 text-[#7C85AD]" />
              <p className="mt-2 text-[10px] font-bold uppercase tracking-widest text-[#7C85AD]">{s.label}</p>
              <p className="mt-0.5 text-[13px] font-bold">{s.value}</p>
            </div>
          ))}
        </motion.section>

        {/* Install steps */}
        <motion.section
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="mt-12"
        >
          <h2 className="text-center text-lg font-extrabold">Cara Pemasangan</h2>
          <div className="mt-5 space-y-3">
            {steps.map((s) => (
              <div key={s.n} className="flex items-start gap-4 rounded-2xl border border-white/10 bg-white/[0.04] p-4 sm:p-5">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#FFC93C] text-sm font-black text-[#231A00]">{s.n}</div>
                <div>
                  <h3 className="text-[14px] font-bold">{s.t}</h3>
                  <p className="mt-0.5 text-[13px] leading-relaxed text-[#8A93B8]">{s.d}</p>
                </div>
              </div>
            ))}
          </div>
        </motion.section>
      </main>

      <footer className="relative mt-auto border-t border-white/5 py-6 text-center">
        <p className="text-xs text-[#7C85AD]">NSL TV v1.0.0 • Build rilis bertanda tangan • Butuh Android 6.0 (API 23) atau lebih baru</p>
      </footer>
    </div>
  );
}
