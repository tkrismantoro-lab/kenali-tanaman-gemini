// Server perantara Kenali Tanaman (Cloudflare Worker).
//
// Aplikasi mengirim foto ke sini, lalu server meneruskannya ke Gemini memakai API key yang
// disimpan sebagai secret GEMINI_API_KEY di Cloudflare. API key tidak pernah ada di aplikasi.
//
// Pemasangan: Workers & Pages -> worker "kenali-tanaman" -> Edit code -> tempel berkas ini -> Deploy,
// lalu Settings -> Variables and Secrets -> tambah dua Secret:
//   GEMINI_API_KEY : API key Gemini pengelola aplikasi
//   APP_TOKEN      : token aplikasi, sama dengan GitHub secret PROXY_APP_TOKEN yang dipakai saat build APK

// Hanya model ini yang boleh dipakai lewat server.
const ALLOWED_MODELS = new Set(["gemini-3.5-flash-lite", "gemini-3.8-flash"]);

// Foto dari aplikasi sekitar 0,2-0,6 MB; batas ini menolak kiriman yang tidak wajar.
const MAX_BODY_BYTES = 4 * 1024 * 1024;

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/") {
      return new Response("Server Kenali Tanaman aktif.", {
        headers: { "content-type": "text/plain; charset=utf-8" },
      });
    }
    if (request.method !== "POST") {
      return error(405, "Metode tidak didukung.");
    }
    if (!env.APP_TOKEN || request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return error(403, "Akses ditolak.");
    }
    if (!env.GEMINI_API_KEY) {
      return error(500, "GEMINI_API_KEY belum diisi di pengaturan server.");
    }

    const match = url.pathname.match(
      /^\/v1beta\/models\/([a-z0-9.\-]+):(streamGenerateContent|generateContent)$/,
    );
    if (!match || !ALLOWED_MODELS.has(match[1])) {
      return error(404, "Alamat tidak dikenal.");
    }

    const declaredLength = Number(request.headers.get("content-length") || "0");
    if (declaredLength > MAX_BODY_BYTES) {
      return error(413, "Foto terlalu besar.");
    }

    const upstream = await fetch(GEMINI_BASE + match[1] + ":" + match[2] + url.search, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-goog-api-key": env.GEMINI_API_KEY,
      },
      body: request.body,
    });

    // Jawaban (termasuk streaming SSE) diteruskan apa adanya ke aplikasi.
    return new Response(upstream.body, {
      status: upstream.status,
      headers: {
        "content-type": upstream.headers.get("content-type") || "application/json",
        "cache-control": "no-store",
      },
    });
  },
};

function error(status, message) {
  return new Response(JSON.stringify({ error: { code: status, message } }), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
