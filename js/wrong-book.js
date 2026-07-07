/**
 * 错题集：localStorage 持久化、筛选、导出、专项练习组卷。
 * 依赖 QuizMeta（quiz-meta.js）做工业/英语分类。
 */
(function () {
  'use strict';

  const STORAGE_KEY = 'quiz-wrong-book-v1';

  function getCategory(quizId) {
    if (window.QuizMeta) return QuizMeta.getQuizCategory(quizId);
    return ['profinet', 'opc', 'modbus', 'serial', 'comprehensive', 'exam100'].includes(quizId)
      ? 'industrial'
      : 'english';
  }

  function nowIso() {
    return new Date().toISOString();
  }

  function loadStore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return { version: 1, items: [] };
      const data = JSON.parse(raw);
      if (!data || !Array.isArray(data.items)) return { version: 1, items: [] };
      return data;
    } catch {
      return { version: 1, items: [] };
    }
  }

  function saveStore(store) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
  }

  function makeItemId(quizFile, q, index) {
    const sort = q?.sort ?? index + 1;
    return `${quizFile}#${sort}`;
  }

  function cloneQuestion(q) {
    return JSON.parse(JSON.stringify(q));
  }

  function buildTags(entry, q) {
    const tags = [];
    if (entry?.id) tags.push(entry.id);
    const type = String(q?.type || '').trim();
    if (type) tags.push(type);
    if (type.includes('填空')) tags.push('填空题');
    return [...new Set(tags)];
  }

  function formatUserAnswer(answer) {
    if (Array.isArray(answer)) return answer.join(', ');
    return String(answer ?? '').trim();
  }

  function upsertFromSession({ quiz, quizFile, entry, items }) {
    if (!quiz || !quizFile || !Array.isArray(items) || !items.length) return { added: 0, updated: 0 };
    const store = loadStore();
    let added = 0;
    let updated = 0;
    const ts = nowIso();

    items.forEach(({ q, index, userAnswer, partial }) => {
      const id = makeItemId(quizFile, q, index);
      const existing = store.items.find((it) => it.id === id);
      const payload = {
        id,
        quizId: entry?.id || '',
        quizFile,
        quizTitle: quiz.title || entry?.title || quizFile,
        category: getCategory(entry?.id || ''),
        sort: q.sort ?? index + 1,
        type: q.type || '',
        tags: buildTags(entry, q),
        question: cloneQuestion(q),
        lastUserAnswer: formatUserAnswer(userAnswer),
        partial: Boolean(partial),
        wrongCount: (existing?.wrongCount || 0) + 1,
        addedAt: existing?.addedAt || ts,
        lastWrongAt: ts,
        aiExplainCache: existing?.aiExplainCache || '',
      };
      if (existing) {
        Object.assign(existing, payload);
        updated += 1;
      } else {
        store.items.push(payload);
        added += 1;
      }
    });

    store.items.sort((a, b) => (a.lastWrongAt < b.lastWrongAt ? 1 : -1));
    saveStore(store);
    return { added, updated };
  }

  function getAll() {
    return loadStore().items.slice();
  }

  function getById(id) {
    return loadStore().items.find((it) => it.id === id) || null;
  }

  function remove(id) {
    const store = loadStore();
    const before = store.items.length;
    store.items = store.items.filter((it) => it.id !== id);
    if (store.items.length === before) return false;
    saveStore(store);
    return true;
  }

  function removeMany(ids) {
    if (!ids?.length) return 0;
    const set = new Set(ids);
    const store = loadStore();
    const before = store.items.length;
    store.items = store.items.filter((it) => !set.has(it.id));
    const removed = before - store.items.length;
    if (removed) saveStore(store);
    return removed;
  }

  function removeAll() {
    const count = loadStore().items.length;
    saveStore({ version: 1, items: [] });
    return count;
  }

  function recordWrongAgain(id, userAnswer) {
    const store = loadStore();
    const item = store.items.find((it) => it.id === id);
    if (!item) return false;
    item.wrongCount = (item.wrongCount || 1) + 1;
    item.lastWrongAt = nowIso();
    item.lastUserAnswer = formatUserAnswer(userAnswer);
    saveStore(store);
    return true;
  }

  function setAiExplainCache(id, text) {
    const store = loadStore();
    const item = store.items.find((it) => it.id === id);
    if (!item) return false;
    item.aiExplainCache = String(text || '');
    saveStore(store);
    return true;
  }

  function filterItems(items, filters) {
    let list = items.slice();
    if (filters.category && filters.category !== 'all') {
      list = list.filter((it) => it.category === filters.category);
    }
    if (filters.quizId && filters.quizId !== 'all') {
      list = list.filter((it) => it.quizId === filters.quizId);
    }
    if (filters.type && filters.type !== 'all') {
      list = list.filter((it) => {
        const t = it.type || '';
        if (filters.type === '填空') return t.includes('填空');
        return t === filters.type;
      });
    }
    if (filters.query) {
      const q = filters.query.trim().toLowerCase();
      if (q) {
        list = list.filter((it) => {
          const title = String(it.question?.title || '').toLowerCase();
          const quizTitle = String(it.quizTitle || '').toLowerCase();
          return title.includes(q) || quizTitle.includes(q);
        });
      }
    }
    return list;
  }

  function stats(items) {
    const all = items || getAll();
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;
    const industrial = all.filter((it) => it.category === 'industrial').length;
    const english = all.filter((it) => it.category === 'english').length;
    const weekNew = all.filter((it) => new Date(it.addedAt).getTime() >= weekAgo).length;
    const byQuiz = {};
    all.forEach((it) => {
      byQuiz[it.quizId] = (byQuiz[it.quizId] || 0) + 1;
    });
    return { total: all.length, industrial, english, weekNew, byQuiz };
  }

  function listQuizSources(items) {
    const map = new Map();
    (items || getAll()).forEach((it) => {
      if (!it.quizId) return;
      if (!map.has(it.quizId)) {
        map.set(it.quizId, { quizId: it.quizId, quizTitle: it.quizTitle, count: 0 });
      }
      map.get(it.quizId).count += 1;
    });
    return [...map.values()].sort((a, b) => a.quizTitle.localeCompare(b.quizTitle, 'zh-CN'));
  }

  function listTypes(items) {
    const set = new Set();
    (items || getAll()).forEach((it) => {
      const t = it.type || '';
      if (!t) return;
      set.add(t.includes('填空') ? '填空' : t);
    });
    return [...set];
  }

  function buildDrillQuiz(items, title) {
    const questions = items.map((it, index) => {
      const q = cloneQuestion(it.question);
      q.sort = index + 1;
      q._wrongBookId = it.id;
      return q;
    });
    return {
      title: title || `错题集 · 专项练习（${questions.length} 题）`,
      meta: { wrongBookDrill: true },
      questions,
    };
  }

  function exportJson() {
    return JSON.stringify(loadStore(), null, 2);
  }

  function downloadExport() {
    const blob = new Blob([exportJson()], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const stamp = new Date().toISOString().slice(0, 10);
    a.href = url;
    a.download = `quiz-wrong-book-${stamp}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  window.QuizWrongBook = {
    STORAGE_KEY,
    getAll,
    getById,
    upsertFromSession,
    remove,
    removeMany,
    removeAll,
    recordWrongAgain,
    setAiExplainCache,
    filterItems,
    stats,
    listQuizSources,
    listTypes,
    buildDrillQuiz,
    exportJson,
    downloadExport,
    getCategory,
  };
})();
