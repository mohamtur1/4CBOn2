/**
 * Cloudflare Worker for 4CBON2 (Hugging Face Spaces edition).
 *
 * Routes traffic to the two public Spaces and hides the `hf.space` backend:
 *
 *   app.<DOMAIN>        -> Gradio app Space   (mangathpup-4cbon2-app.hf.space)
 *   <DOMAIN> and other  -> Static landing Space (mangathpup-4cbon2-static.hf.space)
 *
 * Everything (HTTP and Gradio WebSockets) is proxied, so relative URLs,
 * cookies and redirects inside the apps keep working on your own domain.
 *
 * CONFIGURATION
 *   Edit the DEFAULT_* constants below, or set Worker bindings (recommended):
 *     ROUTE_DOMAIN     = 4cbon.com
 *     HF_APP_ORIGIN    = https://mangathpup-4cbon2-app.hf.space
 *     HF_STATIC_ORIGIN = https://mangathpup-4cbon2-static.hf.space
 */

const DEFAULT_DOMAIN = "4cbon.com";
const DEFAULT_APP_ORIGIN = "https://mangathpup-4cbon2-app.hf.space";
const DEFAULT_STATIC_ORIGIN = "https://mangathpup-4cbon2-static.hf.space";

// Hop-by-hop headers that must never be forwarded to the origin.
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

export default {
  async fetch(request, env) {
    const domain = (env && env.ROUTE_DOMAIN) || DEFAULT_DOMAIN;
    const appOrigin = (env && env.HF_APP_ORIGIN) || DEFAULT_APP_ORIGIN;
    const staticOrigin = (env && env.HF_STATIC_ORIGIN) || DEFAULT_STATIC_ORIGIN;

    const url = new URL(request.url);
    const host = url.hostname.toLowerCase();

    // Route the "app" subdomain to the Gradio Space; everything else to the
    // static landing Space.
    const isApp = host === `app.${domain}`;
    const origin = isApp ? appOrigin : staticOrigin;

    return proxy(request, origin, host);
  },
};

async function proxy(request, origin, publicHost) {
  const url = new URL(request.url);
  const target = new URL(origin);

  const targetUrl =
    `${target.origin}${url.pathname}${url.search}`;

  // ---- WebSocket (Gradio /queue/join, /queue/data) ----------------------
  const upgrade = (request.headers.get("upgrade") || "").toLowerCase();
  if (upgrade === "websocket") {
    const wsInit = {
      method: request.method,
      headers: request.headers,
    };
    wsInit.headers.set("Host", target.host);
    const wsResponse = await fetch(targetUrl, wsInit);
    if (wsResponse.webSocket) {
      return new Response(null, { status: 101, webSocket: wsResponse.webSocket });
    }
    return wsResponse;
  }

  // ---- Regular HTTP -----------------------------------------------------
  const init = {
    method: request.method,
    headers: request.headers,
    redirect: "manual",
  };
  init.headers.set("Host", target.host);
  // Clear hop-by-hop headers before forwarding.
  for (const h of HOP_BY_HOP) init.headers.delete(h);

  if (!["GET", "HEAD"].includes(request.method)) {
    init.body = request.body;
  }

  const response = await fetch(targetUrl, init);

  // ---- Rewrite response for the public host ----------------------------
  const out = new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: response.headers,
  });

  // Rewrite Location redirects that point back at the hf.space backend.
  const location = out.headers.get("Location");
  if (location) {
    try {
      const loc = new URL(location, target.origin);
      if (loc.hostname.endsWith(target.hostname)) {
        loc.host = publicHost;
        out.headers.set("Location", loc.toString());
      }
    } catch (_) { /* leave untouched */ }
  }

  // Rewrite Set-Cookie Domain so cookies stick to the public host.
  const setCookies = out.headers.getSetCookie
    ? out.headers.getSetCookie()
    : [out.headers.get("Set-Cookie")].filter(Boolean);
  if (setCookies.length) {
    out.headers.delete("Set-Cookie");
    for (const c of setCookies) {
      out.headers.append("Set-Cookie", c.replace(/Domain=[^;]+;?/i, ""));
    }
  }

  // CORS preflight passthrough.
  if (request.method === "OPTIONS") {
    out.headers.set("Access-Control-Allow-Origin", "*");
    out.headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    out.headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRFToken");
  }

  return out;
}
