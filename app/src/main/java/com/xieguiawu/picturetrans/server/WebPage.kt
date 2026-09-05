package com.xieguiawu.picturetrans.server

/**
 * PC 浏览器单页应用（无外部依赖，离线可用）。
 * 注意：本字符串内不允许出现 `$` 字符（Kotlin raw string 转义陷阱）。
 */
object WebPage {

    const val HTML: String = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Picture Trans</title>
<style>
:root { --bg:#f5f6f8; --card:#fff; --ink:#1c1e21; --muted:#65676b; --line:#e4e6eb; --accent:#1b6ef3; }
* { box-sizing:border-box; margin:0; padding:0; }
body { font-family:system-ui,-apple-system,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif; background:var(--bg); color:var(--ink); }
header { position:sticky; top:0; z-index:10; background:var(--accent); color:#fff; padding:14px 18px; }
header h1 { font-size:18px; font-weight:600; }
header p { font-size:12px; opacity:.85; margin-top:2px; }
nav { display:flex; gap:4px; padding:10px 12px 0; max-width:1080px; margin:0 auto; }
nav button { flex:1; border:none; background:transparent; padding:10px 8px; font-size:15px; color:var(--muted); border-bottom:2px solid transparent; cursor:pointer; }
nav button.on { color:var(--accent); border-bottom-color:var(--accent); font-weight:600; }
main { max-width:1080px; margin:0 auto; padding:12px; }
.toolbar { display:flex; align-items:center; gap:10px; margin:6px 0 12px; flex-wrap:wrap; }
.toolbar .count { color:var(--muted); font-size:13px; }
button.primary { background:var(--accent); color:#fff; border:none; border-radius:8px; padding:8px 14px; font-size:14px; cursor:pointer; }
button.ghost { background:var(--card); color:var(--ink); border:1px solid var(--line); border-radius:8px; padding:8px 14px; font-size:14px; cursor:pointer; }
button:disabled { opacity:.5; cursor:default; }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:10px; }
.card { background:var(--card); border:1px solid var(--line); border-radius:10px; overflow:hidden; display:flex; flex-direction:column; }
.card .thumb { position:relative; aspect-ratio:1/1; background:#eceef1; display:flex; align-items:center; justify-content:center; font-size:40px; }
.card img.thumbimg { position:absolute; inset:0; width:100%; height:100%; object-fit:cover; cursor:pointer; }
.card .meta { padding:8px 10px; font-size:12px; color:var(--muted); }
.card .name { color:var(--ink); font-size:13px; word-break:break-all; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; min-height:34px; }
.card .row { display:flex; align-items:center; justify-content:space-between; padding:0 8px 8px; }
.card input[type=checkbox] { width:17px; height:17px; accent-color:var(--accent); }
.badge { position:absolute; left:8px; top:8px; background:rgba(0,0,0,.55); color:#fff; font-size:11px; padding:2px 6px; border-radius:6px; }
.dl { border:none; background:#eef4ff; color:var(--accent); width:30px; height:30px; border-radius:8px; cursor:pointer; font-size:15px; }
.empty { text-align:center; color:var(--muted); padding:48px 0; font-size:14px; }
#drop { border:2px dashed var(--line); border-radius:12px; background:var(--card); padding:36px 16px; text-align:center; color:var(--muted); cursor:pointer; }
#drop.over { border-color:var(--accent); background:#f0f6ff; }
#queue { margin-top:12px; display:flex; flex-direction:column; gap:8px; }
.qitem { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:8px 12px; font-size:13px; word-break:break-all; }
.qbar { height:6px; background:#eceef1; border-radius:3px; margin-top:6px; overflow:hidden; }
.qbar>div { height:100%; background:var(--accent); width:0; transition:width .2s; }
.qdone { color:#1a7f37; } .qerr { color:#c62828; }
.hidden { display:none; }
</style>
</head>
<body>
<header>
  <h1>Picture Trans</h1>
  <p>手机与电脑已在同一局域网 · 保持本页打开即可互传</p>
</header>
<nav>
  <button data-tab="images" class="on">图片</button>
  <button data-tab="videos">视频</button>
  <button data-tab="downloads">文件</button>
  <button data-tab="upload">上传</button>
</nav>
<main>
  <section id="browse">
    <div class="toolbar">
      <label style="display:flex;align-items:center;gap:6px;font-size:13px;"><input type="checkbox" id="selAll"> 全选</label>
      <span class="count" id="count"></span>
      <span style="flex:1"></span>
      <button class="ghost" id="refresh">刷新</button>
      <button class="primary" id="dlSel" disabled>下载所选</button>
    </div>
    <div class="grid" id="grid"></div>
    <div class="empty hidden" id="empty">这里还没有内容</div>
  </section>
  <section id="upload" class="hidden">
    <div id="drop">把文件拖到这里，或点击选择<br><span style="font-size:12px">支持多选 · 图片和视频自动进手机相册，其他进下载目录</span></div>
    <input type="file" id="file" multiple class="hidden">
    <div id="queue"></div>
  </section>
</main>
<script>
"use strict";
var BASE = location.pathname;
var API = BASE + "api/list?c=";
var THUMB = BASE + "thumb?id=";
var FILE = BASE + "file?id=";
var UPLOAD = BASE + "upload";
var state = { tab: "images", items: [], selected: {} };

function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; }
function fmtSize(b) { if (b < 1024) return b + " B"; if (b < 1048576) return (b/1024).toFixed(1) + " KB"; if (b < 1073741824) return (b/1048576).toFixed(1) + " MB"; return (b/1073741824).toFixed(2) + " GB"; }
function fmtDate(s) { var d = new Date(s); return d.getFullYear() + "-" + String(d.getMonth()+1).padStart(2,"0") + "-" + String(d.getDate()).padStart(2,"0"); }
function fmtDur(ms) { var s = Math.round(ms/1000); return Math.floor(s/60) + ":" + String(s%60).padStart(2,"0"); }
function isMedia(c) { return c === "images" || c === "videos"; }

function loadTab(tab) {
  state.tab = tab; state.selected = {};
  document.getElementById("selAll").checked = false;
  var grid = document.getElementById("grid");
  grid.textContent = "";
  var empty = document.getElementById("empty");
  empty.classList.add("hidden"); empty.textContent = "这里还没有内容";
  var want = tab;
  fetch(API + tab).then(function(r){ return r.json(); }).then(function(items){
    if (state.tab !== want) return;
    state.items = items;
    if (!items.length) { empty.classList.remove("hidden"); updateBar(); return; }
    items.forEach(function(it){ grid.appendChild(card(it)); });
    updateBar();
  }).catch(function(){
    empty.textContent = "加载失败，请点刷新重试";
    empty.classList.remove("hidden");
  });
}

function card(it) {
  var c = el("div", "card"); c.dataset.id = it.id;
  var thumb = el("div", "thumb");
  if (isMedia(state.tab)) {
    var img = document.createElement("img");
    img.className = "thumbimg"; img.loading = "lazy";
    img.src = THUMB + it.id + "&c=" + state.tab + "&s=320";
    img.onclick = function(){ window.open(fileUrl(it, false)); };
    img.onerror = function(){ img.remove(); };
    thumb.appendChild(img);
  } else {
    var icon = "\uD83D\uDCC4";
    if (it.mimeType && it.mimeType.indexOf("image/") === 0) icon = "\uD83D\uDDBC";
    if (it.mimeType && it.mimeType.indexOf("video/") === 0) icon = "\uD83C\uDFAC";
    thumb.appendChild(el("div", null, icon));
  }
  if (it.durationMs > 0) thumb.appendChild(el("div", "badge", fmtDur(it.durationMs)));
  c.appendChild(thumb);
  var meta = el("div", "meta");
  meta.appendChild(el("div", "name", it.displayName));
  meta.appendChild(el("div", null, fmtSize(it.sizeBytes) + " · " + fmtDate(it.dateModifiedMs)));
  c.appendChild(meta);
  var row = el("div", "row");
  var cb = document.createElement("input"); cb.type = "checkbox";
  cb.onchange = function(){ if (cb.checked) state.selected[it.id] = true; else delete state.selected[it.id]; updateBar(); };
  row.appendChild(cb);
  var dl = el("button", "dl", "\u2B07");
  dl.title = "下载";
  dl.onclick = function(){ triggerDownload(it); };
  row.appendChild(dl);
  c.appendChild(row);
  return c;
}

function fileUrl(it, dl) { return FILE + it.id + "&c=" + state.tab + (dl ? "&dl=1" : ""); }
function triggerDownload(it) {
  var a = document.createElement("a");
  a.href = fileUrl(it, true); a.download = it.displayName;
  document.body.appendChild(a); a.click(); a.remove();
}

function updateBar() {
  var n = Object.keys(state.selected).length;
  document.getElementById("count").textContent = "共 " + state.items.length + " 项";
  var btn = document.getElementById("dlSel");
  btn.disabled = n === 0;
  btn.textContent = n ? "下载所选 (" + n + ")" : "下载所选";
}

document.getElementById("selAll").onchange = function() {
  var on = document.getElementById("selAll").checked;
  state.selected = {};
  if (on) state.items.forEach(function(it){ state.selected[it.id] = true; });
  document.querySelectorAll("#grid input[type=checkbox]").forEach(function(cb){ cb.checked = on; });
  updateBar();
};
document.getElementById("refresh").onclick = function(){ loadTab(state.tab); };
document.getElementById("dlSel").onclick = function() {
  var sel = state.items.filter(function(it){ return state.selected[it.id]; });
  sel.forEach(function(it, i){ setTimeout(function(){ triggerDownload(it); }, i * 400); });
};

document.querySelectorAll("nav button").forEach(function(b){
  b.onclick = function(){
    document.querySelectorAll("nav button").forEach(function(x){ x.classList.remove("on"); });
    b.classList.add("on");
    var t = b.dataset.tab;
    document.getElementById("browse").classList.toggle("hidden", t === "upload");
    document.getElementById("upload").classList.toggle("hidden", t !== "upload");
    if (t !== "upload") loadTab(t);
  };
});

var drop = document.getElementById("drop"), fileInput = document.getElementById("file"), queue = document.getElementById("queue");
drop.onclick = function(){ fileInput.click(); };
fileInput.onchange = function(){ enqueue(Array.prototype.slice.call(fileInput.files)); fileInput.value = ""; };
["dragover","dragenter"].forEach(function(ev){
  drop.addEventListener(ev, function(e){ e.preventDefault(); drop.classList.add("over"); });
});
["dragleave","drop"].forEach(function(ev){
  drop.addEventListener(ev, function(e){ e.preventDefault(); drop.classList.remove("over"); });
});
drop.addEventListener("drop", function(e){ enqueue(Array.prototype.slice.call(e.dataTransfer.files)); });

function enqueue(files) {
  files.forEach(function(f) {
    var item = el("div", "qitem");
    var label = el("div", null, f.name + " (" + fmtSize(f.size) + ")");
    var bar = el("div", "qbar"); var fill = el("div"); bar.appendChild(fill);
    item.appendChild(label); item.appendChild(bar);
    queue.insertBefore(item, queue.firstChild);
    var fd = new FormData();
    fd.append("file", f, f.name);
    var xhr = new XMLHttpRequest();
    xhr.open("POST", UPLOAD);
    xhr.upload.onprogress = function(ev){ if (ev.lengthComputable) fill.style.width = (ev.loaded * 100 / ev.total) + "%"; };
    xhr.onload = function(){
      if (xhr.status === 200) { label.className = "qitem qdone"; label.textContent += " · 完成"; }
      else { label.className = "qitem qerr"; label.textContent += " · 失败(" + xhr.status + ")"; }
    };
    xhr.onerror = function(){ label.className = "qitem qerr"; label.textContent += " · 网络错误"; };
    xhr.send(fd);
  });
}

loadTab("images");
</script>
</body>
</html>"""
}
