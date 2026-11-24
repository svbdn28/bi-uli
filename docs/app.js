// ULI for iPhone: the screens of the Android app (ui/*.kt, AppViewModel.kt) as a small offline web app.
// State lives in `S`; every change re-renders the current screen from it. One-shot animations are asked for
// through `FX` and play only on the render that follows the event, so a later re-render never replays them.
import {
  Srs, Card, Progress, Round, Exam, Flow, Q, Code, Options, TOPICS, topicOf,
  comesBackIn, comesBackAfter, roundEndNote, chanceText, todayLine, questionsWord, formatPoints, formatDuration,
} from './engine.js';

const $app = document.getElementById('app');
const KP = 'uli.progress';
const KS = 'uli.session';
const KTIP = 'uli.tipHidden';

// ------------------------------------------------------------------ small helpers

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const now = () => Date.now();
const uptime = () => performance.now();
const $ = (sel) => $app.querySelector(sel);

const ICONS = {
  check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  forward: 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  chevron: 'M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z',
  down: 'M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z',
  more: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  lightbulb: 'M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z',
  timer: 'M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z',
  replay: 'M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z',
  stack: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-1 9H9V9h10v2zm-4 4H9v-2h6v2zm4-8H9V5h10v2z',
  balance: 'M13 7.83c.85-.3 1.53-.98 1.83-1.83H18l-3 7c0 1.66 1.57 3 3.5 3s3.5-1.34 3.5-3l-3-7h2V4h-6.17c-.41-1.17-1.52-2-2.83-2s-2.42.83-2.83 2H3v2h2l-3 7c0 1.66 1.57 3 3.5 3S9 14.66 9 13L6 6h3.17c.3.85.98 1.53 1.83 1.83V19H2v2h20v-2h-9V7.83zM20.37 13h-3.74l1.87-4.36L20.37 13zm-13 0H3.63L5.5 8.64L7.37 13zM12 6c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z',
  translate: 'M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z',
  touch: 'M9 11.24V7.5C9 6.12 10.12 5 11.5 5S14 6.12 14 7.5v3.74c1.21-.81 2-2.18 2-3.74C16 5.01 13.99 3 11.5 3S7 5.01 7 7.5c0 1.56.79 2.93 2 3.74zm9.84 4.63l-4.54-2.26c-.17-.07-.35-.11-.54-.11H13v-6c0-.83-.67-1.5-1.5-1.5S10 6.67 10 7.5v10.74l-3.43-.72c-.08-.01-.15-.03-.24-.03-.31 0-.59.13-.79.33l-.79.8 4.94 4.94c.27.27.65.44 1.06.44h6.79c.75 0 1.33-.55 1.44-1.28l.75-5.27c.01-.07.02-.14.02-.2 0-.62-.38-1.16-.91-1.38z',
  share: 'M12 1.5l-4.6 4.6 1.4 1.4L11 5.3V15h2V5.3l2.2 2.2 1.4-1.4L12 1.5zM5 9v12.5h14V9h-4v2h2v8.5H7V11h2V9H5z',
};
const icon = (name, extra = '') => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true" ${extra}><path d="${ICONS[name]}"/></svg>`;

// Material-like shapes, drawn once as polar curves in a 100x100 box.
function polar(fn, steps = 180) {
  const pts = [];
  let max = 0;
  for (let k = 0; k < steps; k++) {
    const t = (k / steps) * Math.PI * 2;
    const r = fn(t);
    max = Math.max(max, r);
    pts.push([t, r]);
  }
  return 'M' + pts.map(([t, r]) => {
    const x = 50 + (Math.cos(t - Math.PI / 2) * r * 50) / max;
    const y = 50 + (Math.sin(t - Math.PI / 2) * r * 50) / max;
    return `${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join('L') + 'Z';
}
const wave = (n, a) => polar((t) => 1 + a * Math.cos(n * t));
const lobes = (n, a, p = 1) => polar((t) => 1 - a + a * Math.pow(Math.abs(Math.cos((n * t) / 2)), p));
const SHAPES = {
  circle: polar(() => 1),
  cookie12: wave(12, 0.055),
  cookie9: wave(9, 0.075),
  cookie6: wave(6, 0.1),
  cookie4: wave(4, 0.12),
  sunny: wave(8, 0.07),
  verySunny: wave(8, 0.13),
  softBurst: wave(10, 0.09),
  clover4: lobes(4, 0.3, 0.6),
  flower: lobes(8, 0.22, 0.7),
  puffy: lobes(6, 0.16, 0.5),
  gem: wave(6, 0.035),
  pill: 'M30 15h40a35 35 0 0 1 0 70H30a35 35 0 0 1 0-70z',
};
const shp = (name) => `<svg class="shp" viewBox="0 0 100 100" aria-hidden="true"><path d="${SHAPES[name]}" fill="currentColor"/></svg>`;
const badge = (ic, shape, bg, fg, size = 40, iconSize = 22) =>
  `<span class="badge" style="width:${size}px;height:${size}px;color:${bg}">${shp(shape)}<span class="ic-wrap" style="position:relative;color:${fg};display:grid">${icon(ic, `style="width:${iconSize}px;height:${iconSize}px"`)}</span></span>`;

/** Prose with commands, paths and flags set in mono (engine Code.spans), never breaking inside them. */
function codeText(text, english = false) {
  let out = '';
  let pos = 0;
  for (const [a, b] of Code.spans(text, english)) {
    if (a > pos) out += esc(Code.gluePaths(text.slice(pos, a)));
    out += `<code class="c">${esc(Code.unbreakable(text.slice(a, b)))}</code>`;
    pos = b;
  }
  if (pos < text.length) out += esc(Code.gluePaths(text.slice(pos)));
  return out;
}

const plural3 = (n, one, few, many) => (n === 1 ? one : n >= 2 && n <= 4 ? few : many);
const agree = (n, stem) => `${n} ${stem}${plural3(n, 'á', 'é', 'ých')}`;
const zo = (n) => ('467'.includes(String(n)[0]) ? 'zo' : 'z');
const examProgress = (answered, total) => `Hotovo ${answered} z ${total}`;
function roundSubtitle(due, nw, size) {
  const word = nw >= 2 && nw <= 4 ? 'nové' : 'nových';
  let s;
  if (due > 0 && nw > 0) s = `${size} otázok: ${due} na zopakovanie, ${nw} ${word}`;
  else if (due > 0) s = `${size} otázok na zopakovanie`;
  else if (nw > 0) s = `${size} otázok, z toho ${nw} ${word}`;
  else s = `${size} otázok na precvičenie`;
  return s.replace('1 nových', '1 nová');
}

// ------------------------------------------------------------------ storage (atomic per key; a damaged file is kept aside)

function readKey(key, normalize) {
  let raw;
  try {
    raw = localStorage.getItem(key);
  } catch (e) {
    return { corrupt: true, backup: null };
  }
  if (raw === null) return { missing: true };
  try {
    return { ok: normalize(JSON.parse(raw)) };
  } catch (e) {
    return { corrupt: true, backup: backup(key, raw) };
  }
}

function backup(key, raw) {
  try {
    // The same damaged file read on every start is kept once, not once per start.
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k.startsWith(key + '.corrupt-') && localStorage.getItem(k) === raw) return k;
    }
    const k = `${key}.corrupt-${now()}`;
    localStorage.setItem(k, raw);
    return k;
  } catch {
    return null;
  }
}

let toldAboutWriteError = false;
function write({ progress = null, session = null, sessionFirst = false } = {}) {
  const steps = [];
  if (progress && !S.damaged) steps.push(() => localStorage.setItem(KP, JSON.stringify(progress)));
  if (session) {
    steps.push(() => {
      if (!session.quiz && !session.exam && !session.lastExam) localStorage.removeItem(KS);
      else localStorage.setItem(KS, JSON.stringify(session));
    });
  }
  if (sessionFirst) steps.reverse();
  for (const step of steps) {
    try {
      step();
    } catch (e) {
      console.error('save failed', e);
      if (!toldAboutWriteError) {
        toldAboutWriteError = true;
        toast('Postup sa nepodarilo uložiť. Skontroluj voľné miesto v telefóne.');
      }
    }
  }
}

const strip = (o) => {
  if (!o) return null;
  const { shownAt, ...rest } = o;
  return rest;
};
const session = () => ({ version: 1, quiz: strip(S.quiz), roundStartBoxes: S.roundStart, exam: strip(S.exam), lastExam: S.lastExam });
const saveSession = () => write({ session: session() });
const persist = (p) => { S.progress = p; write({ progress: p }); };

// ------------------------------------------------------------------ state

const questions = await fetch('questions.json').then((r) => r.json());
const byId = new Map(questions.map((q) => [q.id, q]));
const ids = questions.map((q) => q.id);

const S = {
  progress: Progress.empty(),
  screen: { name: 'home' },
  quiz: null,
  exam: null,
  roundSummary: null,
  examSummary: null,
  lastExam: null,
  roundStart: {},
  damaged: null,
  dialog: null,
  menu: false,
  compactFor: null,
  tipHidden: false,
};
let FX = {};

function init() {
  const loaded = readKey(KP, Progress.normalize);
  let readable = true;
  if (loaded.ok) S.progress = loaded.ok;
  else if (loaded.corrupt) { S.damaged = { backup: loaded.backup }; readable = false; }
  const sv = readKey(KS, (o) => { if (!o || typeof o !== 'object') throw new Error('session'); return o; });
  const saved = sv.ok || {};
  let r;
  try {
    r = Flow.restore(saved, S.progress, byId, readable, uptime());
  } catch (e) {
    console.warn('session dropped', e);
    r = Flow.restore({}, S.progress, byId, readable, uptime());
  }
  S.quiz = r.quiz;
  S.roundStart = r.roundStart;
  S.exam = r.exam;
  S.lastExam = r.lastExam;
  S.examSummary = r.lastExam ? summarize(r.lastExam) : null;
  S.progress = r.progress;
  if (r.progressChanged) write({ progress: S.progress });
  try { S.tipHidden = localStorage.getItem(KTIP) === '1'; } catch { /* private mode */ }
  // Back on the screen the app was closed on, while there is still something to show there.
  let key = null;
  try { key = sessionStorage.getItem('uli.screen'); } catch { /* ignore */ }
  if (key === 'quiz' && S.quiz) S.screen = { name: 'quiz' };
  else if (key === 'exam' && S.exam) S.screen = { name: 'exam' };
  else if (key === 'exam-end' && S.examSummary) S.screen = { name: 'examEnd' };
  else if (key && key.startsWith('browse')) S.screen = { name: 'browse', topic: key.split(':')[1] || null };
  try { navigator.storage?.persist?.(); } catch { /* best effort */ }
}

const summarize = (e) => ({ result: Exam.result(e, byId), exam: e, durationMs: (e.finishedAt ?? e.startedAt) - e.startedAt });

// ------------------------------------------------------------------ navigation (with a history entry, so a back swipe works)

let ignorePop = false;
function go(to, dir = 'in') {
  const wasHome = S.screen.name === 'home';
  S.screen = to;
  S.menu = false;
  FX.nav = dir;
  try {
    sessionStorage.setItem('uli.screen', { quiz: 'quiz', exam: 'exam', examEnd: 'exam-end', browse: `browse:${to.topic || ''}` }[to.name] || 'home');
  } catch { /* ignore */ }
  if (to.name !== 'home' && wasHome) history.pushState({ uli: 1 }, '');
  if (to.name === 'home' && !wasHome && history.state?.uli) { ignorePop = true; history.back(); }
  render();
}
window.addEventListener('popstate', () => {
  if (ignorePop) { ignorePop = false; return; }
  if (S.dialog) { S.dialog = null; }
  if (S.screen.name === 'exam') {
    if (S.exam && S.exam.exam.index > 0) { history.pushState({ uli: 1 }, ''); askQuitExam(); return; }
    leaveExam();
    return;
  }
  if (S.screen.name !== 'home') go({ name: 'home' }, 'back');
});

const home = () => go({ name: 'home' }, 'back');

// ------------------------------------------------------------------ actions (AppViewModel)

const shuffledOrder = (q) => Options.order(q);
const isFresh = () => Object.keys(S.progress.cards).length === 0 && !S.quiz;

function toggleSk() { persist({ ...S.progress, showSk: !S.progress.showSk }); render(); }

function resetProgress() {
  S.quiz = null; S.exam = null; S.lastExam = null; S.examSummary = null; S.roundStart = {};
  S.progress = Progress.empty(S.progress.showSk);
  write({ progress: S.progress, session: session(), sessionFirst: true });
  render();
}

function startRound(kind = 'smart', roundIds = Srs.buildRound(ids, S.progress, now())) {
  S.roundStart = Object.fromEntries(roundIds.map((id) => [id, Progress.card(S.progress, id).box]));
  const round = Round.start(roundIds);
  S.quiz = { kind, round, order: shuffledOrder(byId.get(Round.current(round))), selected: [], answered: false, correct: false, seq: S.progress.seq, shownAt: uptime() };
  FX.enter = true;
  saveSession();
  go({ name: 'quiz' });
}

function continueOrStart() { if (S.quiz) { FX.enter = true; go({ name: 'quiz' }); } else startRound(); }

function startMistakes() {
  const m = Srs.mistakes(ids, S.progress, now());
  if (m.length) startRound('mistakes', m);
}

const current = () => (S.quiz ? byId.get(Round.current(S.quiz.round)) : null);

/** Options rise in one after another; until one is in place it takes no taps. */
function settled(shownAt, pos) { return !shownAt || uptime() - shownAt >= 70 + 55 * pos + 220; }

function pick(questionId, index, pos) {
  const s = S.quiz; const q = current();
  if (!s || !q) return;
  if (!settled(s.shownAt, pos)) return;
  const picked = Flow.pick(s, q, questionId, index, uptime());
  if (!picked) return;
  S.quiz = picked;
  if (q.kind === 'multi') { saveSession(); render(); } else submit(questionId);
}

function submit(questionId) {
  const s = S.quiz; const q = current();
  if (!s || !q || s.answered || q.id !== questionId) return;
  if (q.kind === 'multi' && s.selected.length !== q.choose) return;
  const [answered, updated] = Flow.submit(s, q, S.progress, now());
  S.quiz = answered;
  S.progress = updated;
  write({ progress: updated, session: session() });
  FX.answered = true;
  render();
}

function next() {
  const s = S.quiz;
  if (!s || !s.answered) return;
  const round = Round.next(s.round);
  if (Round.isFinished(round)) {
    const mastered = round.ids.filter((id) => (S.roundStart[id] ?? 0) < Srs.MASTERED_BOX && Card.isMastered(Progress.card(S.progress, id))).length;
    const climbed = [...new Set(round.ids)].filter((id) => Progress.card(S.progress, id).box > (S.roundStart[id] ?? 0)).length;
    S.roundSummary = { kind: s.kind, ids: round.ids, score: Round.score(round), total: Round.total(round), mistakes: Round.mistakes(round), newlyMastered: mastered, climbed };
    S.quiz = null;
    S.roundStart = {};
    saveSession();
    go({ name: 'roundEnd' });
    return;
  }
  S.quiz = { kind: s.kind, round, order: shuffledOrder(byId.get(Round.current(round))), selected: [], answered: false, correct: false, seq: S.progress.seq, shownAt: uptime() };
  saveSession();
  FX.enter = true;
  FX.next = true;
  render();
}

function startExam() {
  const e = Exam.start(ids, now());
  S.exam = { exam: e, order: shuffledOrder(byId.get(Exam.current(e))), selected: [], shownAt: uptime() };
  saveSession();
  FX.enter = true;
  go({ name: 'exam' });
}
const resumeExam = () => { if (S.exam) { FX.enter = true; go({ name: 'exam' }); } };
const openLastExam = () => { if (S.examSummary) go({ name: 'examEnd' }); };
function discardExam() { S.exam = null; saveSession(); render(); }
function leaveExam() { if ((S.exam?.exam.index ?? 0) === 0) { S.exam = null; saveSession(); } home(); }

function examPick(questionId, index, pos) {
  const s = S.exam;
  if (!s) return;
  const q = byId.get(Exam.current(s.exam));
  if (!q || !settled(s.shownAt, pos)) return;
  const p = Flow.examPick(s, q, questionId, index, uptime());
  if (!p) return;
  S.exam = p;
  saveSession();
  render();
}

function examNext(questionId) {
  const s = S.exam;
  if (!s) return;
  const q = byId.get(Exam.current(s.exam));
  if (!q || q.id !== questionId || s.selected.length !== q.choose) return;
  const e = Exam.answer(s.exam, s.selected, now());
  // A new attempt with an answer replaces the last one's review.
  S.lastExam = null;
  S.examSummary = null;
  if (Exam.isFinished(e)) {
    S.progress = Flow.applyExam(S.progress, e, byId, now());
    S.examSummary = summarize(e);
    S.lastExam = e;
    S.exam = null;
    write({ progress: S.progress, session: session(), sessionFirst: true });
    go({ name: 'examEnd' });
    return;
  }
  S.exam = { exam: e, order: shuffledOrder(byId.get(Exam.current(e))), selected: [], shownAt: uptime() };
  saveSession();
  FX.enter = true;
  FX.next = true;
  render();
}

function retryLoadProgress() {
  const r = readKey(KP, Progress.normalize);
  if (r.ok) {
    S.damaged = null;
    const b = Flow.rebase(session(), r.ok, byId);
    S.quiz = b.quiz; S.roundStart = b.roundStart; S.exam = b.exam; S.lastExam = b.lastExam;
    S.examSummary = b.lastExam ? summarize(b.lastExam) : null;
    S.progress = b.progress;
    write({ progress: b.progressChanged ? S.progress : null, session: session() });
  } else if (r.missing) {
    S.damaged = null;
    write({ progress: S.progress });
  } else {
    S.damaged = { backup: r.backup };
  }
  render();
}
function acceptFreshProgress() { S.damaged = null; write({ progress: S.progress }); render(); }

/** Everything this app stored on the phone: progress, the open round, backups, the offline copy. */
async function wipeEverything() {
  try {
    const keys = [];
    for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k.startsWith('uli.')) keys.push(k); }
    keys.forEach((k) => localStorage.removeItem(k));
    sessionStorage.clear();
  } catch { /* ignore */ }
  try { for (const k of await caches.keys()) await caches.delete(k); } catch { /* ignore */ }
  try { for (const r of await navigator.serviceWorker.getRegistrations()) await r.unregister(); } catch { /* ignore */ }
  $app.innerHTML = `<div class="screen" style="display:grid;place-items:center;padding:32px;text-align:center">
    <div><div class="title-l">Všetko vymazané</div><p class="body-m muted">Postup, rozrobené kolo aj offline kópia appky sú preč z tohto telefónu.
    Teraz môžeš ikonu ULI odstrániť z plochy.</p></div></div>`;
}

// ------------------------------------------------------------------ dialogs and toasts

function confirmDialog(title, text, confirmLabel, onConfirm, cancelLabel = 'Zrušiť', danger = true) {
  S.dialog = { title, text, confirmLabel, onConfirm, cancelLabel, danger };
  render();
}
function askQuitExam() {
  confirmDialog('Odísť zo skúšky?', 'Pokus ostane uložený a dokončíš ho z Domova. Čas beží ďalej, ako na ostrej skúške.', 'Odísť', home, 'Zostať', false);
}
let toastTimer = 0;
function toast(text) {
  document.querySelector('.toast')?.remove();
  const el = document.createElement('div');
  el.className = 'toast';
  el.setAttribute('role', 'status');
  el.textContent = text;
  document.body.appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 4000);
}

function dialogHtml() {
  const d = S.dialog;
  if (!d) return '';
  return `<div class="overlay" data-a="dialog-cancel" role="presentation">
    <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-t" data-stop>
      <h2 id="dlg-t">${esc(d.title)}</h2><p>${esc(d.text)}</p>
      <div class="btns"><button class="btn-text" data-a="dialog-cancel">${esc(d.cancelLabel)}</button>
      <button class="btn-text ${d.danger ? 'danger' : ''}" data-a="dialog-ok">${esc(d.confirmLabel)}</button></div>
    </div></div>`;
}

// ------------------------------------------------------------------ shared pieces

function bigButton(text, action, { enabled = true, trailing = false, attrs = '' } = {}) {
  return `<button class="btn-big press" data-a="${action}" ${attrs} ${enabled ? '' : 'disabled'}>${esc(text)}${trailing ? icon('forward') : ''}</button>`;
}

function hook(text) {
  return `<div class="hook">${badge('lightbulb', 'flower', 'var(--amber)', 'var(--on-amber)', 30, 17)}
    <div class="grow"><div class="label-m muted">Zapamätaj si</div><div class="t">${codeText(text)}</div></div></div>`;
}

function terminal(demo) {
  const lines = demo.split('\n').map((line) => {
    if (line.startsWith('$ ')) return `<div class="ln"><span class="pr">$ </span><span class="cmd">${esc(Code.gluePaths(line.slice(2)))}</span></div>`;
    if (line === '$') return `<div class="ln"><span class="pr">$</span></div>`;
    return `<div class="ln">${esc(Code.gluePaths(line.replace(/\t/g, '    '))) || '&nbsp;'}</div>`;
  }).join('');
  return `<div class="terminal mono"><div class="head"><i></i>bob@uli: ~</div><div class="body">${lines}</div></div>`;
}

function terminalPeek(demo) {
  const ls = demo.split('\n');
  const first = (ls.find((l) => l.startsWith('$ ')) || ls[0]).replace(/^\$ /, '');
  return `<div class="peekbox"><button class="peek press" data-a="peek" aria-expanded="false" aria-label="Ukázať ukážku v termináli">
      <div class="grow"><div class="label-l">Ukážka v termináli</div><div class="first mono"><b>$ </b>${esc(first)}</div></div>
      <span class="round">${icon('down')}</span></button><template>${terminal(demo)}</template></div>`;
}

function disputed(note) {
  return `<button class="disputed" data-a="open" aria-expanded="false">
    <div class="row">${icon('balance')}<span class="label-l">Na skúške vs. v praxi</span>${icon('down', 'class="ic x"')}</div>
    <div class="more"><div class="body-m" style="color:var(--on-surface)">${codeText(note)}</div>
    <div class="label-m muted" style="margin-top:6px">Na skúške platí kľúč, odpovedz podľa neho.</div></div></button>`;
}

function explanation(q, wrongNotes) {
  let h = '<div class="stack" style="gap:16px">';
  for (const n of wrongNotes) {
    h += `<div class="why-not"><div class="label-m" style="color:var(--wrong)">Prečo nie tvoja odpoveď</div><div class="body-m" style="margin-top:2px">${codeText(n)}</div></div>`;
  }
  h += `<div class="body-l" style="line-height:26px">${codeText(q.explanation)}</div>`;
  if (q.hook) h += hook(q.hook);
  if (q.demo) h += terminal(q.demo);
  const d = Q.disputedNote(q);
  if (d) h += disputed(d);
  return h + '</div>';
}

function answerRow(q, i, ok, codeOpts) {
  const t = q.options[i].text;
  const text = codeOpts.has(i) ? `<span class="mono">${esc(Code.gluePaths(t))}</span>` : `<span class="body-m">${codeText(t, true)}</span>`;
  return `<div class="ans ${ok ? 'ok' : 'bad'}">${badge(ok ? 'check' : 'close', ok ? 'sunny' : 'puffy', '', '', 24, 15)}${text}</div>`;
}
const correctAnswers = (q, codeOpts) => `<div class="stack" style="gap:6px">${Q.correctIndices(q).map((i) => answerRow(q, i, true, codeOpts)).join('')}</div>`;
function wrongAnswers(q, picked, codeOpts) {
  const wrong = picked.filter((i) => i >= 0 && i < q.options.length && !q.options[i].correct).sort((a, b) => a - b);
  let h = `<div class="stack" style="gap:6px">${wrong.map((i) => answerRow(q, i, false, codeOpts)).join('')}`;
  if (!picked.length) h += '<div class="body-m" style="color:var(--wrong)">Bez odpovede</div>';
  return h + '</div>';
}

function reviewItem(q, picked) {
  const codeOpts = Code.codeOptions(q);
  const picks = picked ? picked.filter((i) => i >= 0 && i < q.options.length) : null;
  const notes = (picks || []).filter((i) => !q.options[i].correct).map((i) => q.options[i].note).filter(Boolean);
  return `<div class="review"><button class="row tap" data-a="open-review" aria-expanded="false" style="width:100%;align-items:flex-start;gap:10px">
      <div class="grow title-m">${codeText(q.question, true)}</div>
      <span class="pill"><span class="lbl">Prečo</span>${icon('down')}</span></button>
    <div class="more"><div style="height:12px"></div>
      ${picks ? wrongAnswers(q, picks, codeOpts) + '<div style="height:6px"></div>' : ''}
      ${correctAnswers(q, codeOpts)}<div style="height:14px"></div>${explanation(q, notes)}</div></div>`;
}

function questionCard(q, { answered, compact, keepHint, label, hint, single }) {
  const showSk = S.progress.showSk;
  return `<div class="qcard ${answered ? 'answered' : ''} ${compact ? 'compact' : ''}" ${single ? 'data-single' : ''}>
    <div>
      ${answered ? '' : `<div class="labels"><div class="tags">${label}</div>
        <button class="sk tap" data-a="sk" aria-pressed="${showSk}" aria-label="Slovenský preklad otázky">${icon('translate')}SK</button></div>`}
      <div class="qtext" lang="en">${codeText(q.question, true)}</div>
      ${showSk && Q.hasDistinctSk(q) ? `<div class="qsk body-l">${codeText(q.questionSk)}</div>` : ''}
    </div>
    ${!answered && (!compact || keepHint) ? `<div class="hint"><div>${hint}</div>${compact ? '' : promptMark()}</div>` : ''}
  </div>`;
}

const promptMark = () => `<span class="badge prompt-mark">${shp('cookie12')}<b class="mono">&gt;<span class="cursor">_</span></b></span>`;

function chooseCounter(choose, picked, answered) {
  let dots = '';
  for (let i = 0; i < choose; i++) dots += i < picked ? `<i class="on">${shp('cookie6')}</i>` : '<i></i>';
  return `<div class="hintline">Vyber ${choose}<span class="dots">${dots}</span>${answered ? '' : `<span class="label-l" style="margin-left:4px">${picked} z ${choose}</span>`}</div>`;
}

const hintLine = (text) => `<div class="hintline">${icon('touch')}${esc(text)}</div>`;

function optionHtml(q, i, pos, st, multi, tappable, action, enter) {
  const opt = q.options[i];
  const isCode = Code.codeOptions(q).has(i);
  const letter = String.fromCharCode(65 + pos);
  let ob;
  const sq = multi && ['idle', 'dim', 'missed'].includes(st) ? 'sq' : '';
  switch (st) {
    case 'selected': ob = shp('cookie4') + (multi ? icon('check') : `<span>${letter}</span>`); break;
    case 'right': ob = shp('sunny') + icon('check'); break;
    case 'wrong': ob = shp('puffy') + icon('close'); break;
    case 'missed': ob = '<i class="ring"></i>' + icon('check'); break;
    default: ob = `<i class="ring"></i><span>${letter}</span>`;
  }
  const picked = st === 'selected' || st === 'wrong' || (st === 'right' && S.quiz?.selected?.includes(i));
  const state = { right: picked ? 'Tvoja odpoveď, správna' : 'Správna odpoveď', wrong: 'Tvoja odpoveď, nesprávna', missed: 'Správna odpoveď, nevybraná' }[st];
  const text = isCode ? `<span class="txt mono" lang="en">${esc(Code.gluePaths(opt.text))}</span>` : `<span class="txt" lang="en">${codeText(opt.text, true)}</span>`;
  const note = st === 'wrong' && opt.note ? `<div class="note">${codeText(opt.note)}</div>` : '';
  return `<div class="opt ${st} ${tappable ? 'tappable' : 'static'} ${enter ? 'enter' : ''}" style="--i:${pos}">
    ${tappable ? '<div class="ledge"></div>' : ''}
    <button class="face tap" ${tappable ? `data-a="${action}" data-q="${q.id}" data-i="${i}" data-p="${pos}"` : 'tabindex="-1" aria-disabled="true"'}
      role="${multi ? 'checkbox' : 'radio'}" aria-checked="${picked}" ${state ? `aria-description="${state}"` : ''}>
      <div class="frow"><span class="ob ${sq} ${st === 'missed' ? 'missed' : ''}">${ob}</span>${text}</div>${note}</button></div>`;
}

function edges(color, label, pill = true) {
  return `<div class="edge-top" style="--edge:${color}"></div><div class="edge-bot" style="--edge:${color}"></div>
    ${pill ? `<button class="more-pill" data-a="more" tabindex="-1">${icon('down')}${esc(label)}</button>` : ''}`;
}

// ------------------------------------------------------------------ Home

function viewHome() {
  const p = S.progress;
  const t = now();
  const readiness = Srs.readiness(ids, p);
  const chance = Srs.passChanceFor(ids, p);
  const mastered = Srs.masteredCount(ids, p);
  const drill = Srs.allMistakes(ids, p, t).length;
  const preview = Srs.buildRound(ids, p, t);
  const openRound = S.quiz;
  const openExam = S.exam;
  const due = preview.filter((id) => Srs.isDue(Progress.card(p, id), t)).length;
  const nw = preview.filter((id) => Card.isNew(Progress.card(p, id))).length;
  const lastExam = S.examSummary && (!openExam || openExam.exam.index === 0) ? S.examSummary : null;
  const above = chance >= Srs.LIKELY_PASS;
  const pct = Math.round(readiness * 100);
  const anim = FX.nav ? 'anim' : '';

  const subtitle = Object.keys(p.cards).length === 0 ? 'Úvod do Linuxu, zápočtový test' : todayLine(p, t);
  let h = `<div class="screen home ${navClass()}"><div class="scroll" id="list"><div class="pad stack gap12" style="padding-top:calc(var(--sat) + 8px);padding-bottom:20px">`;

  h += `<div class="header"><div class="mark mono" aria-hidden="true"><span>&gt;<span class="cursor">_</span></span></div>
    <div class="grow"><div class="title-l" style="line-height:22px">ULI</div><div class="body-s muted" style="font-size:13px;line-height:18px">${esc(subtitle)}</div></div>
    <div class="menu-wrap"><button class="icon-btn" data-a="menu" aria-label="Viac" aria-expanded="${S.menu}">${icon('more')}</button>
    ${S.menu ? `<div class="menu" role="menu"><button role="menuitem" data-a="reset">Vymazať postup</button><button role="menuitem" data-a="wipe">Odstrániť všetky dáta appky</button></div>` : ''}</div></div>`;

  if (isIos() && !isStandalone() && !S.tipHidden) {
    h += `<div class="install-tip"><div class="grow body-m"><b>Pridaj si ULI na plochu.</b> V Safari ťukni na <svg class="share-ic" viewBox="0 0 24 24" aria-label="Zdieľať"><path d="${ICONS.share}"/></svg> Zdieľať a vyber <b>Pridať na plochu</b>. Potom ide aj bez internetu.</div>
      <button class="icon-btn" data-a="tip-hide" aria-label="Skryť" style="width:40px;height:40px">${icon('close')}</button></div>`;
  }

  if (S.damaged) {
    const b = S.damaged.backup;
    h += `<div class="card-warn"><div class="title-m">${b ? 'Nepodarilo sa načítať tvoj postup, záloha je uložená' : 'Nepodarilo sa načítať tvoj postup ani uložiť zálohu'}</div>
      <div class="body-m" style="margin-top:4px;padding-right:8px">${b ? `Kópia súboru je v aplikácii ako ${esc(b)}. ` : ''}Pôvodný súbor sa neprepíše. Medzitým sa učíš s prázdnym postupom.<br>Kým nevyberieš, nový postup sa neukladá.</div>
      <div class="row" style="justify-content:flex-end"><button class="btn-text" data-a="retry-load">Skúsiť znova</button><button class="btn-text" data-a="fresh">Začať odznova</button></div></div>`;
  }

  if (openExam) {
    h += `<div class="card-exam"><div class="row" style="gap:12px">${badge('timer', 'clover4', 'var(--tertiary)', 'var(--on-tertiary)', 40, 20)}
      <div class="grow"><div class="title-m">Rozrobená skúška</div><div class="body-m" style="opacity:.8">${examProgress(openExam.exam.index, openExam.exam.ids.length)}</div></div>
      <button class="btn-text" data-a="discard-exam">Zahodiť</button></div>
      <button class="go press" data-a="resume-exam">Pokračovať v skúške</button></div>`;
  }

  h += `<div class="readiness"><div class="row" style="gap:16px">
      <span class="badge cookie-num ${above ? 'ok' : ''}">${shp('cookie12')}<b>${pct}<small>%</small></b></span>
      <div class="grow"><div class="title-l">Odhad na skúške</div>
        <div class="body-m muted" style="font-size:16px;margin-top:2px">Zvládnuté: ${mastered} ${zo(ids.length)} ${ids.length} otázok</div>
        <div class="title-s" style="font-size:16px;line-height:22px;color:${above ? 'var(--right)' : 'var(--on-surface)'}">Šanca prejsť: ${chanceText(chance)}</div></div></div>
    <div class="meter ${anim}"><div class="line">hranica 80 %</div>
      <div class="track"><i class="t"></i><i class="z"></i><i class="f" style="width:${(readiness * 100).toFixed(2)}%;background:${above ? 'var(--right)' : 'var(--primary)'}"></i><i class="l"></i></div></div></div>`;

  h += `<div class="stack gap3 grp">
    <button class="mode-row press" data-a="${openExam ? 'resume-exam' : 'start-exam'}">${badge('timer', 'clover4', 'var(--tertiary-c)', 'var(--on-tertiary-c)', 44, 22)}
      <span class="title">Skúška nanečisto</span><span class="detail">${openExam ? examProgress(openExam.exam.index, openExam.exam.ids.length) : '50 otázok'}</span>${icon('chevron', 'class="ic chev"')}</button>
    ${lastExam ? `<button class="last-exam press" data-a="last-exam"><span class="grow">Posledná skúška: ${lastExam.result.correct}/${lastExam.result.total} · ${lastExam.result.wrong.length ? 'pozrieť chyby' : 'pozrieť výsledok'}</span>${icon('chevron', 'class="ic chev"')}</button>` : ''}
    <button class="mode-row press" data-a="mistakes" ${drill > 0 ? '' : 'disabled'}>${badge('replay', 'softBurst', 'var(--wrong-c)', 'var(--on-wrong-c)', 44, 22)}
      <span class="title">Chyby</span><span class="detail">${drill === 0 ? 'Zatiaľ žiadne' : `${drill} ${questionsWord(drill)}`}</span>${icon('chevron', 'class="ic chev"')}</button>
    <button class="mode-row press" data-a="browse">${badge('stack', 'cookie6', 'var(--secondary-c)', 'var(--on-secondary-c)', 44, 22)}
      <span class="title">Všetky otázky</span><span class="detail">${ids.length} otázok</span>${icon('chevron', 'class="ic chev"')}</button></div>`;

  h += `<div class="title-l" style="padding:8px 0 0 8px">Témy</div><div class="stack gap3 grp">`;
  for (const topic of TOPICS) {
    const tIds = questions.filter((q) => q.topic === topic.key).map((q) => q.id);
    const m = tIds.filter((id) => Card.isMastered(Progress.card(p, id))).length;
    const l = tIds.filter((id) => { const c = Progress.card(p, id); return !Card.isNew(c) && !Card.isMastered(c); }).length;
    const total = tIds.length;
    const done = m === total;
    const wm = (m / total) * 100;
    const wl = (l / total) * 100;
    let legend;
    if (done) legend = `<span><i style="background:var(--primary)"></i>Všetky zvládnuté</span>`;
    else if (m === 0 && l === 0) legend = `<span><i class="hollow" style="background:var(--sc-highest)"></i>Všetko nové</span>`;
    else {
      legend = `<span><i style="background:var(--primary)"></i>${agree(m, 'zvládnut')}</span>`;
      if (l > 0) legend += `<span><i style="background:var(--learning)"></i>${agree(l, 'rozroben')}</span>`;
      if (total - m - l > 0) legend += `<span><i class="hollow" style="background:var(--sc-highest)"></i>${agree(total - m - l, 'nov')}</span>`;
    }
    h += `<button class="topic-row press" data-a="topic" data-t="${topic.key}">
      <div class="row"><span class="grow body-l">${esc(topic.title)}</span>${done ? badge('check', 'verySunny', 'var(--right)', 'var(--on-right)', 22, 14) : `<span class="label-m muted">${total} ${questionsWord(total)}</span>`}</div>
      <div class="bar" style="margin-top:8px">${l > 0 ? `<i style="left:calc(${wm}% + ${m > 0 ? 3 : 0}px);width:max(10px, calc(${wl}% - 3px));background:var(--learning)"></i>` : ''}${m > 0 ? `<i style="left:0;width:max(10px, ${wm}%);background:var(--primary)"></i>` : ''}</div>
      <div class="legend">${legend}</div></button>`;
  }
  h += `</div></div></div><div class="status-scrim"></div><div class="fade-bottom"></div>`;

  const title = openRound ? 'Pokračovať' : isFresh() ? 'Začať' : 'Ďalšie kolo';
  const sub = openRound ? `Rozrobené kolo, otázka ${Round.displayNumber(openRound.round)} z ${Round.total(openRound.round)}`
    : isFresh() ? `Prvých ${preview.length} otázok, od úvodu do Linuxu` : roundSubtitle(due, nw, preview.length);
  h += `<div class="actions" style="padding-bottom:calc(12px + var(--sab))"><button class="continue" data-a="continue">
    <div class="grow"><div class="t">${title}</div><div class="s">${esc(sub)}</div></div>
    <span class="badge" style="width:60px;height:60px;color:var(--primary-c)">${shp('cookie9')}<span style="position:relative;color:var(--on-primary-c);display:grid">${icon('forward', 'style="width:26px;height:26px"')}</span></span></button></div></div>`;
  return h;
}

const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const isStandalone = () => navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;

// ------------------------------------------------------------------ Quiz

function segments(s) {
  const r = s.round;
  const retrying = Round.isRetry(r) && !s.answered;
  const segs = r.ids.map((id) => {
    if (retrying && id === Round.current(r)) return 'cur retry';
    const f = Round.first(r, id);
    if (f === true) return 'right';
    if (f === false) return 'wrong';
    if (id === Round.current(r)) return 'cur';
    return 'up';
  });
  return `<div class="segs" aria-hidden="true">${segs.map((c, i) => `<div class="seg ${c} ${c === 'right' && segs[i + 1] === 'right' ? 'join' : ''}"></div>`).join('')}</div>`;
}

function comesBack(s) {
  const r = s.round;
  const id = r.queue[r.index];
  if (id === undefined) return null;
  if (!s.correct) {
    for (let k = r.index + 1; k < r.queue.length; k++) if (r.queue[k] === id) return comesBackAfter(k - r.index);
    return null;
  }
  return comesBackIn(Progress.card(S.progress, id).dueAt - now());
}

function verdictBadge(correct) {
  if (!correct) {
    return `<span class="vb" style="width:56px;height:56px"><span class="core" style="width:44px;height:44px;color:var(--wrong)">${shp('puffy')}<span style="position:relative;color:var(--on-wrong);display:grid">${icon('close')}</span></span></span>`;
  }
  const colors = ['var(--amber)', 'var(--tertiary)', 'var(--right)', 'var(--learning)', 'var(--amber)', 'var(--tertiary)', 'var(--right)', 'var(--amber)'];
  const shapes = ['cookie6', 'clover4', 'sunny', 'gem', 'flower', 'cookie6', 'clover4', 'gem'];
  let parts = '';
  for (let i = 0; i < 8; i++) {
    const a = ((-100 + i * 45 + (i % 2) * 12) * Math.PI) / 180;
    const d1 = 36 + (i % 3) * 2;
    const d0 = 18 + (i % 3) * 2;
    const size = 6 + (i % 3) * 2;
    parts += `<span class="p" style="width:calc(${size}px * var(--k));height:calc(${size}px * var(--k));color:${colors[i]};
      --x:calc(${(Math.cos(a) * d1).toFixed(1)}px * var(--k));--y:calc(${(Math.sin(a) * d1).toFixed(1)}px * var(--k));
      --x0:calc(${(Math.cos(a) * d0).toFixed(1)}px * var(--k));--y0:calc(${(Math.sin(a) * d0).toFixed(1)}px * var(--k));--r:${90 + i * 30}deg">${shp(shapes[i])}</span>`;
  }
  return `<span class="vb" style="width:calc(76px * var(--k));height:calc(76px * var(--k))">${parts}
    <span class="core" style="width:calc(46px * var(--k));height:calc(46px * var(--k));color:var(--right)">${shp('verySunny')}<span style="position:relative;color:var(--on-right);display:grid">${icon('check', 'style="width:calc(24px * var(--k));height:calc(24px * var(--k))"')}</span></span></span>`;
}

const streakPill = (n) => `<span class="streak"><span class="badge" style="width:18px;height:18px">${shp('sunny')}<b class="rounded">${n}</b></span>za sebou</span>`;

function feedback(q, s) {
  const correct = s.correct;
  const streak = Round.streak(s.round);
  const back = comesBack(s);
  let v;
  if (correct) {
    v = `<div class="verdict ok" style="--k:1">
      <div class="inline">${verdictBadge(true)}<div class="grow" style="margin-left:14px"><div class="word ok-c">Správne</div>${back ? `<div class="label-l muted">${esc(back)}</div>` : ''}</div>${streak >= 2 ? `<span style="margin-left:8px">${streakPill(streak)}</span>` : ''}</div>
      <div class="big">${verdictBadge(true)}<div class="word ok-c">Správne</div><div class="row" style="margin-top:6px;gap:10px">${back ? `<span class="label-l muted">${esc(back)}</span>` : ''}${streak >= 2 ? streakPill(streak) : ''}</div></div></div>`;
  } else {
    v = `<div class="verdict"><div class="inline">${verdictBadge(false)}<div class="grow" style="margin-left:10px"><div class="word bad-c">Nesprávne</div>${back ? `<div class="label-l muted">${esc(back)}</div>` : ''}</div></div></div>`;
  }
  let h = `<div class="sheet" role="status">${v}`;
  if (q.hook) h += `<div style="height:14px"></div>${hook(q.hook)}`;
  h += `<div style="height:20px"></div><div class="label-l muted">Prečo</div><div style="height:4px"></div>
    <div class="body-l" style="font-size:16px;line-height:25px">${codeText(q.explanation)}</div>`;
  if (q.demo) h += `<div style="height:18px"></div>${correct ? terminal(q.demo) : terminalPeek(q.demo)}`;
  const d = Q.disputedNote(q);
  if (d) h += `<div style="height:14px"></div>${disputed(d)}`;
  return h + '</div>';
}

function viewQuiz() {
  const s = S.quiz;
  if (!s) return viewHome();
  const r = s.round;
  const qid = Round.current(r);
  const q = byId.get(qid);
  const answered = s.answered;
  const multi = q.kind === 'multi';
  const sel = s.selected;
  const key = `${qid}:${r.index}`;
  const compact = S.compactFor === key;
  const left = Round.toFix(r, answered);
  const retry = Round.isRetry(r);
  const counter = !retry ? `${Round.displayNumber(r)}/${Round.total(r)}` : left === 0 ? 'Opravené' : `Oprava · ešte ${left}`;
  const counterLabel = !retry ? `Otázka ${Round.displayNumber(r)} z ${Round.total(r)}` : left === 0 ? 'Oprava, všetko opravené' : `Oprava, na opravu ostáva ${left}`;

  let opts = '';
  s.order.forEach((i, pos) => {
    const opt = q.options[i];
    let st;
    if (!answered) st = sel.includes(i) ? 'selected' : 'idle';
    else if (opt.correct && sel.includes(i)) st = 'right';
    else if (opt.correct && !multi) st = 'right';
    else if (opt.correct) st = 'missed';
    else if (sel.includes(i)) st = 'wrong';
    else st = 'dim';
    // Once answered only what matters stays: the right one(s) and your pick.
    if (st === 'dim') { if (FX.answered) opts += optionHtml(q, i, pos, 'idle', multi, false, '', false).replace('class="opt idle', 'class="opt idle gone'); return; }
    opts += optionHtml(q, i, pos, st, multi, !answered, 'pick', FX.enter && !answered);
  });

  const hint = multi ? chooseCounter(q.choose, sel.length, answered)
    : q.kind === 'truefalse' ? hintLine('Pravda, alebo nepravda? Ťukni.') : hintLine('Ťukni na odpoveď');
  const last = r.index + 1 >= r.queue.length;

  return `<div class="screen quiz ${navClass()}">
    <div class="topbar"><button class="icon-btn" data-a="home" aria-label="Zavrieť kolo">${icon('close')}</button>${segments(s)}
      <span class="counter ${retry ? (left === 0 ? 'fixed' : 'retry') : ''}" aria-label="${counterLabel}">${counter}</span></div>
    <div class="qwrap ${FX.next ? 'slide-in' : ''} ${FX.answered ? 'fx' : ''}">
      <div class="qscroll">
        ${questionCard(q, { answered, compact, keepHint: multi, label: `<span class="tag">${esc(topicOf(q.topic).title)}</span>`, hint, single: !multi })}
        <div class="qbottom ${answered ? (s.correct ? 'rightans' : 'wrongans') : ''}">
          <div class="opts ${multi ? 'multi' : ''} ${answered ? '' : 'unanswered'}" role="${multi ? 'group' : 'radiogroup'}">${opts}</div>
          ${answered ? feedback(q, s) : ''}
        </div>
      </div>
      ${edges(answered ? 'var(--sc)' : 'var(--bg)', answered ? 'Pokračuje nižšie' : 'Ďalšie možnosti')}
    </div>
    ${answered ? `<div class="actions panel">${bigButton(last ? 'Dokončiť kolo' : 'Ďalej', 'next', { trailing: true })}</div>`
      : multi ? `<div class="actions">${bigButton('Skontrolovať', 'submit', { enabled: sel.length === q.choose, attrs: `data-q="${q.id}"` })}</div>`
        : '<div style="height:var(--sab);flex:none"></div>'}
  </div>`;
}

// ------------------------------------------------------------------ Exam

function viewExam() {
  const st = S.exam;
  if (!st) return viewHome();
  const e = st.exam;
  const q = byId.get(Exam.current(e));
  const multi = q.kind === 'multi';
  const number = e.index + 1;
  const total = e.ids.length;
  const key = `exam:${q.id}:${e.index}`;
  const compact = S.compactFor === key;
  let opts = '';
  st.order.forEach((i, pos) => {
    opts += optionHtml(q, i, pos, st.selected.includes(i) ? 'selected' : 'idle', multi, true, 'exam-pick', FX.enter);
  });
  const hint = multi ? chooseCounter(q.choose, st.selected.length, false)
    : `<div class="title-s muted">Vyber a pokračuj. Výsledok uvidíš na konci.</div>`;
  return `<div class="screen exam ${navClass()}">
    <div class="topbar"><button class="icon-btn" data-a="quit-exam" aria-label="Ukončiť skúšku">${icon('close')}</button>
      <div class="exambar" role="progressbar" aria-valuenow="${e.index}" aria-valuemax="${total}"><i style="width:${(e.index / total) * 100}%"></i></div>
      <span class="timer">${icon('timer')}<span class="mono" id="timer">${formatDuration(now() - e.startedAt)}</span></span></div>
    <div class="qwrap ${FX.next ? 'slide-in' : ''}">
      <div class="qscroll">
        ${questionCard(q, { answered: false, compact, keepHint: multi, label: `<span class="tag">Otázka ${number} z ${total}</span>`, hint, single: !multi })}
        <div class="qbottom" style="margin-top:14px"><div class="opts unanswered" style="padding-bottom:8px">${opts}</div></div>
      </div>
      ${edges('var(--bg)', 'Ďalšie možnosti')}
    </div>
    <div class="actions">${bigButton(number === total ? 'Odovzdať' : 'Ďalej', 'exam-next', { enabled: st.selected.length === q.choose, trailing: number !== total, attrs: `data-q="${q.id}"` })}</div>
  </div>`;
}

// ------------------------------------------------------------------ Round end

function viewRoundEnd() {
  const s = S.roundSummary;
  if (!s) return viewHome();
  const ratio = s.score / s.total;
  const good = ratio >= 0.8;
  const readiness = Math.round(Srs.readiness(ids, S.progress) * 100);
  const chance = Srs.passChanceFor(ids, S.progress);
  const likely = chance >= Srs.LIKELY_PASS;
  const mastered = Srs.masteredCount(ids, S.progress);
  const title = s.score === s.total ? 'Bez chyby' : good ? 'Výborne' : ratio >= 0.6 ? 'Dobré kolo' : 'Ešte to dáš';
  const statA = s.newlyMastered > 0 || s.climbed === 0
    ? `<div><div class="v" style="color:${s.newlyMastered > 0 ? 'var(--right)' : 'var(--on-surface)'}">+${s.newlyMastered}</div><div class="title-s">novo zvládnuté</div><div class="label-m muted">spolu ${mastered} ${zo(ids.length)} ${ids.length}</div></div>`
    : `<div><div class="v" style="color:var(--right)">+${s.climbed}</div><div class="title-s">o krok bližšie</div><div class="label-m muted">zvládnutá po 3. správnej</div></div>`;
  let h = `<div class="screen ${navClass()}"><div class="scroll" id="list"><div class="pad stack gap10" style="padding-top:calc(var(--sat) + 24px);padding-bottom:24px;text-align:center">
    <div style="position:relative">${good && FX.nav ? confetti() : ''}<span class="badge score ${good ? 'good' : ''}">${shp('verySunny')}<b>${s.score}<small>/${s.total}</small></b></span></div>
    <div style="padding:4px 0 10px"><div class="display-s">${title}</div>
      <div class="body-m muted" style="margin-top:6px;padding:0 24px">${esc(roundEndNote(s.kind, s.ids, s.mistakes, S.progress, now()))}</div></div>
    <div class="stats">${statA}<hr><div><div class="v" style="color:${likely ? 'var(--right)' : 'var(--on-surface)'}">${readiness} %</div><div class="title-s">odhad na skúške</div><div class="label-m muted">šanca prejsť ${chanceText(chance)}</div></div></div>`;
  if (s.mistakes.length) {
    h += `<div class="title-l" style="text-align:left;padding:18px 0 2px 8px">Na zopakovanie (${s.mistakes.length})</div>
      <div class="stack gap3 grp" style="text-align:left">${s.mistakes.map((id) => reviewItem(byId.get(id), null)).join('')}</div>`;
  }
  h += `</div></div><div class="status-scrim"></div>${bottomActions('Ďalšie kolo', 'next-round', 'Domov', 'home')}</div>`;
  return h;
}

function confetti() {
  const colors = ['var(--amber)', 'var(--right)', 'var(--tertiary)', 'var(--learning)', 'var(--primary)', 'var(--wrong)'];
  const shapes = ['sunny', 'clover4', 'cookie6', 'gem', 'flower', 'pill'];
  let parts = '';
  for (let i = 0; i < 18; i++) {
    const a = ((200 + i * (140 / 17) + (i % 3) * 4) * Math.PI) / 180;
    const d1 = 80 + 40 + (i % 4) * 22;
    const size = 12 + (i % 3) * 6;
    parts += `<i style="width:${size}px;height:${size}px;color:${colors[i % 6]};--x0:${(Math.cos(a) * 80).toFixed(1)}px;--y0:${(Math.sin(a) * 80).toFixed(1)}px;
      --x1:${(Math.cos(a) * d1).toFixed(1)}px;--y1:${(Math.sin(a) * d1 + 60).toFixed(1)}px;--r:${180 + i * 25}deg">${shp(shapes[i % 6])}</i>`;
  }
  return `<div class="confetti" style="top:86px">${parts}</div>`;
}

function bottomActions(primary, primaryAction, secondary, secondaryAction) {
  return `<div style="position:relative;flex:none"><div style="position:absolute;left:0;right:0;bottom:100%;height:20px;background:linear-gradient(transparent,var(--bg));pointer-events:none"></div>
    <button class="more-pill" data-a="more" style="bottom:calc(100% + 6px)">${icon('down')}Pokračuje nižšie</button></div>
    <div class="actions two">${bigButton(primary, primaryAction, { trailing: true })}<button class="btn-text" data-a="${secondaryAction}">${esc(secondary)}</button></div>`;
}

// ------------------------------------------------------------------ Exam end

function viewExamEnd() {
  const sum = S.examSummary;
  if (!sum) return viewHome();
  const res = sum.result;
  const pass = res.passed;
  const pct = Math.round(Srs.readiness(ids, S.progress) * 100);
  const chance = Srs.passChanceFor(ids, S.progress);
  const above = chance >= Srs.LIKELY_PASS;
  let h = `<div class="screen ${navClass()}"><div class="scroll" id="list"><div class="pad stack gap10" style="padding-top:calc(var(--sat) + 16px);padding-bottom:24px">
    <div class="result ${pass ? 'pass' : 'fail'}">
      <div class="label-l">Skúška nanečisto</div>
      <div class="row" style="margin-top:4px"><div class="grow display-s">${pass ? 'Prešiel si' : 'Ešte nie'}</div>
        ${badge(pass ? 'check' : 'close', pass ? 'verySunny' : 'puffy', pass ? 'var(--right)' : 'var(--wrong)', pass ? 'var(--on-right)' : 'var(--on-wrong)', 56, 28)}</div>
      <div class="pts" style="margin-top:18px">${formatPoints(res.points)}<small> / 80 b</small></div>
      <div class="pbar"><i style="left:0;right:0;background:currentColor;opacity:.12"></i><i style="left:0;min-width:12px;width:${(res.points / Exam.MAX_POINTS) * 100}%;background:${pass ? 'var(--right)' : 'var(--wrong)'}"></i><i class="l"></i></div>
      <div class="minis"><div><b>${res.correct} z ${res.total}</b><span>správne</span></div><div><b>${formatDuration(sum.durationMs)}</b><span>čas</span></div><div><b>64 b</b><span>hranica</span></div></div>
    </div>
    <div class="row surface r26" style="padding:12px 16px 12px 14px;gap:14px">
      <span class="badge" style="width:58px;height:58px;color:${above ? 'var(--right)' : 'var(--amber)'}">${shp('cookie12')}<b class="rounded" style="position:relative;font-size:14px;font-weight:800;color:${above ? 'var(--on-right)' : 'var(--on-amber)'}">${pct} %</b></span>
      <div class="grow"><div class="title-s" style="font-size:16px;line-height:22px">Odhad na skúške je teraz ${pct} %</div>
        <div class="body-m muted">Rastie, keď odpoveď vydrží aj o pár hodín a dní. Jedna dobrá skúška ho len posunie.</div></div></div>`;
  if (res.wrong.length) {
    h += `<div class="title-l" style="padding:14px 0 2px 8px">Kde si chyboval (${res.wrong.length})</div>
      <div class="stack gap3 grp">${res.wrong.map((id) => reviewItem(byId.get(id), sum.exam.answers[id] || [])).join('')}</div>`;
  }
  h += `</div></div><div class="status-scrim"></div>${bottomActions('Hotovo', 'home', 'Nová skúška', 'start-exam')}</div>`;
  return h;
}

// ------------------------------------------------------------------ Browse

function viewBrowse() {
  const p = S.progress;
  let h = `<div class="screen ${navClass()}"><div class="row" style="padding:calc(var(--sat) + 4px) 16px 4px 4px;gap:4px;flex:none">
      <button class="icon-btn" data-a="home" aria-label="Späť">${icon('back')}</button>
      <div class="grow"><div class="title-l">Všetky otázky</div><div class="body-s muted">Zvládnuté ${Srs.masteredCount(ids, p)} ${zo(ids.length)} ${ids.length}. Ťukni a uvidíš odpoveď.</div></div></div>
    <div class="scroll" id="list"><div class="pad" style="padding-top:4px;padding-bottom:calc(24px + var(--sab))">`;
  for (const topic of TOPICS) {
    const qs = questions.filter((q) => q.topic === topic.key);
    const m = qs.filter((q) => Card.isMastered(Progress.card(p, q.id))).length;
    h += `<div class="bhead" id="t-${topic.key}"><span class="grow title-m">${esc(topic.title)}</span>
      <span class="rounded" style="font-size:15px;font-weight:700;color:${m === qs.length ? 'var(--right)' : 'var(--on-surface-v)'}">${m}/${qs.length}</span></div>
      <div class="stack gap3 grp" style="--outer:22px">`;
    for (const q of qs) {
      const c = Progress.card(p, q.id);
      const mark = Card.isMastered(c) ? `<span class="mm done" aria-label="Zvládnutá">${shp('sunny')}${icon('check')}</span>`
        : Card.isNew(c) ? '<span class="mm new" aria-label="Nová"></span>'
          : `<span class="mm learn" aria-label="Rozrobená">${shp('cookie6')}</span>`;
      const codeOpts = Code.codeOptions(q);
      h += `<div class="brow" id="q-${q.id}"><button class="row tap" data-a="open-brow" aria-expanded="false" style="width:100%;align-items:flex-start;gap:12px">
          ${mark}<div class="grow"><div class="qt" lang="en">${codeText(q.question, true)}</div>
          ${p.showSk && Q.hasDistinctSk(q) ? `<div class="more body-m muted" style="margin-top:4px">${codeText(q.questionSk)}</div>` : ''}</div>
          <span class="mono" style="font-size:12px;color:var(--outline);margin-top:3px">${q.id}</span></button>
        <div class="more"><div style="height:14px"></div>${correctAnswers(q, codeOpts)}<div style="height:14px"></div>${explanation(q, [])}</div></div>`;
    }
    h += '</div>';
  }
  return h + '</div></div></div>';
}

// ------------------------------------------------------------------ render loop

function navClass() {
  if (FX.nav === 'in') return 'nav-in';
  if (FX.nav === 'back') return 'nav-back';
  return '';
}

let timer = 0;
function render() {
  const view = { home: viewHome, quiz: viewQuiz, exam: viewExam, roundEnd: viewRoundEnd, examEnd: viewExamEnd, browse: viewBrowse }[S.screen.name] || viewHome;
  const fx = FX;
  $app.innerHTML = view() + dialogHtml();
  FX = {};
  after(fx);
}

function after(fx) {
  clearInterval(timer);
  const list = $('#list') || $('.qscroll');
  if (list) {
    const scrim = $('.status-scrim');
    const fade = $('.home .fade-bottom');
    const top = $('.edge-top');
    const bot = $('.edge-bot');
    const pill = $('.more-pill');
    const update = () => {
      const more = list.scrollTop + list.clientHeight < list.scrollHeight - 2;
      scrim?.classList.toggle('on', list.scrollTop > 0);
      fade?.classList.toggle('on', more);
      top?.classList.toggle('on', list.scrollTop > 0);
      bot?.classList.toggle('on', more);
      pill?.classList.toggle('on', more);
    };
    list.addEventListener('scroll', update, { passive: true });
    requestAnimationFrame(update);
    S._update = update;
  }
  if (S.screen.name === 'browse' && S.screen.topic && fx.nav) {
    const el = $(`#t-${S.screen.topic}`);
    if (el && list) list.scrollTop = el.offsetTop - 4;
  }
  if (S.screen.name === 'quiz' || S.screen.name === 'exam') {
    const sc = $('.qscroll');
    const card = $('.qcard');
    const key = S.screen.name === 'quiz' && S.quiz ? `${Round.current(S.quiz.round)}:${S.quiz.round.index}`
      : S.exam ? `exam:${Exam.current(S.exam.exam)}:${S.exam.exam.index}` : null;
    // When the question and its options do not fit, the card drops its decoration (decided once per question).
    const answered = S.screen.name === 'quiz' && S.quiz?.answered;
    if (sc && card && !answered && S.compactFor !== key && sc.scrollHeight > sc.clientHeight + 1) {
      S.compactFor = key;
      render();
      return;
    }
    if (answered && S.quiz.correct) hero(sc, card);
  }
  if (S.screen.name === 'exam' && S.exam) {
    const started = S.exam.exam.startedAt;
    timer = setInterval(() => { const t = document.getElementById('timer'); if (t) t.textContent = formatDuration(now() - started); }, 1000);
  }
}

/** A right answer's verdict grows into whatever room the sheet leaves (up to 360 px), so the win fills the frame. */
function hero(sc, card) {
  const v = $('.verdict');
  const bottom = $('.qbottom');
  if (!v || !bottom) return;
  requestAnimationFrame(() => requestAnimationFrame(() => {
    const natural = card.offsetHeight + 8 + bottom.offsetHeight;
    const extra = Math.max(0, Math.min(360, sc.clientHeight - natural));
    const total = 76 + extra;
    v.style.minHeight = `${total}px`;
    const big = total >= 158;
    v.classList.toggle('big', big);
    v.style.setProperty('--k', big ? Math.min(1.45, Math.max(1, (total - 82) / 76)) : Math.min(1.6, Math.max(1, total / 76)));
    S._update?.();
  }));
}

// ------------------------------------------------------------------ events

const handlers = {
  continue: continueOrStart,
  'next-round': () => startRound(),
  home,
  menu: () => { S.menu = !S.menu; render(); },
  reset: () => { S.menu = false; confirmDialog('Začať odznova?', 'Vymaže sa celý tvoj postup: zvládnuté otázky, chyby aj odhad. Nedá sa to vrátiť.', 'Vymazať postup', resetProgress); },
  wipe: () => {
    S.menu = false;
    confirmDialog('Odstrániť všetky dáta?', 'Z telefónu sa vymaže postup, rozrobené kolo, zálohy aj offline kópia appky. Nedá sa to vrátiť.', 'Odstrániť všetko', wipeEverything);
  },
  'tip-hide': () => { S.tipHidden = true; try { localStorage.setItem(KTIP, '1'); } catch { /* ignore */ } render(); },
  'retry-load': retryLoadProgress,
  fresh: () => {
    const b = S.damaged?.backup;
    confirmDialog('Začať odznova?', 'Poškodený súbor sa prepíše postupom, ktorý máš teraz v aplikácii. ' +
      (b ? `Kópia ${b} ostane uložená.` : 'Zálohu sa nepodarilo vytvoriť, takže starý postup sa už nebude dať obnoviť.'), 'Začať odznova', acceptFreshProgress);
  },
  'start-exam': startExam,
  'resume-exam': resumeExam,
  'discard-exam': () => confirmDialog('Zahodiť rozrobenú skúšku?', 'Odpovede z tohto pokusu sa nezapočítajú. Postup v otázkach ostane.', 'Zahodiť', discardExam, 'Nechať'),
  'last-exam': openLastExam,
  mistakes: () => {
    const r = S.quiz?.round;
    if (r) {
      confirmDialog('Rozrobené kolo sa zahodí', `Si na otázke ${Round.displayNumber(r)} z ${Round.total(r)}. Chyby začnú nové kolo a toto sa zahodí. Odpovede, ktoré si už dal, ostanú započítané.`,
        'Zahodiť a začať Chyby', startMistakes, 'Nechať kolo');
    } else startMistakes();
  },
  browse: () => go({ name: 'browse', topic: null }),
  topic: (el) => go({ name: 'browse', topic: el.dataset.t }),
  sk: toggleSk,
  pick: (el) => pick(+el.dataset.q, +el.dataset.i, +el.dataset.p),
  submit: (el) => submit(+el.dataset.q),
  next,
  'exam-pick': (el) => examPick(+el.dataset.q, +el.dataset.i, +el.dataset.p),
  'exam-next': (el) => examNext(+el.dataset.q),
  'quit-exam': () => { if ((S.exam?.exam.index ?? 0) === 0) leaveExam(); else askQuitExam(); },
  more: () => { const l = $('#list') || $('.qscroll'); if (l) l.scrollBy({ top: l.clientHeight * 0.6, behavior: 'smooth' }); },
  open: (el) => { el.classList.toggle('open'); el.setAttribute('aria-expanded', el.classList.contains('open')); },
  'open-review': (el) => {
    const box = el.closest('.review');
    box.classList.toggle('open');
    el.setAttribute('aria-expanded', box.classList.contains('open'));
    el.querySelector('.lbl').textContent = box.classList.contains('open') ? 'Skryť' : 'Prečo';
  },
  'open-brow': (el) => {
    const box = el.closest('.brow');
    const wasOpen = box.classList.contains('open');
    $app.querySelectorAll('.brow.open').forEach((b) => { b.classList.remove('open'); b.querySelector('[data-a]').setAttribute('aria-expanded', 'false'); });
    if (!wasOpen) { box.classList.add('open'); el.setAttribute('aria-expanded', 'true'); }
  },
  peek: (el) => {
    // The demo folded into one row after a miss; a tap opens the full terminal in place.
    const box = el.closest('.peekbox');
    const tpl = box.querySelector('template');
    box.innerHTML = tpl.innerHTML;
    setTimeout(() => box.scrollIntoView({ behavior: 'smooth', block: 'nearest' }), 50);
    S._update?.();
  },
  'dialog-ok': () => { const d = S.dialog; S.dialog = null; render(); d?.onConfirm(); },
  'dialog-cancel': () => { S.dialog = null; render(); },
};

$app.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (S.menu && !e.target.closest('.menu-wrap')) { S.menu = false; render(); return; }
  if (!el) return;
  // A tap inside the dialog card never counts as a tap on the backdrop behind it.
  if (el.dataset.a === 'dialog-cancel' && el.classList.contains('overlay') && e.target.closest('[data-stop]')) return;
  if (el.disabled) return;
  handlers[el.dataset.a]?.(el, e);
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && S.dialog) { S.dialog = null; render(); }
});

// Back from the background after a while: due cards and the greeting have moved on.
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.screen.name === 'home' && !S.dialog) render();
});

init();
render();

if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('sw.js').catch((e) => console.warn('offline copy unavailable', e));
}
