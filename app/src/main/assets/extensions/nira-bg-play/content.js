/*
 * Nira Background Playback — content script.
 *
 * WebExtension content scripts run in an isolated world: prototype patches and
 * `Object.defineProperty(document, ...)` overrides applied here are NOT visible
 * to the page's own JavaScript. To actually neutralise YouTube's pause-on-hidden
 * logic we must inject a <script> element into the page whose body runs in the
 * page's own world.
 *
 * Strategy (per rxliuli/youtube-music-background-play):
 *   1. Force document.hidden === false and document.visibilityState === "visible".
 *   2. Swallow visibilitychange / blur / pagehide / freeze events in capture phase.
 *   3. Monkey-patch HTMLVideoElement.prototype.pause so we can distinguish
 *      "the site called pause()" (intentional) from "GeckoView fired a native
 *      pause" (unintentional). If a pause event fires more than ~250ms after
 *      the last intentional pause() call, we resume playback automatically.
 *   4. Keep navigator.mediaSession.setPositionState updated so the Android
 *      media notification tracks progress correctly.
 *
 * Scope is limited to youtube.com / youtube-nocookie.com by the manifest. We
 * double-check hostname before injecting.
 *
 * Every operation is wrapped in try/catch so we never break the page.
 */

(function () {
  "use strict";

  try {
    var host = "";
    try { host = window.location && window.location.hostname; } catch (e) { host = ""; }
    if (typeof host !== "string" ||
        (!/(^|\.)youtube\.com$/.test(host) &&
         !/(^|\.)youtube-nocookie\.com$/.test(host))) {
      return;
    }

    // The whole patch runs in the page's world. Everything inside the function
    // literal below is stringified and injected via <script>textContent. Do
    // NOT reference any closure variable from here — the injected script sees
    // only the page's globals.
    function pagePatch() {
      "use strict";
      try {
        // ── 1. Page Visibility API always reports visible ─────────────────
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

        // ── 2. Swallow the events YouTube listens to ─────────────────────
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

        // ── 2b. Kill mediaSession update churn ───────────────────────────
        // YouTube's player mutates navigator.mediaSession several times a
        // second while playing (setPositionState, metadata, playbackState).
        // Each mutation propagates GeckoView → AC BrowserStore →
        // MediaSessionFeature → MediaSessionServiceDelegate.handleMediaPlaying
        // → AudioFocus.request(). On Android 12 with AUDIOFOCUS_FLAG_DELAY_OK
        // (which AC 153 uses), rapid re-requests cause the system to return
        // AUDIOFOCUS_REQUEST_DELAYED, which AC translates into
        // controller.pause(). The audible symptom: press Play in the PiP
        // notification, ~150 ms of audio, pause, forever.
        //
        // Verified via `dumpsys audio`: our client sits in the focus stack
        // with `flags: DELAY_OK`. Reproduced end-to-end in device logs.
        //
        // Since we can't patch AC / GeckoView from a content script, we
        // silence the JS mutations that cause the churn instead.
        try {
          if (navigator.mediaSession) {
            var ms = navigator.mediaSession;
            var proto = Object.getPrototypeOf(ms);

            // setPositionState: complete no-op. Its only user-visible effect
            // is the seek bar position on the Android media notification,
            // which we're happy to sacrifice for stable playback. YT calls
            // this on every timeupdate (~4×/sec), and each call ripples all
            // the way to AudioFocus.request() — see the AC 153
            // MediaSessionServiceDelegate / AudioFocus code path.
            try {
              ms.setPositionState = function () { /* no-op */ };
            } catch (e) { /* ignore */ }

            // playbackState: dedupe by value only. If YT writes the same
            // state twice in a row, only the first goes through. No time
            // window — the value dedup is enough because YT toggles the
            // state on real transitions only. This prevents the initial-play
            // burst of `playbackState = "playing"` writes without breaking
            // legitimate paused/playing transitions.
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

            // metadata: dedupe by title+artist only. Artwork URLs can differ
            // across writes for the same track (YT sometimes appends
            // cache-busting query params), and we don't care about surface
            // changes to artwork; the important invariant is that the same
            // track doesn't cause repeated metadata churn. This means every
            // legitimate track change (title changes) is allowed through
            // exactly once. On the same track, no writes are propagated.
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

        // ── 3. Video pause monkey-patch + auto-resume ────────────────────
        // Distinguish "the OS/user just paused via the media notification"
        // from "YouTube's own JS is reactively pausing" (e.g. because we
        // entered PiP mode, the site's fullscreen state changed, etc.).
        //
        // The media-notification pause path is synchronous:
        //   OS pause tap -> MediaSession.Controller.pause() -> Gecko IPC ->
        //   navigator.mediaSession action handler -> video.pause() -> 'pause'
        //   event. Total delay: ~1-5 ms.
        //
        // YouTube's reactive pause path is asynchronous: a state observer
        // fires, then some player logic runs, then video.pause() is called.
        // Observed on device: ~50-150 ms between the play call and the
        // reactive pause.
        //
        // We therefore honour pauses that arrive within 30ms of an explicit
        // video.pause() call (treat as intentional) and reverse anything
        // later. See device log analysis in bg-pip commit history for the
        // exact timings.
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

          // NOTE: intentionally NOT calling navigator.mediaSession.setPositionState
          // on `timeupdate`. That fires ~4x/sec and every update propagates
          // through AC's BrowserStore -> MediaSessionFeature -> handleMediaPlaying
          // -> audioFocus.request() on the Android side, which causes the audio
          // pipeline to briefly stall every time. YouTube already emits its own
          // MediaSessionState updates at a saner rate, so we simply do not
          // interfere with position reporting here.

          try {
            video.addEventListener("pause", function () {
              // Genuine end-of-video: leave it alone.
              if (video.ended) return;
              // No src attached (page swapped it out): leave it alone.
              if (!video.currentSrc && !video.src) return;
              // Site-initiated pause within the intentional window: honour it.
              if (Date.now() - lastIntentionalPauseAt <= INTENTIONAL_WINDOW_MS) return;
              // Otherwise this is a native pause we do not want. Resume on the
              // next microtask so we don't fight the same event synchronously.
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
        // Never break the page.
      }
    }

    // Inject the patch into the page's world at document_start.
    try {
      var script = document.createElement("script");
      script.textContent = "(" + pagePatch.toString() + ")();";
      var parent = document.documentElement || document.head || document.body;
      if (parent) {
        parent.appendChild(script);
        // Remove the tag; the code has already executed.
        try { script.parentNode.removeChild(script); } catch (e) {}
      }
    } catch (e) {
      // As a last-resort fallback, run the patch in the content-script world.
      // Prototype patches will not cross worlds, but the visibility getter
      // overrides may still help in Xray-relaxed scenarios.
      try { pagePatch(); } catch (_) {}
    }
  } catch (outer) {
    // Never break the page.
  }
})();
