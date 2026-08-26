/**
 * 4CBON2 Cloudflare Worker — Hugging Face Spaces proxy
 *
 * Routes (leave these bound as they already are):
 *   4cbon.com / www.4cbon.com  → static landing Space
 *   app.4cbon.com              → Gradio Space
 *
 * Do NOT use mangathpup-4cbon2-static.hf.space — that host 404s.
 * Do NOT forward Host / X-Forwarded-* / CF-* headers — HF then 403s.
 *
 * Paste this entire file into the existing Worker (ES module format).
 * Do not change DNS records or Worker route bindings.
 */

const UPSTREAM = {
  static: "https://mangathpup-4cbon2-static.static.hf.space",
  app: "https://mangathpup-4cbon2-app.hf.space",
};

const PUBLIC = {
  static: "https://4cbon.com",
  app: "https://app.4cbon.com",
};

const APP_HOSTS = new Set(["app.4cbon.com"]);
const APEX_HOSTS = new Set(["4cbon.com", "www.4cbon.com"]);

const STRIP_TO_HF = [
  "host",
  "x-forwarded-host",
  "x-forwarded-proto",
  "x-forwarded-for",
  "x-real-ip",
  "cf-connecting-ip",
  "cf-ipcountry",
  "cf-ray",
  "cf-visitor",
  "cf-ew-via",
  "cdn-loop",
  "true-client-ip",
];

function pickUpstream(hostname) {
  const host = (hostname || "").toLowerCase().split(":")[0];
  if (APP_HOSTS.has(host)) return UPSTREAM.app;
  if (APEX_HOSTS.has(host)) return UPSTREAM.static;
  return UPSTREAM.static;
}

function sanitizeHeaders(incoming, { websocket = false } = {}) {
  const headers = new Headers();
  for (const [key, value] of incoming.entries()) {
    const lower = key.toLowerCase();
    if (STRIP_TO_HF.includes(lower)) continue;
    if (
      !websocket &&
      [
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailers",
        "transfer-encoding",
        "upgrade",
      ].includes(lower)
    ) {
      continue;
    }
    headers.set(key, value);
  }
  if (!headers.has("User-Agent")) {
    headers.set(
      "User-Agent",
      "Mozilla/5.0 (compatible; 4CBON2-Worker/1.0; +https://4cbon.com)",
    );
  }
  if (!websocket) headers.set("Accept-Encoding", "identity");
  return headers;
}

function rewritePublicUrls(text) {
  return text
    .replaceAll(UPSTREAM.app, PUBLIC.app)
    .replaceAll("https://mangathpup-4cbon2-static.hf.space", PUBLIC.static)
    .replaceAll(UPSTREAM.static, PUBLIC.static)
    .replaceAll('src="https://app.4cbon.com"', 'src="https://app.4cbon.com/?embed=true"')
    .replaceAll('src="https://app.4cbon.com/"', 'src="https://app.4cbon.com/?embed=true"');
}

function rewriteLocation(location, requestUrl, upstreamOrigin) {
  try {
    const resolved = new URL(location, upstreamOrigin);
    if (resolved.origin === new URL(upstreamOrigin).origin) {
      const publicUrl = new URL(requestUrl);
      publicUrl.pathname = resolved.pathname;
      publicUrl.search = resolved.search;
      publicUrl.hash = resolved.hash;
      return publicUrl.toString();
    }
  } catch {
    /* leave as-is */
  }
  return location;
}

function unavailable() {
  return new Response(
    "⚠️ 4CBON2 is temporarily unavailable. Please try again later.",
    { status: 503, headers: { "content-type": "text/plain; charset=utf-8" } },
  );
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const hostname = url.hostname.toLowerCase();

    if (hostname === "www.4cbon.com") {
      url.hostname = "4cbon.com";
      return Response.redirect(url.toString(), 301);
    }

    if (url.pathname === "/health") {
      return new Response(
        JSON.stringify({ status: "ok", time: new Date().toISOString() }),
        { status: 200, headers: { "content-type": "application/json; charset=utf-8" } },
      );
    }

    const origin = pickUpstream(hostname);
    const target = new URL(url.pathname + url.search, origin);
    const isWebsocket = (request.headers.get("Upgrade") || "").toLowerCase() === "websocket";

    // Gradio SSE / websocket must pass through without buffering.
    if (isWebsocket) {
      try {
        return await fetch(target.toString(), {
          method: request.method,
          headers: sanitizeHeaders(request.headers, { websocket: true }),
          body: request.body,
        });
      } catch {
        return unavailable();
      }
    }

    const method = request.method === "HEAD" ? "GET" : request.method;
    let upstreamResponse;
    try {
      upstreamResponse = await fetch(target.toString(), {
        method,
        headers: sanitizeHeaders(request.headers),
        body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
        redirect: "follow",
      });
    } catch {
      return unavailable();
    }

    if (upstreamResponse.status === 403) return unavailable();

    const outHeaders = new Headers(upstreamResponse.headers);
    for (const name of STRIP_TO_HF) outHeaders.delete(name);
    outHeaders.set("Access-Control-Allow-Origin", "*");
    outHeaders.set(
      "Content-Security-Policy",
      "frame-ancestors 'self' https://4cbon.com https://www.4cbon.com https://app.4cbon.com",
    );
    outHeaders.delete("x-frame-options");

    const location = outHeaders.get("location");
    if (location) {
      outHeaders.set("location", rewriteLocation(location, request.url, origin));
    }

    const contentType = (outHeaders.get("content-type") || "").toLowerCase();
    const isSse = contentType.includes("text/event-stream");
    const shouldRewrite =
      !isSse &&
      request.method !== "HEAD" &&
      (contentType.includes("text/html") ||
        contentType.includes("json") ||
        contentType.includes("javascript"));

    if (shouldRewrite) {
      const text = rewritePublicUrls(await upstreamResponse.text());
      outHeaders.delete("content-length");
      return new Response(text, {
        status: upstreamResponse.status,
        statusText: upstreamResponse.statusText,
        headers: outHeaders,
      });
    }

    return new Response(request.method === "HEAD" ? null : upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: outHeaders,
    });
  },
};
