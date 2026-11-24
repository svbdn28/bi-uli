// The learning engine, ported 1:1 from the Android app (app/src/main/java/dev/hamper/uli/engine, Flow.kt, ui/Copy.kt).
// No DOM here, so tests/engine.test.js runs it in Node.
//
// Data shapes (plain JSON, saved as is):
//   CardState   {box, lastSeenAt, dueAt, timesWrong, timesRight, lastWrong (bool|null), rightSince (ms|null)}
//   ProgressData{version, cards: {id: CardState}, showSk, seq, examApplied (ms|null), last: {questionId, picked: [], correct}|null}
//   Round       {ids, queue, index, firstAttempt: [[id, bool], ...] (in answer order), gap}
//   Exam        {ids, index, answers: {id: [picks]}, startedAt, finishedAt|null}
//   QuizState   {kind: 'smart'|'mistakes', round, order, selected: [], answered, correct, seq, shownAt (not saved)}
//   ExamState   {exam, order, selected: [], shownAt (not saved)}
// Sets are kept as sorted arrays.

export const MIN = 60_000;
export const HOUR = 60 * MIN;
export const DAY = 24 * HOUR;

const setOf = (a) => [...new Set(a)].sort((x, y) => x - y);
const sameSet = (a, b) => {
  const x = setOf(a), y = setOf(b);
  return x.length === y.length && x.every((v, i) => v === y[i]);
};

// ---------------------------------------------------------------- Content

export const TOPICS = [
  ['intro', 'Úvod do Linuxu'],
  ['opensource', 'Open source a licencie'],
  ['cli', 'Príkazový riadok'],
  ['help', 'Pomoc a manuály'],
  ['files', 'Súbory a adresáre'],
  ['archives', 'Archívy a kompresia'],
  ['pipes', 'Presmerovanie a grep'],
  ['scripts', 'Skripty a editor'],
  ['hardware', 'Hardvér a systém'],
  ['network', 'Sieť'],
  ['users', 'Používatelia a skupiny'],
  ['perms', 'Práva'],
  ['links', 'Odkazy'],
].map(([key, title]) => ({ key, title }));

export const topicOf = (key) => TOPICS.find((t) => t.key === key);

export const Q = {
  correctIndices: (q) => q.options.map((o, i) => (o.correct ? i : -1)).filter((i) => i >= 0),
  disputed: (q) => q.source !== 'ok',
  /** The Slovak line adds nothing when it is the English verbatim. */
  hasDistinctSk: (q) => !!q.questionSk && q.questionSk.trim() !== '' && q.questionSk.trim() !== q.question.trim(),
  /** The real-world side of a disputed question, as a sentence. */
  disputedNote(q) {
    if (!Q.disputed(q)) return null;
    let s = q.source.replace(/^disputed:/, '').trim();
    s = s.charAt(0).toUpperCase() + s.slice(1);
    return s.endsWith('.') ? s : s + '.';
  },
  /** Exact-set scoring: a multi question counts only when every correct option and nothing else is picked. */
  isCorrect: (q, selected) => sameSet(selected, Q.correctIndices(q)),
};

export const Options = {
  aboutTheOthers: /^(none|all|neither|both)( of)?( the)? ?(above|of these|of them|these|are correct|are true|the above)?[.!]?$/i,
  refersToOthers(text) {
    const t = text.trim();
    return t.split(' ').length <= 4 && Options.aboutTheOthers.test(t);
  },
  /** Shuffled, but options that refer to the others keep their place at the end. */
  order(q, random = Math.random) {
    const pinned = [], free = [];
    q.options.forEach((o, i) => (Options.refersToOthers(o.text) ? pinned : free).push(i));
    return [...shuffle(free, random), ...pinned];
  },
  isPermutation: (order, q) =>
    Array.isArray(order) && order.length === q.options.length && [...order].sort((a, b) => a - b).every((v, i) => v === i),
};

export function shuffle(a, random = Math.random) {
  const r = [...a];
  for (let i = r.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [r[i], r[j]] = [r[j], r[i]];
  }
  return r;
}

// ---------------------------------------------------------------- Srs

export const Card = {
  empty: () => ({ box: 0, lastSeenAt: 0, dueAt: 0, timesWrong: 0, timesRight: 0, lastWrong: null, rightSince: null }),
  isNew: (s) => s.box === 0,
  isMastered: (s) => s.box >= Srs.MASTERED_BOX,
  /** The latest answer was a miss (old saves without lastWrong: inferred). */
  justMissed: (s) => (s.lastWrong ?? null) !== null ? s.lastWrong : s.box === 1 && s.timesWrong > 0,
};

export const Progress = {
  empty: (showSk = false) => ({ version: 1, cards: {}, showSk, seq: 0, examApplied: null, last: null }),
  card: (p, id) => ({ ...Card.empty(), ...(p.cards[id] || {}) }),
  /** Fills in defaults for a parsed file; throws when it is not progress at all. */
  normalize(raw) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('not an object');
    const p = { ...Progress.empty(), ...raw };
    if (!p.cards || typeof p.cards !== 'object') throw new Error('cards');
    return p;
  },
};

export const Srs = {
  MAX_BOX: 5,
  MASTERED_BOX: 3,
  ROUND_SIZE: 10,
  SETTLE: 20 * HOUR,
  P_GUESS: 0.25,
  P_MISSED: 0.2,
  P_FIXED: 0.45,
  LIKELY_PASS: 0.8,

  interval(box) {
    switch (box) {
      case 0: return 0;
      case 1: return 10 * MIN;
      case 2: return 4 * HOUR;
      case 3: return DAY;
      case 4: return 3 * DAY;
      default: return 7 * DAY;
    }
  },

  minSpan(box) {
    let s = 0;
    for (let b = 1; b < Math.min(box, Srs.MAX_BOX); b++) s += Srs.interval(b);
    return s;
  },

  runStart(s) {
    if (Card.isNew(s) || Card.justMissed(s)) return null;
    return s.rightSince ?? s.lastSeenAt - Srs.minSpan(s.box);
  },

  isSettled(s) {
    const start = Srs.runStart(s);
    return start !== null && s.lastSeenAt - start >= Srs.SETTLE;
  },

  pCorrect(s) {
    if (Card.isNew(s)) return Srs.P_GUESS;
    if (Card.justMissed(s)) return Srs.P_MISSED;
    if (s.box === 1 && s.timesWrong > 0) return Srs.P_FIXED;
    switch (s.box) {
      case 1: return 0.6;
      case 2: return 0.75;
      case 3: return Srs.isSettled(s) ? 0.85 : 0.75;
      case 4: return 0.93;
      default: return 0.97;
    }
  },

  /** One answer in any mode. Wrong drops to box 1; right climbs only when new or due. */
  answer(s, correct, now) {
    if (!correct) {
      return { ...s, box: 1, lastSeenAt: now, dueAt: now + Srs.interval(1), timesWrong: s.timesWrong + 1, lastWrong: true, rightSince: null };
    }
    if (Card.isNew(s) || s.dueAt <= now) {
      const box = Math.min(s.box + 1, Srs.MAX_BOX);
      return {
        ...s, box, lastSeenAt: now, dueAt: now + Srs.interval(box), timesRight: s.timesRight + 1,
        lastWrong: false, rightSince: Srs.runStart(s) ?? now,
      };
    }
    return { ...s, lastSeenAt: now, lastWrong: false, rightSince: Srs.runStart(s) ?? now };
  },

  applyExam(progress, ids, wrong, now) {
    const cards = { ...progress.cards };
    for (const id of ids) cards[id] = Srs.answer(Progress.card(progress, id), !wrong.includes(id), now);
    return { ...progress, cards };
  },

  readiness(ids, p) {
    if (!ids.length) return 0;
    return ids.reduce((a, id) => a + Srs.pCorrect(Progress.card(p, id)), 0) / ids.length;
  },

  passChanceFor(ids, p) {
    return Srs.passChance(ids.map((id) => Srs.pCorrect(Progress.card(p, id))));
  },

  passChance(p, size = Exam.SIZE, need = PASS_CORRECT) {
    if (!p.length) return 0;
    const bigN = p.length;
    const n = Math.min(size, bigN);
    if (need > n) return 0;
    const mean = p.reduce((a, b) => a + b, 0) / bigN;
    const mu = n * mean;
    const bernoulli = p.reduce((a, x) => a + x * (1 - x), 0) / bigN;
    const spread = p.reduce((a, x) => a + (x - mean) * (x - mean), 0) / bigN;
    const fpc = bigN > 1 ? (bigN - n) / (bigN - 1) : 0;
    const variance = n * bernoulli + n * spread * fpc;
    if (variance < 1e-12) return mu >= need - 0.5 ? 1 : 0;
    const z = (need - 0.5 - mu) / Math.sqrt(variance);
    return Math.min(1, Math.max(0, 1 - Srs.normalCdf(z)));
  },

  normalCdf(z) {
    const t = 1 / (1 + 0.2316419 * Math.abs(z));
    const poly = t * (0.31938153 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
    const tail = (Math.exp((-z * z) / 2) / Math.sqrt(2 * Math.PI)) * poly;
    return z >= 0 ? 1 - tail : tail;
  },

  masteredCount: (ids, p) => ids.filter((id) => Card.isMastered(Progress.card(p, id))).length,
  isDue: (s, now) => s.box > 0 && s.dueAt <= now,

  buildRound(order, p, now, size = Srs.ROUND_SIZE) {
    const picked = new Set();
    const c = (id) => Progress.card(p, id);
    order.filter((id) => Srs.isDue(c(id), now))
      .sort((a, b) => c(a).box - c(b).box || c(a).dueAt - c(b).dueAt)
      .forEach((id) => { if (picked.size < size) picked.add(id); });
    order.filter((id) => Card.isNew(c(id)))
      .forEach((id) => { if (picked.size < size) picked.add(id); });
    order.filter((id) => !picked.has(id))
      .sort((a, b) => c(a).box - c(b).box || c(a).lastSeenAt - c(b).lastSeenAt)
      .forEach((id) => { if (picked.size < size) picked.add(id); });
    return [...picked];
  },

  isMistake: (s) => s.timesWrong > 0 && s.box < Srs.MASTERED_BOX,

  allMistakes(order, p, now) {
    const c = (id) => Progress.card(p, id);
    return order.filter((id) => Srs.isMistake(c(id))).sort((a, b) =>
      (Srs.isDue(c(a), now) ? 0 : 1) - (Srs.isDue(c(b), now) ? 0 : 1) ||
      c(a).box - c(b).box || c(a).dueAt - c(b).dueAt || c(b).timesWrong - c(a).timesWrong);
  },

  mistakes: (order, p, now, size = Srs.ROUND_SIZE) => Srs.allMistakes(order, p, now).slice(0, size),
};

// ---------------------------------------------------------------- Sessions

export const Round = {
  start: (ids) => ({ ids: [...ids], queue: [...ids], index: 0, firstAttempt: [], gap: 3 }),
  current: (r) => (r.index < r.queue.length ? r.queue[r.index] : null),
  isFinished: (r) => r.index >= r.queue.length,
  isRetry(r) {
    const c = Round.current(r);
    return c !== null && r.queue.slice(0, r.index).includes(c);
  },
  first: (r, id) => { const e = r.firstAttempt.find(([k]) => k === id); return e ? e[1] : undefined; },
  score: (r) => r.firstAttempt.filter(([, v]) => v).length,
  total: (r) => r.ids.length,
  mistakes: (r) => r.ids.filter((id) => Round.first(r, id) === false),
  streak(r) {
    let n = 0;
    for (let i = r.firstAttempt.length - 1; i >= 0 && r.firstAttempt[i][1]; i--) n++;
    return n;
  },
  toFix(r, answered) {
    const from = Math.min(answered ? r.index + 1 : r.index, r.queue.length);
    return new Set(r.queue.slice(from).filter((id) => Round.first(r, id) === false)).size;
  },
  displayNumber: (r) => Math.min(new Set(r.queue.slice(0, r.index + 1)).size, r.ids.length),
  answer(r, correct) {
    const id = Round.current(r);
    if (id === null) return r;
    const first = Round.first(r, id) !== undefined ? r.firstAttempt : [...r.firstAttempt, [id, correct]];
    let queue = r.queue;
    if (!correct) {
      queue = [...r.queue];
      queue.splice(Math.min(r.index + r.gap, queue.length), 0, id);
    }
    return { ...r, queue, firstAttempt: first };
  },
  next: (r) => ({ ...r, index: r.index + 1 }),
};

export const Exam = {
  SIZE: 50,
  POINTS_PER_QUESTION: 1.6,
  MAX_POINTS: 80,
  PASS_POINTS: 64,
  start: (pool, now, random = Math.random, size = Exam.SIZE) =>
    ({ ids: shuffle(pool, random).slice(0, size), index: 0, answers: {}, startedAt: now, finishedAt: null }),
  current: (e) => (e.index < e.ids.length ? e.ids[e.index] : null),
  isFinished: (e) => e.index >= e.ids.length,
  answer(e, selected, now) {
    const id = Exam.current(e);
    if (id === null) return e;
    const next = e.index + 1;
    return { ...e, answers: { ...e.answers, [id]: setOf(selected) }, index: next, finishedAt: next >= e.ids.length ? now : e.finishedAt };
  },
  result(e, byId) {
    const wrong = e.ids.filter((id) => !Q.isCorrect(byId.get(id), e.answers[id] || []));
    const correct = e.ids.length - wrong.length;
    const points = correct * Exam.POINTS_PER_QUESTION;
    return { correct, total: e.ids.length, wrong, points, passed: points >= Exam.PASS_POINTS - 1e-9 };
  },
};

export const PASS_CORRECT = Math.ceil(Exam.PASS_POINTS / Exam.POINTS_PER_QUESTION - 1e-9);

// ---------------------------------------------------------------- Flow (guards for taps, double applies, damaged saves)

export const Flow = {
  SETTLE_MS: 350,
  tooSoon: (shownAt, now) => shownAt > 0 && now - shownAt >= 0 && now - shownAt < Flow.SETTLE_MS,

  toggle(q, selected, index) {
    if (q.kind !== 'multi') return [index];
    if (selected.includes(index)) return selected.filter((i) => i !== index);
    if (selected.length < q.choose) return setOf([...selected, index]);
    return selected;
  },

  pick(s, q, questionId, index, now) {
    if (s.answered || q.id !== questionId || Round.current(s.round) !== questionId || !(index >= 0 && index < q.options.length)) return null;
    if (Flow.tooSoon(s.shownAt || 0, now)) return null;
    return { ...s, selected: Flow.toggle(q, s.selected, index) };
  },

  examPick(s, q, questionId, index, now) {
    if (Exam.current(s.exam) !== questionId || q.id !== questionId || !(index >= 0 && index < q.options.length)) return null;
    if (Flow.tooSoon(s.shownAt || 0, now)) return null;
    return { ...s, selected: Flow.toggle(q, s.selected, index) };
  },

  /** Answers the current round card: [new quiz state, progress to save]. Never applies an answer twice. */
  submit(s, q, progress, now) {
    const ok = Q.isCorrect(q, s.selected);
    const answered = { ...s, round: Round.answer(s.round, ok), answered: true, correct: ok };
    if (progress.seq > s.seq) return [answered, progress];
    const card = Progress.card(progress, q.id);
    const updated = !Round.isRetry(s.round) ? Srs.answer(card, ok, now) : { ...card, lastSeenAt: now };
    return [answered, {
      ...progress,
      cards: { ...progress.cards, [q.id]: updated },
      seq: progress.seq + 1,
      last: { questionId: q.id, picked: setOf(s.selected), correct: ok },
    }];
  },

  applyExam(progress, e, byId, now) {
    if (progress.examApplied === e.startedAt) return progress;
    const res = Exam.result(e, byId);
    return { ...Srs.applyExam(progress, e.ids, res.wrong, now), examApplied: e.startedAt };
  },

  /** What of a saved session can be continued against the current question bank. */
  restore(saved, progress, byId, catchUp = true, shownAt = 0) {
    const fits = (q, order, selected) =>
      !!q && Options.isPermutation(order, q) && Array.isArray(selected) && selected.every((i) => i >= 0 && i < q.options.length);
    const answersFit = (e) =>
      Array.isArray(e.ids) && e.ids.length > 0 && e.ids.every((id) => byId.has(id)) && new Set(e.ids).size === e.ids.length &&
      Object.entries(e.answers || {}).every(([id, picks]) =>
        e.ids.includes(+id) && picks.every((i) => i >= 0 && i < byId.get(+id).options.length));

    let quiz = null;
    try {
      const s = saved.quiz;
      if (s) {
        const r = s.round;
        if (r.queue.length && r.ids.every((id) => byId.has(id)) && r.queue.every((id) => byId.has(id)) && !Round.isFinished(r) &&
          fits(byId.get(Round.current(r)), s.order, s.selected)) {
          quiz = { ...Flow.caughtUp(s, progress, byId), shownAt };
        }
      }
    } catch { quiz = null; }
    let exam = null;
    try {
      const s = saved.exam;
      if (s && !Exam.isFinished(s.exam) && answersFit(s.exam) && s.exam.startedAt !== progress.examApplied &&
        fits(byId.get(Exam.current(s.exam)), s.order, s.selected)) exam = { ...s, shownAt };
    } catch { exam = null; }
    let last = null;
    try {
      const e = saved.lastExam;
      if (e && Exam.isFinished(e) && answersFit(e)) last = e;
    } catch { last = null; }
    let p = progress;
    let changed = false;
    if (last && catchUp && progress.examApplied !== last.startedAt) {
      p = Flow.applyExam(progress, last, byId, last.finishedAt ?? last.startedAt);
      changed = true;
    } else if (last && !catchUp) {
      p = { ...progress, examApplied: last.startedAt };
    }
    return { quiz, roundStart: quiz ? saved.roundStartBoxes || {} : {}, exam, lastExam: last, progress: p, progressChanged: changed };
  },

  caughtUp(s, progress, byId) {
    if (s.answered || progress.seq <= s.seq) return s;
    const id = Round.current(s.round);
    if (id === null) return s;
    const last = progress.last;
    const q = byId.get(id);
    if (!last || last.questionId !== id || last.picked.some((i) => i < 0 || i >= q.options.length)) return { ...s, seq: progress.seq };
    return { ...s, round: Round.answer(s.round, last.correct), selected: last.picked, answered: true, correct: last.correct };
  },

  rebase(saved, loaded, byId) {
    const r = Flow.restore({ ...saved, quiz: saved.quiz ? { ...saved.quiz, seq: loaded.seq } : null }, loaded, byId, true);
    const roundStart = {};
    if (r.quiz) for (const id of r.quiz.round.ids) roundStart[id] = Progress.card(loaded, id).box;
    return {
      ...r,
      quiz: r.quiz ? { ...r.quiz, shownAt: saved.quiz?.shownAt ?? 0 } : null,
      exam: r.exam ? { ...r.exam, shownAt: saved.exam?.shownAt ?? 0 } : null,
      roundStart,
    };
  },
};

// ---------------------------------------------------------------- Code (shell code inside prose, set in mono)

const isLetter = (c) => /\p{L}/u.test(c);
const isDigit = (c) => /\p{Nd}/u.test(c);
const isWhitespace = (c) => /\s/.test(c);

export const Code = {
  commands: new Set([
    'ls', 'cp', 'mv', 'rm', 'cd', 'pwd', 'chmod', 'chown', 'chgrp', 'tar', 'gzip', 'gunzip', 'bzip2', 'xz',
    'zip', 'unzip', 'grep', 'egrep', 'echo', 'ln', 'usermod', 'useradd', 'userdel', 'groupmod', 'groupadd',
    'id', 'who', 'w', 'su', 'sudo', 'ps', 'df', 'top', 'man', 'less', 'vim', 'nano', 'locate', 'updatedb',
    'whereis', 'whatis', 'apropos', 'ifconfig', 'ipconfig', 'ip', 'ss', 'netstat', 'dig', 'ssh', 'env',
    'sysctl', 'gdisk', 'sgdisk', 'cgdisk', 'sfdisk', 'fdisk', 'lsmod', 'chage', 'getent', 'printf', 'cat',
    'tee', 'info', 'history', 'test', 'bash', 'csh', 'tcsh', 'ksh', 'zsh', 'sh', 'groups', 'touch', 'mkdir',
    'printenv', 'export', 'alias', 'type', 'which', 'head', 'tail', 'sort', 'wc', 'find', 'free', 'cmdline',
  ]),
  ambiguous: new Set(['free', 'test', 'info', 'type', 'find', 'sort', 'less', 'head', 'tail', 'export', 'alias', 'touch', 'top']),
  trailing: '.,:;)“”"?',
  leading: '(„“"',
  symbols: new Set(['/', '^', '$', '|', ';', '!', '&&', '||', '>', '<', '~', '#!', '*']),
  fileName: /^[A-Za-z0-9_~-]*[A-Za-z][A-Za-z0-9_~-]*(\.[A-Za-z0-9]+)+$/,
  extensions: new Set(['tar', 'gz', 'bz2', 'xz', 'zip', 'tgz', 'txt', 'conf', 'sh', 'log', 'iso', 'jpg', 'png', 'cfg', 'd']),
  operatorWords: new Set(['operátor', 'operator', 'operátorom', 'operátora']),
  letterCase: /^(mal|veľk)(é|ý|á|ým|ou)$/iu,
  number: /^[0-9]{1,6}$/,
  assignment: /^[A-Za-z_][A-Za-z0-9_]*=\S*$/,

  isFileName(t) {
    return Code.fileName.test(t) && Code.extensions.has(t.slice(t.lastIndexOf('.') + 1).toLowerCase());
  },

  isSymbolic(t) {
    if (!t) return false;
    if (Code.symbols.has(t)) return true;
    if (t.length === 1) return false;
    return t.startsWith('/') || t.startsWith('~/') || t.startsWith('./') ||
      (t.startsWith('-') && t.length >= 2 && t.length <= 12 && isLetter(t[1])) || t.startsWith('--') ||
      t.startsWith('$') || t.startsWith('^') || t.startsWith('#!') || t.startsWith('!') ||
      (t.startsWith('.') && isLetter(t[1])) ||
      t.includes('[') || t.includes('*') || t.includes('>') || t.includes('|') ||
      Code.assignment.test(t) || Code.isFileName(t);
  },

  tokens(text) {
    const out = [];
    let i = 0;
    while (i < text.length) {
      while (i < text.length && isWhitespace(text[i])) i++;
      if (i >= text.length) break;
      const s = i;
      while (i < text.length && !isWhitespace(text[i])) i++;
      let a = s, b = i;
      while (a < b && Code.leading.includes(text[a])) a++;
      while (b > a && Code.trailing.includes(text[b - 1]) && !(b - a === 1)) b--;
      out.push({ start: s, end: i, core: text.slice(a, b), coreStart: a });
    }
    return out;
  },

  endsClause(text, t) {
    const at = t.coreStart + t.core.length;
    return at < t.end && ',:;).'.includes(text[at]);
  },

  /** Ranges [start, end) to render as code. English prose marks a command only before a flag/path or "command". */
  spans(text, english) {
    const toks = Code.tokens(text);
    const code = toks.map((t) => Code.isSymbolic(t.core));
    toks.forEach((t, i) => {
      if (code[i] || !Code.commands.has(t.core)) return;
      const next = toks[i + 1];
      const nextIsCode = !!next && Code.isSymbolic(next.core);
      const sentenceStart = i === 0 || '.:'.includes(text[toks[i - 1].end - 1]);
      if (english) code[i] = nextIsCode || next?.core?.toLowerCase() === 'command';
      else if (Code.ambiguous.has(t.core)) code[i] = nextIsCode || next?.core === '=' || (sentenceStart && t.start === t.coreStart);
      else code[i] = true;
    });
    toks.forEach((t, i) => {
      if (code[i] || i === 0 || t.core.length !== 1 || !isLetter(t.core) || t.start !== t.coreStart) return;
      if (!Code.letterCase.test(toks[i - 1].core)) return;
      const next = toks[i + 1]?.core;
      const conjunction = 'aiouvksz'.includes(t.core) && next != null && isLetter(next[0]);
      if (!conjunction) code[i] = true;
    });
    toks.forEach((t, i) => {
      if (code[i] || t.core !== '=' || t.start !== t.coreStart) return;
      const prev = toks[i - 1];
      const named = !prev || Code.operatorWords.has(prev.core.toLowerCase()) || Code.endsClause(text, prev) || text[prev.end - 1] === ':';
      const listed = ['a', 'alebo', 'and', 'or'].includes(toks[i + 1]?.core) && !!toks[i + 2] && Code.isSymbolic(toks[i + 2].core);
      if (named || listed) code[i] = true;
    });
    for (let i = 1; i < toks.length; i++) {
      const t = toks[i], prev = toks[i - 1];
      if (code[i] || !code[i - 1] || Code.endsClause(text, prev) || t.start !== t.coreStart) continue;
      if (!Code.number.test(t.core)) continue;
      if (prev.core.startsWith('-') || Code.commands.has(prev.core) || Code.number.test(prev.core)) code[i] = true;
    }
    const ranges = [];
    let i = 0;
    while (i < toks.length) {
      if (!code[i]) { i++; continue; }
      const start = toks[i].coreStart;
      let j = i;
      while (j + 1 < toks.length && code[j + 1] && !Code.endsClause(text, toks[j]) && toks[j + 1].start === toks[j + 1].coreStart) j++;
      const last = toks[j];
      ranges.push([start, last.coreStart + last.core.length]);
      i = j + 1;
    }
    return ranges;
  },

  /** Inline code never breaks inside a path, flag or operator: a word joiner after every symbol. */
  unbreakable(code, keepTogether = 24) {
    const glue = code.length <= keepTogether;
    let out = '';
    for (const ch of code) {
      out += ch === ' ' && glue ? ' ' : ch;
      if (!/[\p{L}\p{Nd}]/u.test(ch) && !isWhitespace(ch)) out += '⁠';
    }
    return out;
  },

  gluePaths: (line) => line.replaceAll('/', '/⁠'),

  /** Option texts that are commands, paths, flags or keys, when most options of the question are. */
  codeOptions(q) {
    if (q.kind === 'truefalse') return new Set();
    const looks = q.options.map((o) => Code.looksLikeCode(o.text));
    return looks.filter(Boolean).length * 2 >= q.options.length
      ? new Set(looks.map((l, i) => (l ? i : -1)).filter((i) => i >= 0)) : new Set();
  },

  looksLikeCode(text) {
    const t = text.trim();
    if (!t || t.length > 40) return false;
    if ([...t].every((c) => isDigit(c) || c === ',')) return false;
    if (t.length <= 2) return true;
    const f = t[0];
    return f === '/' || f === '-' || f === '~' || f === '^' || /\p{Ll}/u.test(f);
  },
};

// ---------------------------------------------------------------- Copy (Slovak strings that depend on data)

export function plural(n, one, few, many) {
  if (n === 1) return one;
  if (n >= 2 && n <= 4) return few;
  return many;
}

export function inTime(ms) {
  const min = Math.max(1, Math.floor((ms + 59_999) / 60_000));
  if (min < 60) return min === 1 ? 'o minútu' : `o ${min} ` + plural(min, 'minútu', 'minúty', 'minút');
  const h = Math.floor((ms + 30 * 60_000) / 3_600_000);
  if (h < 24) return h === 1 ? 'o hodinu' : `o ${h} ` + plural(h, 'hodinu', 'hodiny', 'hodín');
  const d = Math.floor((ms + 12 * 3_600_000) / 86_400_000);
  if (d === 1) return 'zajtra';
  if (d === 7) return 'o týždeň';
  return `o ${d} ` + plural(d, 'deň', 'dni', 'dní');
}

export const comesBackIn = (ms) => (ms <= 0 ? null : 'Znova ' + inTime(ms));

export const comesBackAfter = (n) =>
  n <= 1 ? 'Vráti sa hneď ako ďalšia' : `Vráti sa o ${n} ` + plural(n, 'otázku', 'otázky', 'otázok');

export function roundEndNote(kind, ids, mistakes, progress, now) {
  const whenBack = (cards, many) => {
    const first = cards.reduce((a, b) => (Progress.card(progress, b).dueAt < Progress.card(progress, a).dueAt ? b : a));
    const card = Progress.card(progress, first);
    const wait = card.dueAt - now;
    const subject = many ? 'Prvá' : 'Otázka';
    const start = wait <= 0 ? `${subject} je na rade hneď` : `${subject} sa vráti ${inTime(wait)}`;
    const step = Srs.interval(Math.min(card.box + 1, Srs.MAX_BOX));
    return `${start}, po správnej odpovedi ${inTime(step)}.`;
  };
  if (mistakes.length) {
    const lead = kind === 'mistakes' ? 'Opravené.' : 'Chyby si hneď opravil.';
    return `${lead} ${whenBack(mistakes, mistakes.length > 1)}`;
  }
  if (kind !== 'mistakes') return 'Všetko na prvý pokus.';
  const left = ids.filter((id) => Srs.isMistake(Progress.card(progress, id)));
  if (!left.length) return 'Všetko na prvý pokus a z Chýb zmizli.';
  return `Všetko na prvý pokus. Z Chýb zmiznú, keď ich trafíš aj neskôr. ${whenBack(left, left.length > 1)}`;
}

export function chanceText(p) {
  if (p < 0.05) return 'zatiaľ nízka';
  if (p > 0.99) return 'nad 99 %';
  return `~${Math.round(p * 100)} %`;
}

export function greeting(hour) {
  if (hour >= 4 && hour <= 9) return 'Dobré ráno';
  if (hour >= 10 && hour <= 17) return 'Dobrý deň';
  return 'Dobrý večer';
}

export function answeredToday(progress, now) {
  const d = new Date(now);
  const midnight = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  return Object.values(progress.cards).filter((c) => c.lastSeenAt >= midnight && c.lastSeenAt <= now).length;
}

export function todayLine(progress, now) {
  const n = answeredToday(progress, now);
  const today = n === 0 ? 'dnes ešte nič' : `dnes ${n} ` + plural(n, 'otázka', 'otázky', 'otázok');
  return `${greeting(new Date(now).getHours())} · ${today}`;
}

export const questionsWord = (n) => (n === 1 ? 'otázka' : n >= 2 && n <= 4 ? 'otázky' : 'otázok');

export const formatPoints = (p) => p.toFixed(1).replace('.', ',').replace(/,0$/, '');

export function formatDuration(ms) {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
