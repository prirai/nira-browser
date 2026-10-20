/*
 * Nira Background Playback - background script.
 *
 * Holds the per-origin allow-list in memory. On startup we open a
 * persistent native-messaging port named "nira.bgplay.allowlist" so
 * the Kotlin host (BrowserApp) can push the current allow-list to us
 * on every change. Content scripts (content.js) then query us via
 * `runtime.sendMessage({ type: "nira:bgplay:isAllowed", origin })`.
 *
 * The allow-list is mirrored into `browser.storage.local.allowlist`
 * so a content script that queries before the first host push can
 * fall back to the last-known value from a previous session instead
 * of defaulting to blocked.
 */
(function () {
  "use strict";

  var allowlist = new Set();

  function normaliseOrigin(origin) {
    // Content scripts compute origin via window.location.origin which
    // omits the port when it is the scheme default (443/80). The host
    // stores origins in the "scheme://host:port" form returned by
    // mozilla-components' String.getOrigin(). Normalise both sides to
    // the no-default-port form here so comparisons work.
    if (typeof origin !== "string") return "";
    try {
      var u = new URL(origin);
      var isDefault =
        (u.protocol === "https:" && u.port === "443") ||
        (u.protocol === "http:" && u.port === "80") ||
        u.port === "";
      return u.protocol + "//" + u.hostname + (isDefault ? "" : ":" + u.port);
    } catch (e) {
      return origin;
    }
  }

  function setAllowlist(origins) {
    allowlist = new Set();
    if (Array.isArray(origins)) {
      for (var i = 0; i < origins.length; i++) {
        allowlist.add(normaliseOrigin(origins[i]));
      }
    }
    try {
      browser.storage.local.set({ allowlist: Array.from(allowlist) });
    } catch (e) { /* ignore */ }
  }

  // Hydrate from the last-known storage.local value so a content
  // script that queries before the first port push still has a sane
  // answer.
  try {
    browser.storage.local
      .get("allowlist")
      .then(function (res) {
        if (res && Array.isArray(res.allowlist)) {
          setAllowlist(res.allowlist);
        }
      })
      .catch(function () { /* ignore */ });
  } catch (e) { /* ignore */ }

  // Open the native-messaging port to Kotlin and listen for allow-list
  // pushes. We use connectNative (persistent port) rather than
  // sendNativeMessage so the Kotlin side can push updates without us
  // having to poll.
  try {
    var port = browser.runtime.connectNative("nira.bgplay.allowlist");
    if (port) {
      port.onMessage.addListener(function (msg) {
        if (!msg || msg.type !== "set") return;
        setAllowlist(msg.origins);
      });
    }
  } catch (e) { /* ignore */ }

  // Content-script query path.
  browser.runtime.onMessage.addListener(function (message, sender, sendResponse) {
    if (!message || message.type !== "nira:bgplay:isAllowed") return;
    var origin = normaliseOrigin(message.origin || "");
    return Promise.resolve({ allowed: allowlist.has(origin) });
  });
})();
