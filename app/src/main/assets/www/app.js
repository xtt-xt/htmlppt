/* =========================================================
   HTML PPT App - 共享逻辑
   依赖 window.Android (JS Bridge)
   ========================================================= */
window.PPT = (function () {
  var S = window.Android; // 原生桥
  var settings = {};
  var themes = {}; // name -> {c1,c2,c3,accent,text,muted}

  /* ---------- 内置主题 ---------- */
  var builtin = [
    { name: "极光",  key: "aurora", c1: "#0f2027", c2: "#203a43", c3: "#2c5364", accent: "#4fc3f7", text: "#f5f7fa", muted: "#c0ccd6" },
    { name: "海洋",  key: "ocean",  c1: "#0a2540", c2: "#114477", c3: "#1a6aa0", accent: "#3fd0a6", text: "#eaf7f3", muted: "#bcd9d2" },
    { name: "日落",  key: "sunset", c1: "#2d1b4e", c2: "#6b3b6e", c3: "#c05a57", accent: "#ffd479", text: "#fff6e8", muted: "#e9d7c4" },
    { name: "森林",  key: "forest", c1: "#0f2f24", c2: "#1d4f39", c3: "#2f7a4c", accent: "#9be15d", text: "#f0f7ef", muted: "#c6dbc6" },
    { name: "夜黑",  key: "night",  c1: "#111", c2: "#1a1a1a", c3: "#222", accent: "#ff6b6b", text: "#f5f5f5", muted: "#9a9a9a" },
    { name: "纸白",  key: "paper",  c1: "#f7f7f7", c2: "#eeeeee", c3: "#e4e4e4", accent: "#e04a4a", text: "#222", muted: "#666" }
  ];

  /* ---------- URL / 本地图片 ---------- */
  function toAbs(p) {
    if (!p) return "";
    if (/^https?:/i.test(p)) return p;
    if (/^file:\/\//i.test(p)) return p;
    if (/^\/storage\//.test(p)) return "file://" + p;
    if (/^\/sdcard\//.test(p)) return "file://" + p.replace(/^\/sdcard\//, "/storage/emulated/0/");
    return p;
  }

  /* ---------- 行内格式 ---------- */
  function inline(t) {
    t = String(t).replace(/!\[([^\]]*)\]\(([^)]+)\)/g, function (m, alt, url) {
      return '<figure><img src="' + toAbs(url) + '" alt="' + alt + '" loading="lazy"><figcaption>' + alt + '</figcaption></figure>';
    });
    t = t.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="' + toAbs('$2') + '" target="_blank" rel="noopener">$1</a>');
    t = t.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    t = t.replace(/`([^`]+)`/g, "<code>$1</code>");
    t = t.replace(/\*([^*\n]+?)\*/g, "<em>$1</em>");
    return t;
  }

  /* ---------- 解析 ---------- */
  function parse(input) {
    var lines = String(input || "").replace(/\r\n/g, "\n").split("\n");
    var pages = [], cur = [];
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      if (/^\s*-{3,}\s*$/.test(line)) { if (cur.length) { pages.push(cur); cur = []; } }
      else cur.push(line);
    }
    if (cur.length) pages.push(cur);

    var out = [];
    pages.forEach(function (pl) { var p = parsePage(pl); if (p) out.push(p); });
    return out;
  }

  var lastIdx = 0;
  function parsePage(lines) {
    var centered = false, footer = "", theme = null, body = [];
    lines.forEach(function (l) {
      var t = l.trim();
      if (/^@center\s*$/.test(t)) { centered = true; return; }
      var fm = t.match(/^@t\((.*)\)\s*$/); if (fm) { footer = fm[1]; return; }
      var th = t.match(/^@theme\(\s*([^)]+)\s*\)\s*$/); if (th) { theme = th[1].trim(); return; }
      body.push(l);
    });
    if (body.every(function (b) { return b.trim() === ""; })) return null;

    var title = null, subtitle = null, blocks = [];
    for (var i = 0; i < body.length; i++) {
      var line = body[i], t = line.trim();
      if (!t) continue;
      if (/^#{1,3}\s/.test(t)) {
        var lvl = t.match(/^#+/)[0].length;
        var txt = t.replace(/^#+\s*/, "");
        if (lvl === 1 && title === null && !subtitle) title = txt;
        else if (lvl === 2 && subtitle === null && title !== null) subtitle = txt;
        else blocks.push({ type: "h" + lvl, text: inline(txt) });
        continue;
      }
      if (/^!\[/.test(t)) { blocks.push({ type: "img", text: inline(t) }); continue; }
      if (/^\s*[-*]\s+/.test(t)) { collectList(body, i, "-", blocks); i = lastIdx; continue; }
      if (/^\s*\d+\.\s+/.test(t)) { collectList(body, i, "1.", blocks); i = lastIdx; continue; }
      if (/^>\s?/.test(t)) {
        var q = [], j = i;
        while (j < body.length && /^>\s?/.test(body[j])) { q.push(body[j].replace(/^>\s?/, "")); j++; }
        blocks.push({ type: "quote", text: inline(q.join("<br>")) });
        i = j - 1; continue;
      }
      blocks.push({ type: "p", text: inline(t) });
    }
    return { title: title, subtitle: subtitle, blocks: blocks, centered: centered, footer: footer, theme: theme };
  }

  function collectList(lines, start, kind, blocks) {
    lastIdx = start;
    var items = [], j = start;
    while (j < lines.length) {
      var line = lines[j];
      if (line.trim() === "") { j++; continue; }
      if (kind === "-" && /\s*[-*]\s+/.test(line)) { items.push(inline(line.replace(/^\s*[-*]\s+/, ""))); j++; }
      else if (kind === "1." && /\s*\d+\.\s+/.test(line)) { items.push(inline(line.replace(/^\s*\d+\.\s+/, ""))); j++; }
      else break;
    }
    lastIdx = j - 1;
    blocks.push({ type: kind === "-" ? "ul" : "ol", items: items });
  }

  /* ---------- 主题加载 ---------- */
  function buildThemeMap() {
    themes = {};
    builtin.forEach(function (t) { themes[t.name] = t; themes[t.key] = t; });
    try {
      if (settings.customThemes) {
        Object.keys(settings.customThemes).forEach(function (k) {
          if (settings.customThemes[k]) themes[k] = settings.customThemes[k];
        });
      }
    } catch (e) { }
  }
  function getTheme(name) {
    if (!name) return builtin[0];
    return themes[name] || builtin[0];
  }
  function setVarsOnEl(el, t) {
    el.style.setProperty("--bg1", t.c1);
    el.style.setProperty("--bg2", t.c2);
    el.style.setProperty("--bg3", t.c3);
    el.style.setProperty("--accent", t.accent);
    el.style.setProperty("--text", t.text);
    el.style.setProperty("--muted", t.muted);
  }
  function applyGlobalTheme(name) {
    var t = getTheme(name);
    var r = document.documentElement.style;
    r.setProperty("--bg1", t.c1); r.setProperty("--bg2", t.c2); r.setProperty("--bg3", t.c3);
    r.setProperty("--accent", t.accent); r.setProperty("--text", t.text); r.setProperty("--muted", t.muted);
    document.body.style.background = "linear-gradient(135deg," + t.c1 + "," + t.c2 + "," + t.c3 + ")";
  }

  /* ---------- settings 持久化 ---------- */
  function loadSettings() {
    try { settings = JSON.parse(S.readSettings()) || {}; } catch (e) { settings = {}; }
    if (!settings.customThemes || typeof settings.customThemes !== "object") settings.customThemes = {};
    buildThemeMap();
    loadCustomFromFiles();
  }
  function saveSettings() { S.saveSettings(JSON.stringify(settings)); }
  function loadCustomFromFiles() {
    settings.customThemes = settings.customThemes || {};
    try {
      var files = JSON.parse(S.listThemes()) || [];
      files.forEach(function (f) {
        if (f.toLowerCase().endsWith(".json")) {
          try {
            var o = JSON.parse(S.readThemeFile(f));
            var nm = f.replace(/\.json$/i, "");
            settings.customThemes[nm] = o;
          } catch (e) { }
        }
      });
    } catch (e) { }
    buildThemeMap();
  }
  function saveCustomTheme(name, t) {
    settings.customThemes = settings.customThemes || {};
    settings.customThemes[name] = t;
    saveSettings();
    try { S.saveThemeFile(name + ".json", JSON.stringify(t)); } catch (e) { }
    buildThemeMap();
  }
  function deleteCustomTheme(name) {
    if (settings.customThemes) delete settings.customThemes[name];
    saveSettings();
    try { S.deleteThemeFile(name + ".json"); } catch (e) { }
    buildThemeMap();
  }
  function listThemeNames() {
    var arr = builtin.map(function (t) { return t; });
    Object.keys(settings.customThemes || {}).forEach(function (k) {
      var t = settings.customThemes[k];
      if (t) arr.push({ name: k, key: k, c1: t.c1, c2: t.c2, c3: t.c3, accent: t.accent, text: t.text, muted: t.muted });
    });
    return arr;
  }

  /* ---------- 渲染幻灯片到容器 ---------- */
  function renderSlides(container, slides, activeIdx) {
    container.innerHTML = "";
    slides.forEach(function (s) {
      var el = document.createElement("div");
      el.className = "slide" + (s.centered ? " centered" : "");
      if (s.theme) setVarsOnEl(el, getTheme(s.theme));
      var html = "";
      if (s.title) html += '<div class="s-title">' + inline(s.title) + '</div>';
      if (s.subtitle) html += '<div class="s-sub">' + inline(s.subtitle) + '</div>';
      html += '<div class="s-body">';
      s.blocks.forEach(function (b) {
        if (b.type === "p") html += '<div class="block"><p>' + b.text + '</p></div>';
        else if (b.type === "h1") html += '<div class="block"><h1>' + b.text + '</h1></div>';
        else if (b.type === "h2") html += '<div class="block"><h2>' + b.text + '</h2></div>';
        else if (b.type === "h3") html += '<div class="block"><h3>' + b.text + '</h3></div>';
        else if (b.type === "ul") html += '<div class="block"><ul>' + b.items.map(function (x) { return '<li>' + x + '</li>'; }).join("") + '</ul></div>';
        else if (b.type === "ol") html += '<div class="block"><ol>' + b.items.map(function (x) { return '<li>' + x + '</li>'; }).join("") + '</ol></div>';
        else if (b.type === "quote") html += '<div class="block"><blockquote>' + b.text + '</blockquote></div>';
        else if (b.type === "img") html += '<div class="block">' + b.text + '</div>';
      });
      html += '</div>';
      if (s.footer) html += '<div class="footer-mark">' + inline(s.footer) + '</div>';
      el.innerHTML = html;
      container.appendChild(el);
    });
    activate(container, activeIdx || 0);
  }

  function activate(container, idx) {
    var els = container.querySelectorAll(".slide");
    if (!els.length) return;
    idx = Math.max(0, Math.min(els.length - 1, idx));
    els.forEach(function (e, i) { e.classList.toggle("active", i === idx); });
    var info = document.getElementById("pageinfo");
    if (info) info.textContent = (idx + 1) + " / " + els.length;
    var prog = document.getElementById("progress");
    if (prog) { var sp = prog.firstElementChild; if (sp) sp.style.width = (els.length <= 1 ? 100 : ((idx + 1) / els.length * 100)) + "%"; }
    return idx;
  }

  /* ---------- 公开 ---------- */
  return {
    get settings() { return settings; },
    builtin: builtin,
    S: S || {},
    inline: inline,
    parse: parse,
    toAbs: toAbs,
    buildThemeMap: buildThemeMap,
    getTheme: getTheme,
    setVarsOnEl: setVarsOnEl,
    applyGlobalTheme: applyGlobalTheme,
    loadSettings: loadSettings,
    saveSettings: saveSettings,
    loadCustomFromFiles: loadCustomFromFiles,
    saveCustomTheme: saveCustomTheme,
    deleteCustomTheme: deleteCustomTheme,
    listThemeNames: listThemeNames,
    renderSlides: renderSlides,
    activate: activate
  };
})();

/* 全局确认/提示框：WebView 默认 confirm/alert 不显示（返回 false），这里用自定义弹窗替代 */
window.auroraConfirm = function(msg, cb){
  var ov=document.createElement('div'); ov.className='modal'; ov.style.display='flex'; ov.style.zIndex='999';
  var box=document.createElement('div'); box.className='box';
  var h=document.createElement('h2'); h.textContent='确认';
  var p=document.createElement('p'); p.textContent=msg; p.style.cssText='color:var(--muted);margin:6px 0 14px;';
  var act=document.createElement('div'); act.className='actions';
  var c=document.createElement('button'); c.className='tbtn ghost'; c.textContent='取消';
  var o=document.createElement('button'); o.className='tbtn primary'; o.textContent='确定';
  act.appendChild(c); act.appendChild(o);
  box.appendChild(h); box.appendChild(p); box.appendChild(act);
  ov.appendChild(box); document.body.appendChild(ov);
  c.onclick=function(){ ov.remove(); };
  o.onclick=function(){ ov.remove(); cb(); };
  ov.addEventListener('click', function(e){ if(e.target===ov) ov.remove(); });
};
window.auroraAlert = function(msg){
  var ov=document.createElement('div'); ov.className='modal'; ov.style.display='flex'; ov.style.zIndex='999';
  var box=document.createElement('div'); box.className='box';
  var h=document.createElement('h2'); h.textContent='提示';
  var p=document.createElement('p'); p.textContent=msg; p.style.cssText='color:var(--muted);margin:6px 0 14px;';
  var act=document.createElement('div'); act.className='actions';
  var o=document.createElement('button'); o.className='tbtn primary'; o.textContent='确定';
  act.appendChild(o);
  box.appendChild(h); box.appendChild(p); box.appendChild(act);
  ov.appendChild(box); document.body.appendChild(ov);
  o.onclick=function(){ ov.remove(); };
  ov.addEventListener('click', function(e){ if(e.target===ov) ov.remove(); });
};
/* 页面切换：淡出后跳转 */
window.pageNav = function(url){
  document.body.classList.add('fade-out');
  setTimeout(function(){ location.href = url; }, 100);
};

/* ---- 每天首次打开自动检查更新 ---- */
window.HOT_VERSION = window.HOT_VERSION || '2026.08.26.4';
window.CHECK_URLS = [
  'https://cdn.jsdelivr.net/gh/xtt-xt/htmlppt@main/update/latest.json',
  'https://raw.githubusercontent.com/xtt-xt/htmlppt/main/update/latest.json'
];
function __appVer(){ if(window.Android && window.Android.getVersion){ try{ return JSON.parse(window.Android.getVersion()).app || '1.0.0'; }catch(e){} } return '1.0.0'; }
window.autoCheckUpdate = function(){
  try { var t = localStorage.getItem('hp_last_check'); var today = new Date().toISOString().slice(0,10); if(t === today) return; localStorage.setItem('hp_last_check', today); } catch(e){}
  var and = window.Android;
  if(!and || !and.checkRemote) return;
  var body = '';
  for(var i=0;i<window.CHECK_URLS.length;i++){ body = and.checkRemote(window.CHECK_URLS[i]); if(body) break; }
  if(!body || body.indexOf('ERR:')===0) return;
  var info; try { info = JSON.parse(body); } catch(e){ return; }
  var appVer = __appVer(), curHot = window.HOT_VERSION;
  if(info.app !== appVer){
    auroraConfirm('发现新主版本 v' + info.app + '（当前 v' + appVer + '），是否前往下载？', function(){ if(and.openUrl) and.openUrl(info.apk_url || ''); });
  } else if(info.hot && info.hot !== curHot){
    auroraConfirm('发现热更新 h' + info.hot + '（当前 h' + curHot + '）\n\n【更新内容】\n' + info.notes + '\n\n是否下载并应用？', function(){
      var r = and.downloadApply(info.hot_url || '', '/sdcard/HTML_PPT/www');
      if(r && r.indexOf('ok:') === 0){ auroraAlert('更新成功：' + r); setTimeout(function(){ if(and.goToHome) and.goToHome(); else location.reload(); }, 600); }
      else { auroraAlert('下载失败：' + r); }
    });
  }
};
