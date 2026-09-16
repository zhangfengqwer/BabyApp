// Shared family album features for Safari/PWA. No home access key is stored here.
function showIdentityPicker() {
  document.querySelector('.bottom-nav').hidden = true;
  document.querySelector('#publishButton').hidden = true;
  main.innerHTML = `<section class="profile"><h1>你是宝宝的哪位家人？</h1><p class="muted">选择一次，以后记住这个身份。免密码身份仅适合可信家庭设备。</p><div class="profile-card">${state.members.map(m => `<button class="settings-row" data-identity="${esc(m.username)}"><span>${esc(m.relationship)}<small>${esc(m.username)}${m.role === 'ADMIN' ? ' · 管理员' : ''}</small></span><span>›</span></button>`).join('')}</div><p>没有找到自己？请家庭管理员先添加成员。</p></section>`;
  main.querySelectorAll('[data-identity]').forEach(button => button.onclick = async () => {
    button.disabled = true;
    try {
      const response = await api('/auth/web-home', {method:'POST',headers:{'X-Family-Username':button.dataset.identity}});
      setTokens(response);
      localStorage.setItem('zhizhiIdentity',button.dataset.identity);
      state.view='timeline'; await start();
    } catch(e) {button.disabled=false;toast(e.message);}
  });
}
function switchWebIdentity() {
  localStorage.removeItem('zhizhiIdentity');localStorage.removeItem('zhizhiTokens');
  state.accessToken='';state.refreshToken='';state.user=null;state.moments=[];
  start();
}
async function openFamilyMembers() {
  try {
    state.members=await api(`/babies/${state.baby.id}/family`);
    overlay.innerHTML=`<section class="overlay-screen feature-screen"><div class="sheet-head"><h2>家庭成员</h2><button class="close" data-close>×</button></div><p class="muted">上传和评论显示家庭关系，管理员可以修改。</p>${state.members.map(m => `<div class="member-row"><div><b>${esc(m.relationship)}</b><small>${esc(m.username)}${m.role==='ADMIN'?' · 管理员':''}</small></div>${state.user.role==='ADMIN'?`<button data-edit-member="${m.id}">编辑</button>`:''}</div>`).join('')}${state.user.role==='ADMIN'?'<button id="addMember" class="primary">＋ 添加家人</button>':''}</section>`;
    overlay.querySelector('[data-close]').onclick=()=>{overlay.innerHTML='';render();};
    overlay.querySelector('#addMember')?.addEventListener('click',()=>openMemberForm());
    overlay.querySelectorAll('[data-edit-member]').forEach(b=>b.onclick=()=>openMemberForm(state.members.find(m=>m.id===b.dataset.editMember)));
  }catch(e){toast(e.message);}
}
function openMemberForm(member) {
  overlay.innerHTML=`<section class="overlay-screen feature-screen"><form><div class="sheet-head"><h2>${member?'编辑家庭关系':'添加家人'}</h2><button type="button" class="close">×</button></div><label class="field">用户名 / 手机号<input name="username" autocomplete="tel" minlength="3" maxlength="64" pattern="[a-zA-Z0-9_.-]{3,64}" value="${esc(member?.username||'')}" ${member?'disabled':''} required></label><p class="muted">可由系统自动填充手机号；没有号码时填写英文、数字用户名，不发送短信。</p><label class="field">家庭关系<input name="relationship" maxlength="32" value="${esc(member?.relationship||'')}" required></label><div class="relationship-presets">${['爸爸','妈妈','爷爷','奶奶','外公','外婆'].map(r=>`<button type="button" data-relation="${r}">${r}</button>`).join('')}</div><p class="feature-error" role="alert"></p><button class="primary">保存</button></form></section>`;
  const form=overlay.querySelector('form');form.querySelector('.close').onclick=openFamilyMembers;
  form.querySelectorAll('[data-relation]').forEach(b=>b.onclick=()=>form.relationship.value=b.dataset.relation);
  form.onsubmit=async e=>{e.preventDefault();const button=form.querySelector('.primary');button.disabled=true;try{
    await api(`/babies/${state.baby.id}/family${member?`/${member.id}`:''}`,{method:member?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...(member?{}:{username:form.username.value.trim().toLowerCase()}),relationship:form.relationship.value.trim()})});await openFamilyMembers();
  }catch(error){form.querySelector('.feature-error').textContent=error.message;button.disabled=false;}};
}
function detailAssetTile(asset,index,canEdit) {
  return `<div class="detail-tile" data-gallery="${index}" data-link-id="${asset.id}" tabindex="0" role="button" aria-label="查看${asset.assetType==='VIDEO'?'视频':'照片'}${canEdit?'，长按显示删除按钮':''}">${asset.assetType==='VIDEO'?`<div class="video-thumb has-poster"><img data-asset="${asset.immichAssetId}" data-size="thumbnail" alt="视频封面处理中"><span>▶ 视频</span></div>`:`<img data-asset="${asset.immichAssetId}" data-size="preview" alt="照片处理中">`}${canEdit?'<button class="asset-remove" aria-label="从时光轴移除这个媒体"></button>':''}</div>`;
}
function bindAssetLongPress(moment) {
  overlay.querySelectorAll('.detail-tile').forEach(tile=>{
    const open=tile.onclick;let timer, x=0,y=0,held=false;
    const cancel=()=>clearTimeout(timer);
    const reveal=()=>{held=true;overlay.querySelectorAll('.detail-tile.is-editing').forEach(t=>t.classList.remove('is-editing'));tile.classList.add('is-editing');};
    if(moment.canEdit){
      tile.onpointerdown=e=>{if(e.target.closest('.asset-remove'))return;x=e.clientX;y=e.clientY;held=false;timer=setTimeout(reveal,550);};
      tile.onpointermove=e=>{if(Math.abs(e.clientX-x)>10||Math.abs(e.clientY-y)>10)cancel();};
      tile.onpointerup=cancel;tile.onpointercancel=cancel;tile.oncontextmenu=e=>{e.preventDefault();reveal();};
      tile.querySelector('.asset-remove').onclick=async e=>{e.stopPropagation();cancel();if(!confirm('只从这一天的时光轴移除，Immich 原文件保留。确定吗？'))return;
        const button=e.currentTarget;button.disabled=true;try{await api(`/moments/${moment.id}/assets/${tile.dataset.linkId}`,{method:'DELETE'});await loadMoments(true);await openDetail(moment.id);}catch(error){button.disabled=false;toast(error.message);}};
    }
    tile.onclick=e=>{if(e.target.closest('.asset-remove'))return;if(held){e.preventDefault();held=false;return;}tile.classList.remove('is-editing');open(e);};
    tile.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();open(e);}else if(moment.canEdit && (e.key==='ContextMenu'||(e.shiftKey&&e.key==='F10'))){e.preventDefault();reveal();}};
  });
}
async function openBabyProfile() {
  if(!state.baby.canEdit)return;
  const baby=state.baby;let avatarId=null,selectedFile=null,previewUrl=null;
  overlay.innerHTML=`<section class="overlay-screen feature-screen"><form><div class="sheet-head"><h2>宝宝名片</h2><button type="button" class="close">×</button></div><div class="profile-avatar"><img ${baby.avatarAssetId?`data-asset="${baby.avatarAssetId}" data-size="thumbnail"`:''} alt="宝宝固定头像"><label class="media-pick">更换头像<input name="avatar" type="file" accept="image/*"></label><p class="muted">头像不会随新照片改变</p></div><label class="field">姓名<input name="babyName" maxlength="64" value="${esc(baby.name)}" required></label><label class="field">昵称<input name="nickname" maxlength="64" value="${esc(baby.nickname||'')}"></label><label class="field">出生日期<input name="birthday" type="date" max="${localDateKey(new Date())}" value="${baby.birthday.slice(0,10)}" required></label><p class="muted">修改生日会重新计算时光轴里的宝宝年龄。</p><label class="field">一句话介绍<textarea name="description" maxlength="2000">${esc(baby.description||'')}</textarea></label><p class="feature-error" role="alert"></p><button class="primary">保存名片</button></form></section>`;
  const form=overlay.querySelector('form');hydrateImages(overlay);
  form.querySelector('.close').onclick=()=>{if(previewUrl)URL.revokeObjectURL(previewUrl);overlay.innerHTML='';};
  form.avatar.onchange=()=>{selectedFile=form.avatar.files[0];avatarId=null;if(previewUrl)URL.revokeObjectURL(previewUrl);if(selectedFile){previewUrl=URL.createObjectURL(selectedFile);const img=form.querySelector('.profile-avatar img');delete img.dataset.asset;img.src=previewUrl;}};
  form.onsubmit=async e=>{e.preventDefault();const button=form.querySelector('.primary');button.disabled=true;form.avatar.disabled=true;try{
    if(selectedFile&&!avatarId)avatarId=(await uploadWebAsset(selectedFile,await mediaCapturedAt(selectedFile))).id;
    state.baby=await api(`/babies/${baby.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:form.babyName.value,nickname:form.nickname.value,birthday:form.birthday.value,description:form.description.value,...(avatarId?{avatarAssetId:avatarId}:{})})});if(previewUrl)URL.revokeObjectURL(previewUrl);overlay.innerHTML='';render();toast('宝宝名片已保存');
  }catch(error){form.querySelector('.feature-error').textContent=error.message;button.disabled=false;form.avatar.disabled=false;}};
}
function validCaptureDate(date) {
  return date instanceof Date && Number.isFinite(date.getTime()) && date.getTime()>=0 && date.getTime()<=Date.now()+86400000 ? date : null;
}
function movieDateInBuffer(view) {
  const typeAt=p=>String.fromCharCode(...[0,1,2,3].map(i=>view.getUint8(p+i)));
  function walk(start,end,depth=0) {
    if(depth>6)return null;
    for(let p=start;p+8<=end;){
      let size=view.getUint32(p), header=8;const type=typeAt(p+4);
      if(size===1){if(p+16>end)return null;size=Number(view.getBigUint64(p+8));header=16;}
      if(size===0)size=end-p;if(size<header||p+size>end)break;
      if(['moov','trak','mdia'].includes(type)){const date=walk(p+header,p+size,depth+1);if(date)return date;}
      if(['mvhd','mdhd','tkhd'].includes(type)&&p+header+12<=p+size){
        const body=p+header,version=view.getUint8(body);
        const seconds=version===1?Number(view.getBigUint64(body+4)):view.getUint32(body+4);
        const date=validCaptureDate(new Date((seconds-2082844800)*1000));if(date)return date;
      }
      p+=size;
    }
    return null;
  }
  const date=walk(0,view.byteLength);if(date)return date;
  // moov is frequently stored at the end of a MOV/MP4 file.
  for(let p=4;p+4<view.byteLength;p++){if(typeAt(p)==='moov'){const size=view.getUint32(p-4);if(size>=8&&p-4+size<=view.byteLength){const found=walk(p-4,p-4+size);if(found)return found;}}}
  return null;
}
async function movieCapturedAt(file) {
  try{
    const size=2*1024*1024;
    let date=movieDateInBuffer(new DataView(await file.slice(0,size).arrayBuffer()));
    if(!date&&file.size>size)date=movieDateInBuffer(new DataView(await file.slice(Math.max(0,file.size-4*size)).arrayBuffer()));
    return date;
  }catch(_){return null;}
}
async function uploadWebAsset(file,capturedAt) {
  const data=new FormData(), time=(validCaptureDate(capturedAt)||new Date()).toISOString();
  data.append('assetData',file,file.name);data.append('fileCreatedAt',time);data.append('fileModifiedAt',time);data.append('isFavorite','false');
  const response=await raw('/media/assets',{method:'POST',body:data});if(!response.ok)throw new Error(`${file.name} 上传失败`);return response.json();
}
function planWebGroups(items,fallback,automatic) {
  const groups=new Map();for(const item of items){const key=automatic&&validCaptureDate(item.capturedAt)?localDateKey(item.capturedAt):fallback;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(item);}if(!items.length)groups.set(fallback,[]);return groups;
}
function openGroupedPublish() {
  let items=[],busy=false;const completed=new Set(), urls=[];
  overlay.innerHTML=`<section class="overlay-screen feature-screen"><form><div class="sheet-head"><h2>留下这一刻</h2><button type="button" class="close">×</button></div><p class="muted">不同日期自动分开归档，同一天追加到已有记录。</p><div class="form-card"><label class="field">想记住的小事<textarea name="content" maxlength="5000" rows="3" placeholder="今天有怎样的可爱瞬间？"></textarea></label><label class="field">地点 · 可选<input name="location" maxlength="255"></label></div><label class="date-toggle">按拍摄日期归档<input name="automatic" type="checkbox" checked></label><label class="field">手选日期 · 无拍摄时间时使用<input name="date" type="date" value="${localDateKey(new Date())}" max="${localDateKey(new Date())}" required></label><label class="media-pick">＋ 选择照片或视频<input name="media" type="file" accept="image/*,video/mp4,video/quicktime" multiple></label><p class="muted">文字与地点用于每个日期，可发布后分别编辑。</p><div class="publish-groups"></div><p class="feature-error" role="alert"></p><div class="publish-actions"><p class="group-summary"></p><button class="primary">发布到时光轴</button></div></form></section>`;
  const form=overlay.querySelector('form'), button=form.querySelector('.primary'), error=form.querySelector('.feature-error');
  const draw=()=>{const groups=planWebGroups(items,form.date.value,form.automatic.checked);form.querySelector('.group-summary').textContent=`将发布 ${groups.size} 个日期${completed.size?`，已完成 ${completed.size} 个`:''}`;form.querySelector('.publish-groups').innerHTML=items.length?[...groups].sort(([a],[b])=>b.localeCompare(a)).map(([date,entries])=>`<section class="form-card"><h3>${date} · ${entries.length} 个媒体${completed.has(date)?' · 已发布':''}</h3>${entries.map(item=>`<div class="selected-media">${item.file.type.startsWith('video/')?`<video muted playsinline preload="metadata" src="${item.url}#t=0.1"></video>`:`<img src="${item.url}" alt="所选照片">`}<div><b>${item.file.type.startsWith('video/')?'▶ 视频':'照片'}</b><small>${item.asset?'上传完成':item.capturedAt?'拍摄时间已识别':'未识别拍摄时间，使用手选日期'}</small></div>${!busy&&!completed.size?`<button type="button" data-remove-selected="${item.index}">移除</button>`:''}</div>`).join('')}</section>`).join(''):'';form.querySelectorAll('[data-remove-selected]').forEach(b=>b.onclick=()=>{items=items.filter(item=>String(item.index)!==b.dataset.removeSelected);draw();});};
  const lock=value=>{[form.date,form.automatic,form.content,form.location,form.media,form.querySelector('.close')].forEach(el=>el.disabled=value);button.disabled=busy;};
  form.querySelector('.close').onclick=()=>{urls.forEach(URL.revokeObjectURL);overlay.innerHTML='';};form.date.onchange=draw;form.automatic.onchange=draw;
  form.media.onchange=async()=>{busy=true;lock(true);error.textContent='正在读取拍摄时间…';try{urls.forEach(URL.revokeObjectURL);urls.length=0;items=[];for(const file of form.media.files){const url=URL.createObjectURL(file);urls.push(url);items.push({file,url,index:items.length,capturedAt:validCaptureDate(await mediaCapturedAt(file)),asset:null});}error.textContent='';}catch(e){error.textContent='无法读取媒体，请重新选择';}finally{busy=false;lock(false);draw();}};
  form.onsubmit=async e=>{e.preventDefault();if(busy)return;if(!items.length&&!form.content.value.trim()){error.textContent='请填写文字或选择照片视频';return;}busy=true;lock(true);button.textContent='正在发布…';error.textContent='';try{
    const groups=planWebGroups(items,form.date.value,form.automatic.checked);
    for(const date of groups.keys()){if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||date>localDateKey(new Date()))throw new Error('请检查日期，不能晚于今天');}
    for(const [i,item] of items.entries()){if(item.asset)continue;error.textContent=`正在上传 ${i+1}/${items.length}`;item.asset=await uploadWebAsset(item.file,item.capturedAt);draw();}
    for(const [date,entries] of [...groups].sort(([a],[b])=>a.localeCompare(b))){if(completed.has(date))continue;await api(`/babies/${state.baby.id}/moments`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({content:form.content.value||undefined,location:form.location.value||undefined,eventDate:new Date(`${date}T00:00:00`).toISOString(),visibility:'FAMILY',assets:entries.map((item,i)=>({immichAssetId:item.asset.id,assetType:item.file.type.startsWith('video/')?'VIDEO':'IMAGE',sortOrder:i}))})});completed.add(date);draw();}
    const target=[...groups.keys()].sort().at(-1);await loadMoments(true);while(state.nextCursor&&!state.moments.some(m=>localDateKey(m.eventDate)===target))await loadMoments();urls.forEach(URL.revokeObjectURL);overlay.innerHTML='';state.view='timeline';render();requestAnimationFrame(()=>document.querySelector(`[data-day="${target}"]`)?.scrollIntoView({block:'start'}));toast(`已发布 ${groups.size} 个日期`);
  }catch(e){error.textContent=`${e.message}，已上传媒体和已成功日期会保留，点击重试继续。`;}finally{busy=false;lock(completed.size>0);button.disabled=false;button.textContent=completed.size?'继续发布剩余日期':'重试发布';draw();}};
  draw();
}
