'use strict';
/**
 * Unit test badge_hook.js (tanpa Android, pakai Node vm).
 * Jalankan: node tools/badge_hook.test.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const SRC = fs.readFileSync(
  path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'badge_hook.js'),
  'utf8'
);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let failures = 0;
function check(name, cond, extra) {
  if (cond) {
    console.log('ok   - ' + name);
  } else {
    failures++;
    console.log('FAIL - ' + name + (extra !== undefined ? ' (' + extra + ')' : ''));
  }
}

function makeSandbox(opts) {
  const forwarded = [];
  const debugLogs = [];
  const sandbox = {
    console: { log: () => {} },
    navigator: opts.navigator || {},
    document: opts.document || { getElementById: () => null, documentElement: {}, body: {} },
    location: { pathname: opts.pathname || '/' },
    TelegramBridge: {
      onUnreadCount: (n) => forwarded.push(n),
      onBadgeDebug: (m) => debugLogs.push(String(m))
    },
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    Date,
    Math,
    Promise
  };
  sandbox.window = sandbox;
  if (opts.angular) sandbox.angular = opts.angular;
  vm.createContext(sandbox);
  vm.runInContext(SRC, sandbox, { filename: 'badge_hook.js' });
  return { sandbox, forwarded, debugLogs };
}

async function testWebK() {
  const { sandbox, forwarded, debugLogs } = makeSandbox({ pathname: '/k/' });

  check('K: shim setAppBadge terpasang', typeof sandbox.navigator.setAppBadge === 'function');

  // Simulasi perilaku tweb (src/lib/uiNotificationsManager.ts):
  // construct -> setAppBadge(0), folder_unread -> setAppBadge(n), duplikat diabaikan.
  sandbox.navigator.setAppBadge(0);
  check('K: setAppBadge(0) diteruskan', JSON.stringify(forwarded) === JSON.stringify([0]), JSON.stringify(forwarded));
  check('K: state.path = k setelah setAppBadge', sandbox.__tgBadge && sandbox.__tgBadge.path === 'k', sandbox.__tgBadge && sandbox.__tgBadge.path);

  sandbox.navigator.setAppBadge(3);
  check('K: setAppBadge(3) diteruskan', JSON.stringify(forwarded) === JSON.stringify([0, 3]), JSON.stringify(forwarded));

  sandbox.navigator.setAppBadge(3);
  check('K: duplikat tidak diteruskan', JSON.stringify(forwarded) === JSON.stringify([0, 3]), JSON.stringify(forwarded));

  sandbox.navigator.clearAppBadge();
  check('K: clearAppBadge -> 0', JSON.stringify(forwarded) === JSON.stringify([0, 3, 0]), JSON.stringify(forwarded));
  check('K: hooked = true', sandbox.__tgBadge.hooked === true);

  // Idempoten: run ulang skrip tidak boleh reset state count.
  vm.runInContext(SRC, sandbox, { filename: 'badge_hook.js' });
  sandbox.navigator.setAppBadge(0); // count masih 0 -> tidak boleh diteruskan lagi
  check('K: idempoten (run ulang tidak reset count)', forwarded.length === 3, JSON.stringify(forwarded));
  sandbox.navigator.setAppBadge(5); // nilai baru tetap diteruskan
  check('K: nilai baru tetap diteruskan setelah run ulang', JSON.stringify(forwarded) === JSON.stringify([0, 3, 0, 5]), JSON.stringify(forwarded));
  check('K: ada log diagnostik', debugLogs.length > 0);
}

async function testWebA() {
  const listeners = {};
  const muted = { 3: true };
  let dialogs = [
    { peerID: 1, unread_count: 2 },
    { peerID: 2, unread_count: 5 },
    { peerID: 3, unread_count: 1 }
  ];

  const $rootScope = {
    $on: (ev, fn) => {
      listeners[ev] = fn;
    }
  };
  const AppMessagesManager = {
    getConversations: () => ({
      then: (ok) => {
        ok({ dialogs: dialogs.slice() });
      }
    })
  };
  const NotificationsManager = {
    getPeerMuted: (peerID) => ({
      then: (ok) => {
        ok(!!muted[peerID]);
      }
    })
  };
  const injector = {
    get: (name) =>
      ({
        $rootScope: $rootScope,
        AppMessagesManager: AppMessagesManager,
        NotificationsManager: NotificationsManager
      })[name]
  };
  const ngAppEl = {};
  const angular = {
    element: () => ({ injector: () => injector })
  };

  const { sandbox, forwarded } = makeSandbox({
    pathname: '/a/',
    angular,
    document: {
      getElementById: (id) => (id === 'ng-app' ? ngAppEl : null),
      documentElement: {},
      body: {}
    }
  });

  await sleep(700); // poller Angular jalan tiap 500ms
  check('A: path = a', sandbox.__tgBadge && sandbox.__tgBadge.path === 'a', sandbox.__tgBadge && sandbox.__tgBadge.path);
  check(
    'A: chat unread tak-bisu = 2 (chat 3 bisu dihitup)',
    JSON.stringify(forwarded) === JSON.stringify([2]),
    JSON.stringify(forwarded)
  );

  // Satu chat terbaca: sisa 1 chat unread tak-bisu.
  dialogs = [
    { peerID: 2, unread_count: 5 },
    { peerID: 3, unread_count: 1 }
  ];
  if (listeners.dialog_unread) listeners.dialog_unread();
  await sleep(700); // debounce 500ms
  check('A: recompute setelah dialog_unread = 1', JSON.stringify(forwarded) === JSON.stringify([2, 1]), JSON.stringify(forwarded));

  // Sisa chat tidak bisu tapi bisu -> badge bersih.
  dialogs = [{ peerID: 3, unread_count: 1 }];
  if (listeners.dialog_unread) listeners.dialog_unread();
  await sleep(700);
  check('A: semua chat bisu -> 0', JSON.stringify(forwarded) === JSON.stringify([2, 1, 0]), JSON.stringify(forwarded));
}

(async () => {
  await testWebK();
  await testWebA();
  console.log(failures ? '\n' + failures + ' test gagal' : '\nsemua test lulus');
  process.exit(failures ? 1 : 0);
})();
