/*
 * Nira Background Playback - content script.
 *
 * WebExtension content scripts run in an isolated world: prototype
 * patches and `Object.defineProperty(document, ...)` overrides applied
 * here are NOT visible to the page's own JavaScript. To actually
 * neutralise sites' pause-on-hidden logic we must inject a <script>
 * element into the page whose body runs in the page's own world.
 *
 * Per-origin gating:
 *   The patch is only applied when the current window.location.origin
 *   is on the Nira allow-list managed by the host app
 *   (BackgroundPlaybackAllowlist). The check is done via
 *   `runtime.sendMessage({ type: "nira:bgplay:isAllowed", origin })` to
 *   the extension's background script, which holds the allow-list
 *   pushed from Kotlin. If the background does not respond within
 *   ~250 ms we fail-closed (do nothing) rather than fail-open - a
 *   stray patch on a non-allow-listed site would be a user-visible
 *   regression.
 *
 * Strategy on allow-listed origins (per rxliuli/youtube-music-background-play):
 *   1. Force document.hidden === false and document.visibilityState === "visible".
 *   2. Swallow visibilitychange / blur / pagehide / freeze events in capture phase.
 *   3. Monkey-patch HTMLVideoElement.prototype.pause so we can distinguish
 *      "the site called pause()" (intentional) from "GeckoView fired a native
 *      pause" (unintentional). If a pause event fires more than ~30ms after
 *      the last intentional pause() call, we resume playback automatically.
 *   4. Keep navigator.mediaSession.setPositionState as a no-op and dedupe
 *      playbackState / metadata writes to avoid AudioFocus churn.
 *
 * Every operation is wrapped in try/catch so we never break the page.
 */

(function () {
  "use strict";

  // Fail-closed check: ask the background script whether this origin
  // is on the allow-list. If no response within the timeout, abort.
  var origin;
  try {
    origin = window.location && window.location.origin;
  } catch (e) {
    origin = "";
  }
  if (!origin) return;

  var decided = false;
  var timeoutHandle = null;

  function timeout() {
    return new Promise(function (resolve) {
      timeoutHandle = setTimeout(function () { resolve({ allowed: false }); }, 250);
    });
  }

  function ask() {
    return new Promise(function (resolve) {
      try {
        var p = browser.runtime.sendMessage({
          type: "nira:bgplay:isAllowed",
          origin: origin,
        });
        if (p && typeof p.then === "function") {
          p.then(resolve).catch(function () { resolve({ allowed: false }); });
        } else {
          resolve({ allowed: false });
        }
      } catch (e) {
        resolve({ allowed: false });
      }
    });
  }

  Promise.race([ask(), timeout()]).then(function (resp) {
    if (decided) return;
    decided = true;
    if (timeoutHandle) { try { clearTimeout(timeoutHandle); } catch (_) {} }
    if (!resp || !resp.allowed) return;
    activate();
  });

  function activate() {
    // The whole patch runs in the page's world. Everything inside the function
    // literal below is stringified and injected via <script>textContent. Do
    // NOT reference any closure variable from here - the injected script sees
    // only the page's globals.
    function pagePatch() {
      "use strict";
      try {
        // == 1. Page Visibility API always reports visible ==
        try {
          Object.defineProperty(document, "hidden", {
            configurable: true,
            get: function () { return false; },
          });
        } catch (e) { /* ignore */ }
        try {
          Object.defineProperty(document, "visibilityState", {
            configurable: true,
            get: function () { return "visible"; },
          });
        } catch (e) { /* ignore */ }
        try {
          Object.defineProperty(document, "webkitHidden", {
            configurable: true,
            get: function () { return false; },
          });
        } catch (e) { /* ignore */ }
        try {
          Object.defineProperty(document, "webkitVisibilityState", {
            configurable: true,
            get: function () { return "visible"; },
          });
        } catch (e) { /* ignore */ }
        try {
          Object.defineProperty(document, "mozHidden", {
            configurable: true,
            get: function () { return false; },
          });
        } catch (e) { /* ignore */ }
        try {
          Object.defineProperty(document, "mozVisibilityState", {
            configurable: true,
            get: function () { return "visible"; },
          });
        } catch (e) { /* ignore */ }
        try {
          document.hasFocus = function () { return true; };
        } catch (e) { /* ignore */ }

        // == 2. Swallow the events sites listen to ==
        var BLOCKED_EVENTS = [
          "visibilitychange",
          "webkitvisibilitychange",
          "mozvisibilitychange",
          "msvisibilitychange",
          "pagehide",
          "freeze",
          "blur",
        ];
        function blocker(e) {
          try { e.stopImmediatePropagation(); } catch (_) {}
          try { e.stopPropagation(); } catch (_) {}
          try { e.preventDefault(); } catch (_) {}
        }
        for (var i = 0; i < BLOCKED_EVENTS.length; i++) {
          try { document.addEventListener(BLOCKED_EVENTS[i], blocker, true); } catch (e) {}
          try { window.addEventListener(BLOCKED_EVENTS[i], blocker, true); } catch (e) {}
        }

        // == 2b. Kill mediaSession update churn ==
        // Sites that use the Media Session API often mutate it many
        // times a second while playing (setPositionState, metadata,
        // playbackState). Each mutation propagates GeckoView -> AC
        // BrowserStore -> MediaSessionFeature ->
        // MediaSessionServiceDelegate.handleMediaPlaying ->
        // AudioFocus.request(). On Android 12+ with
        // AUDIOFOCUS_FLAG_DELAY_OK rapid re-requests cause the system
        // to return AUDIOFOCUS_REQUEST_DELAYED, which AC translates
        // into controller.pause(). The audible symptom: press Play,
        // ~150 ms of audio, pause, forever.
        try {
          if (navigator.mediaSession) {
            var ms = navigator.mediaSession;
            var proto = Object.getPrototypeOf(ms);

            try {
              ms.setPositionState = function () { /* no-op */ };
            } catch (e) { /* ignore */ }

            try {
              var pbDesc =
                Object.getOwnPropertyDescriptor(ms, "playbackState") ||
                (proto && Object.getOwnPropertyDescriptor(proto, "playbackState"));
              if (pbDesc && pbDesc.set) {
                var lastPbValue = null;
                Object.defineProperty(ms, "playbackState", {
                  configurable: true,
                  get: function () { return pbDesc.get.call(ms); },
                  set: function (v) {
                    if (v === lastPbValue) return;
                    lastPbValue = v;
                    try { pbDesc.set.call(ms, v); } catch (e) {}
                  },
                });
              }
            } catch (e) { /* ignore */ }

            try {
              var mdDesc =
                Object.getOwnPropertyDescriptor(ms, "metadata") ||
                (proto && Object.getOwnPropertyDescriptor(proto, "metadata"));
              if (mdDesc && mdDesc.set) {
                var lastMdKey = null;
                Object.defineProperty(ms, "metadata", {
                  configurable: true,
                  get: function () { return mdDesc.get.call(ms); },
                  set: function (v) {
                    try {
                      var key = v ? ((v.title || "") + "|" + (v.artist || "")) : "";
                      if (key === lastMdKey) return;
                      lastMdKey = key;
                    } catch (e) { /* ignore */ }
                    try { mdDesc.set.call(ms, v); } catch (e) {}
                  },
                });
              }
            } catch (e) { /* ignore */ }
          }
        } catch (e) { /* ignore */ }

        // == 3. Video pause monkey-patch + auto-resume ==
        var INTENTIONAL_WINDOW_MS = 30;

        function watchVideo(video) {
          if (!video || video.__niraBgWatched) return;
          video.__niraBgWatched = true;

          var lastIntentionalPauseAt = 0;
          try {
            var origPause = video.pause.bind(video);
            video.pause = function () {
              lastIntentionalPauseAt = Date.now();
              return origPause();
            };
          } catch (e) { /* ignore */ }

          try {
            video.addEventListener("play", function () {
              lastIntentionalPauseAt = 0;
            });
          } catch (e) {}

          try {
            video.addEventListener("pause", function () {
              if (video.ended) return;
              if (!video.currentSrc && !video.src) return;
              if (Date.now() - lastIntentionalPauseAt <= INTENTIONAL_WINDOW_MS) return;
              Promise.resolve().then(function () {
                if (!video.paused || video.ended) return;
                try {
                  var p = video.play();
                  if (p && typeof p.catch === "function") p.catch(function () {});
                } catch (e) { /* ignore */ }
              });
            });
          } catch (e) {}
        }

        function scanAndWatch(root) {
          try {
            var vids = (root || document).querySelectorAll("video");
            for (var i = 0; i < vids.length; i++) watchVideo(vids[i]);
          } catch (e) { /* ignore */ }
        }

        function boot() {
          scanAndWatch(document);
          try {
            var mo = new MutationObserver(function (mutations) {
              for (var m = 0; m < mutations.length; m++) {
                var added = mutations[m].addedNodes;
                if (!added) continue;
                for (var n = 0; n < added.length; n++) {
                  var node = added[n];
                  if (!node) continue;
                  if (node.tagName === "VIDEO") {
                    watchVideo(node);
                  } else if (node.querySelectorAll) {
                    scanAndWatch(node);
                  }
                }
              }
            });
            mo.observe(document.documentElement || document, {
              childList: true,
              subtree: true,
            });
          } catch (e) { /* ignore */ }
        }

        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", boot, { once: true });
        } else {
          boot();
        }
      } catch (outer) {
        /* never break the page */
      }
    }

    // Inject into the page's world. Using <script> textContent runs
    // the body synchronously in the page's isolated world.
    try {
      var script = document.createElement("script");
      script.textContent = "(" + pagePatch.toString() + ")();";
      var parent = document.documentElement || document.head || document.body;
      if (parent) {
        parent.appendChild(script);
        try { script.parentNode.removeChild(script); } catch (e) {}
      }
    } catch (e) {
      try { pagePatch(); } catch (_) {}
    }
  }
})();
