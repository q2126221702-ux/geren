(function () {
  'use strict';

  const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

  const state = {
    manifest: null,
    quiz: null,
    currentIndex: 0,
    answers: {},
    submitted: false,
    aiCache: {},
    aiScoreCache: {},
    lastResult: null,
    aiExplainLoading: false,
    aiAnalysisLoading: false,
    aiAnalysisText: '',
    aiAnalysisCollapsed: false,
    analysisCooldownUntil: 0,
    analysisCooldownTimer: null,
    testCooldownUntil: 0,
    testCooldownTimer: null,
    wrongDrillMode: false,
    parentQuiz: null,
  };

  const $ = (id) => document.getElementById(id);

  const POS_ZH_MAP = {
    'n.': '名词',
    'v.': '动词',
    'adj.': '形容词',
    'adv.': '副词',
    'prep.': '介词',
    'int.': '感叹词',
    'abbr.': '缩写',
    'conj.': '连词',
    'pron.': '代词',
    'num.': '数词',
    'art.': '冠词',
    'phr.': '短语',
    phr: '短语',
  };

  const REMIX_FALLBACK = {
    zh2en: 'en2zh',
    en2zh: 'zh2en',
    spell: 'en2zh',
    pos: 'spell',
    assoc: 'en2zh',
    phrase_cloze: 'en2zh',
  };

  const POS_ALIASES = {
    名词: ['名词', 'n', 'n.'],
    动词: ['动词', 'v', 'v.'],
    形容词: ['形容词', 'adj', 'adj.'],
    副词: ['副词', 'adv', 'adv.'],
    介词: ['介词', 'prep', 'prep.'],
    感叹词: ['感叹词', 'int', 'int.'],
    缩写: ['缩写', 'abbr', 'abbr.'],
    连词: ['连词', 'conj', 'conj.'],
    代词: ['代词', 'pron', 'pron.'],
    数词: ['数词', 'num', 'num.'],
    冠词: ['冠词', 'art', 'art.'],
    短语: ['短语', 'phr'],
  };

  function normPos(s) {
    return String(s || '')
      .trim()
      .toLowerCase()
      .replace(/\.$/, '');
  }

  function posZhFromEn(pos) {
    if (!pos) return '短语';
    return POS_ZH_MAP[pos] || POS_ZH_MAP[pos.endsWith('.') ? pos : `${pos}.`] || pos;
  }

  function expandPosAcceptSet(correctAnswer) {
    const set = new Set();
    const parts = String(correctAnswer || '')
      .split(/[；;]/)
      .map((p) => p.trim())
      .filter(Boolean);
    for (const part of parts) {
      set.add(normPos(part));
      if (POS_ALIASES[part]) {
        POS_ALIASES[part].forEach((a) => set.add(normPos(a)));
      }
      for (const [zh, aliases] of Object.entries(POS_ALIASES)) {
        if (aliases.some((a) => normPos(a) === normPos(part))) {
          set.add(normPos(zh));
          aliases.forEach((a) => set.add(normPos(a)));
        }
      }
    }
    return set;
  }

  function matchPosAnswer(userAnswer, correctAnswer) {
    const user = normPos(userAnswer);
    if (!user) return false;
    return expandPosAcceptSet(correctAnswer).has(user);
  }

  function fillInputPlaceholder(q) {
    if (q.fill_hint) return q.fill_hint;
    if (q.memory?.fill_hint) return q.memory.fill_hint;
    const lang = q.memory?.lang;
    if (lang === 'pos') return '填中文词性，如：名词、动词、形容词（也可填 n. / v. / adj.）';
    if (lang === 'en') return '填英文单词，小写即可；短语按原样填写';
    if (lang === 'zh') return '填中文释义，关键词即可';
    return '请输入答案';
  }

  function formatAiScoreBadge(idx) {
    const s = state.aiScoreCache[idx];
    if (!s || s.score === null || s.score === undefined) return '';
    const scoreText = window.QuizAI
      ? QuizAI.formatScoreNumber(s.score)
      : String(s.score);
    return `
      <div id="ai-score-badge" class="mt-3 rounded-lg border border-violet-200 bg-violet-100/80 px-4 py-3">
        <p class="text-xs text-violet-700">AI 评分</p>
        <p class="text-2xl font-bold text-violet-800">${escapeHtml(scoreText)}<span class="text-base font-normal text-violet-600"> / ${escapeHtml(String(s.maxScore))} 分</span></p>
      </div>`;
  }

  function applyAiExplainResult(idx, result, outputEl) {
    if (!window.QuizAI) return;
    const q = state.quiz.questions[idx];
    const maxScore = QuizAI.getSelfGradeMaxScore(q);
    let text = result?.text ?? result;
    let score = result?.score ?? null;
    let max = result?.maxScore ?? maxScore;

    if (QuizAI.needsAiScore(q) && typeof text === 'string') {
      const parsed = QuizAI.parseAiScore(text, maxScore);
      text = parsed.body || text;
      if (parsed.score !== null) {
        score = parsed.score;
        max = parsed.maxScore;
      }
    }

    state.aiCache[idx] = text;
    if (QuizAI.needsAiScore(q)) {
      if (score !== null) {
        state.aiScoreCache[idx] = { score, maxScore: max };
      }
    }

    if (outputEl) {
      outputEl.innerHTML = QuizAI.formatAiHtml(text);
    }
  }

  function updateAiScoreBadgeDom(idx) {
    const container = $('question-container');
    if (!container) return;
    let badge = container.querySelector('#ai-score-badge');
    const html = formatAiScoreBadge(idx);
    if (html) {
      if (badge) {
        badge.outerHTML = html;
      } else {
        const btn = container.querySelector('#btn-ai-explain');
        if (btn) btn.insertAdjacentHTML('afterend', html);
      }
    } else if (badge) {
      badge.remove();
    }
  }

  function aiExplainButtonLabel(q, idx) {
    if (!window.QuizAI) return 'AI 解析';
    const needScore = QuizAI.needsAiScore(q);
    const cached = state.aiCache[idx];
    const hasScore = state.aiScoreCache[idx]?.score !== null && state.aiScoreCache[idx]?.score !== undefined;
    if (state.aiExplainLoading) {
      return needScore ? '评分与解析生成中…' : '解析生成中…';
    }
    if (cached || hasScore) {
      return needScore ? '重新评分与解析' : '重新生成 AI 解析';
    }
    return needScore ? 'AI 解析与评分' : 'AI 解析';
  }

  const pages = {
    home: $('page-home'),
    quiz: $('page-quiz'),
    result: $('page-result'),
    settings: $('page-settings'),
  };

  function showPage(name) {
    Object.values(pages).forEach((el) => el.classList.add('hidden'));
    pages[name].classList.remove('hidden');
    window.scrollTo(0, 0);
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }

  function scrollToQuestionArea() {
    const q = state.quiz?.questions[state.currentIndex];
    let el = $('question-container');
    if (q && isEssayQuestion(q)) {
      if (state.submitted) {
        el =
          document.querySelector('#ai-explain-output') ||
          document.querySelector('#btn-ai-explain') ||
          document.querySelector('.essay-prompt-fold') ||
          el;
      } else {
        el = document.querySelector('.essay-prompt') || el;
      }
    }
    if (!el) return;
    const header = pages.quiz?.querySelector('header');
    const offset = header ? header.offsetHeight + 12 : 0;
    const y = el.getBoundingClientRect().top + window.pageYOffset - offset;
    window.scrollTo({ top: Math.max(0, y), behavior: 'smooth' });
  }

  function bindEssayInputScrollGuard(textarea) {
    if (!textarea || textarea.dataset.scrollGuard === '1') return;
    textarea.dataset.scrollGuard = '1';
    let lockY = null;
    const restore = () => {
      if (lockY === null) return;
      window.scrollTo({ top: lockY, left: 0, behavior: 'instant' });
    };
    textarea.addEventListener('focus', () => {
      if (!window.matchMedia('(max-width: 1023px)').matches) return;
      lockY = window.scrollY;
      restore();
      requestAnimationFrame(restore);
      [50, 120, 250, 400].forEach((ms) => setTimeout(restore, ms));
    });
    textarea.addEventListener('blur', () => {
      lockY = null;
    });
  }

  function normalizeText(text) {
    return String(text || '')
      .toLowerCase()
      .replace(/\s+/g, '')
      .replace(/[（）()]/g, '')
      .replace(/[；;，,、/／]/g, ';');
  }

  function simplifyAnswer(text) {
    return normalizeText(text).replace(/（[^）]*）|\([^)]*\)/g, '');
  }

  function decodeHtml(str) {
    const el = document.createElement('textarea');
    el.innerHTML = String(str || '');
    return el.value;
  }

  function extractChoiceText(correctAnswer) {
    const match = String(correctAnswer).match(/^[A-Z]\.\s*(.+)$/);
    return match ? match[1].trim() : String(correctAnswer).trim();
  }

  function extractChoiceLetter(correctAnswer) {
    const match = String(correctAnswer).match(/^([A-Z])\./);
    return match ? match[1] : null;
  }

  function isChoiceQuestion(q) {
    return q.type === '单选题';
  }

  function isMultiChoiceQuestion(q) {
    return q.type === '多选题';
  }

  function isJudgmentQuestion(q) {
    return q.type === '判断题';
  }

  function isSelectableQuestion(q) {
    return isChoiceQuestion(q) || isMultiChoiceQuestion(q) || isJudgmentQuestion(q);
  }

  function isFillQuestion(q) {
    return q.type.includes('填空');
  }

  function isEssayQuestion(q) {
    return q.type === '问答题';
  }

  function isAnswered(index) {
    const val = state.answers[index];
    if (val === undefined || val === null) return false;
    if (Array.isArray(val)) return val.length > 0;
    return String(val).trim() !== '';
  }

  function letterToIndex(letter) {
    const idx = LETTERS.indexOf(letter);
    return idx >= 0 ? String(idx) : letter;
  }

  function indexToLetter(index) {
    const n = Number(index);
    return Number.isInteger(n) && n >= 0 && n < LETTERS.length ? LETTERS[n] : index;
  }

  function normalizeIndexList(answer) {
    return String(answer || '')
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter((s) => s !== '')
      .sort((a, b) => Number(a) - Number(b))
      .join(',');
  }

  function userAnswerToIndexList(userAnswer) {
    if (Array.isArray(userAnswer)) {
      return normalizeIndexList(userAnswer.map(letterToIndex).join(','));
    }
    const user = String(userAnswer || '').trim();
    if (/^[A-Z](,[A-Z])*$/.test(user.replace(/\s/g, ''))) {
      return normalizeIndexList(
        user
          .split(',')
          .map((s) => letterToIndex(s.trim()))
          .join(',')
      );
    }
    return normalizeIndexList(user);
  }

  function parseIndexSet(answer) {
    return new Set(
      normalizeIndexList(answer)
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s !== '')
    );
  }

  /** 高考数学多选题：有错选 0 分；部分选对按正确个数占满分比例给分 */
  function gaokaoMultiPartialScore(fullScore, correctCount, selectedCorrectCount) {
    if (selectedCorrectCount <= 0 || selectedCorrectCount >= correctCount) return 0;
    const raw = (fullScore * selectedCorrectCount) / correctCount;
    return Number.isInteger(raw) ? raw : Math.round(raw * 10) / 10;
  }

  function gradeMultiChoiceGaokao(q, userAnswer) {
    const maxScore = Number(q.full_score) || 1;
    const correctSet = parseIndexSet(q.correct_answer);
    const userSet = parseIndexSet(userAnswerToIndexList(userAnswer));

    if (userSet.size === 0) {
      return { correct: false, partial: false, score: 0, maxScore };
    }

    const hasWrong = [...userSet].some((i) => !correctSet.has(i));
    if (hasWrong) {
      return { correct: false, partial: false, score: 0, maxScore };
    }

    const selectedCorrectCount = [...userSet].filter((i) => correctSet.has(i)).length;
    if (selectedCorrectCount === correctSet.size) {
      return { correct: true, partial: false, score: maxScore, maxScore };
    }

    const partialScore = gaokaoMultiPartialScore(maxScore, correctSet.size, selectedCorrectCount);
    return { correct: false, partial: true, score: partialScore, maxScore };
  }

  function gradeQuestion(q, userAnswer) {
    if (isEssayQuestion(q)) {
      return { correct: null, score: 0, maxScore: 0 };
    }

    const maxScore = Number(q.full_score) || 1;

    if (isJudgmentQuestion(q)) {
      const user = String(userAnswer || '').trim();
      if (!user) {
        return { correct: false, score: 0, maxScore };
      }
      const userIndex = letterToIndex(user);
      const ok = userIndex === String(q.correct_answer).trim();
      return { correct: ok, score: ok ? maxScore : 0, maxScore };
    }

    if (isMultiChoiceQuestion(q)) {
      return gradeMultiChoiceGaokao(q, userAnswer);
    }

    if (isChoiceQuestion(q)) {
      const letter = extractChoiceLetter(q.correct_answer);
      const correctText = extractChoiceText(q.correct_answer);
      const user = String(userAnswer || '').trim();
      const ok =
        user === letter ||
        user === q.correct_answer ||
        user === correctText ||
        user === `${letter}. ${correctText}`;
      return { correct: ok, score: ok ? maxScore : 0, maxScore };
    }

    if (isFillQuestion(q)) {
      const userNorm = normalizeText(userAnswer);
      if (!userNorm) {
        return { correct: false, score: 0, maxScore };
      }

      const lang = q.memory?.lang;
      if (lang === 'en') {
        const normalizeEn = (s) =>
          String(s || '')
            .trim()
            .toLowerCase()
            .replace(/\s+/g, ' ')
            .replace(/\.{3,}/g, '...');
        const user = normalizeEn(userAnswer);
        const answers = String(q.correct_answer)
          .split(/[；;]/)
          .map(normalizeEn)
          .filter(Boolean);
        const ok = answers.some((a) => user === a);
        return { correct: ok, score: ok ? maxScore : 0, maxScore };
      }

      if (lang === 'pos') {
        const ok = matchPosAnswer(userAnswer, q.correct_answer);
        return { correct: ok, score: ok ? maxScore : 0, maxScore };
      }

      const ok = matchZhFillAnswer(q, userAnswer);
      return { correct: ok, score: ok ? maxScore : 0, maxScore };
    }

    return { correct: false, score: 0, maxScore };
  }

  async function loadManifest() {
    const res = await fetch('data/manifest.json');
    if (!res.ok) throw new Error('无法加载题库列表，请通过本地服务器访问本页面');
    state.manifest = await res.json();
  }

  async function loadQuiz(file) {
    const res = await fetch(`data/${encodeURIComponent(file)}`);
    if (!res.ok) throw new Error('无法加载题目文件');
    state.quiz = await res.json();
  }

  function renderQuizList() {
    const list = $('quiz-list');
    list.innerHTML = state.manifest.quizzes
      .map(
        (q) => `
      <button
        data-file="${escapeAttr(q.file)}"
        class="quiz-item w-full text-left bg-white rounded-lg shadow-sm border border-gray-200 px-5 py-4 hover:border-primary hover:shadow transition"
      >
        <div class="flex items-center justify-between gap-3">
          <span class="font-medium">${escapeHtml(q.title)}</span>
          <span class="text-sm text-gray-400 shrink-0">${q.count} 题</span>
        </div>
      </button>`
      )
      .join('');

    list.querySelectorAll('.quiz-item').forEach((btn) => {
      btn.addEventListener('click', () => startQuiz(btn.dataset.file));
    });
  }

  function startQuiz(file) {
    loadQuiz(file)
      .then(() => {
        state.currentIndex = 0;
        state.answers = {};
        state.submitted = false;
        state.aiCache = {};
        state.aiScoreCache = {};
        state.lastResult = null;
        state.wrongDrillMode = false;
        state.parentQuiz = null;
        $('quiz-title').textContent = state.quiz.title;
        $('total-count').textContent = state.quiz.questions.length;
        showPage('quiz');
        renderQuestion();
        renderAnswerCard();
        updateProgress();
        scrollToQuestionArea();
      })
      .catch((err) => alert(err.message));
  }

  function typeLabel(type) {
    if (type === '单选题') return '单选题';
    if (type === '多选题') return '多选题';
    if (type === '判断题') return '判断题';
    if (type.includes('填空')) return '填空题';
    if (type === '问答题') return '问答题';
    return type;
  }

  function getSelectedLetters(idx) {
    const val = state.answers[idx];
    if (Array.isArray(val)) return val;
    if (typeof val === 'string' && val.includes(',')) {
      return val.split(',').map((s) => s.trim()).filter(Boolean);
    }
    return val ? [val] : [];
  }

  function renderSelectableOptions(q, idx, review) {
    const isMulti = isMultiChoiceQuestion(q);
    const selected = getSelectedLetters(idx);
    const correctLetters = isMulti
      ? normalizeIndexList(q.correct_answer)
          .split(',')
          .map(indexToLetter)
      : [indexToLetter(isJudgmentQuestion(q) ? q.correct_answer : extractChoiceLetter(q.correct_answer))];

    return `<div class="space-y-3">${q.options
      .map((opt, i) => {
        const letter = LETTERS[i];
        const id = `opt-${idx}-${i}`;
        const isSelected = selected.includes(letter);
        const checked = isSelected ? 'checked' : '';
        let extra = '';
        if (review) {
          const isCorrectOption = correctLetters.includes(letter);
          if (isCorrectOption) extra = ' ring-2 ring-green-400';
          else if (isSelected) extra = ' ring-2 ring-red-400';
        }
        const inputType = isMulti ? 'checkbox' : 'radio';
        return `
          <label class="option-item block cursor-pointer">
            <input type="${inputType}" name="q-${idx}" value="${letter}" id="${id}" class="sr-only" ${checked} ${state.submitted ? 'disabled' : ''} />
            <div class="option-body flex items-start gap-3 border border-gray-200 rounded-lg px-4 py-3 hover:border-primary/50 transition${extra}">
              <span class="shrink-0 w-7 h-7 rounded-full border border-gray-300 flex items-center justify-center text-sm font-medium">${letter}</span>
              <span class="pt-0.5">${escapeHtml(decodeHtml(opt))}</span>
            </div>
          </label>`;
      })
      .join('')}</div>`;
  }

  function formatCorrectAnswer(q) {
    if (isJudgmentQuestion(q)) {
      const idx = Number(q.correct_answer);
      const text = q.options[idx] ? decodeHtml(q.options[idx]) : q.correct_answer;
      return q.options[idx] ? `${indexToLetter(q.correct_answer)}. ${text}` : q.correct_answer;
    }
    if (isMultiChoiceQuestion(q)) {
      return normalizeIndexList(q.correct_answer)
        .split(',')
        .map((i) => {
          const letter = indexToLetter(i);
          const text = q.options[Number(i)] ? decodeHtml(q.options[Number(i)]) : '';
          return `${letter}. ${text}`;
        })
        .join('；');
    }
    if (isChoiceQuestion(q)) {
      const letter = extractChoiceLetter(q.correct_answer);
      const text = decodeHtml(extractChoiceText(q.correct_answer));
      return letter ? `${letter}. ${text}` : text;
    }
    if (isFillQuestion(q) && q.memory?.lang === 'pos') {
      const zh = q.memory?.pos_zh || posZhFromEn(q.memory?.pos);
      const enPos = q.memory?.pos || '';
      return enPos ? `${zh}（${enPos}）` : zh;
    }
    if (isFillQuestion(q) && q.memory?.lang === 'zh') {
      const short = zhShort(q.correct_answer);
      const full = q.memory?.zh;
      return full && full !== short ? `${short}（${full}）` : short;
    }
    if (isFillQuestion(q) && q.memory?.lang === 'en') {
      return q.memory?.en || q.correct_answer;
    }
    return q.correct_answer;
  }

  function advanceToNextQuestion(idx) {
    if (idx < state.quiz.questions.length - 1) {
      state.currentIndex = idx + 1;
      renderQuestion();
      renderAnswerCard();
      updateProgress();
      if (window.matchMedia('(max-width: 1023px)').matches) scrollToQuestionArea();
    }
  }

  function selectSingleAnswer(q, idx, value) {
    const changed = state.answers[idx] !== value;
    state.answers[idx] = value;
    renderAnswerCard();
    updateProgress();
    if (changed && (isChoiceQuestion(q) || isJudgmentQuestion(q))) {
      advanceToNextQuestion(idx);
    }
  }

  function renderQuestion() {
    const q = state.quiz.questions[state.currentIndex];
    const idx = state.currentIndex;
    const container = $('question-container');
    const disabled = state.submitted ? 'disabled' : '';
    const review = state.submitted;

    let body = '';
    let essayLead = '';

    if (isSelectableQuestion(q)) {
      body = renderSelectableOptions(q, idx, review);
    } else if (isFillQuestion(q)) {
      const val = state.answers[idx] || '';
      const ph = fillInputPlaceholder(q);
      body = `
        <input
          type="text"
          id="fill-input"
          value="${escapeAttr(val)}"
          placeholder="${escapeAttr(ph)}"
          ${disabled}
          class="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
        />`;
    } else if (isEssayQuestion(q)) {
      const { lead, passage } = parseEssayTitle(q.title);
      const val = state.answers[idx] || '';
      let promptBlock = '';
      if (passage) {
        if (review) {
          promptBlock = `
        <details class="essay-prompt-fold mb-4 rounded-lg border border-gray-200 bg-gray-50">
          <summary class="text-sm font-medium text-gray-600 px-4 py-3">英文原文（点击展开 / 收起）</summary>
          <div class="essay-prompt px-4 pb-4 max-h-[min(50vh,16rem)] overflow-y-auto text-[15px] leading-relaxed whitespace-pre-line text-gray-800 border-t border-gray-100">${escapeHtml(passage)}</div>
        </details>`;
        } else {
          promptBlock = `
        <div class="essay-prompt mb-4 rounded-lg border border-gray-200 bg-gray-50 p-4 max-h-[min(42vh,18rem)] overflow-y-auto text-[15px] leading-relaxed whitespace-pre-line text-gray-800">${escapeHtml(passage)}</div>`;
        }
      }
      body = `
        ${promptBlock}
        <p class="text-sm font-medium text-gray-600 mb-2">你的翻译</p>
        ${
          review
            ? `<div class="text-sm text-gray-700 bg-gray-50 rounded-lg p-3 whitespace-pre-line border border-gray-200 mb-2 min-h-[3rem]">${val ? escapeHtml(val) : '<span class="text-gray-400">（未作答）</span>'}</div>`
            : `<textarea
          id="essay-input"
          rows="6"
          placeholder="对照上方英文，在此输入中文翻译"
          ${disabled}
          class="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary resize-y"
        >${escapeHtml(val)}</textarea>`
        }`;
      essayLead = lead;
    } else {
      body = `<p class="text-sm text-gray-500">暂不支持「${escapeHtml(typeLabel(q.type))}」题型，请跳过此题。</p>`;
    }

    let reviewBlock = '';
    if (review && !isEssayQuestion(q)) {
      const answered = isAnswered(idx);
      const result = gradeQuestion(q, state.answers[idx]);
      const statusText = !answered
        ? '未作答'
        : result.correct
          ? '✓ 回答正确'
          : result.partial
            ? `△ 部分正确（得 ${result.score} / ${result.maxScore} 分）`
            : '✗ 回答错误';
      const statusClass = !answered
        ? 'text-gray-500'
        : result.correct
          ? 'text-green-600'
          : result.partial
            ? 'text-amber-600'
            : 'text-red-600';
      reviewBlock = `
        <div class="mt-5 pt-5 border-t border-gray-100">
          <p class="text-sm ${statusClass}">${statusText}</p>
          <p class="text-sm text-gray-600 mt-1">正确答案：${escapeHtml(formatCorrectAnswer(q))}</p>${
            q.memory && !result.correct
              ? `<p class="text-sm text-violet-700 mt-2 bg-violet-50 rounded-lg p-2">💡 ${escapeHtml(q.memory.hook || '')}</p>
                 <p class="text-xs text-gray-500 mt-1">${escapeHtml(q.memory.en)} · ${escapeHtml(q.memory.zh)}</p>`
              : ''
          }
        </div>`;
    } else if (review && isEssayQuestion(q)) {
      const aiScore = state.aiScoreCache[idx];
      const scoreLine =
        aiScore && aiScore.score !== null && aiScore.score !== undefined
          ? `<p class="text-sm font-medium text-violet-700 mb-3 rounded-lg bg-violet-50 border border-violet-100 px-3 py-2">AI 评分：${escapeHtml(QuizAI.formatScoreNumber(aiScore.score))} / ${escapeHtml(String(aiScore.maxScore))} 分</p>`
          : '';
      reviewBlock = `
        <div class="mt-5 pt-5 border-t border-gray-100">
          ${scoreLine}
          <p class="text-sm text-gray-500 mb-1">参考答案</p>
          <p class="text-sm text-gray-700 bg-gray-50 rounded-lg p-3">${escapeHtml(q.correct_answer)}</p>
        </div>`;
    }

    let aiBlock = '';
    if (review && window.QuizAI) {
      const cached = state.aiCache[idx];
      const showOutput = Boolean(cached || state.aiExplainLoading);
      const scoreBadge = formatAiScoreBadge(idx);
      aiBlock = `
        <div class="mt-4 pt-4 border-t border-gray-100">
          <button type="button" id="btn-ai-explain" class="text-sm px-4 py-2 rounded bg-violet-600 text-white hover:bg-violet-700 disabled:opacity-50" ${state.aiExplainLoading ? 'disabled' : ''}>
            ${escapeHtml(aiExplainButtonLabel(q, idx))}
          </button>
          ${scoreBadge}
          <div id="ai-explain-output" class="${showOutput ? 'ai-output mt-3 text-sm text-gray-700 bg-violet-50 rounded-lg p-4 border border-violet-100' : 'hidden'}">${cached ? QuizAI.formatAiHtml(cached) : ''}</div>
        </div>`;
    }

    container.innerHTML = `
      <div class="flex items-center gap-2 mb-4">
        <span class="text-xs px-2 py-0.5 rounded bg-primary-light text-primary font-medium">${typeLabel(q.type)}</span>
        <span class="text-sm text-gray-400">${idx + 1} / ${state.quiz.questions.length}</span>
      </div>
      <h2 class="${questionTitleClass(q)}">${essayLead ? escapeHtml(decodeHtml(essayLead)) : formatQuestionTitle(q)}</h2>
      ${body}
      ${reviewBlock}
      ${aiBlock}`;

    if (review && window.QuizAI) {
      const explainBtn = container.querySelector('#btn-ai-explain');
      if (explainBtn) {
        explainBtn.addEventListener('click', () => runAiExplain(idx));
      }
    }

    if (!state.submitted) {
      if (isMultiChoiceQuestion(q)) {
        container.querySelectorAll('input[type="checkbox"]').forEach((input) => {
          input.addEventListener('change', () => {
            const picked = Array.from(
              container.querySelectorAll('input[type="checkbox"]:checked')
            ).map((el) => el.value);
            state.answers[idx] = picked;
            renderAnswerCard();
            updateProgress();
          });
        });
      } else {
        container.querySelectorAll('input[type="radio"]').forEach((input) => {
          input.addEventListener('change', () => {
            selectSingleAnswer(q, idx, input.value);
          });
          const label = input.closest('.option-item');
          if (label) {
            label.addEventListener('click', () => {
              setTimeout(() => {
                if (input.checked) selectSingleAnswer(q, idx, input.value);
              }, 0);
            });
          }
        });
      }

      const fillInput = container.querySelector('#fill-input');
      if (fillInput) {
        fillInput.addEventListener('input', (e) => {
          state.answers[idx] = e.target.value;
          renderAnswerCard();
          updateProgress();
        });
      }

      const essayInput = container.querySelector('#essay-input');
      if (essayInput) {
        bindEssayInputScrollGuard(essayInput);
        essayInput.addEventListener('input', (e) => {
          state.answers[idx] = e.target.value;
          renderAnswerCard();
          updateProgress();
        });
      }
    }

    $('btn-prev').disabled = idx === 0;
    $('btn-next').disabled = idx === state.quiz.questions.length - 1;
  }

  function renderAnswerCard() {
    const card = $('answer-card');
    card.innerHTML = state.quiz.questions
      .map((q, i) => {
        let cls = 'answer-card-btn h-10 w-full rounded border-2 border-gray-200 text-sm hover:border-primary flex items-center justify-center';
        if (i === state.currentIndex) cls += ' current';
        if (state.submitted && !isEssayQuestion(q)) {
          const result = gradeQuestion(q, state.answers[i]);
          if (result.correct) cls += ' correct';
          else if (result.partial) cls += ' partial';
          else cls += ' wrong';
        } else if (isAnswered(i)) {
          cls += ' answered';
        }
        return `<button data-index="${i}" class="${cls}">${i + 1}</button>`;
      })
      .join('');

    card.querySelectorAll('button').forEach((btn) => {
      btn.addEventListener('click', () => {
        state.currentIndex = Number(btn.dataset.index);
        renderQuestion();
        renderAnswerCard();
        updateProgress();
        if (window.matchMedia('(max-width: 1023px)').matches) scrollToQuestionArea();
      });
    });

    const answered = state.quiz.questions.filter((_, i) => isAnswered(i)).length;
    $('answered-count').textContent = answered;
    $('btn-submit').classList.toggle('hidden', state.submitted);
  }

  function updateProgress() {
    const total = state.quiz.questions.length;
    const answered = state.quiz.questions.filter((_, i) => isAnswered(i)).length;
    const pct = total ? Math.round((answered / total) * 100) : 0;
    $('progress-bar').style.width = `${pct}%`;
    $('quiz-progress-text').textContent = `已完成 ${answered} / ${total} 题`;
  }

  function submitQuiz() {
    if (state.submitted) return;

    const unanswered = state.quiz.questions.filter((_, i) => !isAnswered(i)).length;
    if (unanswered > 0) {
      const ok = confirm(`还有 ${unanswered} 题未作答，确定交卷吗？`);
      if (!ok) return;
    }

    state.submitted = true;
    let score = 0;
    let total = 0;

    state.quiz.questions.forEach((q, i) => {
      const result = gradeQuestion(q, state.answers[i]);
      if (result.maxScore > 0) {
        score += result.score;
        total += result.maxScore;
      }
    });

    const correctCount = state.quiz.questions.filter((q, i) => {
      const result = gradeQuestion(q, state.answers[i]);
      return result.correct === true;
    }).length;
    const partialCount = state.quiz.questions.filter((q, i) => {
      const result = gradeQuestion(q, state.answers[i]);
      return result.partial === true;
    }).length;
    const objectiveCount = state.quiz.questions.filter((q) => !isEssayQuestion(q)).length;

    const rate = total ? Math.round((score / total) * 100) : 0;
    let rateText = `全对 ${correctCount} 题`;
    if (partialCount > 0) rateText += `，部分正确 ${partialCount} 题`;
    rateText += ` / 客观题 ${objectiveCount} 题，得分率 ${rate}%（问答题不计分；多选题按高考数学规则计分）`;

    const resultSnapshot = {
      score: score.toFixed(1).replace(/\.0$/, ''),
      total: total.toFixed(1).replace(/\.0$/, ''),
      rate,
      correctCount,
      partialCount,
      objectiveCount,
      rateText,
    };

    if (state.wrongDrillMode && state.parentQuiz) {
      const parent = state.parentQuiz;
      state.quiz = parent.quiz;
      state.answers = parent.answers;
      state.wrongDrillMode = false;
      state.parentQuiz = null;
      state.lastResult = parent.lastResult;
      $('result-score').textContent = parent.lastResult.score;
      $('result-total').textContent = parent.lastResult.total;
      $('result-rate').textContent = parent.lastResult.rateText || rateText;
      const banner = $('wrong-drill-banner');
      if (banner) {
        banner.textContent = `突击练习完成：${resultSnapshot.score}/${resultSnapshot.total}（${resultSnapshot.correctCount}/${resultSnapshot.objectiveCount} 题正确）。下方为原卷错词记忆。`;
        banner.classList.remove('hidden');
      }
    } else {
      state.lastResult = resultSnapshot;
      $('result-score').textContent = resultSnapshot.score;
      $('result-total').textContent = resultSnapshot.total;
      $('result-rate').textContent = rateText;
      $('wrong-drill-banner')?.classList.add('hidden');
    }

    $('ai-analysis-panel').classList.add('hidden');
    $('ai-analysis-output').innerHTML = '';
    state.aiAnalysisText = '';
    state.aiAnalysisCollapsed = false;
    updateAnalysisCollapseBtn();
    updateAnalysisButtonLabel();
    updateAnalysisModeBadge();
    renderWrongMemoryPanel();
    showPage('result');
  }

  function isIwordsFillQuiz() {
    return state.quiz?.quiz_type === 'iwords_fill';
  }

  function getWrongFillQuestions() {
    if (!state.quiz) return [];
    return state.quiz.questions
      .map((q, i) => ({ q, i }))
      .filter(({ q }) => isFillQuestion(q) && q.memory)
      .filter(({ i }) => isAnswered(i))
      .filter(({ q, i }) => {
        const result = gradeQuestion(q, state.answers[i]);
        return !result.correct;
      });
  }

  function zhShort(zh) {
    return String(zh || '')
      .split(/[，；;,/]/)[0]
      .trim();
  }

  function zhAnswerParts(zh) {
    return String(zh || '')
      .replace(/，/g, '；')
      .split(/[；;]/)
      .map((p) => simplifyAnswer(p.trim()))
      .filter(Boolean);
  }

  function buildZhAnswer(zh) {
    if (/[；;,，]/.test(zh)) {
      const parts = zh
        .replace(/，/g, '；')
        .split(/[；;]/)
        .map((p) => p.trim())
        .filter(Boolean);
      return parts.slice(0, 2).join('；') || parts[0];
    }
    return zhShort(zh);
  }

  function collectZhExpectedParts(q) {
    const parts = new Set();
    String(q.correct_answer || '')
      .split(/[；;]/)
      .map((p) => simplifyAnswer(p))
      .filter(Boolean)
      .forEach((p) => parts.add(p));
    if (q.memory?.zh) {
      zhAnswerParts(q.memory.zh).forEach((p) => parts.add(p));
    }
    return [...parts];
  }

  function matchZhFillAnswer(q, userAnswer) {
    const userNorm = normalizeText(userAnswer);
    if (!userNorm) return false;

    const expectedParts = collectZhExpectedParts(q);
    const userParts = userNorm.includes(';')
      ? userNorm.split(';').map((p) => simplifyAnswer(p)).filter(Boolean)
      : [simplifyAnswer(userNorm)];

    const matchPart = (expected, user) => {
      if (!expected || !user) return false;
      if (user === expected) return true;
      if (user.includes(expected)) return true;
      if (expected.startsWith(user) && user.length >= 2) return true;
      return expected.includes(user) && user.length >= 2;
    };

    if (expectedParts.length > 1) {
      return expectedParts.some((expected) =>
        userParts.some((user) => matchPart(expected, user))
      );
    }
    return matchPart(expectedParts[0], userParts[0]);
  }

  function buildIwordsQuestion(meta, pattern, sort, unitTag) {
    const en = meta.en || '';
    const zh = meta.zh || '';
    const pos = meta.pos || '';
    const ph = meta.phonetic || '';
    const tag = unitTag || '';
    const posLine = pos ? `${pos} ` : '';
    const phLine = ph ? `  ${ph}` : '';
    let title = '';
    let answer = '';
    let lang = 'en';
    let remix = 'zh2en';

    if (pattern === 'zh2en') {
      title = `${tag}【中→英】\n${posLine}${zh}${phLine}\n请填写英文：______`;
      answer = en;
      lang = 'en';
      remix = 'en2zh';
    } else if (pattern === 'en2zh') {
      title = `${tag}【英→中】\n${en}${phLine}  ${pos}\n中文释义：______`;
      answer = buildZhAnswer(zh);
      lang = 'zh';
      remix = 'zh2en';
    } else if (pattern === 'spell') {
      const letters = en.replace(/\s/g, '').length;
      title = `${tag}【拼写】\n${posLine}${zh}\n首字母 ${en[0]?.toUpperCase() || '?'}，共 ${letters} 个字母：______`;
      answer = en;
      lang = 'en';
      remix = 'en2zh';
    } else if (pattern === 'pos') {
      title = `${tag}【词性】\n${en}${phLine}  ${zh}\n词性（填中文，如名词、动词、形容词）：______`;
      const posZh = meta.pos_zh || posZhFromEn(pos);
      const bare = (pos || 'phr').replace(/\.$/, '');
      const accepts = [];
      [posZh, pos || 'phr', bare, `${bare}.`].forEach((item) => {
        if (item && !accepts.includes(item)) accepts.push(item);
      });
      answer = accepts.join(';');
      lang = 'pos';
      remix = 'spell';
    } else if (pattern === 'assoc') {
      const head = en.slice(0, Math.max(2, Math.min(3, en.length - 1)));
      const tail = '_'.repeat(Math.max(1, en.length - head.length));
      title = `${tag}【联想拼写】\n${zh}（${pos || '短语'}）\n${head}${tail} 共 ${en.length} 字母：______`;
      answer = en;
      lang = 'en';
      remix = 'en2zh';
    } else if (pattern === 'phrase_cloze') {
      if (en.includes('...') || en.includes('/') || en.includes(' / ')) {
        title = `${tag}【短语填空】\n${zh}\n请填写完整英文短语：______`;
        answer = en;
      } else {
        const words = en.split(/\s+/).filter(Boolean);
        if (words.length >= 2) {
          const mid = Math.floor(words.length / 2);
          const blank = `${words.slice(0, mid).join(' ')} ______ ${words.slice(mid).join(' ')}`;
          title = `${tag}【短语填空】\n${zh}\n${blank}`;
          answer = words.slice(mid).length > 2 ? en : words.slice(mid).join(' ');
        } else {
          title = `${tag}【中→英】\n${zh}\n请填写英文：______`;
          answer = en;
        }
      }
      lang = 'en';
      remix = 'en2zh';
    } else {
      title = `${tag}【中→英】\n${posLine}${zh}${phLine}\n请填写英文：______`;
      answer = en;
      lang = 'en';
      remix = 'en2zh';
    }

    const memory = {
      ...meta,
      pattern,
      lang,
      remix_pattern: remix,
      hook: meta.hook || `${en} — ${zhShort(zh)}`,
      pos_zh: meta.pos_zh || posZhFromEn(pos),
    };
    const row = {
      sort,
      type: '填空题(客观)',
      title,
      options: [''],
      correct_answer: answer,
      your_answer: '',
      score: '0',
      full_score: 1,
      memory,
    };
    row.fill_hint = meta.fill_hint || fillInputPlaceholder(row);
    return row;
  }

  function remixQuestion(q, sort) {
    const m = q.memory;
    if (!m) return { ...q, sort };
    const unitMatch = String(q.title).match(/【([^·]+· [^】]+)】/);
    const tag = m.unit_tag || (unitMatch ? `【${unitMatch[1]}】` : '');
    const pattern = m.remix_pattern || REMIX_FALLBACK[m.pattern] || 'zh2en';
    return buildIwordsQuestion(m, pattern, sort, tag);
  }

  function renderWrongMemoryPanel() {
    const panel = $('wrong-memory-panel');
    const list = $('wrong-memory-list');
    const summary = $('wrong-memory-summary');
    const drillBtn = $('btn-wrong-drill');
    if (!panel || !list) return;

    const wrong = getWrongFillQuestions();
    if (!wrong.length || !isIwordsFillQuiz()) {
      panel.classList.add('hidden');
      list.innerHTML = '';
      return;
    }

    panel.classList.remove('hidden');
    summary.textContent = `共 ${wrong.length} 个错词。先看记忆钩，再盖住答案自己回忆一遍，或换形式再练。`;

    list.innerHTML = wrong
      .map(({ q, i }, cardIdx) => {
        const user = String(state.answers[i] || '').trim();
        const correctDisplay = formatCorrectAnswer(q);
        const m = q.memory;
        const hook = m.hook || `${m.en} = ${zhShort(m.zh)}`;
        const prompt = String(q.title)
          .replace(/______/g, '___')
          .split('\n')
          .slice(0, 3)
          .join(' · ');
        return `
        <div class="memory-card border border-gray-200 rounded-lg p-4 bg-amber-50/40" data-card="${cardIdx}">
          <p class="text-xs text-gray-500 mb-1">${escapeHtml(prompt)}</p>
          <p class="text-sm"><span class="text-red-600">你的：${escapeHtml(user)}</span>
            <span class="text-gray-400 mx-1">→</span>
            <span class="memory-answer text-green-700 font-medium">${escapeHtml(correctDisplay)}</span>
          </p>
          <p class="text-sm text-violet-800 mt-2 memory-hook">💡 ${escapeHtml(hook)}</p>
          <p class="text-xs text-gray-600 mt-1">完整：${escapeHtml(m.en)} · ${escapeHtml(m.zh)}</p>
          <button type="button" class="memory-cover-btn text-xs text-primary mt-2 hover:underline" data-card="${cardIdx}">
            盖住答案，自己回忆
          </button>
        </div>`;
      })
      .join('');

    list.querySelectorAll('.memory-cover-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const card = btn.closest('.memory-card');
        const ans = card.querySelector('.memory-answer');
        const covered = ans.dataset.covered === '1';
        if (covered) {
          ans.textContent = ans.dataset.full;
          ans.dataset.covered = '0';
          btn.textContent = '盖住答案，自己回忆';
        } else {
          ans.dataset.full = ans.textContent;
          ans.textContent = '●●●';
          ans.dataset.covered = '1';
          btn.textContent = '显示答案';
        }
      });
    });

    if (drillBtn) {
      drillBtn.classList.toggle('hidden', wrong.length === 0);
    }
  }

  function startWrongDrill() {
    if (state.wrongDrillMode) return;
    const wrong = getWrongFillQuestions();
    if (!wrong.length) return;

    state.parentQuiz = {
      quiz: state.quiz,
      answers: { ...state.answers },
      lastResult: state.lastResult,
      submitted: true,
    };
    state.wrongDrillMode = true;

    const remixed = wrong.map(({ q }, idx) => remixQuestion(q, idx + 1));
    state.quiz = {
      title: `${state.parentQuiz.quiz.title} · 错词突击`,
      quiz_type: 'iwords_fill',
      questions: remixed,
    };
    state.currentIndex = 0;
    state.answers = {};
    state.submitted = false;
    state.aiCache = {};
    state.aiScoreCache = {};
    state.lastResult = null;

    $('quiz-title').textContent = state.quiz.title;
    $('total-count').textContent = remixed.length;
    $('wrong-memory-panel')?.classList.add('hidden');
    showPage('quiz');
    renderQuestion();
    renderAnswerCard();
    updateProgress();
  }

  function exitWrongDrill() {
    if (!state.wrongDrillMode || !state.parentQuiz) return;
    state.quiz = state.parentQuiz.quiz;
    state.answers = state.parentQuiz.answers;
    state.lastResult = state.parentQuiz.lastResult;
    state.submitted = true;
    state.wrongDrillMode = false;
    state.parentQuiz = null;
    $('wrong-drill-banner')?.classList.add('hidden');
    if (state.lastResult) {
      $('result-score').textContent = state.lastResult.score;
      $('result-total').textContent = state.lastResult.total;
      if (state.lastResult.rateText) {
        $('result-rate').textContent = state.lastResult.rateText;
      }
    }
    showPage('result');
    renderWrongMemoryPanel();
  }

  function requireAiConfigured(options) {
    if (!window.QuizAI) {
      alert('AI 模块未加载');
      return false;
    }
    if (!QuizAI.isConfigured()) {
      const locked = QuizAI.hasStoredKey() && !QuizAI.isKeyUnlocked();
      const msg = locked
        ? 'Key 已加密保存，请先在「AI 设置」输入解锁口令，或清除 Key 后使用站点默认 AI。'
        : '尚未配置 AI。请在「AI 设置」填写 API Key，或使用站点默认 AI（需 Worker 代理可用）。';
      if (options && options.inlineEl) {
        options.inlineEl.classList.remove('hidden');
        options.inlineEl.innerHTML =
          `<span class="text-amber-700">${escapeHtml(msg)}</span> ` +
          `<button type="button" class="text-violet-600 underline" data-go-settings>去 AI 设置</button>`;
        options.inlineEl.querySelector('[data-go-settings]')?.addEventListener('click', openSettings);
        return false;
      }
      if (options && options.noRedirect) {
        alert(msg);
        return false;
      }
      if (confirm(`${msg}\n\n是否前往 AI 设置？`)) {
        openSettings();
      }
      return false;
    }
    return true;
  }

  async function runAiExplain(idx) {
    if (!requireAiConfigured() || state.aiExplainLoading) return;

    const q = state.quiz.questions[idx];
    const gradeResult = gradeQuestion(q, state.answers[idx]);
    const maxScore = QuizAI.getSelfGradeMaxScore(q);
    state.aiExplainLoading = true;
    delete state.aiScoreCache[idx];
    renderQuestion();

    let outputEl = $('question-container').querySelector('#ai-explain-output');
    if (outputEl) {
      outputEl.classList.remove('hidden');
      outputEl.innerHTML = '<span class="text-gray-500 ai-loading">正在生成解析</span>';
    }

    try {
      const result = await QuizAI.explainQuestion(
        q,
        state.answers[idx],
        gradeResult,
        (partial) => {
          if (!outputEl) return;
          if (QuizAI.needsAiScore(q)) {
            const parsed = QuizAI.parseAiScore(partial, maxScore);
            if (parsed.score !== null) {
              state.aiScoreCache[idx] = { score: parsed.score, maxScore: parsed.maxScore };
              updateAiScoreBadgeDom(idx);
            }
            outputEl.innerHTML = QuizAI.formatAiHtml(parsed.body || partial);
          } else {
            outputEl.innerHTML = QuizAI.formatAiHtml(partial);
          }
        }
      );
      applyAiExplainResult(idx, result, outputEl);
      updateAiScoreBadgeDom(idx);
    } catch (err) {
      if (outputEl) {
        outputEl.innerHTML = `<span class="text-red-600">${escapeHtml(err.message)}</span>`;
      }
    } finally {
      state.aiExplainLoading = false;
      renderQuestion();
    }
  }

  function updateAnalysisCollapseBtn() {
    const btn = $('btn-close-analysis');
    const output = $('ai-analysis-output');
    if (!btn || !output) return;
    btn.textContent = state.aiAnalysisCollapsed ? '展开' : '收起';
    output.classList.toggle('hidden', state.aiAnalysisCollapsed);
  }

  function toggleAnalysisCollapse() {
    if (!state.aiAnalysisText && !state.aiAnalysisLoading) return;
    state.aiAnalysisCollapsed = !state.aiAnalysisCollapsed;
    updateAnalysisCollapseBtn();
  }

  function updateAnalysisButtonLabel() {
    const btn = $('btn-ai-analysis');
    if (!btn || state.aiAnalysisLoading) return;
    const ownKey = window.QuizAI && QuizAI.hasOwnKey();
    if (!ownKey) {
      const left = state.analysisCooldownUntil - Date.now();
      if (left > 0) {
        btn.textContent = `请等待 ${Math.ceil(left / 1000)} 秒`;
        btn.disabled = true;
        return;
      }
    }
    if (state.aiAnalysisText) {
      btn.textContent = ownKey ? '重新分析' : '查看学情分析';
    } else {
      btn.textContent = 'AI 学情分析';
    }
    btn.disabled = false;
  }

  function updateAnalysisModeBadge() {
    const badge = $('ai-analysis-mode-badge');
    if (!badge || !window.QuizAI) return;
    if (QuizAI.hasOwnKey()) {
      badge.textContent = '完整模式';
      badge.className =
        'text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 shrink-0';
      badge.classList.remove('hidden');
    } else if (QuizAI.usesProxy()) {
      badge.textContent = '站点免费';
      badge.className =
        'text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-500 shrink-0';
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  }

  function startAnalysisCooldown(seconds) {
    state.analysisCooldownUntil = Date.now() + seconds * 1000;
    updateAnalysisButtonLabel();
    if (state.analysisCooldownTimer) clearInterval(state.analysisCooldownTimer);
    state.analysisCooldownTimer = setInterval(() => {
      if (Date.now() >= state.analysisCooldownUntil) {
        clearInterval(state.analysisCooldownTimer);
        state.analysisCooldownTimer = null;
        updateAnalysisButtonLabel();
        return;
      }
      updateAnalysisButtonLabel();
    }, 500);
  }

  async function runAiAnalysis(forceRegenerate) {
    if (state.aiAnalysisLoading || !state.lastResult) return;

    const panel = $('ai-analysis-panel');
    const output = $('ai-analysis-output');
    const ownKey = window.QuizAI && QuizAI.hasOwnKey();
    panel.classList.remove('hidden');
    updateAnalysisModeBadge();

    if (!window.QuizAI) {
      output.innerHTML = '<span class="text-red-600">AI 模块未加载</span>';
      return;
    }
    if (!QuizAI.isConfigured()) {
      state.aiAnalysisCollapsed = false;
      updateAnalysisCollapseBtn();
      requireAiConfigured({ inlineEl: output });
      return;
    }

    if (state.aiAnalysisText && !forceRegenerate) {
      state.aiAnalysisCollapsed = false;
      updateAnalysisCollapseBtn();
      output.innerHTML = QuizAI.formatAiHtml(state.aiAnalysisText);
      updateAnalysisButtonLabel();
      return;
    }

    if (!ownKey && Date.now() < state.analysisCooldownUntil) {
      output.innerHTML = `<span class="text-amber-600">站点免费 AI 冷却中，请 ${Math.ceil((state.analysisCooldownUntil - Date.now()) / 1000)} 秒后再试；或填写自带 Key 启用完整模式</span>`;
      updateAnalysisButtonLabel();
      return;
    }

    if (forceRegenerate) {
      state.aiAnalysisText = '';
    }

    state.aiAnalysisCollapsed = false;
    updateAnalysisCollapseBtn();
    state.aiAnalysisLoading = true;
    output.innerHTML = ownKey
      ? '<span class="text-gray-500 ai-loading">正在生成详细学情分析</span>'
      : '<span class="text-gray-500 ai-loading">正在分析学情，请稍候</span>';
    $('btn-ai-analysis').disabled = true;
    if (!ownKey) {
      startAnalysisCooldown(120);
    }

    const stats = {
      ...state.lastResult,
      gradeFn: gradeQuestion,
    };

    try {
      const text = await QuizAI.analyzeResult(
        state.quiz,
        state.answers,
        stats,
        ownKey
          ? (partial) => {
              output.innerHTML = QuizAI.formatAiHtml(partial);
            }
          : null
      );
      state.aiAnalysisText = text || '';
      output.innerHTML = QuizAI.formatAiHtml(state.aiAnalysisText);
      if (!state.aiAnalysisText.trim()) {
        output.innerHTML = '<span class="text-amber-600">未收到完整分析，请稍后重试</span>';
        state.aiAnalysisText = '';
      }
    } catch (err) {
      state.aiAnalysisText = '';
      output.innerHTML = `<span class="text-red-600">${escapeHtml(err.message)}</span>`;
    } finally {
      state.aiAnalysisLoading = false;
      updateAnalysisButtonLabel();
    }
  }

  function populateProviderSelect() {
    const select = $('ai-provider');
    if (!select || !window.QuizAI) return;
    select.innerHTML = Object.entries(QuizAI.PROVIDERS)
      .map(([id, p]) => `<option value="${id}">${escapeHtml(p.name)}</option>`)
      .join('');
  }

  function updateSettingsFormHints() {
    if (!window.QuizAI) return;
    const providerId = $('ai-provider').value;
    const info = QuizAI.getProviderInfo(providerId);
    $('ai-provider-hint').textContent = info.hint;
    $('ai-key-link').href = info.keyUrl;
    $('ai-default-model').textContent = `默认模型：${info.defaultModel}`;
  }

  function updateAiSourceHint() {
    const el = $('ai-source-hint');
    if (!el || !window.QuizAI) return;
    const label = QuizAI.getAiSourceLabel();
    if (QuizAI.isConfigured()) {
      el.textContent = `当前 AI 来源：${label}`;
      el.classList.remove('hidden');
      if (QuizAI.hasOwnKey()) {
        el.className = 'text-xs text-emerald-700';
      } else {
        el.className = 'text-xs text-gray-600';
      }
    } else {
      el.classList.add('hidden');
    }
  }

  function updateKeyCryptoUi() {
    if (!window.QuizAI) return;
    const s = QuizAI.loadSettings();
    const keyInput = $('ai-api-key');
    const unlockBlock = $('ai-unlock-block');
    const savedHint = $('ai-key-saved-hint');
    keyInput.value = '';

    if (s.keyEncrypted) {
      keyInput.placeholder = '已加密保存在本机（留空则不修改 Key）';
      if (!s.keyUnlocked) {
        unlockBlock.classList.remove('hidden');
      } else {
        unlockBlock.classList.add('hidden');
      }
      if (savedHint) {
        const hint = QuizAI.getStoredKeyHint();
        savedHint.textContent = hint;
        savedHint.classList.toggle('hidden', !hint);
      }
    } else if (s.keyStored) {
      keyInput.placeholder = '已保存在本机浏览器（留空则不修改，输入新 Key 可替换）';
      unlockBlock.classList.add('hidden');
      if (savedHint) {
        savedHint.textContent = QuizAI.getStoredKeyHint();
        savedHint.classList.remove('hidden');
      }
    } else {
      keyInput.placeholder = '粘贴你的 API Key';
      unlockBlock.classList.add('hidden');
      if (savedHint) savedHint.classList.add('hidden');
    }
  }

  function loadSettingsForm() {
    if (!window.QuizAI) return;
    const s = QuizAI.loadSettings();
    populateProviderSelect();
    $('ai-provider').value = s.provider;
    $('ai-api-key').value = '';
    $('ai-model').value = s.model;
    $('ai-encrypt-passphrase').value = '';
    $('ai-unlock-passphrase').value = '';
    updateSettingsFormHints();
    updateKeyCryptoUi();
    updateAiSourceHint();
    setSettingsStatus('', '');
  }

  function setSettingsStatus(text, type) {
    const el = $('ai-settings-status');
    el.textContent = text;
    el.className = 'text-sm';
    if (!text) {
      el.classList.add('hidden');
      return;
    }
    el.classList.remove('hidden');
    if (type === 'ok') el.classList.add('text-green-600');
    else if (type === 'err') el.classList.add('text-red-600');
    else el.classList.add('text-gray-600');
  }

  function openSettings() {
    loadSettingsForm();
    showPage('settings');
  }

  async function saveSettingsForm() {
    if (!window.QuizAI) return;
    try {
      await QuizAI.saveSettings({
        provider: $('ai-provider').value,
        apiKey: $('ai-api-key').value,
        model: $('ai-model').value,
        encryptPassphrase: $('ai-encrypt-passphrase').value,
      });
      $('ai-encrypt-passphrase').value = '';
      $('ai-api-key').value = '';
      updateKeyCryptoUi();
      updateAiSourceHint();
      const s = QuizAI.loadSettings();
      if (s.keyEncrypted) {
        setSettingsStatus('已保存到本机浏览器（Key 已加密）。刷新后需重新解锁', 'ok');
      } else if (s.keyStored) {
        setSettingsStatus('已保存到本机浏览器，关闭页面后仍会保留', 'ok');
      } else {
        setSettingsStatus('已保存服务商与模型设置', 'ok');
      }
    } catch (err) {
      setSettingsStatus(err.message || '保存失败', 'err');
    }
  }

  async function unlockSettingsKey() {
    if (!window.QuizAI) return;
    try {
      await QuizAI.unlockKey($('ai-unlock-passphrase').value);
      $('ai-unlock-passphrase').value = '';
      updateKeyCryptoUi();
      updateAiSourceHint();
      setSettingsStatus('解锁成功，本次访问可使用完整模式', 'ok');
    } catch (err) {
      setSettingsStatus(err.message, 'err');
    }
  }

  function clearSettingsForm() {
    if (!window.QuizAI) return;
    const hadKey = QuizAI.hasStoredKey();
    const hadModel = Boolean(String($('ai-model').value || QuizAI.loadSettings().model || '').trim());
    if (!hadKey && !hadModel) {
      setSettingsStatus('没有可清除的本机 Key 或模型', 'err');
      return;
    }
    if (
      !confirm(
        '确定清除本机浏览器中保存的 API Key 和模型吗？\n\n将删除 localStorage 中的全部相关数据，此操作不可恢复。'
      )
    ) {
      return;
    }

    $('ai-api-key').value = '';
    $('ai-model').value = '';
    $('ai-encrypt-passphrase').value = '';
    $('ai-unlock-passphrase').value = '';
    QuizAI.lockKey();
    const cleared = QuizAI.clearSavedCredentials();
    state.aiCache = {};
    state.aiAnalysisText = '';
    $('ai-analysis-panel')?.classList.add('hidden');
    $('ai-analysis-output').innerHTML = '';
    updateKeyCryptoUi();
    updateAiSourceHint();
    updateTestButtonLabel();

    if (cleared && QuizAI.isFullyCleared()) {
      if (QuizAI.isProxyAvailable()) {
        setSettingsStatus('已彻底清除本机 Key / 模型，将使用站点默认 AI', 'ok');
      } else {
        setSettingsStatus('已彻底清除本机 Key / 模型', 'ok');
      }
    } else {
      setSettingsStatus('清除未完成，请刷新页面后重试', 'err');
    }
  }

  function updateTestButtonLabel() {
    const btn = $('btn-test-ai');
    if (!btn) return;
    if (window.QuizAI && QuizAI.canUseOwnKey()) {
      btn.textContent = '测试连接';
      btn.disabled = false;
      return;
    }
    const left = state.testCooldownUntil - Date.now();
    if (left > 0) {
      btn.textContent = `请等待 ${Math.ceil(left / 1000)} 秒`;
      btn.disabled = true;
    } else {
      btn.textContent = '测试连接';
      btn.disabled = false;
    }
  }

  function startTestCooldown(seconds) {
    state.testCooldownUntil = Date.now() + seconds * 1000;
    updateTestButtonLabel();
    if (state.testCooldownTimer) clearInterval(state.testCooldownTimer);
    state.testCooldownTimer = setInterval(() => {
      if (Date.now() >= state.testCooldownUntil) {
        clearInterval(state.testCooldownTimer);
        state.testCooldownTimer = null;
        updateTestButtonLabel();
        return;
      }
      updateTestButtonLabel();
    }, 500);
  }

  async function testSettingsConnection() {
    if (!window.QuizAI) return;
    try {
      await QuizAI.saveSettings({
        provider: $('ai-provider').value,
        apiKey: $('ai-api-key').value,
        model: $('ai-model').value,
        encryptPassphrase: $('ai-encrypt-passphrase').value,
      });
    } catch (err) {
      setSettingsStatus(err.message || '保存失败', 'err');
      return;
    }
    if (!QuizAI.isConfigured()) {
      setSettingsStatus('请先填写 API Key，或启用站点默认 AI 代理', 'err');
      return;
    }
    try {
      await QuizAI.ensureUnlocked($('ai-unlock-passphrase').value);
    } catch (err) {
      setSettingsStatus(err.message, 'err');
      return;
    }
    if (!QuizAI.canUseOwnKey() && !QuizAI.isProxyAvailable()) {
      setSettingsStatus('请解锁 Key 或启用站点默认 AI', 'err');
      return;
    }
    if (!QuizAI.canUseOwnKey() && Date.now() < state.testCooldownUntil) {
      updateTestButtonLabel();
      setSettingsStatus('点击过快会触发站点 AI 限流，请稍候再试', 'err');
      return;
    }
    setSettingsStatus('正在测试连接…', '');
    $('btn-test-ai').disabled = true;
    try {
      const reply = await QuizAI.testConnection();
      $('ai-encrypt-passphrase').value = '';
      $('ai-api-key').value = '';
      updateKeyCryptoUi();
      updateAiSourceHint();
      setSettingsStatus(`连接成功：${reply.slice(0, 80)}`, 'ok');
      if (!QuizAI.canUseOwnKey()) {
        startTestCooldown(30);
      }
    } catch (err) {
      setSettingsStatus(err.message, 'err');
      if (!QuizAI.canUseOwnKey()) {
        const isRateLimit = /过于频繁|429|配额|quota|rate|速率限制/i.test(err.message);
        startTestCooldown(isRateLimit ? 120 : 15);
      }
    } finally {
      $('btn-test-ai').disabled = false;
      updateTestButtonLabel();
    }
  }

  function parseEssayTitle(title) {
    const text = decodeHtml(String(title || ''));
    const splitAt = text.indexOf('\n\n');
    if (splitAt === -1) {
      return { lead: text.trim(), passage: '' };
    }
    return {
      lead: text.slice(0, splitAt).trim(),
      passage: text.slice(splitAt + 2).trim(),
    };
  }

  function formatFillTitle(title) {
    return escapeHtml(decodeHtml(title)).replace(
      /_{2,}/g,
      '<span class="fill-blank"></span>'
    );
  }

  function formatQuestionTitle(q) {
    if (isFillQuestion(q)) return formatFillTitle(q.title);
    return escapeHtml(decodeHtml(q.title));
  }

  function questionTitleClass(q) {
    const base = 'text-base leading-relaxed font-medium mb-5';
    if (isEssayQuestion(q) || String(q.title || '').includes('\n')) {
      return `${base} whitespace-pre-line`;
    }
    return base;
  }

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function escapeAttr(str) {
    return escapeHtml(str).replace(/'/g, '&#39;');
  }

  function bindEvents() {
    $('btn-back').addEventListener('click', () => {
      if (state.wrongDrillMode && state.parentQuiz) {
        if (confirm('退出错词突击，返回成绩单？')) exitWrongDrill();
        return;
      }
      if (!state.submitted && Object.keys(state.answers).length > 0) {
        if (!confirm('返回将丢失当前作答，确定吗？')) return;
      }
      showPage('home');
    });

    $('btn-prev').addEventListener('click', () => {
      if (state.currentIndex > 0) {
        state.currentIndex--;
        renderQuestion();
        renderAnswerCard();
        updateProgress();
        if (window.matchMedia('(max-width: 1023px)').matches) scrollToQuestionArea();
      }
    });

    $('btn-next').addEventListener('click', () => {
      if (state.currentIndex < state.quiz.questions.length - 1) {
        state.currentIndex++;
        renderQuestion();
        renderAnswerCard();
        updateProgress();
        if (window.matchMedia('(max-width: 1023px)').matches) scrollToQuestionArea();
      }
    });

    $('btn-submit').addEventListener('click', submitQuiz);

    $('btn-review').addEventListener('click', () => {
      state.currentIndex = 0;
      showPage('quiz');
      renderQuestion();
      renderAnswerCard();
      updateProgress();
    });

    $('btn-retry').addEventListener('click', () => {
      state.currentIndex = 0;
      state.answers = {};
      state.submitted = false;
      state.aiCache = {};
      state.aiScoreCache = {};
      state.lastResult = null;
      state.wrongDrillMode = false;
      state.parentQuiz = null;
      $('wrong-drill-banner')?.classList.add('hidden');
      $('wrong-memory-panel')?.classList.add('hidden');
      showPage('quiz');
      renderQuestion();
      renderAnswerCard();
      updateProgress();
    });

    $('btn-home').addEventListener('click', () => {
      state.wrongDrillMode = false;
      state.parentQuiz = null;
      showPage('home');
    });

    $('btn-wrong-drill')?.addEventListener('click', startWrongDrill);
    $('btn-exit-drill')?.addEventListener('click', exitWrongDrill);

    $('btn-open-settings').addEventListener('click', openSettings);
    $('btn-settings-back').addEventListener('click', () => showPage('home'));
    $('ai-provider').addEventListener('change', updateSettingsFormHints);
    $('btn-save-settings').addEventListener('click', () => {
      saveSettingsForm();
    });
    $('btn-unlock-key').addEventListener('click', unlockSettingsKey);
    $('btn-clear-settings').addEventListener('click', clearSettingsForm);
    $('btn-test-ai').addEventListener('click', testSettingsConnection);
    $('btn-ai-analysis').addEventListener('click', () => {
      const force = window.QuizAI && QuizAI.hasOwnKey() && Boolean(state.aiAnalysisText);
      runAiAnalysis(force);
    });
    $('btn-close-analysis').addEventListener('click', toggleAnalysisCollapse);
  }

  async function init() {
    bindEvents();
    try {
      await loadManifest();
      renderQuizList();
    } catch (err) {
      $('load-error').textContent = err.message;
      $('load-error').classList.remove('hidden');
    }
  }

  init();
})();
