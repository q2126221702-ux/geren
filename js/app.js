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
  };

  const $ = (id) => document.getElementById(id);

  const pages = {
    home: $('page-home'),
    quiz: $('page-quiz'),
    result: $('page-result'),
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
      const user = String(userAnswer || '').trim();
      if (!user && (!Array.isArray(userAnswer) || userAnswer.length === 0)) {
        return { correct: false, score: 0, maxScore };
      }
      const ok =
        userAnswerToIndexList(userAnswer) === normalizeIndexList(q.correct_answer);
      return { correct: ok, score: ok ? maxScore : 0, maxScore };
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
          : '✗ 回答错误';
      const statusClass = !answered
        ? 'text-gray-500'
        : result.correct
          ? 'text-green-600'
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

    container.innerHTML = `
      <div class="flex items-center gap-2 mb-4">
        <span class="text-xs px-2 py-0.5 rounded bg-primary-light text-primary font-medium">${typeLabel(q.type)}</span>
        <span class="text-sm text-gray-400">${idx + 1} / ${state.quiz.questions.length}</span>
      </div>
      <h2 class="text-base leading-relaxed font-medium mb-5">${
        isFillQuestion(q) ? formatFillTitle(q.title) : escapeHtml(decodeHtml(q.title))
      }</h2>
      ${body}
      ${reviewBlock}`;

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
            state.answers[idx] = input.value;
            renderAnswerCard();
            updateProgress();
            if ((isChoiceQuestion(q) || isJudgmentQuestion(q)) && idx < state.quiz.questions.length - 1) {
              state.currentIndex = idx + 1;
              renderQuestion();
              renderAnswerCard();
              updateProgress();
            }
          });
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
          cls += result.correct ? ' correct' : ' wrong';
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
    const objectiveCount = state.quiz.questions.filter((q) => !isEssayQuestion(q)).length;

    $('result-score').textContent = score.toFixed(1).replace(/\.0$/, '');
    $('result-total').textContent = total.toFixed(1).replace(/\.0$/, '');
    const rate = total ? Math.round((score / total) * 100) : 0;
    $('result-rate').textContent =
      `答对 ${correctCount} / ${objectiveCount} 道客观题，得分率 ${rate}%（问答题不计分）`;
    showPage('result');
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
      showPage('quiz');
      renderQuestion();
      renderAnswerCard();
      updateProgress();
    });

    $('btn-home').addEventListener('click', () => showPage('home'));
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
