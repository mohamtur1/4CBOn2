/**
 * 4CBON2 Cloudflare Worker — Hugging Face Spaces proxy
 *
 * Routes (leave these bound as they already are):
 *   4cbon.com / www.4cbon.com  → static landing Space
 *   app.4cbon.com              → Gradio Space
 *
 * Why this file exists
 * --------------------
 * Hugging Face static Spaces are NOT served on *.hf.space.
 * The official host for mangathpup/4cbon2-static is:
 *   https://mangathpup-4cbon2-static.static.hf.space
 *
 * Fetching https://mangathpup-4cbon2-static.hf.space returns HF's 404 page
 * (and can surface as "403 Forbidden: requests to …hf.space are not allowed"
 * when the incoming Host / X-Forwarded-Host from 4cbon.com is forwarded).
 *
 * Paste this entire file into the existing Worker in the Cloudflare dashboard.
 * Do not change DNS records or Worker route bindings.
 */

const UPSTREAM = {
  static: "https://mangathpup-4cbon2-static.static.hf.space",
  app: "https://mangathpup-4cbon2-app.hf.space",
};

const APP_HOSTS = new Set(["app.4cbon.com"]);
const APEX_HOSTS = new Set(["4cbon.com", "www.4cbon.com"]);

const HOP_BY_HOP = [
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
  // These leak the custom domain to Hugging Face and trigger
  // "403 Forbidden: requests to <space>.hf.space are not allowed".
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
  // Fallback: treat unknown hosts as the landing page.
  return UPSTREAM.static;
}

function sanitizeHeaders(incoming) {
  const headers = new Headers();
  for (const [key, value] of incoming.entries()) {
    if (HOP_BY_HOP.includes(key.toLowerCase())) continue;
    headers.set(key, value);
  }
  // Present as a normal browser GET so HF does not treat this as a
  // custom-domain / reverse-proxy request.
  if (!headers.has("User-Agent")) {
    headers.set(
      "User-Agent",
      "Mozilla/5.0 (compatible; 4CBON2-Worker/1.0; +https://4cbon.com)",
    );
  }
  headers.set("Accept-Encoding", "identity");
  return headers;
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

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const hostname = url.hostname.toLowerCase();

    // www → apex (expected behaviour)
    if (hostname === "www.4cbon.com") {
      url.hostname = "4cbon.com";
      return Response.redirect(url.toString(), 301);
    }

    // Local health check — do not proxy this to Hugging Face.
    if (url.pathname === "/health") {
      return new Response(
        JSON.stringify({ status: "ok", time: new Date().toISOString() }),
        { status: 200, headers: { "content-type": "application/json; charset=utf-8" } },
      );
    }

    const origin = pickUpstream(hostname);
    const target = new URL(url.pathname + url.search, origin);
    const method = request.method === "HEAD" ? "GET" : request.method;
    const headers = sanitizeHeaders(request.headers);

    let upstreamResponse;
    try {
      upstreamResponse = await fetch(target.toString(), {
        method,
        headers,
        body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
        redirect: "follow",
      });
    } catch (err) {
      return new Response(
        "⚠️ 4CBON2 is temporarily unavailable. Please try again later.",
        { status: 503, headers: { "content-type": "text/plain; charset=utf-8" } },
      );
    }

    if (upstreamResponse.status === 403) {
      return new Response(
        "⚠️ 4CBON2 is temporarily unavailable. Please try again later.",
        { status: 503, headers: { "content-type": "text/plain; charset=utf-8" } },
      );
    }

    const outHeaders = new Headers(upstreamResponse.headers);
    for (const name of HOP_BY_HOP) outHeaders.delete(name);
    outHeaders.set("Access-Control-Allow-Origin", "*");
    outHeaders.delete("content-security-policy");
    outHeaders.delete("x-frame-options");

    const location = outHeaders.get("location");
    if (location) {
      outHeaders.set("location", rewriteLocation(location, request.url, origin));
    }

    return new Response(request.method === "HEAD" ? null : upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: outHeaders,
    });
  },
};
