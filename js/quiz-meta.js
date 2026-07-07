/**
 * 全站共享元数据：题库分类、版本号等。
 * 供 app.js、wrong-book.js 及测试脚本对齐使用。
 */
(function () {
  'use strict';

  const INDUSTRIAL_QUIZ_IDS = new Set([
    'profinet',
    'opc',
    'modbus',
    'serial',
    'comprehensive',
    'exam100',
  ]);

  function getQuizCategory(quizId) {
    return INDUSTRIAL_QUIZ_IDS.has(quizId) ? 'industrial' : 'english';
  }

  window.QuizMeta = {
    INDUSTRIAL_QUIZ_IDS,
    getQuizCategory,
  };
})();
