(function () {
  'use strict';

  const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';

  const state = {
    manifest: null,
    quiz: null,
    currentIndex: 0,
    answers: {},
    submitted: false,
    reviewMode: false,
    aiCache: {},
    lastResult: null,
    aiExplainLoading: false,
    aiAnalysisLoading: false,
    aiAnalysisText: '',
    aiAnalysisCollapsed: false,
    analysisCooldownUntil: 0,
    analysisCooldownTimer: null,
    testCooldownUntil: 0,
    testCooldownTimer: null,
  };

  const $ = (id) => document.getElementById(id);

  const pages = {
    home: $('page-home'),
    quiz: $('page-quiz'),
    result: $('page-result'),
    settings: $('page-settings'),
  };

  function showPage(name) {
    Object.values(pages).forEach((el) => el.classList.add('hidden'));
    pages[name].classList.remove('hidden');
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

      const expectedParts = String(q.correct_answer)
        .split(/[；;]/)
        .map((p) => simplifyAnswer(p))
        .filter(Boolean);

      const userParts = userNorm.includes(';')
        ? userNorm.split(';').map((p) => simplifyAnswer(p)).filter(Boolean)
        : [simplifyAnswer(userNorm)];

      const matchPart = (expected, user) => {
        if (!expected || !user) return false;
        if (user === expected) return true;
        if (user.includes(expected)) return true;
        if (expected.startsWith(user) && user.length >= 2) return true;
        return (
          expected.includes(user) &&
          user.length >= 2 &&
          user.length >= expected.length * 0.3
        );
      };

      const ok =
        expectedParts.length > 1
          ? expectedParts.every((expected) =>
              userParts.some((user) => matchPart(expected, user))
            )
          : matchPart(expectedParts[0], userParts[0]);

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
        data-file="${q.file}"
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
        state.reviewMode = false;
        state.aiCache = {};
        state.lastResult = null;
        $('quiz-title').textContent = state.quiz.title;
        $('total-count').textContent = state.quiz.questions.length;
        showPage('quiz');
        renderQuestion();
        renderAnswerCard();
        updateProgress();
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
      return q.options[idx] ? `${indexToLetter(q.correct_answer)}. ${q.options[idx]}` : q.correct_answer;
    }
    if (isMultiChoiceQuestion(q)) {
      return normalizeIndexList(q.correct_answer)
        .split(',')
        .map((i) => {
          const letter = indexToLetter(i);
          const text = q.options[Number(i)] || '';
          return `${letter}. ${text}`;
        })
        .join('；');
    }
    return q.correct_answer;
  }

  function advanceToNextQuestion(idx) {
    if (idx < state.quiz.questions.length - 1) {
      state.currentIndex = idx + 1;
      renderQuestion();
      renderAnswerCard();
      updateProgress();
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

    if (isSelectableQuestion(q)) {
      body = renderSelectableOptions(q, idx, review);
    } else if (isFillQuestion(q)) {
      const val = state.answers[idx] || '';
      body = `
        <input
          type="text"
          id="fill-input"
          value="${escapeAttr(val)}"
          placeholder="请输入答案"
          ${disabled}
          class="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
        />`;
    } else if (isEssayQuestion(q)) {
      const val = state.answers[idx] || '';
      body = `
        <textarea
          id="essay-input"
          rows="6"
          placeholder="请输入你的回答（问答题需自行对照参考答案）"
          ${disabled}
          class="w-full border border-gray-300 rounded-lg px-4 py-3 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary resize-y"
        >${escapeHtml(val)}</textarea>`;
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
          <p class="text-sm text-gray-600 mt-1">正确答案：${escapeHtml(formatCorrectAnswer(q))}</p>
        </div>`;
    } else if (review && isEssayQuestion(q)) {
      reviewBlock = `
        <div class="mt-5 pt-5 border-t border-gray-100">
          <p class="text-sm text-gray-500 mb-1">参考答案</p>
          <p class="text-sm text-gray-700 bg-gray-50 rounded-lg p-3">${escapeHtml(q.correct_answer)}</p>
        </div>`;
    }

    let aiBlock = '';
    if (review && window.QuizAI) {
      const cached = state.aiCache[idx];
      const showOutput = Boolean(cached || state.aiExplainLoading);
      aiBlock = `
        <div class="mt-4 pt-4 border-t border-gray-100">
          <button type="button" id="btn-ai-explain" class="text-sm px-4 py-2 rounded bg-violet-600 text-white hover:bg-violet-700 disabled:opacity-50" ${state.aiExplainLoading ? 'disabled' : ''}>
            ${state.aiExplainLoading ? '解析生成中…' : cached ? '重新生成 AI 解析' : 'AI 解析'}
          </button>
          <div id="ai-explain-output" class="${showOutput ? 'ai-output mt-3 text-sm text-gray-700 bg-violet-50 rounded-lg p-4 border border-violet-100' : 'hidden'}">${cached ? QuizAI.formatAiHtml(cached) : ''}</div>
        </div>`;
    }

    container.innerHTML = `
      <div class="flex items-center gap-2 mb-4">
        <span class="text-xs px-2 py-0.5 rounded bg-primary-light text-primary font-medium">${typeLabel(q.type)}</span>
        <span class="text-sm text-gray-400">${idx + 1} / ${state.quiz.questions.length}</span>
      </div>
      <h2 class="text-base leading-relaxed font-medium mb-5">${
        isFillQuestion(q) ? formatFillTitle(q.title) : escapeHtml(decodeHtml(q.title))
      }</h2>
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
        let cls = 'answer-card-btn w-9 h-9 rounded border border-gray-200 text-sm hover:border-primary';
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

    $('result-score').textContent = score.toFixed(1).replace(/\.0$/, '');
    $('result-total').textContent = total.toFixed(1).replace(/\.0$/, '');
    const rate = total ? Math.round((score / total) * 100) : 0;
    let rateText = `全对 ${correctCount} 题`;
    if (partialCount > 0) rateText += `，部分正确 ${partialCount} 题`;
    rateText += ` / 客观题 ${objectiveCount} 题，得分率 ${rate}%（问答题不计分；多选题按高考数学规则计分）`;
    $('result-rate').textContent = rateText;

    state.lastResult = {
      score: score.toFixed(1).replace(/\.0$/, ''),
      total: total.toFixed(1).replace(/\.0$/, ''),
      rate,
      correctCount,
      partialCount,
      objectiveCount,
    };

    $('ai-analysis-panel').classList.add('hidden');
    $('ai-analysis-output').innerHTML = '';
    state.aiAnalysisText = '';
    state.aiAnalysisCollapsed = false;
    updateAnalysisCollapseBtn();
    updateAnalysisButtonLabel();
    updateAnalysisModeBadge();
    showPage('result');
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
    state.aiExplainLoading = true;
    renderQuestion();

    let outputEl = $('question-container').querySelector('#ai-explain-output');
    if (outputEl) {
      outputEl.classList.remove('hidden');
      outputEl.innerHTML = '<span class="text-gray-500 ai-loading">正在生成解析</span>';
    }

    try {
      const text = await QuizAI.explainQuestion(
        q,
        state.answers[idx],
        gradeResult,
        (partial) => {
          if (outputEl) {
            outputEl.innerHTML = QuizAI.formatAiHtml(partial);
          }
        }
      );
      state.aiCache[idx] = text;
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
    if (s.keyEncrypted) {
      keyInput.placeholder = '已加密保存（留空则不修改 Key）';
      if (!s.keyUnlocked) {
        unlockBlock.classList.remove('hidden');
      } else {
        unlockBlock.classList.add('hidden');
      }
    } else {
      keyInput.placeholder = '粘贴你的 API Key';
      unlockBlock.classList.add('hidden');
    }
  }

  function loadSettingsForm() {
    if (!window.QuizAI) return;
    const s = QuizAI.loadSettings();
    populateProviderSelect();
    $('ai-provider').value = s.provider;
    $('ai-api-key').value = s.apiKey;
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
        setSettingsStatus('已保存（Key 已加密）。刷新后需重新解锁', 'ok');
      } else {
        setSettingsStatus('已保存到本机浏览器', 'ok');
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
    const hadKey = $('ai-api-key').value.trim() !== '' || QuizAI.hasStoredKey();
    const hadModel = $('ai-model').value.trim() !== '';
    if (!hadKey && !hadModel && !QuizAI.isConfigured()) {
      setSettingsStatus('没有可清除的内容', 'err');
      return;
    }
    $('ai-api-key').value = '';
    $('ai-model').value = '';
    $('ai-encrypt-passphrase').value = '';
    $('ai-unlock-passphrase').value = '';
    QuizAI.clearSavedCredentials();
    updateKeyCryptoUi();
    updateAiSourceHint();
    if (QuizAI.isProxyAvailable()) {
      setSettingsStatus('已清除自带 Key / 模型，将使用站点默认 AI', 'ok');
    } else {
      setSettingsStatus('已清除 API Key 和模型设置', 'ok');
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

  function formatFillTitle(title) {
    return escapeHtml(decodeHtml(title)).replace(
      /_{2,}/g,
      '<span class="fill-blank"></span>'
    );
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
      }
    });

    $('btn-next').addEventListener('click', () => {
      if (state.currentIndex < state.quiz.questions.length - 1) {
        state.currentIndex++;
        renderQuestion();
        renderAnswerCard();
        updateProgress();
      }
    });

    $('btn-submit').addEventListener('click', submitQuiz);

    $('btn-review').addEventListener('click', () => {
      state.reviewMode = true;
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
      state.reviewMode = false;
      state.aiCache = {};
      state.lastResult = null;
      showPage('quiz');
      renderQuestion();
      renderAnswerCard();
      updateProgress();
    });

    $('btn-home').addEventListener('click', () => showPage('home'));

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
