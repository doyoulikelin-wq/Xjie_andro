// Xjie 健捷 · Web Demo
// 连接生产后端 https://www.jianjieaitech.com
// nginx 会剥掉一层 /api/，FastAPI 路由本身又带 /api 前缀，
// 因此公网请求实际形如 .../api/api/<route>。
const API_BASE = "https://www.jianjieaitech.com/api";

const PROACTIVE_MESSAGES = [
  "今天也辛苦啦，先深呼吸三秒，再继续看数据～",
  "喝水了吗？小口慢饮，对血糖更友好哦。",
  "久坐超过 1 小时？起来走两分钟，曲线会感谢你。",
  "不用追求完美，今天比昨天稳一点就很棒。",
  "早睡 30 分钟，明早的空腹血糖会更好看。",
  "心情也是健康的一部分，今天感觉怎么样？",
  "饭后散步 10 分钟，等同一颗\u201c温柔降糖药\u201d。",
  "别忘了给自己一点鼓励，你已经在认真管理身体了。",
  "上传一份最新体检报告，我可以帮你看看变化趋势。",
  "拍一下今天的餐食，我来估算它对血糖的影响。",
  "还没连 CGM？连上后就能看到 24 小时血糖曲线啦。",
  "补一条今日运动记录，让健康画像更准一点。",
  "把最近的化验单传上来，我帮你挑出值得复查的项目。",
  "写两句今天的心情，我会把它和血糖一起分析。",
  "上传过往病例，AI 总结会更贴近你的真实情况。",
  "加一条睡眠时长，我可以告诉你它和早餐血糖的关系。",
  "把家里常吃的菜拍给我，下次就能秒识别营养成分。",
  "还差一份用药记录，补上后就能给出更安全的建议。",
];

// -------- auth & fetch helpers --------
const STORE = {
  get token() { return localStorage.getItem("xjie_token"); },
  set token(v) { v ? localStorage.setItem("xjie_token", v) : localStorage.removeItem("xjie_token"); },
  get refresh() { return localStorage.getItem("xjie_refresh"); },
  set refresh(v) { v ? localStorage.setItem("xjie_refresh", v) : localStorage.removeItem("xjie_refresh"); },
};

async function api(path, opts = {}) {
  const init = {
    method: opts.method || "GET",
    headers: { "Content-Type": "application/json", ...(opts.headers || {}) },
    credentials: "omit",
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  };
  if (STORE.token) init.headers.Authorization = `Bearer ${STORE.token}`;
  let r = await fetch(API_BASE + path, init);
  if (r.status === 401 && STORE.refresh && !opts._retry) {
    const ok = await tryRefresh();
    if (ok) return api(path, { ...opts, _retry: true });
  }
  const text = await r.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!r.ok) throw Object.assign(new Error(`${r.status}`), { status: r.status, data });
  return data;
}

async function tryRefresh() {
  try {
    const r = await fetch(API_BASE + "/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: STORE.refresh }),
    });
    if (!r.ok) return false;
    const d = await r.json();
    STORE.token = d.access_token;
    if (d.refresh_token) STORE.refresh = d.refresh_token;
    return true;
  } catch { return false; }
}

async function login(phone, password) {
  const r = await fetch(API_BASE + "/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone, username: phone, password }),
  });
  if (!r.ok) throw new Error("登录失败：" + r.status);
  const d = await r.json();
  STORE.token = d.access_token;
  STORE.refresh = d.refresh_token;
  return d;
}

function logout() {
  STORE.token = null;
  STORE.refresh = null;
  location.reload();
}

// -------- UI utilities --------
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);

function showApp() {
  $("#login").hidden = true;
  $("#app").hidden = false;
  boot();
}

function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function renderMarkdown(md) {
  if (!md) return "";
  let h = escapeHtml(md);
  h = h.replace(/^## (.+)$/gm, "<h2>$1</h2>");
  h = h.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  h = h.replace(/\n/g, "<br/>");
  return h;
}

// -------- Login wiring --------
$("#loginBtn").addEventListener("click", async () => {
  $("#loginErr").textContent = "";
  $("#loginBtn").disabled = true;
  try {
    await login($("#phone").value.trim(), $("#password").value);
    showApp();
  } catch (e) {
    $("#loginErr").textContent = e.message;
  } finally { $("#loginBtn").disabled = false; }
});

$("#logoutBtn").addEventListener("click", logout);

$$(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    $$(".nav-item").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    const t = btn.dataset.tab;
    $$(".tab").forEach((s) => (s.hidden = s.dataset.tab !== t));
    if (t === "chat") scrollChatBottom();
  });
});

// -------- Proactive rotation --------
function startProactive() {
  let idx = Math.floor(Math.random() * PROACTIVE_MESSAGES.length);
  const el = $("#proactiveText");
  const tick = () => {
    el.style.opacity = 0;
    setTimeout(() => {
      el.textContent = PROACTIVE_MESSAGES[idx % PROACTIVE_MESSAGES.length];
      idx++;
      el.style.opacity = 1;
    }, 600);
  };
  tick();
  setInterval(tick, 5000);
  // 后端如果有主动消息就覆盖
  api("/api/agent/proactive").then((d) => {
    if (d && d.message) el.textContent = d.message;
  }).catch(() => {});
}

// -------- Boot all data --------
async function boot() {
  startProactive();
  try {
    const me = await api("/api/users/me");
    $("#userSubject").textContent = "受试者 ID：" + (me.id ?? "--");
    renderAccount(me);
  } catch (e) { console.warn(e); }

  loadHome();
  loadHealth();
  loadMeals();
  loadOmics();
  loadMood();
}

// -------- Home / Dashboard --------
async function loadHome() {
  try {
    const d = await api("/api/dashboard/health");
    const g = d.glucose?.last_24h || {};
    $("#mAvg").textContent = g.avg?.toFixed?.(0) ?? "--";
    $("#mTir").textContent = g.tir_70_180_pct?.toFixed?.(0) ?? "--";
    $("#mKcal").textContent = d.kcal_today ?? 0;
  } catch (e) {
    $("#mAvg").textContent = "--";
  }
  try {
    const t = await api("/api/agent/today");
    const goals = (t.today_goals || []).map(x => "· " + x).join("\n");
    $("#agentToday").textContent = [t.greeting, goals].filter(Boolean).join("\n\n");
  } catch { $("#agentToday").textContent = "今日简报暂无数据。"; }

  try {
    const to = new Date();
    const from = new Date(to.getTime() - 24 * 3600 * 1000);
    const rows = await api(`/api/glucose?from=${from.toISOString()}&to=${to.toISOString()}&limit=2000`);
    drawGlucose(rows);
  } catch (e) { console.warn("glucose load", e); }
}

function drawGlucose(rows) {
  const c = $("#glucoseCanvas");
  const rect = c.getBoundingClientRect();
  c.width = rect.width * devicePixelRatio;
  c.height = 260 * devicePixelRatio;
  const ctx = c.getContext("2d");
  ctx.scale(devicePixelRatio, devicePixelRatio);
  const W = rect.width, H = 260, pad = { l: 36, r: 12, t: 10, b: 22 };
  ctx.clearRect(0, 0, W, H);

  if (!rows || !rows.length) {
    ctx.fillStyle = "#9ca3af"; ctx.font = "13px sans-serif";
    ctx.fillText("暂无血糖数据", W / 2 - 40, H / 2);
    return;
  }
  const xs = rows.map(r => new Date(r.ts).getTime());
  const ys = rows.map(r => r.glucose_mgdl);
  const xMin = Math.min(...xs), xMax = Math.max(...xs);
  const yMin = Math.min(50, ...ys) - 5, yMax = Math.max(200, ...ys) + 5;
  const xScale = (x) => pad.l + (x - xMin) / (xMax - xMin || 1) * (W - pad.l - pad.r);
  const yScale = (y) => pad.t + (1 - (y - yMin) / (yMax - yMin)) * (H - pad.t - pad.b);

  // target band 70–180
  ctx.fillStyle = "rgba(22,166,106,0.10)";
  ctx.fillRect(pad.l, yScale(180), W - pad.l - pad.r, yScale(70) - yScale(180));

  // grid y lines
  ctx.strokeStyle = "#e5e7eb"; ctx.lineWidth = 1; ctx.font = "10px sans-serif"; ctx.fillStyle = "#9ca3af";
  [70, 140, 180].forEach(v => {
    const y = yScale(v);
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(W - pad.r, y); ctx.stroke();
    ctx.fillText(v, 4, y + 3);
  });

  // line
  ctx.strokeStyle = "#1456C8"; ctx.lineWidth = 1.6; ctx.beginPath();
  rows.forEach((r, i) => {
    const x = xScale(new Date(r.ts).getTime()), y = yScale(r.glucose_mgdl);
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
  });
  ctx.stroke();

  // dots for low/high
  rows.forEach(r => {
    const v = r.glucose_mgdl;
    if (v < 70 || v > 180) {
      const x = xScale(new Date(r.ts).getTime()), y = yScale(v);
      ctx.fillStyle = v < 70 ? "#D84C4C" : "#E5A33C";
      ctx.beginPath(); ctx.arc(x, y, 2.2, 0, Math.PI * 2); ctx.fill();
    }
  });

  // x axis: hour ticks
  ctx.fillStyle = "#9ca3af";
  for (let i = 0; i <= 6; i++) {
    const t = xMin + (xMax - xMin) * i / 6;
    const d = new Date(t);
    const x = xScale(t);
    const lab = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
    ctx.fillText(lab, x - 14, H - 6);
  }
}

// -------- Health data --------
async function loadHealth() {
  try {
    const s = await api("/api/health-data/summary");
    $("#healthSummary").innerHTML = renderMarkdown(s.summary_text || "暂无摘要。");
  } catch { $("#healthSummary").textContent = "暂未生成 AI 摘要。"; }

  try {
    const w = await api("/api/health-data/indicators/watched");
    const items = w.items || [];
    if (!items.length) { $("#watchedList").textContent = "暂无关注指标。"; return; }
    const names = items.map(i => i.indicator_name);
    const trend = await api("/api/health-data/indicators/trend?" +
      names.map(n => "names=" + encodeURIComponent(n)).join("&")).catch(() => ({ indicators: [] }));
    const map = Object.fromEntries((trend.indicators || []).map(t => [t.name, t]));
    $("#watchedList").innerHTML = items.map(it => {
      const t = map[it.indicator_name];
      const last = t && t.points && t.points.length ? t.points[t.points.length - 1] : null;
      const abn = last && last.abnormal;
      const valTxt = last ? `${last.value} ${t.unit || ""}` : "--";
      return `<div class="row"><span>${escapeHtml(it.indicator_name)}</span><span class="${abn ? "bad" : ""}">${escapeHtml(valTxt)}${abn ? " ↑" : ""}</span></div>`;
    }).join("");
  } catch { $("#watchedList").textContent = "加载失败。"; }

  try {
    const d = await api("/api/health-data/documents?limit=10");
    const items = d.items || [];
    if (!items.length) { $("#docList").textContent = "暂无文档。"; return; }
    $("#docList").innerHTML = items.map(it => `
      <div class="row">
        <span>${escapeHtml(it.name || it.doc_type)}</span>
        <span style="color:#6b7280;font-size:12px">${(it.doc_date || "").slice(0, 10)} <span class="tag">${escapeHtml(it.doc_type || "")}</span></span>
      </div>`).join("");
  } catch { $("#docList").textContent = "加载失败。"; }
}

// -------- Meals --------
async function loadMeals() {
  try {
    const now = new Date();
    const from = new Date(now.getTime() - 7 * 24 * 3600 * 1000);
    const rows = await api(`/api/meals?from=${from.toISOString()}&to=${now.toISOString()}`);
    if (!rows.length) { $("#mealsList").textContent = "最近 7 天暂无餐食记录。"; return; }
    $("#mealsList").innerHTML = rows.map(m => `
      <div class="row">
        <span>${escapeHtml((m.tags && m.tags.join(" / ")) || m.notes || "未命名")}</span>
        <span style="color:#6b7280;font-size:12px">${new Date(m.meal_ts).toLocaleString("zh-CN")} · ${m.kcal ?? "--"} kcal</span>
      </div>`).join("");
  } catch (e) { $("#mealsList").textContent = "加载失败。"; }
}

// -------- Omics triad --------
async function loadOmics() {
  try {
    const d = await api("/api/omics/demo/triad");
    const scores = [
      ["代谢组学", d.metabolomics_score],
      ["CGM 血糖", d.cgm_score],
      ["心率维度", d.heart_score],
      ["三维耦合", d.overlap_score],
    ];
    $("#triadScores").innerHTML = scores.map(([l, v]) => `
      <div class="bar-row">
        <span class="label">${l}</span>
        <div class="bar-bg"><div class="bar-fg" style="width:${Math.round((v || 0) * 100)}%"></div></div>
        <span style="width:44px;text-align:right;color:#1456C8;font-weight:600">${((v || 0) * 100).toFixed(0)}%</span>
      </div>`).join("");
    $("#triadInsights").innerHTML = "<ul class='insights'>" +
      (d.insights || []).map(i => `<li>${escapeHtml(i)}</li>`).join("") + "</ul>";
  } catch { $("#triadScores").textContent = "加载失败"; $("#triadInsights").textContent = ""; }
}

// -------- Mood --------
async function loadMood() {
  try {
    const rows = await api("/api/mood/days?days=14");
    if (!rows.length) { $("#moodGrid").textContent = "暂无心情记录。"; return; }
    $("#moodGrid").innerHTML = rows.map(d => {
      const v = d.avg;
      return `<div class="mood-cell ${v == null ? "empty" : ""}">
        <div class="d">${d.date.slice(5)}</div>
        <div class="v">${v == null ? "·" : v.toFixed(1)}</div>
      </div>`;
    }).join("");
  } catch { $("#moodGrid").textContent = "加载失败。"; }
}

// -------- Account / Settings --------
function renderAccount(me) {
  const p = me.profile || {};
  const kv = [
    ["受试者 ID", me.id],
    ["角色", me.role || "受试者"],
    ["性别", p.gender === "male" ? "男" : p.gender === "female" ? "女" : "未设置"],
    ["年龄", p.age ?? "未设置"],
    ["身高", p.height_cm ? `${p.height_cm} cm` : "未设置"],
    ["体重", p.weight_kg ? `${p.weight_kg} kg` : "未设置"],
    ["注册时间", (me.created_at || "").slice(0, 10)],
  ];
  $("#accountInfo").innerHTML = kv.map(([k, v]) =>
    `<div class="line"><span class="k">${k}</span><span>${escapeHtml(String(v))}</span></div>`).join("");

  const s = me.settings || {}; const c = me.consent || {};
  const kv2 = [
    ["干预级别", s.intervention_level || "--"],
    ["每日提醒上限", s.daily_reminder_limit ?? "未限制"],
    ["允许自动升级提醒", s.allow_auto_escalation ? "是" : "否"],
    ["允许 AI 对话", c.allow_ai_chat ? "已同意" : "未同意"],
    ["允许数据上传", c.allow_data_upload ? "已同意" : "未同意"],
    ["同意版本", c.version || "--"],
  ];
  $("#consentInfo").innerHTML = kv2.map(([k, v]) =>
    `<div class="line"><span class="k">${k}</span><span>${escapeHtml(String(v))}</span></div>`).join("");
}

// -------- Chat --------
let chatConvId = null;

function appendMsg(role, text) {
  const el = document.createElement("div");
  el.className = "msg " + role;
  el.textContent = text;
  $("#chatMsgs").appendChild(el);
  scrollChatBottom();
  return el;
}

function scrollChatBottom() {
  const m = $("#chatMsgs"); if (m) m.scrollTop = m.scrollHeight;
}

async function sendChat() {
  const input = $("#chatInput");
  const text = input.value.trim();
  if (!text) return;
  input.value = "";
  appendMsg("user", text);
  const holder = appendMsg("assistant", "思考中…");
  try {
    const body = { message: text };
    if (chatConvId) body.conversation_id = chatConvId;
    const d = await api("/api/agent/chat", { method: "POST", body });
    chatConvId = d.conversation_id || d.id || chatConvId;
    const reply = d.reply || d.message || d.content || d.answer || JSON.stringify(d);
    holder.textContent = reply;
  } catch (e) {
    holder.textContent = "出错了：" + (e.data?.detail || e.message);
  }
}

$("#chatSend").addEventListener("click", sendChat);
$("#chatInput").addEventListener("keydown", (e) => { if (e.key === "Enter") sendChat(); });

// -------- bootstrap on load --------
(function start() {
  if (STORE.token) showApp();
})();
