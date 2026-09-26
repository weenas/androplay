// Chinese or English: the visitor's choice if saved, else the browser's language.
(function () {
  var KEY = 'castbay-lang';
  function saved() {
    try { return localStorage.getItem(KEY); } catch (e) { return null; }
  }
  function save(lang) {
    try { localStorage.setItem(KEY, lang); } catch (e) { /* private mode: still switches */ }
  }
  function apply(lang) {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.documentElement.dataset.lang = lang;
    var title = document.querySelector('meta[name="title-' + lang + '"]');
    if (title) document.title = title.content;
  }
  var initial = saved() || ((navigator.language || '').toLowerCase().indexOf('zh') === 0 ? 'zh' : 'en');
  apply(initial);
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-switch-lang]').forEach(function (button) {
      button.addEventListener('click', function () {
        var next = document.documentElement.dataset.lang === 'zh' ? 'en' : 'zh';
        save(next);
        apply(next);
      });
    });
  });
})();
