const state = {
  accessToken: "",
  refreshToken: "",
  user: null,
  baby: null,
  moments: [],
  nextCursor: null,
  view: "timeline",
  galleryUrls: [],
};
const main = document.querySelector("#main"),
  overlay = document.querySelector("#overlay"),
  toastEl = document.querySelector("#toast");
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>'"]/g,
    (ch) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[
        ch
      ],
  );
const toast = (message) => {
  toastEl.textContent = message;
  toastEl.classList.add("show");
  setTimeout(() => toastEl.classList.remove("show"), 2500);
};
const isoDay = (date) => new Date(date).toISOString().slice(0, 10);
const localDateKey = (date) => {
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
const localDay = (date) => {
  const d = new Date(date);
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
};
function ageAt(birthday, target) {
  let b = new Date(`${String(birthday).slice(0, 10)}T00:00:00`),
    t = new Date(`${isoDay(target)}T00:00:00`);
  let y = t.getFullYear() - b.getFullYear(),
    m = t.getMonth() - b.getMonth(),
    d = t.getDate() - b.getDate();
  if (d < 0) {
    m--;
    d += new Date(t.getFullYear(), t.getMonth(), 0).getDate();
  }
  if (m < 0) {
    y--;
    m += 12;
  }
  if (y <= 0 && m <= 0) return `出生${Math.max(0, d)}天`;
  return (
    `${y > 0 ? `${y}岁` : ""}${m > 0 ? `${m}个月` : ""}${d > 0 ? `${d}天` : ""}` ||
    "出生当天"
  );
}
async function raw(path, options = {}, retry = true) {
  const headers = new Headers(options.headers || {});
  if (state.accessToken)
    headers.set("Authorization", `Bearer ${state.accessToken}`);
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401 && retry && state.refreshToken) {
    const refreshed = await fetch("/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: state.refreshToken }),
    });
    if (refreshed.ok) {
      setTokens((await refreshed.json()).data);
      return raw(path, options, false);
    }
  }
  return response;
}
async function api(path, options = {}) {
  const response = await raw(path, options);
  const payload = await response
    .json()
    .catch(() => ({ message: "服务器返回异常" }));
  if (!response.ok)
    throw new Error(
      Array.isArray(payload.message)
        ? payload.message.join("；")
        : payload.message || "请求失败",
    );
  return payload.data;
}
function setTokens(data) {
  state.accessToken = data.accessToken;
  state.refreshToken = data.refreshToken;
  state.user = data.user;
  localStorage.setItem(
    "zhizhiTokens",
    JSON.stringify({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      user: data.user,
    }),
  );
}
async function authenticate() {
  try {
    const saved = JSON.parse(localStorage.getItem("zhizhiTokens") || "null");
    if (saved) Object.assign(state, saved);
    if (state.accessToken) {
      const response = await raw("/babies", {}, false);
      if (response.ok) return (await response.json()).data;
    }
    const data = await api("/auth/web-home", { method: "POST" });
    setTokens(data);
    return api("/babies");
  } catch (error) {
    throw new Error(`${error.message}。请确认已连接家庭 Wi-Fi 或 Tailscale。`);
  }
}
async function start() {
  try {
    const babies = await authenticate();
    state.baby = babies[0];
    if (!state.baby) throw new Error("尚未创建宝宝资料");
    await loadMoments(true);
    render();
  } catch (error) {
    main.innerHTML = `<section class="error-state"><h2>暂时无法进入相册</h2><p>${esc(error.message)}</p><button class="retry" onclick="location.reload()">重新连接</button></section>`;
  }
}
async function loadMoments(reset = false) {
  const cursor =
    !reset && state.nextCursor
      ? `&cursor=${encodeURIComponent(state.nextCursor)}`
      : "";
  const data = await api(`/babies/${state.baby.id}/moments?limit=30${cursor}`);
  state.moments = reset ? data.items : [...state.moments, ...data.items];
  state.nextCursor = data.nextCursor;
}
function hero() {
  const b = state.baby,
    today = new Date(),
    avatar = b.avatarAssetId
      ? `<img class="baby-avatar" data-asset="${b.avatarAssetId}" data-size="thumbnail" alt="${esc(b.name)}的头像">`
      : '<div class="baby-avatar fallback">之</div>';
  return `<header class="hero"><div class="app-title"><span class="mark">之</span><div><h1>之之成长手册</h1><p>把每一天，留给长大的你</p></div></div><section class="baby-card">${avatar}<div><div class="baby-name">${esc(b.name)}</div><div class="baby-age">${esc(ageAt(b.birthday, today))}</div></div><div class="baby-meta"><span>生日 ${localDay(b.birthday)}</span><span>来到家里第 ${Math.max(1, Math.floor((today - new Date(b.birthday)) / 86400000) + 1)} 天</span></div></section></header>`;
}
function tabs() {
  return `<div class="view-tabs"><button data-subview="timeline" class="${state.view === "timeline" ? "active" : ""}">时光轴</button><button data-subview="calendar" class="${state.view === "calendar" ? "active" : ""}">日历</button></div>`;
}
function mediaCell(asset, index, momentId) {
  if (asset.assetType === "VIDEO")
    return `<button class="video-thumb has-poster" data-detail="${momentId}"><img data-asset="${asset.immichAssetId}" data-size="thumbnail" alt="视频封面处理中"><span>▶ 视频</span></button>`;
  return `<img data-asset="${asset.immichAssetId}" data-size="thumbnail" data-detail="${momentId}" data-index="${index}" alt="照片处理中">`;
}
function timeline() {
  const cards = state.moments.map((moment) => {
    const assets = moment.assets || [],
      grid = assets.length === 1 ? "one" : assets.length === 2 ? "two" : "many";
    return `<article class="moment" data-day="${localDateKey(moment.eventDate)}"><h3 class="moment-date">${esc(ageAt(state.baby.birthday, moment.eventDate))}<small>· ${localDay(moment.eventDate)}</small></h3><section class="moment-card">${assets.length ? `<div class="media-grid ${grid}">${assets.slice(0, 4).map((a, i) => mediaCell(a, i, moment.id)).join("")}</div>` : ""}<div class="moment-body" data-detail="${moment.id}">${moment.content ? `<div class="moment-copy">${esc(moment.content)}</div>` : ""}${moment.location ? `<div class="moment-location">⌖ ${esc(moment.location)}</div>` : ""}<div class="moment-footer"><span>${esc(moment.author?.nickname || "家人")}</span><span><button data-like="${moment.id}" class="${moment.likedByMe ? "liked" : ""}">♡ ${moment._count?.likes || 0}</button>　留言 ${moment._count?.comments || 0}</span></div></div></section></article>`;
  });
  return `<div class="feed">${cards.join("") || '<div class="empty">还没有成长记录</div>'}${state.nextCursor ? '<button id="loadMore" class="primary">加载更早记录</button>' : ""}</div>`;
}
function calendar() {
  const groups = new Map();
  for (const m of state.moments) {
    const d = new Date(m.eventDate),
      key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(m);
  }
  return `<div class="calendar-list">${
    [...groups]
      .map(([key, items]) => {
        const [year, month] = key.split("-").map(Number),
          first = new Date(year, month - 1, 1).getDay(),
          days = new Date(year, month, 0).getDate(),
          byDay = new Map(
            items.map((m) => [new Date(m.eventDate).getDate(), m]),
          );
        let cells = Array(first).fill('<div class="day empty-day"></div>');
        for (let day = 1; day <= days; day++) {
          const m = byDay.get(day),
            a = m?.assets?.[0];
          cells.push(
            `<button class="day ${a ? "has-media" : ""}" ${m ? `data-detail="${m.id}"` : ""}><span>${day}</span>${a ? `<img data-asset="${a.immichAssetId}" data-size="thumbnail" alt="媒体处理中">` : ""}${a?.assetType === "VIDEO" ? '<i class="calendar-video">▶</i>' : ""}${m?.assets?.length > 1 ? `<span class="count">${m.assets.length}</span>` : ""}</button>`,
          );
        }
        return `<section class="month"><h2>${year}年${month}月</h2><div class="week"><span>日</span><span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span></div><div class="month-grid">${cells.join("")}</div></section>`;
      })
      .join("") || '<div class="empty">还没有可显示的日期</div>'
  }</div>`;
}
function profile() {
  return `<section class="profile"><h1>我的</h1><div class="profile-card"><b>${esc(state.user?.nickname || "家人")}</b><p class="muted">${esc(state.user?.role || "")}</p></div><div class="profile-card install-note"><b>安装到 iPhone</b><p>使用 Safari 打开本页，点击底部“分享”，选择“添加到主屏幕”。以后直接点击“之之成长”图标即可进入。</p><p>在家连接家庭 Wi‑Fi；外出时先连接 Tailscale。</p></div></section>`;
}
function render() {
  document
    .querySelectorAll(".nav-item")
    .forEach((b) =>
      b.classList.toggle(
        "active",
        b.dataset.view ===
          (state.view === "profile"
            ? "profile"
            : state.view === "calendar"
              ? "calendar"
              : "timeline"),
      ),
    );
  document.querySelector("#publishButton").hidden = state.view === "profile";
  main.innerHTML =
    state.view === "profile"
      ? profile()
      : hero() + tabs() + (state.view === "calendar" ? calendar() : timeline());
  hydrateImages();
  bindMain();
}
const sleep = (milliseconds) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));
async function loadAssetImage(img) {
  if (img.dataset.loaded || img.dataset.loading) return;
  img.dataset.loading = "1";
  for (let attempt = 0; attempt < 12 && img.isConnected; attempt++) {
    try {
      const response = await raw(
        `/media/assets/${img.dataset.asset}/view?size=${img.dataset.size || "thumbnail"}`,
      );
      if (response.ok) {
        const url = URL.createObjectURL(await response.blob());
        state.galleryUrls.push(url);
        img.src = url;
        img.dataset.loaded = "1";
        img.alt = img.alt.replace("处理中", "");
        delete img.dataset.loading;
        return;
      }
    } catch {}
    img.alt = img.alt.includes("视频") ? "视频封面处理中" : "照片处理中";
    await sleep(Math.min(1000 + attempt * 750, 7000));
  }
  delete img.dataset.loading;
  if (img.isConnected) {
    img.alt = "媒体仍在处理中";
    setTimeout(() => loadAssetImage(img), 10000);
  }
}
function hydrateImages(root = document) {
  return Promise.all(
    [...root.querySelectorAll("img[data-asset]:not([data-loaded])")].map(
      loadAssetImage,
    ),
  );
}
function bindMain() {
  main.querySelectorAll("[data-subview]").forEach(
    (b) =>
      (b.onclick = () => {
        state.view = b.dataset.subview;
        render();
      }),
  );
  main.querySelectorAll("[data-detail]").forEach(
    (el) =>
      (el.onclick = (e) => {
        if (e.target.closest("[data-like]")) return;
        openDetail(el.dataset.detail);
      }),
  );
  main.querySelectorAll("[data-like]").forEach(
    (b) =>
      (b.onclick = async () => {
        const m = state.moments.find((x) => x.id === b.dataset.like);
        await api(`/moments/${m.id}/like`, {
          method: m.likedByMe ? "DELETE" : "POST",
        });
        await loadMoments(true);
        render();
      }),
  );
  const more = document.querySelector("#loadMore");
  if (more)
    more.onclick = async () => {
      more.disabled = true;
      await loadMoments();
      render();
    };
}
async function openDetail(id) {
  overlay.innerHTML =
    '<section class="overlay-screen"><div class="startup"><p>正在打开记录…</p></div></section>';
  try {
    const m = await api(`/moments/${id}`);
    const comments = await api(`/moments/${id}/comments`);
    overlay.innerHTML = `<section class="overlay-screen detail"><div class="detail-head"><div><h2>${esc(ageAt(m.baby.birthday, m.eventDate))}</h2><span class="muted">${localDay(m.eventDate)}</span></div><button class="close" data-close>×</button></div><div class="detail-media">${m.assets.map((a, i) => (a.assetType === "VIDEO" ? `<button class="video-thumb has-poster" data-gallery="${i}"><img data-asset="${a.immichAssetId}" data-size="thumbnail" alt="视频封面处理中"><span>▶ 视频</span></button>` : `<img data-asset="${a.immichAssetId}" data-size="preview" data-gallery="${i}" alt="照片处理中">`)).join("")}</div><div class="detail-text">${esc(m.content || "")}</div>${m.location ? `<p class="muted">⌖ ${esc(m.location)}</p>` : ""}<div class="actions"><button id="detailLike" class="${m.likedByMe ? "liked" : ""}">♡ ${m._count.likes}</button>${m.canEdit ? '<button id="deleteMoment" class="danger">删除</button>' : ""}</div><section class="comments"><h3>家人留言</h3><div>${comments.items.map((c) => `<div class="comment"><b>${esc(c.user?.nickname || "家人")}</b>${esc(c.content)}</div>`).join("") || '<p class="muted">还没有留言</p>'}</div><form class="comment-form"><input maxlength="2000" placeholder="写留言…"><button>发送</button></form></section></section>`;
    hydrateImages(overlay);
    overlay.querySelector("[data-close]").onclick = () => {
      const savedScroll = window.scrollY;
      overlay.innerHTML = "";
      loadMoments(true).then(() => {
        render();
        requestAnimationFrame(() => window.scrollTo(0, savedScroll));
      });
    };
    overlay
      .querySelectorAll("[data-gallery]")
      .forEach(
        (el) =>
          (el.onclick = () =>
            openGallery(m.assets, Number(el.dataset.gallery))),
      );
    overlay.querySelector("#detailLike").onclick = async () => {
      await api(`/moments/${m.id}/like`, {
        method: m.likedByMe ? "DELETE" : "POST",
      });
      openDetail(m.id);
      await loadMoments(true);
    };
    const del = overlay.querySelector("#deleteMoment");
    if (del)
      del.onclick = async () => {
        if (!confirm("只删除这条动态，Immich 原照片会保留。确定删除吗？"))
          return;
        await api(`/moments/${m.id}`, { method: "DELETE" });
        overlay.innerHTML = "";
        await loadMoments(true);
        render();
        toast("已删除");
      };
    overlay.querySelector(".comment-form").onsubmit = async (e) => {
      e.preventDefault();
      const input = e.currentTarget.querySelector("input");
      if (!input.value.trim()) return;
      await api(`/moments/${m.id}/comments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: input.value }),
      });
      openDetail(m.id);
    };
  } catch (error) {
    overlay.innerHTML = "";
    toast(error.message);
  }
}
async function openGallery(assets, index) {
  let current = index;
  const draw = async () => {
    const a = assets[current];
    overlay.innerHTML = `<div class="gallery"><span class="gallery-count">${current + 1} / ${assets.length}</span>${a.assetType === "VIDEO" ? "<video controls autoplay playsinline></video>" : '<img alt="完整照片">'}</div>`;
    const response = await raw(
      `/media/assets/${a.immichAssetId}/view?size=fullsize`,
    );
    if (!response.ok) {
      toast("大图加载失败");
      return;
    }
    const url = URL.createObjectURL(await response.blob());
    state.galleryUrls.push(url);
    const media = overlay.querySelector(
      a.assetType === "VIDEO" ? "video" : "img",
    );
    media.src = url;
    let startX = 0;
    overlay.querySelector(".gallery").ontouchstart = (e) =>
      (startX = e.touches[0].clientX);
    overlay.querySelector(".gallery").ontouchend = (e) => {
      const dx = e.changedTouches[0].clientX - startX;
      if (Math.abs(dx) > 60) {
        current = (current + (dx < 0 ? 1 : -1) + assets.length) % assets.length;
        draw();
      } else overlay.innerHTML = "";
    };
  };
  draw();
}
function openPublish() {
  const today = new Date(),
    offset = today.getTimezoneOffset() * 60000,
    local = new Date(today - offset).toISOString().slice(0, 16);
  overlay.innerHTML = `<div class="overlay-screen" style="background:rgba(0,0,0,.25)"><form class="sheet"><div class="sheet-head"><h2>发布成长动态</h2><button type="button" class="close">×</button></div><label class="field">事件日期<input name="date" type="datetime-local" value="${local}" required></label><label class="field">记录这一刻<textarea name="content" maxlength="5000"></textarea></label><label class="field">地点（可选）<input name="location" maxlength="255"></label><label class="field">照片或视频（数量不限）<input name="media" type="file" accept="image/*,video/mp4,video/quicktime" multiple></label><div class="upload-list"></div><button class="primary">发布</button></form></div>`;
  const form = overlay.querySelector("form");
  form.querySelector(".close").onclick = () => (overlay.innerHTML = "");
  form.media.onchange = async () => {
    const files = [...form.media.files];
    if (!files.length) return;
    const capturedAt = await mediaCapturedAt(files[0]);
    if (!capturedAt) return;
    const localCapturedAt = new Date(
      capturedAt.getTime() - capturedAt.getTimezoneOffset() * 60000,
    )
      .toISOString()
      .slice(0, 16);
    form.date.value = localCapturedAt;
    toast("已按第一张照片的拍摄时间选择日期");
  };
  form.onsubmit = async (e) => {
    e.preventDefault();
    const button = form.querySelector(".primary"),
      files = [...form.media.files],
      list = form.querySelector(".upload-list");
    if (!files.length && !form.content.value.trim())
      return toast("请选择照片视频或填写文字");
    button.disabled = true;
    button.textContent = "正在上传…";
    const assets = [];
    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i],
          data = new FormData(),
          date = new Date(form.date.value).toISOString();
        data.append("assetData", file, file.name);
        data.append("fileCreatedAt", date);
        data.append("fileModifiedAt", date);
        data.append("isFavorite", "false");
        list.innerHTML = `正在上传 ${i + 1} / ${files.length}：${esc(file.name)}<div class="progress"><i style="width:${Math.round((i / files.length) * 100)}%"></i></div>`;
        const response = await raw("/media/assets", {
          method: "POST",
          body: data,
        });
        if (!response.ok) throw new Error(`${file.name} 上传失败`);
        const uploaded = await response.json();
        assets.push({
          immichAssetId: uploaded.id,
          assetType: file.type.startsWith("video/") ? "VIDEO" : "IMAGE",
          sortOrder: i,
        });
      }
      const publishedDay = localDateKey(form.date.value);
      await api(`/babies/${state.baby.id}/moments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          content: form.content.value || undefined,
          eventDate: new Date(form.date.value).toISOString(),
          location: form.location.value || undefined,
          visibility: "FAMILY",
          assets,
        }),
      });
      overlay.innerHTML = "";
      await loadMoments(true);
      state.view = "timeline";
      render();
      requestAnimationFrame(() =>
        document.querySelector(`[data-day="${publishedDay}"]`)?.scrollIntoView({ block: "start" }),
      );
      toast("发布成功");
    } catch (error) {
      toast(error.message);
      button.disabled = false;
      button.textContent = "重试发布";
    }
  };
}

async function mediaCapturedAt(file) {
  // 浏览器无法统一读取 HEIC/视频元数据；JPEG 优先读取 EXIF，其余使用文件时间。
  if (file.type === "image/jpeg") {
    try {
      const view = new DataView(await file.slice(0, 512 * 1024).arrayBuffer());
      if (view.getUint16(0) === 0xffd8) {
        let offset = 2;
        while (offset + 4 < view.byteLength) {
          const marker = view.getUint16(offset);
          const length = view.getUint16(offset + 2);
          if (marker === 0xffe1 && length >= 8) {
            const parsed = exifDate(view, offset + 10);
            if (parsed) return parsed;
          }
          if (length < 2) break;
          offset += 2 + length;
        }
      }
    } catch (_) {}
  }
  const fallback = new Date(file.lastModified);
  return Number.isNaN(fallback.getTime()) ? null : fallback;
}

function exifDate(view, tiff) {
  const little = view.getUint16(tiff) === 0x4949;
  const u16 = (p) => view.getUint16(p, little);
  const u32 = (p) => view.getUint32(p, little);
  const readText = (p, length) => {
    let value = "";
    for (let i = 0; i < length - 1 && p + i < view.byteLength; i++)
      value += String.fromCharCode(view.getUint8(p + i));
    return value;
  };
  const findDate = (ifdOffset, allowExifPointer) => {
    const start = tiff + ifdOffset;
    if (start + 2 > view.byteLength) return null;
    const count = u16(start);
    for (let i = 0; i < count; i++) {
      const entry = start + 2 + i * 12;
      if (entry + 12 > view.byteLength) break;
      const tag = u16(entry);
      if (allowExifPointer && tag === 0x8769) {
        const nested = findDate(u32(entry + 8), false);
        if (nested) return nested;
      }
      if (tag === 0x9003 || tag === 0x9004 || tag === 0x0132) {
        const length = u32(entry + 4);
        const valueOffset = length <= 4 ? entry + 8 : tiff + u32(entry + 8);
        const match = readText(valueOffset, length).match(
          /^(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})/,
        );
        if (match)
          return new Date(
            +match[1],
            +match[2] - 1,
            +match[3],
            +match[4],
            +match[5],
            +match[6],
          );
      }
    }
    return null;
  };
  return findDate(u32(tiff + 4), true);
}
document.querySelectorAll(".nav-item").forEach(
  (button) =>
    (button.onclick = () => {
      state.view = button.dataset.view;
      render();
    }),
);
document.querySelector("#publishButton").onclick = openPublish;
if ("serviceWorker" in navigator)
  navigator.serviceWorker.register("/web/service-worker.js").catch(() => {});
start();
