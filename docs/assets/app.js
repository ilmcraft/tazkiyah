/*
 * তাযকিয়া — web version.
 *
 * Reads the same content the Android app syncs (tazkiyah-content/ in the GitHub repo), so speakers, topics and
 * lecture counts are always current. Lectures play in a focused player like the app's FocusedVideoFrame:
 * YouTube's own controls are off, a shield sits over the video, and a cover card hides it whenever it is not
 * playing — so YouTube's links, suggestions and end screens can't be reached. Favorites, history and resume
 * positions stay in this browser (localStorage) only.
 */
(() => {
  "use strict";

  // ------------------------------------------------------------------ settings
  const OWNER = "mdhelaluddin-ctg-bd";
  const REPO = "tazkiyah";
  const CONTENT_BASES = [
    `https://raw.githubusercontent.com/${OWNER}/${REPO}/main/tazkiyah-content/`,
    `https://cdn.jsdelivr.net/gh/${OWNER}/${REPO}@main/tazkiyah-content/`,
  ];
  const REPO_URL = `https://github.com/${OWNER}/${REPO}`;
  const APK_URL = `${REPO_URL}/releases/latest/download/tazkiyah.apk`;
  const DEFAULT_TITLE = "তাযকিয়া — বাংলা ইসলামিক লেকচার";
  const NEW_DAYS = 7;
  const SPEEDS = [0.75, 1, 1.25, 1.5, 1.75, 2];
  const MONTHS = ["জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"];

  const ICONS = {
    home: "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    speakers: "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
    topics: "M12 2l-5.5 9h11L12 2zm5.5 11a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9zM3 13.5h8v8H3v-8z",
    library: "M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5a2.5 2.5 0 1 1-2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z",
    search: "M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    play: "M8 5.14v13.72a1 1 0 0 0 1.52.85l10.79-6.86a1 1 0 0 0 0-1.7L9.52 4.29A1 1 0 0 0 8 5.14z",
    pause: "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z",
    replay: "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
    forward: "M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z",
    fullscreen: "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z",
    volumeUp: "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z",
    volumeDown: "M18.5 12A4.5 4.5 0 0 0 16 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z",
    volumeOff: "M16.5 12A4.5 4.5 0 0 0 14 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.8 8.8 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.99 8.99 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z",
    close: "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
    download: "M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z",
    share: "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11A2.99 2.99 0 0 0 21 5a3 3 0 1 0-5.91.7L8.04 9.81A2.99 2.99 0 0 0 3 12a3 3 0 0 0 5.04 2.19l7.12 4.16c-.05.21-.08.43-.08.65A2.92 2.92 0 1 0 18 16.08z",
    moon: "M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.39 5.39 0 0 1-4.4 2.26 5.4 5.4 0 0 1-3.14-9.8c-.44-.06-.9-.1-1.36-.1z",
    sun: "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM2 13h2a1 1 0 1 0 0-2H2a1 1 0 1 0 0 2zm18 0h2a1 1 0 1 0 0-2h-2a1 1 0 1 0 0 2zM11 2v2a1 1 0 1 0 2 0V2a1 1 0 1 0-2 0zm0 18v2a1 1 0 1 0 2 0v-2a1 1 0 1 0-2 0zM5.99 4.58a1 1 0 1 0-1.41 1.41l1.06 1.06a1 1 0 1 0 1.41-1.41L5.99 4.58zm12.37 12.37a1 1 0 1 0-1.41 1.41l1.06 1.06a1 1 0 1 0 1.41-1.41l-1.06-1.06zm1.06-10.96a1 1 0 1 0-1.41-1.41l-1.06 1.06a1 1 0 1 0 1.41 1.41l1.06-1.06zM7.05 18.36a1 1 0 1 0-1.41-1.41l-1.06 1.06a1 1 0 1 0 1.41 1.41l1.06-1.06z",
    heart: "M12 20.5l-1.3-1.18C5.6 14.7 2.5 11.9 2.5 8.4 2.5 5.6 4.7 3.5 7.4 3.5c1.6 0 3.1.74 4.1 1.9 1-1.16 2.5-1.9 4.1-1.9 2.7 0 4.9 2.1 4.9 4.9 0 3.5-3.1 6.3-8.2 10.92L12 20.5z",
  };
  const icon = (name) => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="${ICONS[name]}"/></svg>`;

  // ------------------------------------------------------------------ helpers
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const bn = (value) => String(value).replace(/[0-9]/g, (d) => "০১২৩৪৫৬৭৮৯"[d]);
  const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  const esc = (value) => String(value == null ? "" : value).replace(/[&<>"']/g, (c) => ESCAPES[c]);
  const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
  const pad2 = (n) => String(n).padStart(2, "0");

  /** 3725 -> "১:০২:০৫" (like the app's formatClock). */
  function clock(totalSec) {
    const s = Math.max(0, Math.floor(totalSec || 0));
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return bn(h ? `${h}:${pad2(m)}:${pad2(sec)}` : `${m}:${pad2(sec)}`);
  }
  /** 4438 -> "১ ঘণ্টা ১৪ মিনিট". */
  function lengthText(sec) {
    if (!sec) return "";
    const total = Math.max(1, Math.round(sec / 60));
    const h = Math.floor(total / 60), m = total % 60;
    if (!h) return `${bn(m)} মিনিট`;
    return m ? `${bn(h)} ঘণ্টা ${bn(m)} মিনিট` : `${bn(h)} ঘণ্টা`;
  }
  /** "2026-11-07" -> "৭ নভেম্বর ২০২৬". */
  function dateText(iso) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || "");
    return m ? `${bn(Number(m[3]))} ${MONTHS[Number(m[2]) - 1]} ${bn(m[1])}` : "";
  }
  const localIso = (d = new Date()) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
  const isNew = (l) => !!l.addedAt && (Date.parse(localIso()) - Date.parse(l.addedAt.slice(0, 10))) / 864e5 <= NEW_DAYS;

  // Bengali search normalisation — a port of the app's BanglaNormalizer (and scripts/bangla.py).
  const INVISIBLE = /[​‌‍⁠﻿­]/g;
  function normalize(input) {
    let s = String(input || "").normalize("NFC");
    s = s.replace(/ত্‍/g, "ৎ"); // legacy khanda-ta, before joiners are removed
    s = s.replace(INVISIBLE, "");
    // NFC keeps these decomposed (composition exclusions); fold both spellings to one code point.
    s = s.replace(/য়/g, "য়").replace(/ড়/g, "ড়").replace(/ঢ়/g, "ঢ়");
    s = s.replace(/[০-৯]/g, (d) => String(d.charCodeAt(0) - 0x09e6)).toLowerCase();
    return s.replace(/\s+/g, " ").trim();
  }
  const NON_WORD = /[^\p{L}\p{M}\p{N}]+/u;
  const tokens = (input) => normalize(input).split(NON_WORD).filter(Boolean);

  // localStorage that never throws (private windows, blocked storage).
  const store = {
    get(key, fallback) {
      try { const v = localStorage.getItem(`tz.${key}`); return v === null ? fallback : JSON.parse(v); } catch (e) { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem(`tz.${key}`, JSON.stringify(value)); return true; } catch (e) { return false; }
    },
  };
  let favorites = store.get("favorites", {}); // lectureId -> time added
  let progress = store.get("progress", {}); //   lectureId -> { p: position s, d: duration s, t: last played ms, c: finished }
  if (!favorites || typeof favorites !== "object") favorites = {};
  if (!progress || typeof progress !== "object") progress = {};

  let toastTimer = 0;
  function toast(text) {
    const el = $("#toast");
    el.textContent = text;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2400);
  }

  // ------------------------------------------------------------------ content
  let catalog = null;
  let contentState = "loading"; // loading | ready | error

  async function fetchJson(path, init) {
    let lastError;
    for (const base of CONTENT_BASES) {
      try {
        const res = await fetch(base + path, init);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return await res.json();
      } catch (e) {
        lastError = e;
      }
    }
    throw lastError;
  }

  /** manifest.json lists every file with its sha256: unchanged files come from the local cache. */
  async function loadContent() {
    const cachedManifest = store.get("content.manifest", null);
    let manifest;
    try {
      manifest = await fetchJson("manifest.json", { cache: "no-cache" });
    } catch (e) {
      if (!cachedManifest) throw e;
      manifest = cachedManifest; // offline: last known content
    }
    const files = {};
    await Promise.all(manifest.files.map(async (file) => {
      const cached = store.get(`content.file:${file.path}`, null);
      if (cached && cached.sha256 === file.sha256) { files[file.path] = cached.data; return; }
      const data = await fetchJson(`${file.path}?v=${file.sha256.slice(0, 12)}`);
      files[file.path] = data;
      store.set(`content.file:${file.path}`, { sha256: file.sha256, data });
    }));
    store.set("content.manifest", manifest);
    return buildCatalog(manifest, files);
  }

  function buildCatalog(manifest, files) {
    const byOrder = (a, b) => (a.order ?? 1e9) - (b.order ?? 1e9) || String(a.name).localeCompare(String(b.name), "bn");
    const speakers = ((files["data/speakers.json"] || {}).speakers || []).slice().sort(byOrder);
    const categories = ((files["data/categories.json"] || {}).categories || []).slice().sort(byOrder);
    const speakerById = new Map(speakers.map((s) => [s.id, s]));
    const categoryById = new Map(categories.map((c) => [c.id, c]));
    speakers.forEach((s) => { s.words = tokens(`${s.name} ${s.nameEn || ""}`); });

    const lectures = [];
    for (const file of manifest.files) {
      if (!file.path.startsWith("data/lectures/")) continue;
      const doc = files[file.path];
      const speaker = doc && speakerById.get(doc.speakerId);
      if (!speaker) continue;
      (doc.lectures || []).forEach((l, index) => {
        const media = l.media || {};
        const playable = (media.type === "youtube" && /^[\w-]{11}$/.test(media.videoId || "")) ||
          ((media.type === "audio" || media.type === "video") && /^https:\/\//.test(media.url || ""));
        if (!playable || !l.id || !l.title) return;
        const text = [l.title, (l.tags || []).join(" "), speaker.name, speaker.nameEn, l.description, l.series && l.series.title]
          .filter(Boolean).join(" ");
        lectures.push(Object.assign({}, l, {
          speakerId: doc.speakerId,
          speaker,
          sortIndex: index,
          categoryIds: (l.categoryIds || []).filter((id) => categoryById.has(id)),
          words: tokens(text),
          titleWords: tokens(l.title),
        }));
      });
    }

    const bySpeaker = new Map(speakers.map((s) => [s.id, []]));
    const byCategory = new Map(categories.map((c) => [c.id, []]));
    const children = new Map();
    categories.forEach((c) => {
      if (c.parentId && categoryById.has(c.parentId)) {
        if (!children.has(c.parentId)) children.set(c.parentId, []);
        children.get(c.parentId).push(c);
      }
    });
    for (const l of lectures) {
      bySpeaker.get(l.speakerId).push(l);
      const ids = new Set(l.categoryIds); // a lecture in "সালাত" also counts for its parent "ইবাদত"
      l.categoryIds.forEach((id) => { const parent = categoryById.get(id).parentId; if (parent && byCategory.has(parent)) ids.add(parent); });
      ids.forEach((id) => byCategory.get(id).push(l));
    }
    return {
      manifest, speakers, categories, lectures, children, bySpeaker, byCategory, speakerById, categoryById,
      lectureById: new Map(lectures.map((l) => [l.id, l])),
    };
  }

  const newestFirst = (a, b) =>
    cmp(b.publishedAt || b.addedAt || "", a.publishedAt || a.addedAt || "") || cmp(b.addedAt || "", a.addedAt || "") || b.sortIndex - a.sortIndex;
  const recentlyAdded = (a, b) => cmp(b.addedAt || "", a.addedAt || "") || cmp(a.speakerId, b.speakerId) || b.sortIndex - a.sortIndex;
  const byPart = (a, b) => (a.series.part ?? 1e9) - (b.series.part ?? 1e9) || a.sortIndex - b.sortIndex;

  function seriesOf(l) {
    if (!l.series) return [];
    return catalog.bySpeaker.get(l.speakerId).filter((x) => x.series && x.series.id === l.series.id).sort(byPart);
  }
  function nextPart(l) {
    const parts = seriesOf(l);
    const i = parts.findIndex((x) => x.id === l.id);
    return i >= 0 && i < parts.length - 1 ? parts[i + 1] : null;
  }

  // ------------------------------------------------------------------ app release (download button)
  let release = null;
  function loadRelease() {
    try { release = JSON.parse(sessionStorage.getItem("tz.release")); } catch (e) { release = null; }
    if (release) return;
    fetch(`https://api.github.com/repos/${OWNER}/${REPO}/releases/latest`)
      .then((r) => (r.ok ? r.json() : null))
      .then((rel) => {
        if (!rel || !rel.tag_name || rel.tag_name.indexOf("app-v") !== 0) return;
        const assets = rel.assets || [];
        const apk = assets.find((a) => a.name === "tazkiyah.apk") || assets.find((a) => /\.apk$/.test(a.name));
        if (!apk) return;
        release = {
          version: rel.tag_name.slice(5),
          url: apk.browser_download_url,
          mb: (apk.size / 1048576).toFixed(1),
          date: dateText((rel.published_at || "").slice(0, 10)),
          sha: apk.digest ? apk.digest.replace("sha256:", "") : "",
        };
        try { sessionStorage.setItem("tz.release", JSON.stringify(release)); } catch (e) { /* ignore */ }
        fillRelease();
      })
      .catch(() => {});
  }
  function fillRelease() {
    if (!release) return;
    $$("[data-apk]").forEach((a) => { a.href = release.url; });
    $$("[data-release-meta]").forEach((el) => {
      el.textContent = `ভার্সন ${bn(release.version)} · ${bn(release.mb)} এমবি · ${release.date} · Android ৮.০+`;
    });
    $$("[data-checksum]").forEach((el) => {
      if (release.sha) el.innerHTML = `ফাইলের SHA-256: <code>${esc(release.sha)}</code>`;
    });
  }

  // ------------------------------------------------------------------ player
  const Player = (() => {
    const root = $("#player");
    const box = $("#player .player-in");
    const frame = $("#frame");
    const cover = $("#cover");
    const coverImg = $("#cover-img");
    const coverBtn = $("#cover-btn");
    const coverMsg = $("#cover-msg");
    const coverNext = $("#cover-next");
    const spinner = $("#spinner");
    const seek = $("#seek");
    const curEl = $("#cur");
    const durEl = $("#dur");
    const toggleBtn = $("#toggle");
    const miniToggle = $("#mini-toggle");
    const speedBtn = $("#speed");
    const fsBtn = $("#fs");
    const muteBtn = $("#mute");
    const volEl = $("#vol");

    let mode = "hidden";
    let lecture = null;
    let engine = null;
    const engines = {};
    let status = "idle"; // idle | cued | buffering | playing | paused | ended | error
    let started = false; // the visitor has started this lecture
    let wantPlay = false;
    let seeking = false;
    let tickTimer = 0;
    let fallbackTimer = 0;
    let lastSave = 0;
    let autoplayId = null;
    let rate = SPEEDS.includes(store.get("rate", 1)) ? store.get("rate", 1) : 1;
    const savedVolume = Number(store.get("volume", 100));
    let volume = Number.isFinite(savedVolume) ? Math.max(0, Math.min(100, Math.round(savedVolume))) : 100; // 0–100
    let muted = store.get("muted", false) === true;
    // iPhone and iPad only let the side buttons change the volume (muting still works), so no slider there.
    const volumeSettable = (() => {
      const ios = /iP(hone|ad|od)/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
      if (ios) return false;
      try { const a = document.createElement("audio"); a.volume = 0.5; return a.volume === 0.5; } catch (e) { return false; }
    })();

    // ---- YouTube engine (IFrame API) ----
    let ytApi = null;
    function loadYouTubeApi() {
      if (!ytApi) {
        ytApi = new Promise((resolve, reject) => {
          if (window.YT && window.YT.Player) { resolve(window.YT); return; }
          const previous = window.onYouTubeIframeAPIReady;
          window.onYouTubeIframeAPIReady = () => { if (typeof previous === "function") previous(); resolve(window.YT); };
          const s = document.createElement("script");
          s.src = "https://www.youtube.com/iframe_api";
          s.async = true;
          s.onerror = () => reject(new Error("YouTube API"));
          document.head.appendChild(s);
          setTimeout(() => reject(new Error("YouTube API timeout")), 20000);
        }).catch((e) => { ytApi = null; throw e; });
      }
      return ytApi;
    }

    function youtubeEngine() {
      let player = null;
      let ready = false;
      let createdWith = null;
      // The video this engine should show. A play() that arrives before it is handed to YouTube marks it
      // `autoplay`, so it is loaded playing instead of a playVideo() racing the old video.
      let target = null;
      const host = document.createElement("div");
      host.className = "yt-host";
      frame.prepend(host);
      function apply() {
        const t = target;
        t.applied = true;
        const args = { videoId: t.videoId, startSeconds: t.start };
        if (t.autoplay) player.loadVideoById(args);
        else player.cueVideoById(args);
      }
      return {
        kind: "youtube",
        isReady: () => ready,
        async load(l, start) {
          const t = { videoId: l.media.videoId, start: Math.floor(start), autoplay: false, applied: false };
          target = t;
          const YT = await loadYouTubeApi();
          if (target !== t) return; // another lecture was opened meanwhile
          if (player) { if (ready) apply(); return; }
          createdWith = t.videoId;
          player = new YT.Player(host, {
            host: "https://www.youtube-nocookie.com",
            width: "100%",
            height: "100%",
            videoId: target.videoId,
            playerVars: {
              autoplay: 0, controls: 0, rel: 0, iv_load_policy: 3, fs: 0, cc_load_policy: 0, disablekb: 1,
              playsinline: 1, modestbranding: 1, start: target.start, origin: location.origin, hl: "bn",
            },
            events: {
              onReady: () => {
                ready = true;
                const iframe = player.getIframe();
                iframe.setAttribute("tabindex", "-1");
                iframe.setAttribute("title", "লেকচারের ভিডিও");
                player.setPlaybackRate(rate);
                player.setVolume(volume);
                if (muted) player.mute(); else player.unMute();
                if (!target) return;
                if (target.videoId !== createdWith || target.autoplay || wantPlay) { target.autoplay = target.autoplay || wantPlay; apply(); }
                else target.applied = true;
              },
              onStateChange: (e) => {
                const S = window.YT.PlayerState;
                const map = { [S.UNSTARTED]: "unstarted", [S.ENDED]: "ended", [S.PLAYING]: "playing", [S.PAUSED]: "paused", [S.BUFFERING]: "buffering", [S.CUED]: "cued" };
                onEngineState(this_ === engine ? map[e.data] || "unstarted" : null);
              },
              onError: (e) => {
                if (this_ !== engine) return;
                onEngineError(e.data === 101 || e.data === 150 ? "embed" : e.data === 100 ? "gone" : "other");
              },
            },
          });
        },
        play() {
          if (target && !target.applied) { target.autoplay = true; return; }
          if (ready) player.playVideo();
        },
        pause() {
          if (target && !target.applied) target.autoplay = false;
          if (ready) player.pauseVideo();
        },
        stop() { if (target) target.autoplay = false; if (ready) player.stopVideo(); },
        seek(t) { if (ready) player.seekTo(t, true); },
        time() { return ready ? player.getCurrentTime() || 0 : 0; },
        duration() { return ready ? player.getDuration() || 0 : 0; },
        setRate(r) { if (ready) player.setPlaybackRate(r); },
        setVolume(vol, m) {
          if (!ready) return; // onReady applies the current volume
          player.setVolume(vol);
          if (m) player.mute(); else player.unMute();
        },
        show(on) { const el = player ? player.getIframe() : host; if (el) el.style.visibility = on ? "" : "hidden"; },
      };
    }

    // ---- direct audio/video files (<video> plays both) ----
    function html5Engine() {
      const v = document.createElement("video");
      v.playsInline = true;
      v.preload = "metadata";
      v.setAttribute("tabindex", "-1");
      frame.prepend(v);
      const applyVolume = (vol, m) => {
        try { v.volume = vol / 100; } catch (e) { /* read-only on some browsers */ }
        v.muted = m;
      };
      applyVolume(volume, muted);
      let startAt = 0;
      const self = () => engines.html5 === engine;
      v.addEventListener("loadedmetadata", () => { if (startAt) v.currentTime = startAt; v.playbackRate = rate; if (self()) onEngineState(wantPlay ? "buffering" : "cued"); });
      v.addEventListener("playing", () => self() && onEngineState("playing"));
      v.addEventListener("waiting", () => self() && onEngineState("buffering"));
      v.addEventListener("pause", () => { if (self() && !v.ended) onEngineState("paused"); });
      v.addEventListener("ended", () => self() && onEngineState("ended"));
      v.addEventListener("error", () => { if (self() && v.getAttribute("src")) onEngineError("other"); });
      return {
        kind: "html5",
        isReady: () => true,
        load(l, start) { startAt = start; v.src = l.media.url; v.load(); },
        play() { const p = v.play(); if (p && p.catch) p.catch(() => self() && onEngineState("paused")); },
        pause() { v.pause(); },
        stop() { v.pause(); v.removeAttribute("src"); v.load(); },
        seek(t) { v.currentTime = t; },
        time() { return v.currentTime || 0; },
        duration() { return Number.isFinite(v.duration) ? v.duration : 0; },
        setRate(r) { v.playbackRate = r; },
        setVolume: applyVolume,
        show(on) { v.style.visibility = on ? "" : "hidden"; },
      };
    }

    let this_ = null; // the YouTube engine object (its callbacks check it is still the active one)
    function engineFor(l) {
      const kind = l.media.type === "youtube" ? "youtube" : "html5";
      if (!engines[kind]) {
        engines[kind] = kind === "youtube" ? youtubeEngine() : html5Engine();
        if (kind === "youtube") this_ = engines.youtube;
      }
      Object.keys(engines).forEach((k) => {
        engines[k].show(k === kind);
        if (k !== kind) engines[k].stop();
      });
      return engines[kind];
    }

    // ---- state ----
    function resumeAt(l) {
      const p = progress[l.id];
      const d = l.durationSec || (p && p.d) || 0;
      if (p && p.p > 10 && (!d || p.p < d - 20)) return Math.max(0, p.p - 3);
      return (l.media && l.media.startSec) || 0;
    }

    function saveProgress() {
      if (!lecture || !engine || !started) return;
      lastSave = Date.now();
      const t = engine.time();
      if (t < 1) return;
      const d = engine.duration() || lecture.durationSec || 0;
      const prev = progress[lecture.id] || {};
      progress[lecture.id] = { p: Math.floor(t), d: Math.floor(d), t: Date.now(), c: !!prev.c || (d > 0 && t >= d - 20) };
      store.set("progress", progress);
    }
    function markFinished() {
      if (!lecture) return;
      progress[lecture.id] = { p: 0, d: Math.floor((engine && engine.duration()) || lecture.durationSec || 0), t: Date.now(), c: true };
      store.set("progress", progress);
    }

    function showTime(t, d) {
      if (!lecture) return;
      if (t === undefined) t = started && engine ? engine.time() : resumeAt(lecture);
      if (d === undefined) d = (engine && engine.duration()) || lecture.durationSec || 0;
      const shown = seeking ? Number(seek.value) : t;
      if (!seeking) {
        seek.max = String(Math.max(1, Math.floor(d)));
        seek.value = String(Math.floor(t));
      }
      curEl.textContent = clock(shown);
      durEl.textContent = clock(d);
      seek.setAttribute("aria-valuetext", `${clock(shown)} / ${clock(d)}`);
      $("#mini-bar").style.width = d ? `${Math.min(100, (shown / d) * 100)}%` : "0";
    }
    function startTicking() {
      stopTicking();
      tickTimer = setInterval(() => {
        showTime();
        if (Date.now() - lastSave > 5000) saveProgress();
      }, 500);
    }
    function stopTicking() { clearInterval(tickTimer); tickTimer = 0; }

    function updateButtons() {
      const playing = status === "playing" || (wantPlay && status !== "paused" && status !== "error");
      [toggleBtn, miniToggle].forEach((b) => {
        b.innerHTML = icon(playing ? "pause" : "play");
        b.setAttribute("aria-label", playing ? "থামান" : "চালান");
      });
      speedBtn.textContent = `${bn(rate)}x`;
      speedBtn.setAttribute("aria-label", `শোনার গতি ${bn(rate)}x`);
    }

    function setCover(kind, message) {
      cover.hidden = !kind;
      if (!kind) return;
      cover.dataset.kind = kind;
      coverBtn.hidden = kind === "artwork";
      coverBtn.innerHTML = icon(kind === "ended" || kind === "error" ? "replay" : "play");
      coverBtn.setAttribute("aria-label", kind === "ended" ? "আবার শুনুন" : kind === "error" ? "আবার চেষ্টা করুন" : "চালান");
      let text = message || "";
      if (kind === "start") {
        const at = resumeAt(lecture);
        if (at > ((lecture.media && lecture.media.startSec) || 0) + 5) text = `যেখানে থেমেছিলেন (${clock(at)}), সেখান থেকে চলবে`;
      } else if (kind === "paused") {
        text = `থেমে আছে · ${clock(engine ? engine.time() : 0)}`;
      } else if (kind === "ended") {
        text = "লেকচারটি শেষ হয়েছে";
      }
      coverMsg.textContent = text;
      coverMsg.hidden = !text;
      const next = kind === "ended" ? nextPart(lecture) : null;
      coverNext.hidden = !next;
      if (next) {
        coverNext.href = `#/lecture/${next.id}`;
        coverNext.textContent = `পরের পর্ব: ${next.title}`;
        coverNext.dataset.id = next.id;
      }
    }

    function tapThrough(on) {
      frame.classList.toggle("tap-through", on);
      if (on) { setCover(null); spinner.hidden = true; }
    }

    function onEngineState(s) {
      if (!lecture || !s) return;
      if (s === "playing") {
        status = "playing";
        started = true;
        wantPlay = true;
        tapThrough(false);
        clearTimeout(fallbackTimer);
        spinner.hidden = true;
        setCover(lecture.media.type === "audio" ? "artwork" : null);
        startTicking();
      } else if (s === "buffering") {
        status = "buffering";
        if (started && !frame.classList.contains("tap-through")) {
          spinner.hidden = false;
          setCover(lecture.media.type === "audio" ? "artwork" : null);
        }
      } else if (s === "paused") {
        status = "paused";
        wantPlay = false;
        spinner.hidden = true;
        stopTicking();
        saveProgress();
        setCover("paused");
      } else if (s === "ended") {
        status = "ended";
        wantPlay = false;
        spinner.hidden = true;
        stopTicking();
        markFinished();
        setCover("ended");
      } else if (!wantPlay) { // cued / unstarted
        status = "cued";
        spinner.hidden = true;
        if (!frame.classList.contains("tap-through")) setCover("start");
      }
      updateButtons();
      showTime();
    }

    function onEngineError(kind) {
      if (!lecture) return;
      status = "error";
      wantPlay = false;
      stopTicking();
      clearTimeout(fallbackTimer);
      tapThrough(false);
      spinner.hidden = true;
      const messages = {
        embed: "এই ভিডিওটি অন্য সাইটে চালানোর অনুমতি নেই। Android অ্যাপে শুনে দেখুন।",
        gone: "ভিডিওটি সরিয়ে ফেলা হয়েছে বা প্রাইভেট করা হয়েছে।",
        api: "প্লেয়ার লোড করা যায়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।",
        other: "ভিডিওটি এখন চালানো যাচ্ছে না। একটু পরে আবার চেষ্টা করুন।",
      };
      setCover("error", messages[kind] || messages.other);
      updateButtons();
    }

    // When the browser refuses to start the video from our button (some phones need a tap on the video
    // itself), open a hole in the middle of the shield for one tap; the edges stay covered.
    function armFallback() {
      clearTimeout(fallbackTimer);
      if (!engine || engine.kind !== "youtube") return;
      fallbackTimer = setTimeout(function check() {
        if (!wantPlay || status === "playing") return;
        if (!engine.isReady() || status === "buffering") { fallbackTimer = setTimeout(check, 1500); return; }
        tapThrough(true);
      }, 3000);
    }

    function load() {
      status = "idle";
      Promise.resolve(engine.load(lecture, resumeAt(lecture))).catch(() => onEngineError("api"));
    }

    function play() {
      if (!lecture || !engine) return;
      if (status === "error") load();
      wantPlay = true;
      started = true;
      if (status === "ended") engine.seek(0);
      setCover(lecture.media.type === "audio" ? "artwork" : null);
      spinner.hidden = false;
      engine.play();
      armFallback();
      updateButtons();
    }
    function pause() {
      wantPlay = false;
      if (engine) engine.pause();
      updateButtons();
    }
    function toggle() {
      if (status === "playing" || (wantPlay && status === "buffering")) pause();
      else play();
    }
    function skip(delta) {
      if (!engine || !started) return;
      const d = engine.duration() || lecture.durationSec || 0;
      const t = Math.max(0, Math.min(d ? d - 1 : Infinity, engine.time() + delta));
      engine.seek(t);
      showTime(t);
    }
    function cycleSpeed() {
      rate = SPEEDS[(SPEEDS.indexOf(rate) + 1) % SPEEDS.length];
      store.set("rate", rate);
      Object.values(engines).forEach((e) => e.setRate(rate));
      updateButtons();
    }

    function paintVolume() {
      const silent = muted || volume === 0;
      muteBtn.innerHTML = icon(silent ? "volumeOff" : volume < 50 ? "volumeDown" : "volumeUp");
      muteBtn.setAttribute("aria-label", silent ? "আওয়াজ চালু করুন" : "আওয়াজ বন্ধ করুন");
      const shown = muted ? 0 : volume;
      volEl.value = String(shown);
      volEl.setAttribute("aria-valuetext", `${bn(shown)}%`);
    }
    function applyVolume() {
      Object.values(engines).forEach((e) => e.setVolume(volume, muted));
      store.set("volume", volume);
      store.set("muted", muted);
      paintVolume();
    }
    function setVolume(v) {
      volume = Math.max(0, Math.min(100, Math.round(v)));
      muted = false;
      applyVolume();
    }
    /** Muting keeps the level; unmuting at 0 goes to half volume. */
    function toggleMute() {
      if (muted || volume === 0) {
        muted = false;
        if (volume === 0) volume = 50;
      } else {
        muted = true;
      }
      applyVolume();
    }
    function toggleFullscreen() {
      const doc = document;
      if (doc.fullscreenElement || doc.webkitFullscreenElement) {
        const exit = doc.exitFullscreen || doc.webkitExitFullscreen;
        if (exit) exit.call(doc);
        return;
      }
      const enter = box.requestFullscreen || box.webkitRequestFullscreen;
      if (enter) { const r = enter.call(box); if (r && r.catch) r.catch(() => {}); }
    }

    function setMode(next) {
      mode = next;
      root.hidden = next === "hidden";
      root.classList.toggle("mini", next === "mini");
      root.classList.toggle("full", next === "full");
      document.body.classList.toggle("has-mini", next === "mini");
    }

    /** Shows the player on a lecture page; keeps playing if it is already the lecture in the player. */
    function open(l) {
      setMode("full");
      const autoplay = autoplayId === l.id;
      autoplayId = null;
      if (lecture && lecture.id === l.id) {
        if (autoplay && status !== "playing") play();
        return;
      }
      saveProgress();
      stopTicking();
      clearTimeout(fallbackTimer);
      lecture = l;
      started = false;
      wantPlay = false;
      status = "idle";
      tapThrough(false);
      spinner.hidden = true;
      coverImg.src = thumbUrl(l, "hqdefault");
      $("#mini-link").href = `#/lecture/${l.id}`;
      $("#mini-title").textContent = l.title;
      $("#mini-speaker").textContent = l.speaker.name;
      setCover("start");
      showTime(resumeAt(l), l.durationSec || 0);
      engine = engineFor(l);
      load();
      if (autoplay) play();
      updateButtons();
    }

    /** Leaving the lecture page: keep a playing lecture in the mini player, otherwise close it. */
    function leave() {
      if (!lecture) { setMode("hidden"); return; }
      if (started && status !== "error" && status !== "ended") setMode("mini");
      else close();
    }

    function close() {
      saveProgress();
      stopTicking();
      clearTimeout(fallbackTimer);
      tapThrough(false);
      wantPlay = false;
      if (engine) engine.stop();
      lecture = null;
      started = false;
      status = "idle";
      setMode("hidden");
    }

    // ---- controls ----
    $$("[data-icon]", root).forEach((el) => { el.innerHTML = icon(el.dataset.icon); });
    if (!(box.requestFullscreen || box.webkitRequestFullscreen)) fsBtn.hidden = true;
    if (!volumeSettable) volEl.hidden = true;
    updateButtons();
    paintVolume();

    coverBtn.addEventListener("click", (e) => { e.stopPropagation(); if (mode === "mini") return openPage(); play(); });
    cover.addEventListener("click", (e) => {
      if (e.target.closest("#cover-next")) { autoplayId = coverNext.dataset.id || null; return; }
      if (mode === "mini") return openPage();
      play();
    });
    $("#shield").addEventListener("click", () => (mode === "mini" ? openPage() : toggle()));
    $("#shield").addEventListener("dblclick", () => mode === "full" && toggleFullscreen());
    toggleBtn.addEventListener("click", toggle);
    miniToggle.addEventListener("click", toggle);
    $("#mini-close").addEventListener("click", close);
    $("#back10").addEventListener("click", () => skip(-10));
    $("#fwd10").addEventListener("click", () => skip(10));
    speedBtn.addEventListener("click", cycleSpeed);
    muteBtn.addEventListener("click", toggleMute);
    volEl.addEventListener("input", () => setVolume(Number(volEl.value)));
    fsBtn.addEventListener("click", toggleFullscreen);
    seek.addEventListener("input", () => { seeking = true; showTime(); });
    seek.addEventListener("change", () => {
      seeking = false;
      const t = Number(seek.value);
      if (engine) engine.seek(t);
      if (engine && !started) { started = true; wantPlay = true; setCover(null); spinner.hidden = false; armFallback(); }
      showTime(t);
      if (status === "paused") setCover("paused");
    });
    document.addEventListener("keydown", (e) => {
      if (!lecture || mode === "hidden" || e.altKey || e.ctrlKey || e.metaKey) return;
      const t = e.target;
      if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
      if (e.key === " " || e.key === "k") {
        if (e.key === " " && t && t.closest && t.closest("button, a, summary")) return;
        e.preventDefault();
        toggle();
      } else if (mode === "full" && (e.key === "ArrowLeft" || e.key === "j")) {
        e.preventDefault();
        skip(-10);
      } else if (mode === "full" && (e.key === "ArrowRight" || e.key === "l")) {
        e.preventDefault();
        skip(10);
      } else if (mode === "full" && e.key === "f") {
        toggleFullscreen();
      } else if (e.key === "m") {
        toggleMute();
      }
    });
    window.addEventListener("pagehide", saveProgress);
    document.addEventListener("visibilitychange", () => { if (document.hidden) saveProgress(); });

    function openPage() { if (lecture) location.hash = `#/lecture/${lecture.id}`; }

    return { open, leave, close, current: () => lecture };
  })();

  // ------------------------------------------------------------------ building blocks
  function thumbUrl(l, size = "mqdefault") {
    if (l.media.type === "youtube") return `https://i.ytimg.com/vi/${l.media.videoId}/${size}.jpg`;
    return l.speaker.photo ? CONTENT_BASES[0] + l.speaker.photo : "assets/icon.svg";
  }
  function photo(s, cls = "spk-photo") {
    if (s.photo) return `<img class="${cls}" src="${esc(CONTENT_BASES[0] + s.photo)}" alt="" loading="lazy" width="104" height="104">`;
    return `<span class="${cls} avatar" aria-hidden="true">${esc(Array.from(s.name)[0] || "")}</span>`;
  }
  function percent(l) {
    const p = progress[l.id];
    if (!p) return 0;
    if (p.c) return 100;
    const d = p.d || l.durationSec;
    return d ? Math.min(100, (p.p / d) * 100) : 0;
  }
  function thumb(l) {
    const pct = percent(l);
    return `<span class="thumb"><img src="${esc(thumbUrl(l))}" alt="" loading="lazy" width="320" height="180">` +
      (l.durationSec ? `<span class="dur">${clock(l.durationSec)}</span>` : "") +
      (pct ? `<span class="bar"><span style="width:${pct.toFixed(1)}%"></span></span>` : "") + `</span>`;
  }
  function favButton(id, labelled) {
    const on = !!favorites[id];
    return `<button type="button" class="fav${labelled ? " labelled" : ""}" data-fav="${esc(id)}" aria-pressed="${on}" ` +
      `aria-label="${on ? "ফেভারিট থেকে সরান" : "ফেভারিটে রাখুন"}">${icon("heart")}` +
      (labelled ? `<span>${on ? "ফেভারিটে আছে" : "ফেভারিট"}</span>` : "") + `</button>`;
  }
  function badges(l) {
    const out = [];
    if (isNew(l)) out.push(`<span class="badge">নতুন</span>`);
    if (progress[l.id] && progress[l.id].c) out.push(`<span class="badge done">শোনা হয়েছে</span>`);
    return out.length ? `<span class="badges">${out.join("")}</span>` : "";
  }
  function lectureRow(l, opts = {}) {
    const meta = [opts.hideSpeaker ? "" : esc(l.speaker.name), lengthText(l.durationSec)].filter(Boolean).join(" · ");
    const part = opts.showPart && l.series && l.series.part ? `<span class="part">পর্ব ${bn(l.series.part)} · </span>` : "";
    return `<li class="lec"${opts.current === l.id ? ' aria-current="true"' : ""}>
      <a class="lec-link" href="#/lecture/${esc(l.id)}">${thumb(l)}
        <span class="lec-body"><span class="lec-title">${part}${esc(l.title)}</span>
        <span class="lec-meta">${meta}</span>${badges(l)}</span></a>${favButton(l.id)}</li>`;
  }
  const lectureList = (list, opts = {}) => `<ul class="lec-list${opts.two ? " two" : ""}">${list.map((l) => lectureRow(l, opts)).join("")}</ul>`;
  function lectureCard(l) {
    return `<li><a class="card-lec" href="#/lecture/${esc(l.id)}">${thumb(l)}
      <span class="card-title">${esc(l.title)}</span>
      <span class="lec-meta">${esc(l.speaker.name)} · ${lengthText(l.durationSec)}</span>${badges(l)}</a></li>`;
  }
  const cardRow = (list) => `<ul class="card-row">${list.map(lectureCard).join("")}</ul>`;
  function section(title, body, moreHref) {
    return `<section class="sec"><div class="sec-head"><h2>${title}</h2>` +
      (moreHref ? `<a class="more" href="${moreHref}">সব দেখুন</a>` : "") + `</div>${body}</section>`;
  }
  function speakerCard(s, withTopics) {
    const lectures = catalog.bySpeaker.get(s.id);
    let topics = "";
    if (withTopics) {
      const top = topicCounts(lectures).slice(0, 3).map(([c, n]) => `${esc(c.name)} (${bn(n)})`);
      if (top.length) topics = `<span class="spk-topics">${top.join(" · ")}</span>`;
    }
    return `<a class="spk" href="#/speaker/${esc(s.id)}">${photo(s)}<span class="spk-text">
      <span class="spk-name">${esc(s.name)}</span>${s.nameEn ? `<span class="spk-en">${esc(s.nameEn)}</span>` : ""}
      <span class="spk-count">${bn(lectures.length)}টি লেকচার · ${hoursText(lectures)}</span>
      ${s.bio ? `<span class="spk-bio">${esc(s.bio)}</span>` : ""}${topics}</span></a>`;
  }
  function hoursText(list) {
    const sec = list.reduce((sum, l) => sum + (l.durationSec || 0), 0);
    if (!sec) return "";
    return sec < 3600 ? `${bn(Math.round(sec / 60))} মিনিট` : `${bn(Math.round(sec / 3600))} ঘণ্টা`;
  }
  /** [[category, count]] for the lectures' own topics (like the app's chips), most first. */
  function topicCounts(list) {
    const counts = new Map();
    list.forEach((l) => l.categoryIds.forEach((id) => counts.set(id, (counts.get(id) || 0) + 1)));
    return catalog.categories.filter((c) => counts.has(c.id)).map((c) => [c, counts.get(c.id)]).sort((a, b) => b[1] - a[1]);
  }
  function downloadButton(big) {
    return `<a class="btn${big ? " big" : ""}" data-apk href="${APK_URL}">${icon("download")}<span>Android অ্যাপ ডাউনলোড</span></a>`;
  }
  const loadingBox = () => `<p class="loading"><span class="spin"></span>লেকচারের তালিকা আনা হচ্ছে…</p>`;
  const errorBox = () => `<div class="empty"><p>লেকচারের তালিকা আনা যায়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন।</p>
    <button type="button" class="btn" data-retry>আবার চেষ্টা করুন</button></div>`;

  // ------------------------------------------------------------------ pages
  function pageHome() {
    const hero = `<section class="hero">
      <img class="hero-icon" src="assets/icon.svg" alt="" width="92" height="92">
      <div>
        <h1>তাযকিয়া</h1>
        <p class="tagline">আত্মশুদ্ধির পথে প্রতিদিন</p>
        <p class="lead">বাছাই করা বাংলা ইসলামিক লেকচার — বক্তা ও বিষয় অনুযায়ী সাজানো, মনোযোগ ধরে রেখে শোনার জন্য। এখানেই শুনুন, অথবা অফলাইনে আর স্ক্রিন বন্ধ রেখে শুনতে Android অ্যাপ নিন।</p>
        <div class="hero-actions">${downloadButton()}<a class="btn ghost" href="#/app">ইনস্টলের নিয়ম</a></div>
        <p class="meta" data-release-meta>ফ্রি · Android ৮.০ বা তার পরের ভার্সন</p>
      </div></section>`;
    if (!catalog) return { html: hero + (contentState === "error" ? errorBox() : loadingBox()), tab: "home" };

    const c = catalog;
    const topics = c.categories.filter((k) => !k.parentId && c.byCategory.get(k.id).length);
    const totalHours = Math.round(c.lectures.reduce((s, l) => s + (l.durationSec || 0), 0) / 3600);
    const cont = Object.entries(progress)
      .filter(([id, p]) => p && !p.c && p.p > 15 && c.lectureById.has(id))
      .sort((a, b) => b[1].t - a[1].t).slice(0, 10).map(([id]) => c.lectureById.get(id));
    const today = todayPick();
    const recent = c.lectures.slice().sort(recentlyAdded).slice(0, 8);
    const updated = c.manifest.updatedAt ? `<p class="updated">তালিকা হালনাগাদ: ${dateText(c.manifest.updatedAt.slice(0, 10))}</p>` : "";

    const html = hero +
      `<div class="stats">
        <a class="stat" href="#/speakers" style="text-decoration:none"><b>${bn(c.speakers.length)}</b><span>জন বক্তা</span></a>
        <div class="stat"><b>${bn(c.lectures.length)}</b><span>টি লেকচার</span></div>
        <div class="stat"><b>${bn(totalHours)}</b><span>ঘণ্টা</span></div>
      </div>${updated}` +
      (cont.length ? section("চালিয়ে যান", cardRow(cont)) : "") +
      (today ? section("আজকের লেকচার", `<a class="today" href="#/lecture/${esc(today.id)}">${thumb(today)}
          <span class="today-body"><b>${esc(today.title)}</b><span>${esc(today.speaker.name)} · ${lengthText(today.durationSec)}</span>
          <span class="btn">${icon("play")}শুনুন</span></span></a>`) : "") +
      section("বক্তাগণ", `<div class="spk-grid">${c.speakers.map((s) => speakerCard(s, false)).join("")}</div>`, "#/speakers") +
      section("নতুন যোগ হয়েছে", cardRow(recent)) +
      section("বিষয়ভিত্তিক", `<div class="chips wrap">${topics.map((t) =>
        `<a class="chip" href="#/topic/${esc(t.id)}">${esc(t.name)} <span class="n">(${bn(c.byCategory.get(t.id).length)})</span></a>`).join("")}</div>`, "#/topics");
    return { html, tab: "home" };
  }

  /** One lecture a day from those not finished yet; the same all day. */
  function todayPick() {
    const pool = catalog.lectures.filter((l) => !(progress[l.id] && progress[l.id].c)).sort((a, b) => cmp(a.id, b.id));
    if (!pool.length) return null;
    let x = Math.floor(Date.parse(localIso()) / 864e5) >>> 0;
    x = Math.imul(x ^ (x >>> 16), 0x45d9f3b) >>> 0;
    x = Math.imul(x ^ (x >>> 16), 0x45d9f3b) >>> 0;
    return pool[(x ^ (x >>> 16)) % pool.length];
  }

  function pageSpeakers() {
    const c = catalog;
    return {
      title: "বক্তাগণ",
      tab: "speakers",
      html: `<h1 class="page-title">বক্তাগণ</h1>
        <p class="sub">${bn(c.speakers.length)} জন বক্তা · মোট ${bn(c.lectures.length)}টি লেকচার</p>
        <div class="spk-grid" style="margin-top:16px">${c.speakers.map((s) => speakerCard(s, true)).join("")}</div>`,
    };
  }

  function pageSpeaker(id, params) {
    const c = catalog;
    const s = c.speakerById.get(id);
    if (!s) return pageMissing("বক্তা পাওয়া যায়নি");
    const all = c.bySpeaker.get(id);
    const counts = topicCounts(all).sort((a, b) => (a[0].order ?? 1e9) - (b[0].order ?? 1e9));
    const topic = c.categoryById.has(params.get("topic")) ? params.get("topic") : "";
    const hasSeries = all.some((l) => l.series);
    let sort = params.get("sort") || "new";
    if (sort === "series" && !hasSeries) sort = "new";
    const list = topic ? all.filter((l) => l.categoryIds.includes(topic)) : all.slice();

    let body;
    if (!list.length) body = `<p class="empty">এই বিষয়ে কোনো লেকচার নেই।</p>`;
    else if (sort === "series") {
      const groups = new Map();
      const single = [];
      list.forEach((l) => {
        if (!l.series) { single.push(l); return; }
        if (!groups.has(l.series.id)) groups.set(l.series.id, []);
        groups.get(l.series.id).push(l);
      });
      body = Array.from(groups.values()).map((parts) => {
        parts.sort(byPart);
        return `<h2 class="group-title">${esc(parts[0].series.title)} (${bn(parts.length)} পর্ব)</h2>${lectureList(parts, { hideSpeaker: true, showPart: true, two: true })}`;
      }).join("") + (single.length ? `<h2 class="group-title">একক লেকচার</h2>${lectureList(single.sort(newestFirst), { hideSpeaker: true, two: true })}` : "");
    } else {
      list.sort(newestFirst);
      if (sort === "old") list.reverse();
      body = lectureList(list, { hideSpeaker: true, two: true });
    }

    const chip = (value, label, n) => `<button type="button" class="chip" data-set="topic=${esc(value)}" aria-pressed="${topic === value}">${esc(label)} <span class="n">(${bn(n)})</span></button>`;
    const sortBtn = (value, label) => `<button type="button" data-set="sort=${value}" aria-pressed="${sort === value}">${label}</button>`;
    return {
      title: s.name,
      tab: "speakers",
      html: `<div class="profile">${photo(s)}<div>
          <h1>${esc(s.name)}</h1>${s.nameEn ? `<p class="en">${esc(s.nameEn)}</p>` : ""}
          <p class="count">${bn(all.length)}টি লেকচার · ${hoursText(all)}</p></div></div>
        ${s.bio ? `<p class="bio">${esc(s.bio)}</p>` : ""}
        <div class="chips" role="group" aria-label="বিষয়">${chip("", "সব বিষয়", all.length)}${counts.map(([cat, n]) => chip(cat.id, cat.name, n)).join("")}</div>
        <div class="seg" role="group" aria-label="সাজানো">${sortBtn("new", "নতুন আগে")}${sortBtn("old", "পুরনো আগে")}${hasSeries ? sortBtn("series", "সিরিজ অনুযায়ী") : ""}</div>
        ${body}`,
    };
  }

  function pageTopics() {
    const c = catalog;
    const parents = c.categories.filter((k) => !k.parentId && c.byCategory.get(k.id).length);
    const cards = parents.map((k) => {
      const kids = (c.children.get(k.id) || []).filter((ch) => c.byCategory.get(ch.id).length);
      return `<div class="topic"><a href="#/topic/${esc(k.id)}"><span class="topic-mark" aria-hidden="true">${esc(Array.from(k.name)[0])}</span>
        <span><b>${esc(k.name)}</b><span class="n">${bn(c.byCategory.get(k.id).length)}টি লেকচার</span></span></a>
        ${kids.length ? `<div class="chips wrap">${kids.map((ch) => `<a class="chip" href="#/topic/${esc(ch.id)}">${esc(ch.name)} <span class="n">(${bn(c.byCategory.get(ch.id).length)})</span></a>`).join("")}</div>` : ""}</div>`;
    }).join("");
    return { title: "বিষয়", tab: "topics", html: `<h1 class="page-title">বিষয়ভিত্তিক লেকচার</h1><p class="sub">${bn(parents.length)}টি বিষয়</p><div class="topic-grid" style="margin-top:16px">${cards}</div>` };
  }

  function pageTopic(id, params) {
    const c = catalog;
    const k = c.categoryById.get(id);
    if (!k) return pageMissing("বিষয় পাওয়া যায়নি");
    const all = c.byCategory.get(id);
    const speakerCounts = c.speakers.map((s) => [s, all.filter((l) => l.speakerId === s.id).length]).filter(([, n]) => n);
    const speaker = c.speakerById.has(params.get("speaker")) ? params.get("speaker") : "";
    const list = (speaker ? all.filter((l) => l.speakerId === speaker) : all.slice()).sort(newestFirst);
    const parent = k.parentId && c.categoryById.get(k.parentId);
    const kids = (c.children.get(k.id) || []).filter((ch) => c.byCategory.get(ch.id).length);
    const chip = (value, label, n) => `<button type="button" class="chip" data-set="speaker=${esc(value)}" aria-pressed="${speaker === value}">${esc(label)} <span class="n">(${bn(n)})</span></button>`;
    return {
      title: k.name,
      tab: "topics",
      html: `${parent ? `<a class="more" href="#/topic/${esc(parent.id)}">${esc(parent.name)}</a>` : ""}
        <h1 class="page-title">${esc(k.name)}</h1><p class="sub">${bn(all.length)}টি লেকচার · ${hoursText(all)}</p>
        ${kids.length ? `<div class="chips wrap">${kids.map((ch) => `<a class="chip" href="#/topic/${esc(ch.id)}">${esc(ch.name)} <span class="n">(${bn(c.byCategory.get(ch.id).length)})</span></a>`).join("")}</div>` : ""}
        ${speakerCounts.length > 1 ? `<div class="chips" role="group" aria-label="বক্তা">${chip("", "সব বক্তা", all.length)}${speakerCounts.map(([s, n]) => chip(s.id, s.name, n)).join("")}</div>` : ""}
        <div style="margin-top:12px">${list.length ? lectureList(list, { two: true }) : `<p class="empty">এই বিষয়ে এখনো কোনো লেকচার নেই।</p>`}</div>`,
    };
  }

  function pageLecture(id) {
    const c = catalog;
    const l = c.lectureById.get(id);
    if (!l) return pageMissing("লেকচারটি পাওয়া যায়নি — হয়তো তালিকা থেকে সরানো হয়েছে।");
    const s = l.speaker;
    const parts = seriesOf(l);
    const inSeries = new Set(parts.map((x) => x.id));
    const more = c.bySpeaker.get(l.speakerId).filter((x) => x.id !== l.id && !inSeries.has(x.id)).sort(newestFirst).slice(0, 8);
    const cats = l.categoryIds.map((cid) => c.categoryById.get(cid));
    const meta = [lengthText(l.durationSec), l.publishedAt ? `প্রকাশ: ${dateText(l.publishedAt)}` : ""].filter(Boolean).join(" · ");
    return {
      title: l.title,
      tab: "",
      lecture: l,
      html: `<article>
          <h1 class="lecture-title">${l.series && l.series.part ? `<span class="part">পর্ব ${bn(l.series.part)} · </span>` : ""}${esc(l.title)}</h1>
          <a class="speaker-chip" href="#/speaker/${esc(s.id)}">${photo(s)}<span>${esc(s.name)}</span></a>
          ${meta ? `<p class="meta">${meta}</p>` : ""}
          <div class="actions-row">${favButton(l.id, true)}
            <button type="button" class="btn ghost" data-share="${esc(l.id)}">${icon("share")}শেয়ার</button></div>
          ${cats.length ? `<div class="chips wrap">${cats.map((k) => `<a class="chip" href="#/topic/${esc(k.id)}">${esc(k.name)}</a>`).join("")}</div>` : ""}
          ${l.description ? `<p class="desc">${esc(l.description)}</p>` : ""}
          ${l.tags && l.tags.length ? `<p class="tags">${l.tags.map((t) => `#${esc(t)}`).join(" ")}</p>` : ""}
          <p class="nudge">ইন্টারনেট ছাড়া শুনতে, বা স্ক্রিন বন্ধ রেখে শুনতে <a href="#/app">Android অ্যাপটি</a> নিন — ফ্রি।</p>
        </article>
        ${parts.length > 1 ? section(`সিরিজ: ${esc(l.series.title)}`, lectureList(parts, { hideSpeaker: true, showPart: true, current: l.id })) : ""}
        ${more.length ? section("এই বক্তার আরও লেকচার", lectureList(more, { hideSpeaker: true, two: true }), `#/speaker/${esc(s.id)}`) : ""}`,
    };
  }

  function rememberSearch(q) {
    const v = String(q || "").trim();
    if (!v) return;
    const recent = store.get("recentSearches", []).filter((x) => x !== v);
    recent.unshift(v);
    store.set("recentSearches", recent.slice(0, 8));
  }
  function searchResults(q) {
    const qt = tokens(q);
    if (!qt.length) {
      const recent = store.get("recentSearches", []);
      return (recent.length ? `<div class="sec-head" style="margin-top:20px"><h2 class="h3">সাম্প্রতিক খোঁজ</h2><button type="button" class="link" data-clear-recent>মুছুন</button></div>
          <div class="chips wrap">${recent.map((r) => `<button type="button" class="chip" data-q="${esc(r)}">${esc(r)}</button>`).join("")}</div>` : "") +
        `<p class="empty">লেকচার খুঁজতে লিখুন — বাংলা বা ইংরেজিতে</p>`;
    }
    const matches = (words) => qt.every((t) => words.some((w) => w.startsWith(t)));
    const speakers = catalog.speakers.filter((s) => matches(s.words));
    const hits = [];
    for (const l of catalog.lectures) {
      if (!matches(l.words)) continue;
      hits.push([qt.filter((t) => l.titleWords.some((w) => w.startsWith(t))).length, l]);
    }
    hits.sort((a, b) => b[0] - a[0] || newestFirst(a[1], b[1]));
    const speakerChips = speakers.length ? `<div class="chips wrap">${speakers.map((s) =>
      `<a class="chip spk-link" href="#/speaker/${esc(s.id)}">${photo(s, "")}${esc(s.name)} <span class="n">(${bn(catalog.bySpeaker.get(s.id).length)})</span></a>`).join("")}</div>` : "";
    if (!hits.length && !speakers.length) return `<p class="empty">"${esc(q.trim())}" দিয়ে কিছু পাওয়া যায়নি</p>`;
    const shown = hits.slice(0, 200);
    return `${speakerChips}<p class="result-count">${bn(hits.length)}টি লেকচার${hits.length > shown.length ? ` (প্রথম ${bn(shown.length)}টি দেখানো হচ্ছে)` : ""}</p>
      ${lectureList(shown.map((h) => h[1]), { two: true })}`;
  }
  function pageSearch(params) {
    const q = params.get("q") || "";
    return {
      title: q ? `খোঁজ: ${q}` : "খুঁজুন",
      tab: "search",
      html: `<h1 class="sr" style="position:absolute;left:-9999px">খুঁজুন</h1>
        <div class="search-box">${icon("search")}<input id="q" type="search" value="${esc(q)}" placeholder="শিরোনাম, বিষয় বা বক্তার নাম"
          autocomplete="off" enterkeyhint="search" aria-label="লেকচার খুঁজুন"></div>
        <div id="results" aria-live="polite">${searchResults(q)}</div>`,
      after() {
        const input = $("#q");
        const out = $("#results");
        if (!q) input.focus();
        let timer = 0;
        input.addEventListener("input", () => {
          clearTimeout(timer);
          timer = setTimeout(() => {
            replaceHash("/search", input.value.trim() ? { q: input.value } : {});
            out.innerHTML = searchResults(input.value);
          }, 150);
        });
        input.addEventListener("keydown", (e) => { if (e.key === "Enter") { rememberSearch(input.value); input.blur(); } });
        out.addEventListener("click", (e) => { if (e.target.closest(".lec-link, .spk-link")) rememberSearch(input.value); });
      },
    };
  }

  function pageLibrary(params) {
    const c = catalog;
    const tab = ["fav", "history", "done"].includes(params.get("tab")) ? params.get("tab") : "fav";
    const favList = Object.entries(favorites).filter(([id]) => c.lectureById.has(id)).sort((a, b) => b[1] - a[1]).map(([id]) => c.lectureById.get(id));
    const played = Object.entries(progress).filter(([id, p]) => p && c.lectureById.has(id)).sort((a, b) => b[1].t - a[1].t);
    const history = played.map(([id]) => c.lectureById.get(id));
    const done = played.filter(([, p]) => p.c).map(([id]) => c.lectureById.get(id));
    const lists = {
      fav: [favList, "ফেভারিটে এখনো কিছু নেই। লেকচারের পাশে ♡ চাপুন।"],
      history: [history, "এখনো কিছু শোনা হয়নি।"],
      done: [done, "শেষ করা লেকচার এখানে দেখা যাবে।"],
    };
    const [list, empty] = lists[tab];
    const tabBtn = (value, label, n) => `<button type="button" data-set="tab=${value}" aria-pressed="${tab === value}">${label} (${bn(n)})</button>`;
    return {
      title: "আমার",
      tab: "library",
      html: `<h1 class="page-title">আমার</h1>
        <p class="sub">এই তালিকা শুধু এই ব্রাউজারে থাকে। অ্যাপের তালিকা আলাদা।</p>
        <div class="seg" role="group" aria-label="তালিকা">${tabBtn("fav", "ফেভারিট", favList.length)}${tabBtn("history", "ইতিহাস", history.length)}${tabBtn("done", "শেষ করা", done.length)}</div>
        ${list.length ? lectureList(list, { two: true }) : `<p class="empty">${empty}</p>`}`,
    };
  }

  function pageApp() {
    const shots = [1, 2, 3, 4].map((n) => `<img src="assets/shot-${n}.webp" alt="অ্যাপের স্ক্রিনশট ${bn(n)}" loading="lazy" width="200" height="433">`).join("");
    return {
      title: "Android অ্যাপ",
      tab: "",
      html: `<section class="hero">
          <img class="hero-icon" src="assets/icon.svg" alt="" width="92" height="92">
          <div><h1>তাযকিয়া Android অ্যাপ</h1>
            <p class="lead">ফ্রি, বিজ্ঞাপন নেই, অ্যাকাউন্ট লাগে না। ওয়েবের সব লেকচার, সাথে অফলাইনে শোনা আর স্ক্রিন বন্ধ রেখে শোনার সুবিধা।</p>
            <div class="hero-actions">${downloadButton(true)}</div>
            <p class="meta" data-release-meta>ফ্রি · Android ৮.০ বা তার পরের ভার্সন</p></div>
        </section>
        ${section("অ্যাপে বাড়তি যা পাবেন", `<div class="cards">
          <div class="card"><b>অফলাইনে সেভ</b><span>শুধু অডিও বা ভিডিও ফোনে রেখে নেট ছাড়াই শুনুন।</span></div>
          <div class="card"><b>স্ক্রিন বন্ধ রেখেও</b><span>অ্যাপ মিনিমাইজ করলেও শোনা চলে; লক স্ক্রিন থেকে নিয়ন্ত্রণ।</span></div>
          <div class="card"><b>স্লিপ টাইমার ও শুধু-অডিও মোড</b><span>ঘুমানোর আগে শুনুন; ডেটা বাঁচাতে শুধু শব্দ।</span></div>
          <div class="card"><b>নতুন লেকচার নিজে থেকেই</b><span>তালিকা হালনাগাদ হয়, নতুন ভার্সন এলে অ্যাপই জানায়।</span></div>
          <div class="card"><b>পছন্দের বাংলা ফন্ট</b><span>কয়েকটি বাংলা ফন্ট থেকে বেছে নিন।</span></div>
          <div class="card"><b>যেখানে থেমেছিলেন</b><span>সেখান থেকেই আবার চালু; ফেভারিট ও ইতিহাস।</span></div></div>`)}
        ${section("দেখতে যেমন", `<div class="shots">${shots}</div>`)}
        ${section("ইনস্টল করবেন যেভাবে", `<ol class="steps">
          <li>ওপরের <b>ডাউনলোড</b> বোতাম চাপুন, <code>tazkiyah.apk</code> ফাইল নামবে।</li>
          <li>ফাইলটি খুলুন। প্রথমবার ফোন <b>"অজানা উৎস থেকে ইনস্টল"</b> (Install unknown apps) অনুমতি চাইবে — যে অ্যাপ দিয়ে খুলছেন (Chrome/Files), তাকে অনুমতি দিন।</li>
          <li><b>Install</b> চাপুন। নতুন ভার্সন এলে অ্যাপই জানিয়ে দেবে; একইভাবে বসালে আপনার ফেভারিট ও ইতিহাস থেকে যায়।</li></ol>
          <p class="note">Play Protect "অজানা অ্যাপ" বলে সতর্ক করতে পারে — অ্যাপটি Play Store-এ নেই বলে এমন বলে। <b>More details → Install anyway</b> চাপলেই হবে।</p>`)}
        ${section("প্রশ্নোত্তর", `
          <details><summary>অ্যাপটি কি নিরাপদ?</summary>
            <p>অ্যাপের পুরো সোর্স কোড <a href="${REPO_URL}">GitHub-এ খোলা</a>; প্রতিটি রিলিজ একই সাইনিং key-তে সই করা। অ্যাপ কোনো অ্যাকাউন্ট চায় না, আপনার কোনো তথ্য সংগ্রহ করে না।</p>
            <p class="meta" data-checksum></p></details>
          <details><summary>Play Store-এ নেই কেন?</summary>
            <p>অফলাইন সেভ আর স্ক্রিন বন্ধ রেখে শোনার সুবিধা ইউটিউবের ডেভেলপার নীতির বাইরে, তাই অ্যাপটি ব্যক্তিগতভাবে বিতরণ করা হয়।</p></details>
          <details><summary>ওয়েব আর অ্যাপে পার্থক্য কী?</summary>
            <p>ওয়েবে ব্রাউজার থেকেই সব লেকচার শোনা যায়। অ্যাপে এর সাথে অফলাইন সেভ, স্ক্রিন বন্ধ রেখে শোনা, লক স্ক্রিন নিয়ন্ত্রণ আর স্লিপ টাইমার আছে। দুই জায়গার ফেভারিট ও ইতিহাস আলাদা।</p></details>
          <details><summary>লেকচারগুলো কোথা থেকে আসে?</summary>
            <p>বক্তাদের অফিশিয়াল ও অন্যান্য ইউটিউব চ্যানেল থেকে বাছাই করা; ভিডিও চলে ইউটিউবের প্লেয়ারেই। লেকচারের স্বত্ব বক্তা ও আপলোডকারীদের; এই অ্যাপ কোনো বক্তা বা ইউটিউবের সাথে সম্পর্কিত নয়।</p></details>
          <details><summary>আইফোনে চলবে?</summary>
            <p>আইফোনের অ্যাপ এখনো নেই — তবে এই ওয়েবসাইট আইফোনের ব্রাউজারেও চলে।</p></details>`)}`,
    };
  }

  function pageMissing(message) {
    return { title: "পাওয়া যায়নি", tab: "", html: `<div class="empty"><p>${esc(message || "পাতাটি পাওয়া যায়নি।")}</p><a class="btn" href="#/">হোমে যান</a></div>` };
  }

  // ------------------------------------------------------------------ router
  const view = $("#view");
  const navStack = [];
  const scrollMemory = new Map();

  function parseHash() {
    const raw = location.hash.replace(/^#/, "") || "/";
    const i = raw.indexOf("?");
    return { path: (i < 0 ? raw : raw.slice(0, i)) || "/", params: new URLSearchParams(i < 0 ? "" : raw.slice(i + 1)) };
  }
  function replaceHash(path, params) {
    const q = new URLSearchParams(params).toString();
    const hash = `#${path}${q ? `?${q}` : ""}`;
    history.replaceState(history.state, "", hash);
    navStack[navStack.length - 1] = hash;
  }
  function setParams(update) {
    const { path, params } = parseHash();
    Object.entries(update).forEach(([k, v]) => (v ? params.set(k, v) : params.delete(k)));
    replaceHash(path, params);
    render({ keepScroll: true });
  }
  function withCatalog(fn, tab) {
    if (catalog) return fn();
    return { tab, html: contentState === "error" ? errorBox() : loadingBox() };
  }

  function render(opts = {}) {
    const { path, params } = parseHash();
    let m;
    let page;
    if (path === "/") page = pageHome();
    else if (path === "/speakers") page = withCatalog(pageSpeakers, "speakers");
    else if ((m = /^\/speaker\/([\w-]+)$/.exec(path))) page = withCatalog(() => pageSpeaker(m[1], params), "speakers");
    else if (path === "/topics") page = withCatalog(pageTopics, "topics");
    else if ((m = /^\/topic\/([\w-]+)$/.exec(path))) page = withCatalog(() => pageTopic(m[1], params), "topics");
    else if ((m = /^\/lecture\/([\w-]+)$/.exec(path))) page = withCatalog(() => pageLecture(m[1]), "");
    else if (path === "/search") page = withCatalog(() => pageSearch(params), "search");
    else if (path === "/library") page = withCatalog(() => pageLibrary(params), "library");
    else if (path === "/app") page = pageApp();
    else page = pageMissing();

    view.innerHTML = page.html;
    document.title = page.title ? `${page.title} — তাযকিয়া` : DEFAULT_TITLE;
    $$(".tabs a").forEach((a) => (a.dataset.tab === page.tab ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current")));
    const searchLink = $("[data-tab-link='search']");
    if (page.tab === "search") searchLink.setAttribute("aria-current", "page"); else searchLink.removeAttribute("aria-current");
    if (page.lecture) Player.open(page.lecture);
    else Player.leave();
    fillRelease();
    if (page.after) page.after();
    if (!opts.keepScroll) window.scrollTo(0, opts.scrollTo || 0);
  }

  window.addEventListener("hashchange", (e) => {
    const oldHash = new URL(e.oldURL).hash || "#/";
    scrollMemory.set(oldHash, window.scrollY);
    const back = navStack.length > 1 && navStack[navStack.length - 2] === location.hash;
    if (back) navStack.pop();
    else navStack.push(location.hash);
    render({ scrollTo: back ? scrollMemory.get(location.hash) || 0 : 0 });
    view.focus({ preventScroll: true });
  });

  // ------------------------------------------------------------------ page-wide actions
  function toggleFavorite(id) {
    if (favorites[id]) delete favorites[id];
    else favorites[id] = Date.now();
    store.set("favorites", favorites);
    const on = !!favorites[id];
    $$(".fav").filter((b) => b.dataset.fav === id).forEach((b) => {
      b.setAttribute("aria-pressed", String(on));
      b.setAttribute("aria-label", on ? "ফেভারিট থেকে সরান" : "ফেভারিটে রাখুন");
      const label = $("span", b);
      if (label) label.textContent = on ? "ফেভারিটে আছে" : "ফেভারিট";
    });
    toast(on ? "ফেভারিটে রাখা হয়েছে" : "ফেভারিট থেকে সরানো হয়েছে");
    const { path } = parseHash();
    if (path === "/library") render({ keepScroll: true });
  }

  async function shareLecture(id) {
    const l = catalog && catalog.lectureById.get(id);
    if (!l) return;
    const url = `${location.origin}${location.pathname}#/lecture/${id}`;
    if (navigator.share) {
      try { await navigator.share({ title: l.title, text: `${l.title} — ${l.speaker.name}`, url }); } catch (e) { /* cancelled */ }
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      toast("লিংক কপি হয়েছে");
    } catch (e) {
      toast(url);
    }
  }

  document.addEventListener("click", (e) => {
    const target = e.target.closest("[data-fav], [data-share], [data-set], [data-q], [data-clear-recent], [data-retry]");
    if (!target) return;
    if (target.dataset.fav) { e.preventDefault(); toggleFavorite(target.dataset.fav); }
    else if (target.dataset.share) shareLecture(target.dataset.share);
    else if (target.dataset.set) {
      const i = target.dataset.set.indexOf("=");
      setParams({ [target.dataset.set.slice(0, i)]: target.dataset.set.slice(i + 1) });
    } else if (target.dataset.q !== undefined) {
      const input = $("#q");
      if (input) { input.value = target.dataset.q; input.dispatchEvent(new Event("input")); }
    } else if (target.hasAttribute("data-clear-recent")) {
      store.set("recentSearches", []);
      $("#results").innerHTML = searchResults("");
    } else if (target.hasAttribute("data-retry")) boot();
  });

  // Theme: follows the system until the visitor picks one.
  const themeBtn = $("#theme-btn");
  const darkQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const currentTheme = () => document.documentElement.dataset.theme || (darkQuery.matches ? "dark" : "light");
  function paintThemeButton() {
    const dark = currentTheme() === "dark";
    themeBtn.innerHTML = icon(dark ? "sun" : "moon");
    themeBtn.setAttribute("aria-label", dark ? "হালকা থিম" : "গাঢ় থিম");
  }
  themeBtn.addEventListener("click", () => {
    const next = currentTheme() === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    store.set("theme", next);
    paintThemeButton();
  });
  if (darkQuery.addEventListener) darkQuery.addEventListener("change", paintThemeButton);

  // ------------------------------------------------------------------ start
  async function boot() {
    contentState = "loading";
    render({ keepScroll: true });
    try {
      catalog = await loadContent();
      contentState = "ready";
    } catch (e) {
      contentState = "error";
    }
    render({ keepScroll: true });
  }

  $$("header [data-icon], nav [data-icon]").forEach((el) => { el.innerHTML = icon(el.dataset.icon); });
  paintThemeButton();
  navStack.push(location.hash || "#/");
  loadRelease();
  boot();
})();
