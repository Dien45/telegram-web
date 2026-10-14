/**
 * Bridge jumlah chat belum terbaca (Telegram Web -> Android).
 *
 * Harus dieksekusi SEBELUM script halaman (disisipkan sebagai anak pertama <head>).
 *
 * Web K (web.telegram.org/k/):
 *   App memanggil navigator.setAppBadge(n) saat event folder_unread untuk folder All
 *   (lihat src/lib/uiNotificationsManager.ts:275,312-317) dengan nilai
 *   folder.unreadUnmutedPeerIds.size = jumlah chat unread tak-bisu.
 *
 * Web A (web.telegram.org/a/):
 *   App berbasis AngularJS tanpa Badging API. Kita hook $rootScope dan menghitung
 *   dari AppMessagesManager.getConversations() + NotificationsManager.getPeerMuted().
 */
(function () {
  'use strict';

  if (window.__tgBadge && window.__tgBadge.installed) {
    return;
  }

  var state = window.__tgBadge = {
    installed: true,
    path: 'none',
    hooked: false,
    shimInstalled: false,
    count: -1
  };

  function debug(msg) {
    try {
      if (typeof TelegramBridge !== 'undefined' && TelegramBridge.onBadgeDebug) {
        TelegramBridge.onBadgeDebug(String(msg));
      } else if (window.console && console.log) {
        console.log('[BadgeHook] ' + msg);
      }
    } catch (e) {}
  }

  function forward(n) {
    n = Math.max(0, n | 0);
    if (state.count === n) return;
    state.count = n;
    try {
      if (typeof TelegramBridge !== 'undefined' && TelegramBridge.onUnreadCount) {
        TelegramBridge.onUnreadCount(n);
        debug('forward ' + n + ' via ' + state.path);
      }
    } catch (e) {}
  }

  // ---------------------------------------------------------------- Web K
  try {
    var hadNative = typeof navigator.setAppBadge === 'function';
    var nativeSet = hadNative ? navigator.setAppBadge : null;
    Object.defineProperty(navigator, 'setAppBadge', {
      configurable: true,
      writable: true,
      value: function (n) {
        if (state.path === 'none') state.path = 'k';
        state.hooked = true;
        forward(n === undefined || n === null ? 0 : n);
        if (hadNative) {
          try { return nativeSet.call(navigator, n); } catch (e) {}
        }
      }
    });
    if (typeof navigator.clearAppBadge !== 'function') {
      Object.defineProperty(navigator, 'clearAppBadge', {
        configurable: true,
        writable: true,
        value: function () {
          forward(0);
        }
      });
    }
    state.shimInstalled = true;
    debug('setAppBadge shim terpasang (native=' + hadNative + ')');
  } catch (e) {
    debug('shim gagal: ' + e);
  }

  // ---------------------------------------------------------------- Web A
  var tries = 0;
  var poller = setInterval(function () {
    tries++;
    var path = '';
    try { path = String((window.location && window.location.pathname) || ''); } catch (e) {}
    var isK = path === '/k' || path.indexOf('/k/') === 0;
    if (isK && tries > 60) { clearInterval(poller); return; }
    if (tries > 360) { clearInterval(poller); return; }
    var injector = null;
    try {
      if (window.angular) {
        var root = document.getElementById('ng-app') || document.documentElement || document.body;
        injector = root && window.angular.element(root).injector();
      }
    } catch (e) {}
    if (!injector) return;
    clearInterval(poller);
    startWebA(injector);
  }, 500);

  function startWebA(injector) {
    var $rootScope, AMM, NM;
    try {
      $rootScope = injector.get('$rootScope');
      AMM = injector.get('AppMessagesManager');
      NM = injector.get('NotificationsManager');
    } catch (e) {
      debug('injector Angular gagal: ' + e);
      return;
    }
    if (!$rootScope || !AMM) return;

    state.path = 'a';
    state.hooked = true;
    debug('Web A terdeteksi, hook $rootScope aktif');

    var scheduled = 0;
    function schedule() {
      if (scheduled) return;
      scheduled = setTimeout(function () {
        scheduled = 0;
        compute();
      }, 500);
    }

    function compute() {
      var res;
      try {
        res = AMM.getConversations('', 0, 100);
      } catch (e) {
        return;
      }
      if (!res || typeof res.then !== 'function') {
        applyDialogs(res && res.dialogs);
        return;
      }
      res.then(function (r) {
        applyDialogs(r && r.dialogs);
      }, function () {});
    }

    function applyDialogs(dialogs) {
      dialogs = dialogs || [];
      var chats = [];
      for (var i = 0; i < dialogs.length; i++) {
        var d = dialogs[i];
        if (d && d.unread_count > 0) chats.push(d);
      }
      if (!chats.length) {
        forward(0);
        return;
      }
      var total = 0;
      var left = chats.length;
      for (var j = 0; j < chats.length; j++) {
        countChat(chats[j], function (muted) {
          if (!muted) total++;
          if (--left === 0) forward(total);
        });
      }
    }

    function countChat(d, cb) {
      var done = false;
      function once(muted) {
        if (done) return;
        done = true;
        cb(!!muted);
      }
      try {
        if (NM && typeof NM.getPeerMuted === 'function') {
          var p = NM.getPeerMuted(d.peerID);
          if (p && typeof p.then === 'function') {
            p.then(function (muted) { once(muted); }, function () { once(false); });
            return;
          }
        }
      } catch (e) {}
      var ns = d.notify_settings;
      once(!!(ns && ns.mute_until && ns.mute_until * 1000 > Date.now()));
    }

    try {
      $rootScope.$on('dialog_unread', schedule);
      $rootScope.$on('dialogs_multiupdate', schedule);
      $rootScope.$on('messages_read', schedule);
      $rootScope.$on('notify_settings', schedule);
    } catch (e) {}
    setInterval(compute, 60000);
    compute();
  }
})();
