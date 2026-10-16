/**
 * 4CBON2 Cloudflare Worker — Hugging Face Spaces proxy
 *
 * Worker name: 4cbon-proxy
 * Format:      ES module  (export default { fetch })
 *
 * Routes (do not change):
 *   *4cbon.com/*        → this Worker
 *   *app.4cbon.com/*    → this Worker
 *
 * Upstreams:
 *   4cbon.com / www.4cbon.com  → mangathpup-4cbon2-static.static.hf.space
 *   app.4cbon.com              → mangathpup-4cbon2-app.hf.space
 *
 * Paste this entire file into the existing Worker. DNS and route
 * bindings stay as they are.
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

const HOP_BY_HOP = [
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
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
    if (!websocket && HOP_BY_HOP.includes(lower)) continue;
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
  const appHost = "mangathpup-4cbon2-app.hf.space";
  return text
    .replaceAll(`https://${appHost}`, PUBLIC.app)
    .replaceAll(`http://${appHost}`, PUBLIC.app)
    .replaceAll(`wss://${appHost}`, "wss://app.4cbon.com")
    .replaceAll(`ws://${appHost}`, "wss://app.4cbon.com")
    .replaceAll("https://mangathpup-4cbon2-static.hf.space", PUBLIC.static)
    .replaceAll(UPSTREAM.static, PUBLIC.static)
    .replaceAll('src="https://app.4cbon.com"', 'src="https://app.4cbon.com/?embed=true"')
    .replaceAll('src="https://app.4cbon.com/"', 'src="https://app.4cbon.com/?embed=true"')
    .replaceAll(
      'src="https://app.4cbon.com?"',
      'src="https://app.4cbon.com/?embed=true"',
    );
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
  return rewritePublicUrls(location);
}

function unavailable() {
  return new Response(
    "⚠️ 4CBON2 is temporarily unavailable. Please try again later.",
    { status: 503, headers: { "content-type": "text/plain; charset=utf-8" } },
  );
}

function withEmbed(url, request) {
  const dest = (request.headers.get("Sec-Fetch-Dest") || "").toLowerCase();
  if (dest === "iframe" && !url.searchParams.has("embed")) {
    url.searchParams.set("embed", "true");
  }
  return url;
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
        JSON.stringify({
          status: "ok",
          time: new Date().toISOString(),
          host: hostname,
        }),
        { status: 200, headers: { "content-type": "application/json; charset=utf-8" } },
      );
    }

    const origin = pickUpstream(hostname);
    const target = withEmbed(new URL(url.pathname + url.search, origin), request);
    const isWebsocket =
      (request.headers.get("Upgrade") || "").toLowerCase() === "websocket";

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
    outHeaders.set("Access-Control-Allow-Headers", "*");
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
        contentType.includes("javascript") ||
        url.pathname === "/config" ||
        url.pathname.endsWith("/config"));

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
