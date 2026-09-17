'use strict';
(() => {
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const dateKey = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
    const today = () => dateKey(new Date());
    const parseDate = value => new Date(value + 'T12:00:00');
    const yen = value => new Intl.NumberFormat('ja-JP', {style:'currency',currency:'JPY',maximumFractionDigits:2}).format(value);
    const names = {home:'HOME',schedule:'日程管理',money:'金銭管理',purchases:'購入／購入予定',favorites:'推し一覧','favorite-new':'推しの登録',inventory:'所持グッズ',account:'アカウント設定'};
    const states = {OWNED:'購入済（サブスク等含）',PAID:'購入済（サブスク等含）',PLANNED:'購入予定（サブスク等含）'};
    const repeatNames = {NONE:'',MONTHLY:'毎月',YEARLY:'毎年'};
    const empty = message => `<p class="empty">${esc(message)}</p>`;
    let data = {name:'',favorites:[],items:[],events:[]};
    let month = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    let ready = false;
    const notified = new Set();
    // メニューと画面遷移
    const mobile = matchMedia('(max-width:760px)');
    const sidebar = $('#app-sidebar');
    const toggle = $('#menu-toggle');
    const workspace = $('.app-workspace');
    let menuAnimationTimers = [];
    function animateMenuItems(open) {
        menuAnimationTimers.forEach(timer => clearTimeout(timer));
        menuAnimationTimers = [];
        const items = $$('nav a', sidebar);
        // 開閉アニメーションはユーザーが明示的に希望しているため、ここでは常に再生する。
        const reduced = false;
        const duration = reduced ? 0 : 900, easing = 'cubic-bezier(.22,.68,0,1)';
        sidebar.style.transition = `transform ${duration}ms ${easing}`;
        sidebar.style.transform = open ? 'translateX(-100%)' : 'translateX(0)';
        if (workspace && !mobile.matches) { workspace.style.transition = `margin-left ${duration}ms ${easing}`; workspace.style.marginLeft = open ? '0' : '256px'; }
        requestAnimationFrame(() => { sidebar.style.transform = open ? 'translateX(0)' : 'translateX(-100%)'; if (workspace && !mobile.matches) workspace.style.marginLeft = open ? '256px' : '0'; });
        items.forEach((item, index) => {
            const delay = reduced ? 0 : (open ? index * 100 : (items.length - index - 1) * 70);
            item.style.transition = 'none';
            item.style.opacity = open ? '0' : '1';
            item.style.transform = open ? 'translateY(-10px)' : 'translateY(0)';
            const timer = setTimeout(() => {
                item.style.transition = reduced ? 'none' : 'opacity 420ms cubic-bezier(.22,.68,0,1), transform 420ms cubic-bezier(.22,.68,0,1)';
                item.style.opacity = open ? '1' : '0';
                item.style.transform = open ? 'translateY(0)' : 'translateY(-8px)';
            }, delay + (reduced ? 0 : 16));
            menuAnimationTimers.push(timer);
        });
    }
    function menu(open, remember = true) {
        document.body.classList.toggle('menu-closed', !open);
        sidebar.inert = !open;
        toggle.setAttribute('aria-expanded', String(open));
        toggle.setAttribute('aria-label', open ? 'メニューを閉じる' : 'メニューを開く');
        $('#menu-shade').hidden = !open || !mobile.matches;
        requestAnimationFrame(() => animateMenuItems(open));
        if (remember && !mobile.matches) { try { localStorage.setItem('favorite-menu', open ? 'open' : 'closed'); } catch (_) {} }
    }
    let preference = 'open';
    try { preference = localStorage.getItem('favorite-menu') || 'open'; } catch (_) {}
    menu(!mobile.matches && preference !== 'closed', false);
    toggle.addEventListener('click', () => { const open = document.body.classList.contains('menu-closed'); menu(open); if (open && mobile.matches) $('a', sidebar).focus(); });
    $('#menu-shade').addEventListener('click', () => { menu(false); toggle.focus(); });
    mobile.addEventListener('change', () => menu(!mobile.matches, false));
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && !document.body.classList.contains('menu-closed')) { menu(false); toggle.focus(); }
        if (e.key === 'Tab' && mobile.matches && !document.body.classList.contains('menu-closed')) {
            const elements = $$('a,button', sidebar), first = elements[0], last = elements.at(-1);
            if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
            else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
        }
    });
    // ルートに応じて画面を表示する
    function navigate() {
        const route = location.pathname.replace(/^\//,'');
        const page = names[route] ? route : (location.hash.slice(1) || 'home');
        const selected = page === 'favorite-new' ? 'favorites' : (names[page] ? page : 'home');
        $$('[data-screen]').forEach(el => el.hidden = el.dataset.screen !== selected);
        $$('[data-page]').forEach(el => { const active = el.dataset.page === selected; el.classList.toggle('active', active); if (active) el.setAttribute('aria-current','page'); else el.removeAttribute('aria-current'); });
        $('#breadcrumb').textContent = names[selected];
        document.title = `${names[selected]} | Saving Favorite`;
        if (mobile.matches) menu(false, false);
        window.scrollTo({top:0});
        const main = $('.app-main');
        if (main) main.animate([{opacity:0, transform:'translateY(18px)'},{opacity:1, transform:'translateY(0)'}], {duration:520, easing:'cubic-bezier(.22,.68,0,1)'});
        if (page === 'favorite-new') { const editor=$('#favorite-editor'); if (editor) editor.hidden=false; }
        if (selected === 'home' && ready) renderInventory($('#home-inventory'), false);
    }
    window.addEventListener('hashchange', navigate);
    window.addEventListener('popstate', navigate);
    document.addEventListener('click', e => {
        const link = e.target.closest('a[data-page]');
        if (!link) return;
        e.preventDefault(); history.pushState({}, '', link.getAttribute('href')); navigate();
    });
    navigate();
    // APIとデータ表示
    const announce = (message, failure = false) => { const status = $('#app-status'); if (status) { status.textContent = message; status.classList.toggle('failure', failure); } };
    async function api(path, method = 'GET', body) {
        const headers = {'Accept':'application/json'};
        if (body) headers['Content-Type'] = 'application/json';
        headers[$('meta[name="csrf-header"]').content] = $('meta[name="csrf-token"]').content;
        const response = await fetch(`api/${path}`, {method, headers, credentials:'same-origin', body:body ? JSON.stringify(body) : undefined});
        if (response.status === 401 || response.redirected) { location.assign('register?mode=login'); throw new Error('ログインし直してください。'); }
        if (!response.ok) { let error; try { error = await response.json(); } catch (_) {} throw new Error(error?.message || (response.status === 403 ? '有効期限が切れました。画面を再読み込みしてください。' : '保存できませんでした。入力内容や通信状態を確認してください。')); }
        return response.headers.get('content-type')?.includes('application/json') ? response.json() : null;
    }
    async function refresh() { data = await api('state'); ready = true; render(); }
    const favoriteName = id => data.favorites.find(f => f.id === Number(id))?.name || '全体の予定';
    const amount = i => Math.round(Number(i.price || 0) * 100) * Number(i.quantity || 0) / 100;
    const safeSize = (value, fallback) => Math.max(24, Math.min(160, Number.isFinite(Number(value)) ? Number(value) : fallback));
    function occurrence(item, monthKey) {
        if (!item.purchasedDate) return null;
        if (item.recurrence === 'NONE') return item.purchasedDate.startsWith(monthKey) ? item.purchasedDate : null;
        if (monthKey < item.purchasedDate.slice(0,7)) return null;
        const [year, m] = monthKey.split('-').map(Number);
        if (item.recurrence === 'YEARLY' && m !== Number(item.purchasedDate.slice(5,7))) return null;
        const day = Math.min(Number(item.purchasedDate.slice(8)), new Date(year,m,0).getDate());
        return `${monthKey}-${String(day).padStart(2,'0')}`;
    }
    function monthItems(monthKey) { return data.items.filter(i => occurrence(i, monthKey)); }
    function isPlanned(i, monthKey) { return i.status === 'PLANNED' || (i.recurrence !== 'NONE' && occurrence(i, monthKey) > today()); }
    function totals(monthKey) {
        const list = monthItems(monthKey);
        const paid = list.filter(i => !isPlanned(i,monthKey)).reduce((n,i) => n + Math.round(amount(i)*100),0)/100;
        const planned = list.filter(i => isPlanned(i,monthKey)).reduce((n,i) => n + Math.round(amount(i)*100),0)/100;
        return {paid,planned,total:paid+planned};
    }
    const stats = values => values.map(([label,value]) => `<div class="stat"><small>${esc(label)}</small><strong>${esc(value)}</strong></div>`).join('');
    // HOME・推し一覧・金銭管理・カレンダーを描画する
    function render() {
        $$('[data-favorite-select]').forEach(select => {
            const old = select.value;
            select.innerHTML = `<option value="">${select.hasAttribute('data-optional') ? (select.id ? 'すべての推し' : '推しを指定しない') : '推しを選んでください'}</option>` + data.favorites.map(f => `<option value="${f.id}">${esc(f.name)}</option>`).join('');
            if (data.favorites.some(f => String(f.id) === old)) select.value = old;
        });
        $('#today-label').textContent = new Intl.DateTimeFormat('ja-JP',{dateStyle:'full'}).format(new Date());
        const total = totals(today().slice(0,7));
        const homeCash=Number(data.cash || 0);
        $('#home-stats').innerHTML = stats([['現在の所持金（支払済み反映）',yen(homeCash-total.paid)],['今月の支払い済み',yen(total.paid)],['支払い予定額',yen(total.planned)],['予定反映後の残額',yen(homeCash-total.total)],['登録している推し',`${data.favorites.length} 人`]]);
        $('#home-favorites').innerHTML = data.favorites.length ? data.favorites.map(f => `<div class="favorite-row"><span class="favorite-avatar" style="--avatar-size:${safeSize(f.imageSize,56)}px">${f.imageUrl ? `<img src="${esc(f.imageUrl)}" alt="">` : '♡'}</span><div><strong>${esc(f.name)}</strong><small>所持グッズ ${data.items.filter(i => i.favoriteId===f.id && (i.status==='OWNED' || i.status==='PAID')).reduce((n,i)=>n+i.quantity,0)} 点</small></div></div>`).join('') : empty('まずは「推しの登録」から、あなたの推しを教えてください。');
        $('#favorite-cards').innerHTML = data.favorites.length ? data.favorites.map(f => `<article class="panel"><span class="favorite-avatar" style="--avatar-size:${safeSize(f.imageSize,56)}px">${f.imageUrl ? `<img src="${esc(f.imageUrl)}" alt="">` : '♡'}</span><h2>${esc(f.name)}</h2><p>今月の支出・予定 ${yen(monthItems(today().slice(0,7)).filter(i => i.favoriteId===f.id).reduce((n,i)=>n+amount(i),0))}</p>${actions('favorites',f.id)}</article>`).join('') : empty('推しがまだ登録されていません。');
        const avatar = $('#sidebar-avatar'); if (avatar) { avatar.style.setProperty('--avatar-size', `${safeSize(data.imageSize,44)}px`); avatar.innerHTML = data.imageUrl ? `<img src="${esc(data.imageUrl)}" alt="プロフィール画像">` : '♙'; }
        const sidebarName = $('#sidebar-name'); if (sidebarName) sidebarName.textContent = data.name || '';
        const profile = $('#profile-form'); if (profile) { profile.elements.name.value = data.name || ''; profile.elements.cash.value = data.cash ?? 0; profile.elements.imageUrl.value = data.imageUrl || ''; const preview=$('[data-preview]',profile); if (preview) { preview.src=data.imageUrl || ''; preview.hidden=!data.imageUrl; } }
        renderCalendars(); renderInventory($('#home-inventory'), false); renderInventory($('#inventory-list'), true); renderMoney(); renderPurchases(); renderEvents(); renderNotifications();
    }
    function actions(kind,id) { return `<div class="entry-actions"><button class="quiet" type="button" data-edit="${kind}" data-id="${id}">編集</button><button class="danger" type="button" data-delete="${kind}" data-id="${id}">削除</button></div>`; }
    // グッズカードを描画する
    function itemMarkup(i, controls, monthKey, editableStatus = false) {
        let link = ''; try { if (i.storeUrl && new URL(i.storeUrl).protocol === 'https:') link = `<a href="${esc(i.storeUrl)}" target="_blank" rel="noopener noreferrer">商品ページを開く ↗</a>`; } catch (_) {}
        const date = monthKey ? occurrence(i,monthKey) : i.purchasedDate;
        const status = monthKey && isPlanned(i,monthKey) ? '購入・支払予定' : states[i.status];
        const statusControl = editableStatus
            ? `<label class="purchase-card-status">購入状態<select data-purchase-status="${i.id}" aria-label="${esc(i.name)}の購入状態"><option value="PLANNED" ${i.status === 'PLANNED' ? 'selected' : ''}>購入予定（サブスク等含）</option><option value="PAID" ${i.status !== 'PLANNED' ? 'selected' : ''}>購入済（サブスク等含）</option></select></label>`
            : `<span class="badge">${esc(status)}</span>`;
        const image = i.imageUrl ? `<img class="item-card-image" src="${esc(i.imageUrl)}" alt="">` : `<span class="item-card-placeholder" aria-hidden="true">♡</span>`;
        return `<article class="entry item-card">${image}<div class="item-card-body"><div class="item-card-heading"><p class="entry-title">${esc(i.name)}</p>${statusControl}</div><p class="muted">${esc(favoriteName(i.favoriteId))} · ${esc(i.category)}</p><p>${yen(i.price || 0)} × ${i.quantity}個 = <strong>${yen(amount(i))}</strong></p><p class="muted">${esc(date || '日付未登録')} ${esc(repeatNames[i.recurrence])}${i.deadline ? ' / 購入期限 '+esc(i.deadline) : ''}</p>${link}${controls ? actions('items',i.id) : ''}</div></article>`;
    }
    function renderInventory(target, controls) {
        const filter = controls ? $('#inventory-filter').value : '';
        const groups = new Map();
        data.items.filter(i => (i.status==='OWNED' || i.status==='PAID') && (!filter || i.favoriteId===Number(filter))).forEach(i => { const key=i.category || 'その他'; if(!groups.has(key)) groups.set(key,[]); groups.get(key).push(i); });
        target.innerHTML = groups.size ? [...groups].map(([category,items]) => `<details class="category-group"><summary>${esc(category)}<small>${items.reduce((n,i)=>n+i.quantity,0)} 点</small></summary><div class="category-content">${items.map(i=>itemMarkup(i,controls)).join('')}</div></details>`).join('') : empty('グッズはまだありません。「所持しているグッズを登録する」から追加できます。');
    }
    function renderPurchases() {
        const status = $('#purchase-status').value;
        const direction = $('#purchase-order').value === 'asc' ? 1 : -1;
        const items = data.items.filter(item => !status || (status === 'PLANNED' ? item.status === 'PLANNED' : item.status !== 'PLANNED'))
            .sort((a,b) => direction * ((a.purchasedDate || '').localeCompare(b.purchasedDate || '') || a.id-b.id));
        $('#purchase-count').textContent = `${items.length}件`;
        $('#purchase-list').innerHTML = items.length ? items.map(item => itemMarkup(item, true, null, true)).join('') : empty('該当する購入・購入予定はありません。');
    }
    $('#purchase-status').addEventListener('change', renderPurchases);
    $('#purchase-order').addEventListener('change', renderPurchases);
    $('#purchase-list').addEventListener('change', async event => {
        const select = event.target.closest('[data-purchase-status]');
        if (!select) return;
        select.disabled = true;
        $('#purchase-error').textContent = '';
        try {
            await api(`items/${select.dataset.purchaseStatus}/status`, 'PUT', {status:select.value});
            await refresh();
        } catch (error) {
            $('#purchase-error').textContent = error.message;
            renderPurchases();
        } finally { select.disabled = false; }
    });

    function renderMoney() {
        const key = $('#money-month').value || today().slice(0,7), sum=totals(key);
        const cash=Number(data.cash || 0), afterPaid=cash-sum.paid, afterPlanned=afterPaid-sum.planned;
        $('#money-stats').innerHTML = stats([['現在の所持金（支払済み反映）',yen(afterPaid)],['支払い予定額',yen(sum.planned)],['予定反映後の残額',yen(afterPlanned)],['今月の支出合計',yen(sum.total)]]);
        $('#money-breakdown').innerHTML = data.favorites.length ? data.favorites.map(f=>{
            const spent=monthItems(key).filter(i=>i.favoriteId===f.id).reduce((n,i)=>n+amount(i),0);
            const budget=f.monthlyBudget == null ? '予算未設定' : `予算 ${yen(f.monthlyBudget)} / ${spent>f.monthlyBudget?'超過':'残り'} ${yen(Math.abs(f.monthlyBudget-spent))}`;
            return `<div class="money-row"><span>${esc(f.name)}<br><small class="muted">${budget}</small></span><strong>${yen(spent)}</strong></div>`;
        }).join('') : empty('推しを登録すると、個別の支出を確認できます。');
        const filter=$('#money-favorite-filter').value;
        const items=monthItems(key).filter(i=>!filter || i.favoriteId===Number(filter));
        $('#money-list').innerHTML=items.length ? items.map(i=>itemMarkup(i,true,key)).join('') : empty('この月の支出・購入予定はありません。');
    }
    function renderEvents() {
        $('#event-list').innerHTML=data.events.length ? [...data.events].sort((a,b)=>a.startDate.localeCompare(b.startDate)).map(e=>`<article class="entry"><p class="entry-title">${esc(e.title)}</p><span class="badge">${esc(e.kind)}</span><p>${esc(e.startDate)} 〜 ${esc(e.endDate)}</p><p class="muted">${esc(favoriteName(e.favoriteId))} · ${e.reminderDays}日前から通知</p><p class="muted">${esc(e.notes)}</p>${actions('events',e.id)}</article>`).join('') : empty('予定はまだありません。カレンダーの日付からも追加できます。');
    }
    function onDate(key) {
        const entries = data.events.filter(e=>e.startDate<=key && e.endDate>=key).map(e=>({title:e.title,subtitle:`${favoriteName(e.favoriteId)} / ${e.startDate}〜${e.endDate}`,purchase:false}));
        data.items.forEach(i=> {
            if (occurrence(i,key.slice(0,7))===key) entries.push({title:i.name,subtitle:`${favoriteName(i.favoriteId)} / ${yen(amount(i))} / ${states[i.status]}`,purchase:true});
            if (i.status==='PLANNED' && i.deadline===key) entries.push({title:`購入期限：${i.name}`,subtitle:favoriteName(i.favoriteId),purchase:true});
        });
        return entries;
    }
    function renderCalendars() {
        const first=new Date(month.getFullYear(),month.getMonth(),1), start=new Date(first); start.setDate(1-first.getDay());
        let grid=['日','月','火','水','木','金','土'].map(d=>`<div class="weekday">${d}</div>`).join('');
        for(let n=0;n<42;n++) {
            const date=new Date(start); date.setDate(start.getDate()+n); const key=dateKey(date), entries=onDate(key);
            grid+=`<button type="button" data-day="${key}" aria-label="${key}、${entries.length}件の予定" class="calendar-day ${date.getMonth()!==month.getMonth()?'outside':''} ${key===today()?'today':''}"><span>${date.getDate()}</span>${entries.slice(0,2).map(e=>`<span class="calendar-tag ${e.purchase?'purchase':''}">${esc(e.title)}</span>`).join('')}${entries.length>2?`<small>＋${entries.length-2}件</small>`:''}</button>`;
        }
        $$('[data-calendar]').forEach(el=>el.innerHTML=`<div class="calendar-toolbar"><strong>${month.getFullYear()}年 ${month.getMonth()+1}月</strong><div><button type="button" class="quiet" data-month="-1" aria-label="前の月">‹</button> <button type="button" class="quiet" data-month="0">今月</button> <button type="button" class="quiet" data-month="1" aria-label="次の月">›</button></div></div><div class="calendar-grid">${grid}</div><p class="calendar-legend">紫：イベント・コラボ　水色：購入・支払日・購入期限</p>`);
    }
    function openDay(key) {
        $('#day-title').textContent=key; const entries=onDate(key);
        $('#day-content').innerHTML=entries.length ? entries.map(e=>`<div class="entry"><p class="entry-title">${esc(e.title)}</p><p class="muted">${esc(e.subtitle)}</p></div>`).join('') : empty('この日の予定はありません。');
        $('#day-add').dataset.date=key; $('#day-dialog').showModal();
    }
    function reminders() {
        const result=[], now=today(), daysTo=key=>Math.round((parseDate(key)-parseDate(now))/86400000);
        data.events.forEach(e=>{
            [['開始',e.startDate],['終了',e.endDate]].forEach(([type,date])=>{ const days=daysTo(date); if(days>=0 && days<=e.reminderDays) result.push({key:`event-${e.id}-${type}-${now}`,title:`${e.title}：${days===0?'今日':days+'日後に'}${type}`,date}); });
        });
        data.items.filter(i=>i.status==='PLANNED' && i.deadline).forEach(i=>{const days=daysTo(i.deadline); if(days>=0&&days<=3) result.push({key:`item-${i.id}-${now}`,title:`${i.name}：購入期限は${days===0?'今日':days+'日後'}`,date:i.deadline});});
        return result.sort((a,b)=>a.date.localeCompare(b.date));
    }
    function renderNotifications() {
        const list=reminders(); $('#notification-count').textContent=list.length;
        $('#notification-list').innerHTML=list.length ? list.map(r=>`<div class="entry"><strong>${esc(r.title)}</strong><p class="muted">${esc(r.date)}</p></div>`).join('') : empty('今のお知らせはありません。');
        if ('Notification' in window && Notification.permission==='granted') list.forEach(r=>{ if(!notified.has(r.key)) { try { new Notification('Saving Favorite',{body:r.title,tag:r.key}); notified.add(r.key); } catch (_) {} } });
    }
    $('#notification-toggle').addEventListener('click',()=>{ $('#notifications').hidden=!$('#notifications').hidden; });
    const metadataDialog = $('#metadata-dialog');
    let metadataTargetForm = null;
    let selectedMetadataIndexes = new Set();
    function updateMetadataSelectionSummary(total) {
        const box=$('#metadata-result-count');
        if (box) box.textContent=`${total}件の商品を取得しました。${selectedMetadataIndexes.size ? ` ${selectedMetadataIndexes.size}件を選択中` : ' 商品を選択してください。'}`;
        const submit=$('#metadata-register-selected');
        if (submit) submit.disabled=selectedMetadataIndexes.size===0;
    }
    function applyMetadata(form, product) {
        if (!product) return;
        if (product.name) form.elements.name.value = product.name;
        if (product.category && form.elements.category) form.elements.category.value = product.category;
        const price = Number(product.price);
        if (Number.isFinite(price) && form.elements.price) form.elements.price.value = String(price);
        if (product.imageUrl && form.elements.imageUrl) { form.elements.imageUrl.value = product.imageUrl; const preview=$('[data-preview]',form); if (preview) { preview.src=product.imageUrl; preview.hidden=false; } const upload=$('button.image-upload-button',form); if (upload) upload.hidden=true; const adjust=$('[data-adjust-image]',form); if (adjust) adjust.hidden=false; }
        if (form.elements.storeUrl) form.elements.storeUrl.value = form.elements.storeUrl.value;
        metadataDialog.close(); announce('商品情報を入力しました。');
    }
    async function fetchMetadata(button, listMode = false) {
        const form=button.closest('form'), url=form?.elements.storeUrl?.value.trim();
        const errorBox = button.closest('label')?.querySelector('[data-metadata-error]');
        if (!url) { if (errorBox) errorBox.textContent='先に商品ページURLを入力してください。'; return; }
        button.disabled=true; if (errorBox) errorBox.textContent='商品情報を取得しています…';
        try {
            const result=await api('metadata','POST',{url}); metadataTargetForm=form;
            const products=result.products?.length ? result.products : [result.product];
            const countBox=$('#metadata-result-count');
            if (countBox) countBox.textContent=`${products.length}件の商品情報を取得しました。`;
            if (products.length===1 && !listMode) applyMetadata(form,products[0]);
            else { form.__metadataProducts=products; selectedMetadataIndexes=new Set(); $('#metadata-products').innerHTML=products.map((p,index)=>`<button type="button" class="metadata-card" data-metadata-index="${index}" aria-pressed="false">${p.imageUrl ? `<img src="${esc(p.imageUrl)}" alt="">` : ''}<span><strong>${esc(p.name)}</strong><small>${p.price == null ? '価格未取得' : yen(p.price)}</small></span></button>`).join(''); updateMetadataSelectionSummary(products.length); metadataDialog.showModal(); }
            if (errorBox) errorBox.textContent='';
        } catch(error) { if (errorBox) errorBox.textContent=error.message; }
        finally { button.disabled=false; }
    }
    $$('[data-fetch-metadata]').forEach(button=>button.addEventListener('click',()=>fetchMetadata(button)));
    $$('[data-fetch-metadata-list]').forEach(button=>button.addEventListener('click',()=>fetchMetadata(button,true)));
    $('#metadata-cancel').addEventListener('click',()=>metadataDialog.close());
    $('#metadata-products').addEventListener('click',e=>{ const card=e.target.closest('[data-metadata-index]'); if(!card || !metadataTargetForm)return; const index=Number(card.dataset.metadataIndex); if(selectedMetadataIndexes.has(index)) selectedMetadataIndexes.delete(index); else selectedMetadataIndexes.add(index); card.classList.toggle('selected',selectedMetadataIndexes.has(index)); card.setAttribute('aria-pressed',selectedMetadataIndexes.has(index)); updateMetadataSelectionSummary((metadataTargetForm.__metadataProducts || []).length); });
    $('#metadata-register-selected').addEventListener('click',async()=>{
        if (!metadataTargetForm || !selectedMetadataIndexes.size) return;
        const form=metadataTargetForm, products=form.__metadataProducts || [], base=Object.fromEntries(new FormData(form)); delete base.id; delete base.imageFile;
        base.favoriteId=base.favoriteId ? Number(base.favoriteId) : null; base.quantity=Number(base.quantity || 1); base.price=Number(base.price || 0); base.deadline=base.deadline || null; base.membershipJoinedDate=base.membershipJoinedDate || null;
        const submit=$('#metadata-register-selected'); submit.disabled=true;
        try { for (const index of selectedMetadataIndexes) { const p=products[index]; await api('items','POST',{...base,name:p.name,category:p.category || base.category,price:Number(p.price || 0),imageUrl:p.imageUrl || base.imageUrl || null}); } metadataDialog.close(); await refresh(); announce(`${selectedMetadataIndexes.size}件の商品を登録しました。`); }
        catch(error) { const box=$('#metadata-result-count'); if(box) box.textContent=error.message; submit.disabled=false; }
    });
    $$('dialog').forEach(dialog=>dialog.addEventListener('click',event=>{ if (event.target === dialog) dialog.close(); }));
    $('#enable-notifications').addEventListener('click',async()=>{
        if(!('Notification' in window)) return announce('このブラウザーは通知に対応していません。アプリ内のお知らせをご利用ください。',true);
        const permission=await Notification.requestPermission(); announce(permission==='granted'?'ブラウザー通知を有効にしました。':'ブラウザー通知は許可されていません。アプリ内のお知らせをご利用ください。'); renderNotifications();
    });
    setInterval(()=>{ if(ready) renderNotifications(); },60000);
    $('#money-month').value=today().slice(0,7);
    $('#money-month').addEventListener('change',renderMoney);
    $('#money-favorite-filter').addEventListener('change',renderMoney);
    $('#inventory-filter').addEventListener('change',()=>renderInventory($('#inventory-list'),true));
    $('#favorite-add').addEventListener('click',()=>{ const editor=$('#favorite-editor'); editor.hidden=false; resetForm($('#favorite-form')); editor.scrollIntoView({block:'center',behavior:'smooth'}); setTimeout(()=>$('#favorite-form input[name="name"]').focus(),250); });
    $('#inventory-add').addEventListener('click',()=>{ const editor=$('#inventory-editor'); editor.hidden=false; resetForm($('#inventory-form')); editor.scrollIntoView({block:'center',behavior:'smooth'}); setTimeout(()=>$('#inventory-form input[name="name"]').focus(),250); });
    // 登録フォームと画像切り抜き
    function resetForm(form) {
        form.reset(); if (form.elements.id) form.elements.id.value=''; $('.form-error',form).textContent='';
        $$('button.image-upload-button',form).forEach(button=>{ const input=button.nextElementSibling; button.textContent=input?.getAttribute('aria-label') || '画像を上げる'; button.hidden=false; });
        $$('[data-adjust-image]',form).forEach(button=>button.hidden=true);
        $$('[data-preview]',form).forEach(preview=>{ preview.hidden=true; preview.removeAttribute('src'); });
        $$('input[type="date"][required]',form).forEach(input=>input.value=today());
        const titles={'favorite-form':['favorite-form-title','推しの登録'],'event-form':['event-form-title','予定を登録'],'money-form':['money-form-title','支出・購入予定を登録'],'inventory-form':['inventory-form-title','グッズを登録']};
        const titleInfo=titles[form.getAttribute('id')]; if (titleInfo) $('#'+titleInfo[0]).textContent=titleInfo[1];
    }
    const itemEditDialog = $('#item-edit-dialog');
    let itemEditSession = null;
    function beginItemEdit(form) {
        const marker = document.createComment('item-form-position');
        form.before(marker);
        itemEditSession = {
            form, marker,
            fields: [...form.elements].filter(el => el.type !== 'file').map(el => [el, el.value]),
            preview: $('[data-preview]', form)?.getAttribute('src'),
            hidden: $$('[data-preview], [data-adjust-image], .image-upload-button', form).map(el => [el, el.hidden]),
            error: $('.form-error', form).textContent,
            title: $('#money-form-title').textContent
        };
        $('#item-edit-content').append(form);
    }
    $('#item-edit-close').addEventListener('click', () => itemEditDialog.close());
    itemEditDialog.addEventListener('close', () => {
        if (!itemEditSession) return;
        const session = itemEditSession;
        session.marker.replaceWith(session.form);
        session.fields.forEach(([el, value]) => { el.value = value; });
        const preview = $('[data-preview]', session.form);
        if (session.preview) preview.src = session.preview;
        else preview.removeAttribute('src');
        session.hidden.forEach(([el, hidden]) => { el.hidden = hidden; });
        $('.form-error', session.form).textContent = session.error;
        $('#money-form-title').textContent = session.title;
        updateMembershipFields(session.form);
        itemEditSession = null;
    });
    const forms=$$('#favorite-form,#event-form,#money-form,#inventory-form,#profile-form');
    const membershipCategory = value => /メンバー|会費/.test(value || '');
    function updateMembershipFields(form) {
        if (!form || form.id !== 'money-form') return;
        const category = form.elements.category?.value || '', active = membershipCategory(category);
        const dateLabel = $('[data-purchase-date-label]', form), hint = $('[data-membership-hint]', form);
        if (dateLabel) dateLabel.firstChild.textContent = active ? '入会日・支払開始日' : '購入日・支払開始日';
        if (hint) hint.hidden = !active;
        const next = $('[data-next-payment]', form);
        if (next) {
            const value = form.elements.purchasedDate?.value;
            if (active && value) {
                const [year, month, day] = value.split('-').map(Number), nextMonth = new Date(year, month, 1);
                const nextDay = Math.min(day, new Date(nextMonth.getFullYear(), nextMonth.getMonth() + 1, 0).getDate());
                next.textContent = ` 次回支払日：${nextMonth.getFullYear()}-${String(nextMonth.getMonth()+1).padStart(2,'0')}-${String(nextDay).padStart(2,'0')}`;
            } else next.textContent = '';
        }
        if (active && form.elements.purchasedDate?.value) form.elements.membershipJoinedDate.value = form.elements.purchasedDate.value;
        if (!active && form.elements.membershipJoinedDate) form.elements.membershipJoinedDate.value = '';
    }
    forms.forEach(form=>{
        resetForm(form);
        if (form.id === 'money-form') { form.elements.category.addEventListener('input',()=>updateMembershipFields(form)); form.elements.purchasedDate.addEventListener('change',()=>updateMembershipFields(form)); }
        $('[data-reset]',form).addEventListener('click',()=>resetForm(form));
        form.addEventListener('submit',async e=>{
            e.preventDefault(); if(!ready) { announce('データを読み込み中です。読み込みが完了してから保存してください。',true); return; }
            const input=Object.fromEntries(new FormData(form)), id=input.id; delete input.id;
            let kind=form.getAttribute('id')==='favorite-form'?'favorites':form.getAttribute('id')==='event-form'?'events':form.getAttribute('id')==='profile-form'?'profile':'items';
            if (input.imageFile instanceof File && input.imageFile.size) { $('.form-error',form).textContent='画像の切り抜く範囲を決定してください。'; return; }
            delete input.imageFile;
            if(kind==='favorites') { input.monthlyBudget=input.monthlyBudget==='' ? null : Number(input.monthlyBudget); }
            if(kind==='profile') input.cash=input.cash==='' ? 0 : Number(input.cash);
            if('favoriteId' in input) input.favoriteId=input.favoriteId ? Number(input.favoriteId) : null;
            if(kind==='items') { input.price=Number(input.price); input.quantity=Number(input.quantity); input.deadline=input.deadline || null; updateMembershipFields(form); input.membershipJoinedDate = input.membershipJoinedDate || null; }
            if(kind==='events') input.reminderDays=Number(input.reminderDays);
            if (kind==='profile') { delete input.id; }
            const submit=$('[type="submit"]',form); submit.disabled=true; $('.form-error',form).textContent='';
            try {
                await api(kind+(id?'/'+id:''),kind==='profile' || id ? 'PUT' : 'POST',input);
                resetForm(form); await refresh(); announce('保存しました。');
                if (kind === 'items' && itemEditDialog.open) itemEditDialog.close();
                if (kind === 'items' && form.id === 'inventory-form') { const editor=$('#inventory-editor'); if (editor) editor.hidden=true; }
                if(kind==='favorites') { history.pushState({},'', '/favorites'); navigate(); }
                if (kind === 'favorites') { const editor=$('#favorite-editor'); if (editor) editor.hidden=true; }
            } catch(error) { $('.form-error',form).textContent=error.message; }
            finally { submit.disabled=false; }
        });
    });
    function fileToDataUrl(file) {
        if (file.size > 2 * 1024 * 1024) return Promise.reject(new Error('画像は2MB以下にしてください。'));
        if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) return Promise.reject(new Error('画像はPNG・JPEG・WebP・GIF形式で選択してください。'));
        return new Promise((resolve,reject)=>{ const reader=new FileReader(); reader.onload=()=>resolve(reader.result); reader.onerror=()=>reject(new Error('画像を読み込めませんでした。')); reader.readAsDataURL(file); });
    }
    let cropTarget=null, cropNaturalWidth=1, cropNaturalHeight=1, cropScale=1, cropX=0, cropY=0;
    let cropWidth=0, cropPointer=null, dragOriginX=0, dragOriginY=0, cropStartX=0, cropStartY=0;
    const cropDialog=$('#crop-dialog'), cropStage=$('#crop-stage'), cropImage=$('#crop-image'), cropZoom=$('#crop-zoom');
    function positionCropImage() {
        const width=cropNaturalWidth*cropScale, height=cropNaturalHeight*cropScale;
        // Keep the entire crop frame covered. The saved area uses these same offsets.
        cropX=Math.min(0,Math.max(cropStage.clientWidth-width,cropX));
        cropY=Math.min(0,Math.max(cropStage.clientHeight-height,cropY));
        cropImage.style.width=`${width}px`; cropImage.style.height=`${height}px`;
        cropImage.style.transform=`translate(${cropX}px,${cropY}px)`;
    }
    async function openCrop(file, target) {
        if (!file) return;
        const ownerForm=target?.closest('form');
        if (typeof file !== 'string' && (ownerForm?.id === 'money-form' || ownerForm?.id === 'inventory-form')) return;
        try {
            const url=typeof file === 'string' ? file : await fileToDataUrl(file);
            cropImage.crossOrigin='anonymous';
            cropImage.src=url;
            await cropImage.decode();
            cropNaturalWidth=cropImage.naturalWidth; cropNaturalHeight=cropImage.naturalHeight;
            if (!cropNaturalWidth || !cropNaturalHeight) throw new Error('画像を読み込めませんでした。');
            cropTarget=target;
            // A closed dialog has no layout: show it before measuring the frame.
            cropDialog.showModal();
            cropWidth=cropStage.clientWidth;
            cropScale=Math.max(cropWidth/cropNaturalWidth,cropStage.clientHeight/cropNaturalHeight);
            cropZoom.value='100'; $('#crop-zoom-output').textContent='100%';
            cropX=(cropWidth-cropNaturalWidth*cropScale)/2;
            cropY=(cropStage.clientHeight-cropNaturalHeight*cropScale)/2;
            positionCropImage();
            cropStage.focus();
        } catch(error) { announce('画像を開けませんでした。PNG・JPEG・WebP・GIF形式の2MB以下の画像を選択してください。',true); target.value=''; cropTarget=null; if(cropDialog.open)cropDialog.close(); }
    }
    const setDirectImage=(file,button,form)=>{
        const selected=file.files[0]; if (!selected) return;
        button.textContent=selected.name;
        const reader=new FileReader();
        reader.onload=()=>{ form.elements.imageUrl.value=reader.result; const preview=$('[data-preview]',form); if (preview) { preview.src=reader.result; preview.hidden=false; } button.hidden=true; $('[data-adjust-image]',form).hidden=false; file.value=''; };
        reader.readAsDataURL(selected);
    };
    // Capture first so an older cached listener cannot open the crop dialog.
    document.addEventListener('change',e=>{
        const file=e.target.closest?.('input.image-upload-input'), form=file?.closest('form');
        if (!file || !form?.hasAttribute('data-direct-image')) return;
        e.stopImmediatePropagation();
        const button=file.previousElementSibling;
        if (button) setDirectImage(file,button,form);
    },true);
    $$('input.image-upload-input').forEach(file => {
        const button=document.createElement('button'); button.type='button'; button.className='image-upload-button'; button.textContent=file.getAttribute('aria-label') || '画像を上げる';
        file.parentNode.insertBefore(button,file); button.addEventListener('click',()=>file.click());
        file.addEventListener('change',()=>{
            const selected=file.files[0]; if (!selected) return;
            button.textContent=selected.name;
            const form=file.closest('form');
            if (form?.id === 'money-form' || form?.id === 'inventory-form') {
                setDirectImage(file,button,form);
            } else openCrop(selected,file);
        });
    });
    $$('[data-adjust-image]').forEach(button=>button.addEventListener('click',()=>{
        const form=button.closest('form'), file=form?.elements.imageFile, image=form?.elements.imageUrl?.value;
        if (image && file) openCrop(image,file);
    }));
    cropZoom.addEventListener('input',()=>{
        if (!cropTarget || !cropDialog.open) return;
        const old=cropScale, width=cropStage.clientWidth, height=cropStage.clientHeight;
        cropScale=Math.max(width/cropNaturalWidth,height/cropNaturalHeight)*Number(cropZoom.value)/100;
        cropX=width/2-(width/2-cropX)*(cropScale/old); cropY=height/2-(height/2-cropY)*(cropScale/old);
        positionCropImage(); $('#crop-zoom-output').textContent=`${cropZoom.value}%`;
    });
    cropStage.addEventListener('pointerdown',e=>{
        if (!cropTarget || cropPointer!==null || e.button!==0) return;
        e.preventDefault(); cropStage.focus(); cropPointer=e.pointerId;
        cropStage.classList.add('dragging'); cropStage.setPointerCapture(e.pointerId);
        dragOriginX=e.clientX; dragOriginY=e.clientY; cropStartX=cropX; cropStartY=cropY;
    });
    cropStage.addEventListener('pointermove',e=>{
        if(e.pointerId!==cropPointer)return;
        cropX=cropStartX+e.clientX-dragOriginX; cropY=cropStartY+e.clientY-dragOriginY; positionCropImage();
    });
    function stopCropDrag() {
        const pointer=cropPointer; cropPointer=null; cropStage.classList.remove('dragging');
        if(pointer!==null && cropStage.hasPointerCapture(pointer))cropStage.releasePointerCapture(pointer);
    }
    cropStage.addEventListener('pointerup',stopCropDrag);
    cropStage.addEventListener('pointercancel',stopCropDrag);
    cropStage.addEventListener('lostpointercapture',stopCropDrag);
    cropStage.addEventListener('keydown',e=>{
        const steps={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};
        if(!steps[e.key] || !cropTarget)return;
        e.preventDefault(); const [x,y]=steps[e.key], step=e.shiftKey?20:5;
        cropX+=x*step; cropY+=y*step; positionCropImage();
    });
    new ResizeObserver(()=>{
        if(!cropDialog.open || !cropTarget || !cropWidth)return;
        const width=cropStage.clientWidth; if(!width || width===cropWidth)return;
        stopCropDrag(); const ratio=width/cropWidth; cropScale*=ratio; cropX*=ratio; cropY*=ratio;
        cropWidth=width; positionCropImage();
    }).observe(cropStage);
    function cancelCrop() { cropDialog.close(); }
    cropDialog.addEventListener('close',()=>{ stopCropDrag(); if(cropTarget)cropTarget.value=''; cropTarget=null; });
    $('#crop-cancel').addEventListener('click',cancelCrop); $('#crop-cancel-bottom').addEventListener('click',cancelCrop);
    $('#crop-apply').addEventListener('click',()=>{
        if(!cropTarget || !cropDialog.open || !(cropScale>0))return;
        positionCropImage();
        const canvas=document.createElement('canvas'); canvas.width=512; canvas.height=512;
        const context=canvas.getContext('2d');
        context.drawImage(cropImage,-cropX/cropScale,-cropY/cropScale,cropStage.clientWidth/cropScale,cropStage.clientHeight/cropScale,0,0,512,512);
        const preview=$('[data-preview]',cropTarget.closest('label')), hidden=cropTarget.closest('form').elements.imageUrl;
        hidden.value=canvas.toDataURL('image/png'); preview.src=hidden.value; preview.hidden=false;
        const uploadButton=$('button.image-upload-button',cropTarget.closest('label')); if (uploadButton) uploadButton.hidden=true;
        const adjustButton=$('[data-adjust-image]',cropTarget.closest('label')); if (adjustButton) adjustButton.hidden=false;
        cropDialog.close();
    });
    $$('[data-size-range]').forEach(range => range.addEventListener('input', () => {
        const output = $('[data-size-output]', range.closest('label')); if (output) { output.value = `${range.value}px`; output.textContent = `${range.value}px`; }
    }));
    $('#close-day').addEventListener('click',()=>$('#day-dialog').close());
    $('#day-add').addEventListener('click',()=>{ const form=$('#event-form'); resetForm(form); form.elements.startDate.value=$('#day-add').dataset.date; form.elements.endDate.value=$('#day-add').dataset.date; $('#day-dialog').close(); history.pushState({},'', '/schedule'); navigate(); setTimeout(()=>form.elements.title.focus(),0); });
    document.addEventListener('click',async e=>{
        const move=e.target.closest('[data-month]'); if(move) { const step=Number(move.dataset.month); month=step===0?new Date(new Date().getFullYear(),new Date().getMonth(),1):new Date(month.getFullYear(),month.getMonth()+step,1); renderCalendars(); return; }
        const day=e.target.closest('[data-day]'); if(day) {openDay(day.dataset.day);return;}
        const edit=e.target.closest('[data-edit]');
        if(edit) {
            const kind=edit.dataset.edit, record=data[kind].find(x=>x.id===Number(edit.dataset.id)); if(!record)return;
            const target=kind==='favorites'?'favorite-new':kind==='events'?'schedule':'money';
            const form=$(kind==='favorites'?'#favorite-form':kind==='events'?'#event-form':target==='inventory'?'#inventory-form':'#money-form');
            if (kind === 'items') beginItemEdit(form);
            resetForm(form); Object.entries(record).forEach(([key,value])=>{if(form.elements.namedItem(key)) form.elements.namedItem(key).value=value??'';});
            if (kind === 'items' && record.status === 'OWNED') form.elements.status.value = 'PAID';
            if (target === 'inventory') { const editor=$('#inventory-editor'); if (editor) editor.hidden=false; }
            if (target === 'inventory' || target === 'money') { const preview=$('[data-preview]',form); if (preview) { preview.src=record.imageUrl || ''; preview.hidden=!record.imageUrl; } const adjust=$('[data-adjust-image]',form); if (adjust) adjust.hidden=!record.imageUrl; }
            if (kind === 'favorites') { const editor=$('#favorite-editor'); if (editor) editor.hidden=false; }
            if (target === 'money' && membershipCategory(record.category)) form.elements.purchasedDate.value = record.membershipJoinedDate || record.purchasedDate || '';
            updateMembershipFields(form);
            const title=kind==='favorites'?'favorite-form-title':kind==='events'?'event-form-title':target==='inventory'?'inventory-form-title':'money-form-title'; $('#'+title).textContent='登録内容を編集';
            if (kind === 'items') { itemEditDialog.showModal(); return; }
            history.pushState({},'', '/'+target); navigate(); setTimeout(()=>{form.scrollIntoView({block:'center'}); $('input:not([type="hidden"])',form).focus();},0); return;
        }
        const remove=e.target.closest('[data-delete]');
        if(remove) {
            const kind=remove.dataset.delete, message=kind==='favorites'?'この推しを削除しますか？紐づくグッズと支出も削除されます。予定は推し未指定で残ります。':'この記録を削除しますか？';
            if(!confirm(message))return;
            remove.disabled=true;
            try {await api(`${kind}/${remove.dataset.id}`,'DELETE'); await refresh(); announce('削除しました。');} catch(error) {announce(error.message,true); remove.disabled=false;}
        }
    });
    refresh().catch(error=>announce(error.message,true));
})();
