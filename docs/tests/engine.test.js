// The Android unit tests (SrsTest, SessionsTest, CopyTest, parts of FlowTest/ContentTest) against the JS engine.
// Run: cd docs && node --test tests/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  Srs, Card, Progress, Round, Exam, Flow, Q, Code, Options, PASS_CORRECT, MIN, HOUR, DAY,
  inTime, roundEndNote, chanceText, todayLine, answeredToday, shuffle,
} from '../engine.js';

const now = 1_000_000_000;
const C = (o = {}) => ({ ...Card.empty(), ...o });
const P = (cards = {}, o = {}) => ({ ...Progress.empty(), cards, ...o });
const near = (a, b, eps = 1e-9) => assert.ok(Math.abs(a - b) <= eps, `${a} != ${b}`);
const seeded = (seed) => () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);

test('right answer climbs one box and caps at five', () => {
  let s = C();
  const boxes = [];
  for (let i = 0; i < 7; i++) { s = Srs.answer(s, true, Math.max(now, s.dueAt)); boxes.push(s.box); }
  assert.deepEqual(boxes, [1, 2, 3, 4, 5, 5, 5]);
  assert.equal(s.timesRight, 7);
});

test('wrong answer drops to box one', () => {
  const s = Srs.answer(C({ box: 4, timesWrong: 2 }), false, now);
  assert.equal(s.box, 1); assert.equal(s.timesWrong, 3); assert.equal(s.dueAt, now + 10 * MIN);
});

test('intervals', () => {
  assert.deepEqual([1, 2, 3, 4, 5].map(Srs.interval), [10 * MIN, 4 * HOUR, DAY, 3 * DAY, 7 * DAY]);
  assert.equal(Srs.answer(C({ box: 2 }), true, now).dueAt, now + DAY);
});

test('readiness averages probability by box', () => {
  const ids = [1, 2, 3, 4, 5, 6];
  const cards = {};
  for (let i = 0; i <= 5; i++) cards[i + 1] = C({ box: i, timesRight: i });
  near(Srs.readiness(ids, P(cards)), (0.25 + 0.6 + 0.75 + 0.75 + 0.93 + 0.97) / 6);
  near(Srs.readiness(ids, P()), 0.25);
  assert.equal(Srs.masteredCount(ids, P(cards)), 3);
});

const zoo = [C()];
for (let box = 1; box <= 5; box++) {
  zoo.push(C({ box, timesRight: box, dueAt: now - 1, lastWrong: false }));
  zoo.push(C({ box, timesRight: box, dueAt: now + DAY, lastWrong: false }));
  zoo.push(C({ box, timesRight: box, timesWrong: 2, dueAt: now - 1 }));
}
zoo.push(C({ box: 1, timesWrong: 1, dueAt: now + 5 * MIN, lastWrong: true }));
zoo.push(C({ box: 1, timesWrong: 1, dueAt: now + 5 * MIN, lastWrong: false }));
zoo.push(C({ box: 1, timesWrong: 3, timesRight: 1, dueAt: now + 5 * MIN }));

test('a wrong answer never raises a card, a right one never lowers it', () => {
  for (const s of zoo) {
    const before = Srs.pCorrect(s);
    assert.ok(Srs.pCorrect(Srs.answer(s, false, now)) <= before);
    assert.ok(Srs.pCorrect(Srs.answer(s, true, now)) >= before);
  }
});

const pool = Array.from({ length: 70 }, (_, i) => i + 1);
const mid = {};
pool.slice(0, 40).forEach((id) => {
  mid[id] = [C({ box: 4, timesRight: 4, dueAt: now - 1 }), C({ box: 1, timesWrong: 1, dueAt: now - 1, lastWrong: true }),
    C({ box: 2, timesRight: 2, dueAt: now + HOUR }), C({ box: 1, timesRight: 1, dueAt: now - 1 })][[0, 1, 2, 3][id % 4]];
});
const done = {};
pool.forEach((id) => { done[id] = C({ box: 5, timesRight: 5, dueAt: now + DAY }); });
const starts = [P(), P(mid), P(done)];
const play = (start, ids, ok) => {
  const cards = { ...start.cards };
  for (const id of ids) cards[id] = Srs.answer(Progress.card(start, id), ok(id), now);
  return { ...start, cards };
};

test('all-wrong round never increases the estimate, all-right one does', () => {
  for (const s of starts) {
    const round = Srs.buildRound(pool, s, now);
    assert.ok(Srs.readiness(pool, play(s, round, () => false)) < Srs.readiness(pool, s));
  }
  for (const s of starts.slice(0, 2)) {
    const round = Srs.buildRound(pool, s, now);
    assert.ok(Srs.readiness(pool, play(s, round, () => true)) > Srs.readiness(pool, s));
  }
});

test('round: due first, lowest box first, then new in content order', () => {
  const order = Array.from({ length: 20 }, (_, i) => i + 1);
  const cards = { 5: C({ box: 3, dueAt: now - 1 }), 9: C({ box: 1, dueAt: now - 1 }), 12: C({ box: 2, dueAt: now - 5 }),
    3: C({ box: 2, dueAt: now + DAY }), 1: C({ box: 4, dueAt: now + DAY }) };
  const round = Srs.buildRound(order, P(cards), now);
  assert.deepEqual(round, [9, 12, 5, 2, 4, 6, 7, 8, 10, 11]);
});

test('round is never empty when nothing is due', () => {
  const order = Array.from({ length: 12 }, (_, i) => i + 1);
  const cards = {};
  order.forEach((id) => { cards[id] = C({ box: id % 2 === 0 ? 4 : 2, dueAt: now + DAY, lastSeenAt: id }); });
  assert.deepEqual(Srs.buildRound(order, P(cards), now), [1, 3, 5, 7, 9, 11, 2, 4, 6, 8]);
});

test('mistakes: wrong cards below mastery, due first', () => {
  const p = P({ 1: C({ box: 1, timesWrong: 1, dueAt: now - 1 }), 2: C({ box: 3, timesWrong: 2 }),
    3: C({ box: 2, timesWrong: 4, dueAt: now + HOUR }), 4: C({ box: 1, timesWrong: 3, dueAt: now - 5 }), 5: C({ box: 2 }) });
  assert.deepEqual(Srs.mistakes([1, 2, 3, 4, 5, 6], p, now), [4, 1, 3]);
  assert.deepEqual(Srs.mistakes([1, 2, 3, 4, 5, 6], p, now, 2), [4, 1]);
});

test('drilling the same cards again and again does not master them', () => {
  let s = Srs.answer(C({ box: 2, timesWrong: 1, dueAt: now }), false, now);
  for (let m = 1; m <= 6; m++) s = Srs.answer(s, true, now + m * MIN);
  assert.equal(s.box, 1);
  s = Srs.answer(s, true, now + 10 * MIN); assert.equal(s.box, 2);
  s = Srs.answer(s, true, now + 11 * MIN); assert.equal(s.box, 2);
  s = Srs.answer(s, true, now + 10 * MIN + 4 * HOUR); assert.equal(s.box, 3);
});

test('box three is capped until it holds', () => {
  let s = Srs.answer(C(), true, now);
  s = Srs.answer(s, true, now + 10 * MIN);
  s = Srs.answer(s, true, now + 10 * MIN + 4 * HOUR);
  assert.equal(s.box, 3); assert.equal(s.rightSince, now); near(Srs.pCorrect(s), 0.75);
  const later = Srs.answer(s, true, now + 20 * HOUR);
  near(Srs.pCorrect(later), 0.85);
  near(Srs.pCorrect(C({ box: 3, timesRight: 3, lastSeenAt: now })), 0.75);
  near(Srs.pCorrect(C({ box: 4, timesRight: 4, lastSeenAt: now })), 0.93);
});

test('pass chance', () => {
  assert.equal(PASS_CORRECT, 40);
  const coin = Srs.passChance(Array(70).fill(0.8));
  assert.ok(coin > 0.53 && coin < 0.6);
  near(Srs.passChance(Array(70).fill(0.85)), 0.883, 0.01);
  assert.equal(Srs.passChance([]), 0);
  assert.equal(Srs.passChance(Array(30).fill(1)), 0);
  near(Srs.normalCdf(1), 0.841345, 1e-5);
  near(Srs.normalCdf(-2), 0.02275, 1e-5);
});

test('wrong card comes back three later until right; first attempt counts', () => {
  let r = Round.start([1, 2, 3, 4, 5]);
  r = Round.answer(r, false);
  assert.deepEqual(r.queue, [1, 2, 3, 1, 4, 5]);
  r = Round.next(r);
  r = Round.next(Round.answer(r, true));
  r = Round.next(Round.answer(r, true));
  assert.equal(Round.current(r), 1); assert.ok(Round.isRetry(r));
  r = Round.answer(r, false);
  assert.deepEqual(r.queue, [1, 2, 3, 1, 4, 5, 1]);
  r = Round.next(Round.answer(Round.next(Round.answer(Round.next(r), true)), true));
  r = Round.next(Round.answer(r, true));
  assert.ok(Round.isFinished(r));
  assert.equal(Round.score(r), 4); assert.deepEqual(Round.mistakes(r), [1]);
});

test('correction counter counts cards left to fix', () => {
  let r = Round.start([1, 2, 3, 4, 5]);
  r = Round.answer(r, false); assert.equal(Round.toFix(r, true), 1);
  r = Round.next(Round.answer(Round.next(r), false));
  r = Round.next(Round.answer(r, true));
  assert.equal(Round.current(r), 1); assert.equal(Round.toFix(r, false), 2);
  r = Round.answer(r, false); assert.equal(Round.toFix(r, true), 2);
});

test('streak and display number', () => {
  let r = Round.start([1, 2, 3, 4, 5, 6]);
  r = Round.next(Round.answer(Round.next(Round.answer(r, true)), false));
  assert.equal(Round.streak(r), 0);
  r = Round.next(Round.answer(Round.next(Round.answer(Round.next(Round.answer(r, true)), true)), true));
  assert.equal(Round.streak(r), 2);
  let d = Round.start([1, 2, 3, 4]);
  d = Round.answer(d, false);
  d = Round.next(Round.answer(Round.next(Round.answer(Round.next(d), true)), true));
  assert.equal(Round.current(d), 1); assert.equal(Round.displayNumber(d), 3);
});

const mkq = (id, kind, correct) => ({ id, topic: 'intro', kind, choose: correct.filter(Boolean).length, question: `q${id}`,
  questionSk: '', options: correct.map((c, i) => ({ text: `o${i}`, correct: c })), explanation: '', source: 'ok' });

test('exam scores points and pass line', () => {
  const byId = new Map(pool.map((id) => [id, mkq(id, 'single', [true, false])]));
  let e = Exam.start(pool, 0, seeded(1));
  assert.equal(new Set(e.ids).size, 50);
  e.ids.forEach((_, i) => { e = Exam.answer(e, i < 40 ? [0] : [1], 99); });
  const res = Exam.result(e, byId);
  assert.equal(res.correct, 40); near(res.points, 64); assert.ok(res.passed);
  assert.equal(e.finishedAt, 99);
  assert.ok(Q.isCorrect(mkq(1, 'multi', [true, true, false, true]), [3, 0, 1]));
  assert.ok(!Q.isCorrect(mkq(1, 'multi', [true, true, false, true]), [0, 1]));
});

test('flow: stale or early taps are ignored, answers apply once', () => {
  const q = mkq(1, 'single', [false, true]);
  const byId = new Map([[1, q], [2, mkq(2, 'single', [true, false])]]);
  const s = { kind: 'smart', round: Round.start([1, 2]), order: [0, 1], selected: [], answered: false, correct: false, seq: 0, shownAt: 1000 };
  assert.equal(Flow.pick(s, q, 1, 1, 1100), null); // too soon
  assert.equal(Flow.pick(s, q, 2, 1, 2000), null); // other card
  const picked = Flow.pick(s, q, 1, 1, 2000);
  const [answered, p1] = Flow.submit(picked, q, P(), now);
  assert.ok(answered.correct); assert.equal(p1.seq, 1); assert.equal(p1.cards[1].box, 1);
  // Died after saving progress, before saving the round: restored answered, not applied twice.
  const r = Flow.restore({ quiz: picked, roundStartBoxes: {} }, p1, byId);
  assert.ok(r.quiz.answered); assert.ok(r.quiz.correct);
  const [, p2] = Flow.submit(picked, q, p1, now);
  assert.equal(p2, p1);
});

test('flow: a finished exam is applied exactly once on restore', () => {
  const byId = new Map(pool.map((id) => [id, mkq(id, 'single', [true, false])]));
  let e = Exam.start(pool, 5, seeded(2));
  e.ids.forEach(() => { e = Exam.answer(e, [0], 9); });
  const r1 = Flow.restore({ lastExam: e }, P(), byId);
  assert.ok(r1.progressChanged); assert.equal(r1.progress.examApplied, 5);
  const r2 = Flow.restore({ lastExam: e }, r1.progress, byId);
  assert.ok(!r2.progressChanged);
});

test('copy', () => {
  assert.equal(inTime(6 * MIN), 'o 6 minút');
  assert.equal(inTime(30_000), 'o minútu');
  assert.equal(inTime(HOUR), 'o hodinu');
  assert.equal(inTime(4 * HOUR), 'o 4 hodiny');
  assert.equal(inTime(24 * HOUR), 'zajtra');
  assert.equal(inTime(72 * HOUR), 'o 3 dni');
  assert.equal(inTime(7 * DAY), 'o týždeň');
  const t = 1_800_000_000_000;
  const p = P({ 1: Srs.answer(C({ box: 2, timesRight: 2 }), false, t - 4 * MIN), 2: Srs.answer(C(), false, t - MIN) });
  assert.equal(roundEndNote('smart', [1, 2, 3], [1, 2], p, t), 'Chyby si hneď opravil. Prvá sa vráti o 6 minút, po správnej odpovedi o 4 hodiny.');
  assert.equal(roundEndNote('mistakes', [2], [2], p, t), 'Opravené. Otázka sa vráti o 9 minút, po správnej odpovedi o 4 hodiny.');
  assert.equal(chanceText(0.04), 'zatiaľ nízka');
  assert.equal(chanceText(0.567), '~57 %');
  assert.equal(chanceText(0.999), 'nad 99 %');
  const evening = new Date(2027, 0, 15, 19, 0).getTime();
  const pp = P({ 1: C({ box: 1, lastSeenAt: evening - 2 * HOUR }), 2: C({ box: 1, lastSeenAt: evening - 20 * HOUR }), 3: C({ box: 2, lastSeenAt: evening - 10 * MIN }) });
  assert.equal(answeredToday(pp, evening), 2);
  assert.equal(todayLine(pp, evening), 'Dobrý večer · dnes 2 otázky');
});

test('content: 70 questions, valid options, demos fit 44 columns', () => {
  const qs = JSON.parse(readFileSync(new URL('../questions.json', import.meta.url)));
  assert.equal(qs.length, 70);
  for (const q of qs) {
    assert.ok(['single', 'multi', 'truefalse'].includes(q.kind));
    assert.equal(Q.correctIndices(q).length, q.choose, `q${q.id}`);
    for (const line of (q.demo || '').split('\n')) assert.ok(line.replace(/\t/g, '    ').length <= 44, `q${q.id}: ${line}`);
    const order = Options.order(q, seeded(q.id));
    assert.ok(Options.isPermutation(order, q));
  }
});

test('code spans', () => {
  const s = (t, en = false) => Code.spans(t, en).map(([a, b]) => t.slice(a, b));
  assert.deepEqual(s('Use the ls -l /etc command.', true), ['ls -l /etc']);
  assert.deepEqual(s('Príkaz chmod 4755 nastaví SUID.'), ['chmod 4755']);
  assert.deepEqual(s('Mac = BSD v obleku'), []);
  assert.deepEqual(s('Reťazce: = a !='), ['=', '!=']);
  assert.ok(Code.looksLikeCode('/etc/passwd'));
  assert.ok(!Code.looksLikeCode('True'));
  assert.equal(shuffle([1, 2, 3]).length, 3);
});
