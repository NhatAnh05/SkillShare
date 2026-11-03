'use strict';
(() => {
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const path = window.location.pathname.replace(/\/$/, '') || '/';
    $$('.nav-item, .side-item, .wk-side-item').forEach(link => {
        if (link.tagName !== 'A') return;
        const target = new URL(link.href, window.location.origin).pathname.replace(/\/$/, '') || '/';
        if (path === target) {
            link.classList.add('active');
            link.setAttribute('aria-current', 'page');
        }
    });
    const csrfName = $('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
    const csrfValue = $('meta[name="_csrf"]')?.content || $('input[name="_csrf"]')?.value;
    const csrfHeaders = () => csrfValue ? {
        [csrfName]: csrfValue
    }
    : {
    }
    ;
    // Các dropdown trong header không tự chồng lên nhau.
    $$('details.menu-wrap').forEach(menu => menu.addEventListener('toggle', () => {
        if (menu.open) $$('details.menu-wrap').filter(other => other !== menu).forEach(other => {
            other.open = false;
        }
        );
    }
    ));
    document.addEventListener('click', event => {
        $$('details.menu-wrap[open]').forEach(menu => {
            if (!menu.contains(event.target)) menu.open = false;
        }
        );
    }
    );
    $$('[data-toggle-password]').forEach(button => button.addEventListener('click', () => {
        const input = $('.password-input', button.parentElement);
        input.type = input.type === 'password' ? 'text' : 'password';
        button.setAttribute('aria-label', input.type === 'password' ? 'Hiện mật khẩu' : 'Ẩn mật khẩu');
    }
    ));
    const min = $('#min-price'), max = $('#max-price');
    if (min && max) {
        const format = value => Number(value).toLocaleString('vi-VN') + 'đ';
        const fill = $('#range-fill');
        const show = () => {
            $('#price-min-label').textContent = format(min.value);
            $('#price-max-label').textContent = format(max.value);
            const range = Number(max.max) - Number(max.min);
            const left = (Number(min.value) - Number(min.min)) / range * 100;
            const right = (Number(max.value) - Number(max.min)) / range * 100;
            fill.style.left = left + '%'; fill.style.width = (right - left) + '%';
            min.style.zIndex = Number(min.value) >= Number(max.value) - 10000 ? '3' : '2';
        }
        ;
        min.addEventListener('input', () => {
            if (+min.value > +max.value) min.value = max.value; show();
        }
        );
        max.addEventListener('input', () => {
            if (+max.value < +min.value) max.value = min.value; show();
        }
        );
        show();
    }
    const skillInput = $('#skill-filter'), skillSelect = $('#skill-select');
    if (skillInput && skillSelect) {
        const options = [...skillSelect.options];
        skillInput.addEventListener('input', () => {
            const needle = skillInput.value.trim().toLocaleLowerCase('vi');
            options.forEach((option, index) => {
                option.hidden = index !== 0 && !option.text.toLocaleLowerCase('vi').includes(needle);
            }
            );
            if (skillSelect.selectedOptions[0]?.hidden) skillSelect.value = '';
        }
        );
    }
    const stars = $$('#star-options input[type="radio"]');
    if (stars.length) {
        const labels = stars.map(input => $('label[for="' + input.id + '"]'));
        let selected = +stars.find(input => input.checked)?.value || 0;
        const paint = (value, preview = false) => {
            labels.forEach((label, index) => label.classList.toggle('selected', !preview && index < value));
            labels.forEach((label, index) => label.classList.toggle('preview', preview && index < value));
            $('#star-hint').textContent = value ? value + ' / 5 sao' : 'Chọn từ 1 đến 5 sao';
        }
        ;
        stars.forEach(input => input.addEventListener('change', () => {
            selected = +input.value; paint(selected);
        }
        ));
        labels.forEach((label, index) => {
            label.addEventListener('mouseenter', () => paint(index + 1, true));
            label.addEventListener('click', () => {
                selected = index + 1; paint(selected);
            }
            );
        }
        );
        $('#star-options').addEventListener('mouseleave', () => paint(selected));
        paint(selected);
    }
    const dialog = $('#order-dialog'), dialogContent = $('#order-dialog-content');
    if (dialog && dialogContent) {
        let currentOrder = null;
        const refreshTable = async () => {
            const response = await fetch('/orders/table', {
                credentials: 'same-origin'
            }
            );
            if (!response.ok) throw new Error('Không thể cập nhật danh sách đơn hàng.');
            const doc = new DOMParser().parseFromString(await response.text(), 'text/html');
            const replacement = doc.querySelector('#orders-table');
            if (replacement) $('#orders-table').replaceWith(replacement);
            const rows = $$('[data-order-id]');
            $('#total-count').textContent = String(rows.length);
            $('#processing-count').textContent = String(rows.filter(row => $('.badge',row)?.textContent?.includes('Đang xử lý')).length);
            $('#completed-count').textContent = String(rows.filter(row => $('.badge',row)?.textContent?.includes('Hoàn thành')).length);
        }
        ;
        const load = async (orderId) => {
            const response = await fetch('/orders/' + orderId + '/details', {
                credentials: 'same-origin'
            }
            );
            if (!response.ok) throw new Error('Không thể tải chi tiết đơn hàng.');
            dialogContent.innerHTML = await response.text();  // HTML từ Thymeleaf đã escape dữ liệu người dùng.
        }
        ;
        document.addEventListener('click', async event => {
            const button = event.target.closest('[data-order-detail]');
            if (!button) return;
            currentOrder = button.dataset.orderDetail;
            dialogContent.textContent = 'Đang tải chi tiết đơn hàng...'; dialog.showModal();
            try {
                await load(currentOrder);
            } catch (error) {
                dialogContent.textContent = error.message;
            }
        }
        );
        $('#order-dialog-close').addEventListener('click', () => dialog.close());
        dialog.addEventListener('click', event => {
            if (event.target === dialog) dialog.close();
        }
        );
        dialog.addEventListener('submit', async event => {
            const form = event.target.closest('form[action$="/state"]');
            if (!form) return;
            event.preventDefault();
            const button = $('button[type="submit"]', form); button.disabled = true;
            try {
                const response = await fetch(form.action, {
                    method:'POST',credentials:'same-origin',headers:csrfHeaders(),body:new FormData(form)
                }
                );
                if (!response.ok) throw new Error('Cập nhật trạng thái thất bại hoặc đơn đã đổi trạng thái.');
                dialogContent.innerHTML = await response.text();
                await refreshTable();
            } catch (error) {
                const feedback = document.createElement('p');feedback.className='error';feedback.textContent=error.message;dialogContent.append(feedback);button.disabled=false;
            }
        }
        );
        // Review khi chưa hoàn thành không thể được kích hoạt.
        document.addEventListener('click', event => {
            if (event.target.closest('.action-icon.is-disabled')) event.preventDefault();
        }
        );
    }
    const notificationMenu = $('#notification-menu'), notificationList = $('#notification-list');
    if (notificationMenu && notificationList) {
        const badge = $('#notification-count');
        const known = new Map();
        let notificationSocket = null, notificationReconnect = null, notificationPoller = null, notificationDelay = 1000, unreadTotal = 0;
        const setCount = count => {
            const value = Number(count || 0);
            unreadTotal = value;
            badge.textContent = value > 99 ? '99+' : String(value);
            badge.hidden = value === 0;
            if($('#all-notifications-list')) $('.section-top .muted')?.replaceChildren(
                document.createTextNode(value+' thông báo chưa đọc'));
        };
        const renderNotifications = () => {
            notificationList.replaceChildren();
            if (!known.size) {
                const empty = document.createElement('p'); empty.className = 'muted'; empty.textContent = 'Chưa có thông báo.';
                notificationList.append(empty); return;
            }
            [...known.values()].sort((a,b) => String(b.createdAt).localeCompare(String(a.createdAt))).slice(0,50).forEach(item => {
                const link = document.createElement('a');
                link.className = 'notification-item' + (item.read ? '' : ' unread');
                link.href = item.url;
                link.dataset.notificationId = item.id;
                const title = document.createElement('strong'); title.textContent = item.title;
                const sender = document.createElement('small'); sender.className='notification-sender';
                sender.textContent = item.sender ? 'Từ: '+item.sender : '';
                const content = document.createElement('span'); content.textContent = item.content;
                const time = document.createElement('small'); time.textContent = item.createdAt.replace('T',' ');
                link.append(title, sender, content, time);
                const row=document.createElement('div');row.className='notification-row';row.append(link);
                if(!item.read){
                    const read=document.createElement('button');read.type='button';read.className='notification-mark-read';
                    read.textContent='Đánh dấu đã đọc';read.dataset.notificationReadId=item.id;
                    read.setAttribute('aria-label','Đánh dấu đã đọc: '+item.title);row.append(read);
                }
                notificationList.append(row);
            });
        };
        const loadNotifications = async (toastNew = false) => {
            const response = await fetch('/notifications', {credentials:'same-origin'});
            if (!response.ok) throw new Error('Không thể tải thông báo.');
            const result = await response.json();
            const previousIds = new Set(known.keys());
            known.clear(); result.items.forEach(item => known.set(String(item.id), item));
            setCount(result.unreadCount); renderNotifications();
            if (toastNew) result.items.filter(item => !item.read && !previousIds.has(String(item.id))).slice(0,3).forEach(showToast);
        };
        const showToast = item => {
            const toast = document.createElement('button'); toast.type='button'; toast.className='notification-toast';
            toast.style.bottom=(80+document.querySelectorAll('.notification-toast').length*72)+'px';
            const title = document.createElement('strong'); title.textContent=item.title;
            const content = document.createElement('span'); content.textContent=item.content;
            toast.append(title,content); document.body.append(toast);
            toast.addEventListener('click',async()=>{
                try{if(item.id)await markIndividual(item.id);window.location.assign(item.url);}
                catch(error){toast.disabled=false;content.textContent=error.message;}
            });
            window.setTimeout(()=>toast.remove(),6000);
        };
        const applyNotification = item => {
            const key=String(item.id), isNew=!known.has(key);
            known.set(key,item); renderNotifications();
            if(isNew&&!item.read)showToast(item);
            if(item.unreadCount!==undefined)setCount(item.unreadCount);
            else if(isNew&&!item.read)setCount(unreadTotal+1);
        };
        const connectNotifications = () => {
            if(notificationSocket&&(notificationSocket.readyState===WebSocket.OPEN||notificationSocket.readyState===WebSocket.CONNECTING))return;
            const scheme=location.protocol==='https:'?'wss:':'ws:';
            notificationSocket=new WebSocket(scheme+'//'+location.host+'/ws/notifications');
            notificationSocket.addEventListener('open',()=>{notificationDelay=1000;clearInterval(notificationPoller);notificationPoller=null;});
            notificationSocket.addEventListener('message',event=>{
                let payload;try{payload=JSON.parse(event.data);}catch{return;}
                if(payload.type==='snapshot'){
                    known.clear();(payload.items||[]).forEach(item=>known.set(String(item.id),item));
                    setCount(payload.unreadCount);renderNotifications();
                }else if(payload.type==='notification'){
                    applyNotification(payload);
                    window.dispatchEvent(new CustomEvent("skillshare:notification",{detail:payload}));
                    $('#chat-widget')&&fetch('/chat/conversations',{credentials:'same-origin'}).then(response=>response.ok?response.json():[]).then(list=>{
                        const chatCount=list.reduce((sum,item)=>sum+Number(item.unreadCount||0),0), chatBadge=$('#chat-unread-count');
                        if(chatBadge){chatBadge.textContent=chatCount>99?'99+':String(chatCount);chatBadge.hidden=chatCount===0;}
                        const headerBadge=$('#chat-header-unread-count');
                        if(headerBadge){headerBadge.textContent=chatCount>99?'99+':String(chatCount);headerBadge.hidden=chatCount===0;}
                    }).catch(()=>{});
                }else if(payload.type==='read'){
                    if(payload.id!==undefined&&known.has(String(payload.id)))known.get(String(payload.id)).read=true;
                    if(payload.id===undefined)known.forEach(item=>item.read=true);
                    setCount(payload.unreadCount);renderNotifications();
                    $$('#all-notifications-list [data-notification-id]').forEach(item=>{
                        if(payload.id===undefined||item.dataset.notificationId===String(payload.id))item.classList.remove('unread');
                    });
                    $$('#all-notifications-list [data-notification-read-id]').forEach(button=>{
                        if(payload.id===undefined||button.dataset.notificationReadId===String(payload.id))button.remove();
                    });
                }
            });
            notificationSocket.addEventListener('close',()=>{
                notificationSocket=null;
                if(!notificationPoller)notificationPoller=setInterval(()=>loadNotifications().catch(()=>{}),30000);
                clearTimeout(notificationReconnect);
                notificationReconnect=setTimeout(connectNotifications,notificationDelay);
                notificationDelay=Math.min(notificationDelay*2,15000);
            });
        };
        const markIndividual=async(id)=>{
            const response=await fetch('/notifications/'+id+'/read',{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
            if(!response.ok)throw new Error('Không thể đánh dấu thông báo đã đọc.');
            const result=await response.json();
            if(known.has(String(id)))known.get(String(id)).read=true;
            setCount(result.unreadCount);renderNotifications();
            $$('#all-notifications-list [data-notification-id]').filter(item=>item.dataset.notificationId===String(id))
                .forEach(item=>item.classList.remove('unread'));
            $$('#all-notifications-list [data-notification-read-id]').filter(item=>item.dataset.notificationReadId===String(id))
                .forEach(item=>item.remove());
        };
        document.addEventListener('click',async event=>{
            const read=event.target.closest('[data-notification-read-id]');if(!read)return;
            read.disabled=true;
            try{await markIndividual(read.dataset.notificationReadId);}
            catch(error){read.disabled=false;showToast({title:'Lỗi',content:error.message,url:'/notifications/all'});}
        });
        notificationList.addEventListener('click',async event=>{
            const link=event.target.closest('[data-notification-id]');if(!link)return;
            event.preventDefault();
            const id=link.dataset.notificationId, item=known.get(String(id));
            try{
                const response=await fetch('/notifications/'+id+'/read',{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
                if(!response.ok)throw new Error('Không thể đánh dấu thông báo đã đọc.');
                const result=await response.json();if(item)item.read=true;setCount(result.unreadCount);renderNotifications();
                window.location.assign(link.href);
            }catch(error){showToast({title:'Lỗi',content:error.message,url:link.href});}
        });
        $('#notification-read-all')?.addEventListener('click',async()=>{
            try{
                const response=await fetch('/notifications/read-all',{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
                if(!response.ok)throw new Error('Không thể cập nhật thông báo.');
                const result=await response.json();
                known.forEach(item=>item.read=true);setCount(result.unreadCount);renderNotifications();
            }catch(error){showToast({title:'Lỗi',content:error.message,url:'/notifications'});}
        });
        $('#all-notifications-read')?.addEventListener('click',async()=>{
            const button=$('#all-notifications-read');button.disabled=true;
            try{
                const response=await fetch('/notifications/read-all',{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
                if(!response.ok)throw new Error('Không thể cập nhật thông báo.');
                $$('#all-notifications-list [data-notification-id]').forEach(item=>item.classList.remove('unread'));
                $('.section-top .muted')?.replaceChildren(document.createTextNode('0 thông báo chưa đọc'));
                setCount((await response.json()).unreadCount);
                $$('#all-notifications-list [data-notification-read-id]').forEach(button=>button.remove());
            }catch(error){showToast({title:'Lỗi',content:error.message,url:'/notifications/all'});}
            finally{button.disabled=false;}
        });
        $('#all-notifications-list')?.addEventListener('click',async event=>{
            const link=event.target.closest('[data-notification-id]');
            if(!link)return;
            event.preventDefault();
            try{
                const response=await fetch('/notifications/'+link.dataset.notificationId+'/read',{
                    method:'POST',credentials:'same-origin',headers:csrfHeaders()
                });
                if(!response.ok)throw new Error('Không thể đánh dấu thông báo đã đọc.');
                link.classList.remove('unread');
                window.location.assign(link.href);
            }catch(error){showToast({title:'Lỗi',content:error.message,url:link.href});}
        });
        loadNotifications().catch(()=>{}).finally(connectNotifications);
    }
    // Chat dạng popup chung; không cần điều hướng tới /chat.
    const fab = $('#chat-fab'), popup = $('#chat-popup');
    if (fab && popup) {
        const contacts = $('#chat-contacts'), history = $('#chat-history'), peer = $('#chat-peer');
        const orderLink = $('#chat-order-link');
        const form = $('#chat-send-form'), feedback = $('#chat-feedback'), unreadBadge = $('#chat-unread-count');
        const currentUserId = Number($('#chat-widget').dataset.currentUserId);
        const currentRole = $('#chat-widget').dataset.currentRole;
        let conversationId = null, poller = null, socket = null, reconnectTimer = null, reconnectDelay = 1000, knownConversationIds = [], contactSequence=0, messagePending=null;
        const fullInbox = document.body.classList.contains('chat-inbox-page');
        const say = text => {
            feedback.hidden = !text;feedback.textContent=text;
        }
        ;
        const message = item => {
            const block=document.createElement('div');block.className='chat-message'+(item.own?' own':'');block.dataset.id=item.id;
            const body=document.createElement('span');body.textContent=item.content;
            const time=document.createElement('small');time.textContent=(item.own?'Bạn':item.sender)+' · '+item.time+(item.own?(item.read?' · Đã đọc':' · Đã gửi'):'');
            block.append(body,time);return block;
        }
        ;
        const sendSocket = payload => {
            if(socket?.readyState===WebSocket.OPEN)socket.send(JSON.stringify(payload));
        };
        const updateContacts = async (preferred, selectPreferred = false) => {
            const sequence=++contactSequence;
            const response=await fetch('/chat/conversations',{credentials:'same-origin'});
            if(!response.ok)throw new Error('Không thể tải danh sách trò chuyện.');
            const list=await response.json();
            if(sequence!==contactSequence)return selectPreferred?updateContacts(preferred,true):list;
            if(conversationId&&!list.some(c=>String(c.id)===String(conversationId))){conversationId=null;history.replaceChildren();form.hidden=true;peer.textContent='Hội thoại đã được chuyển hoặc bạn không còn quyền truy cập.';}
            knownConversationIds=list.map(item=>item.id);
            if(socket?.readyState===WebSocket.OPEN&&conversationId)sendSocket({type:'subscribe',conversationId});
            contacts.replaceChildren();
            for(const c of list) {
                const button=document.createElement('button');button.type='button';button.className='chat-contact';button.dataset.id=c.id;
                const avatar=document.createElement('span');avatar.className='chat-contact-avatar';
                if(c.participantAvatar){const image=document.createElement('img');image.src=c.participantAvatar;image.alt='';avatar.append(image);}
                else avatar.textContent=(c.participantName||'?').slice(0,1).toLocaleUpperCase('vi');
                const details=document.createElement('span');details.className='chat-contact-details';
                const name=document.createElement('strong');name.textContent=c.participantName;
                const preview=document.createElement('small');preview.textContent=c.lastContent||'Chưa có tin nhắn';
                const time=document.createElement('small');time.className='chat-contact-time';time.textContent=c.lastSentAt?new Date(c.lastSentAt).toLocaleString('vi-VN'):'';
                details.append(name,preview,time);
                const badge=document.createElement('span');badge.className='chat-contact-count';badge.textContent=c.unreadCount>99?'99+':String(c.unreadCount);badge.hidden=!c.unreadCount;
                button.append(avatar,details,badge);button.classList.toggle('active',String(c.id)===String(conversationId));
                button.addEventListener('click',()=>select(c));contacts.append(button);
            }
            if(selectPreferred && list.length) {
                const chosen=list.find(c=>String(c.id)===String(preferred))||list[0];
                await select(chosen);
            } else if(selectPreferred && !list.length) {
                conversationId=null;peer.textContent='Chưa có cuộc trò chuyện';history.replaceChildren();form.hidden=true;
            }
            const count=list.reduce((total,item)=>total+Number(item.unreadCount||0),0);
            unreadBadge.textContent=count>99?'99+':String(count);unreadBadge.hidden=count===0;
            const headerBadge=$('#chat-header-unread-count');
            if(headerBadge){headerBadge.textContent=count>99?'99+':String(count);headerBadge.hidden=count===0;}
            $$('[data-chat-unread-badge]').forEach(badge=>{
                badge.textContent=count>99?'99+':String(count);
                badge.hidden=count===0;
            });
            return list;
        };
        const nearHistoryEnd = () => history.scrollHeight-history.scrollTop-history.clientHeight < 80;
        const connectSocket = () => {
            if(socket && (socket.readyState===WebSocket.OPEN||socket.readyState===WebSocket.CONNECTING))return;
            const scheme=location.protocol==='https:'?'wss:':'ws:';
            socket=new WebSocket(scheme+'//'+location.host+'/ws/chat');
            socket.addEventListener('open',()=>{
                reconnectDelay=1000;
                if(conversationId)sendSocket({type:'subscribe',conversationId});
                clearInterval(poller);poller=null;
                updateContacts(conversationId,false).then(()=>{if(conversationId&&!popup.hidden)return refreshMessages(false);}).catch(error=>say(error.message));
            });
            socket.addEventListener('message',event=>{
                let item;
                try { item=JSON.parse(event.data); } catch { return; }
                if(item.type==='error'){say(item.message||'Không thể xử lý tin nhắn.');messagePending=null;$('button[type="submit"]',form).disabled=false;return;}
                if(item.type==='inbox'){updateContacts(conversationId,false).catch(error=>say(error.message));return;}
                if(item.type==='read'){
                    if(String(item.conversationId)===String(conversationId)&&Number(item.readerId)!==currentUserId)
                        $$('.chat-message.own small',history).forEach(status=>{status.textContent=status.textContent.replace('Đã gửi','Đã đọc');});
                    updateContacts(conversationId,false).catch(error=>say(error.message));
                    return;
                }
                if(item.type!=='message')return;
                item.own=Number(item.senderId)===currentUserId;
                if(item.own&&messagePending&&String(item.conversationId)===String(messagePending.conversationId)&&item.content===messagePending.content){
                    if($('input[name="content"]',form).value.trim()===messagePending.content)$('input[name="content"]',form).value='';
                    messagePending=null;$('button[type="submit"]',form).disabled=false;
                }
                if(String(item.conversationId)===String(conversationId) && !popup.hidden){
                    if(!history.querySelector('[data-id="'+item.id+'"]')){
                        const shouldScroll=nearHistoryEnd();
                        history.append(message(item));
                        if(shouldScroll)history.scrollTop=history.scrollHeight;
                    }
                    if(!item.own&&nearHistoryEnd()&&!document.hidden)markConversationRead();
                    else if(!item.own)$('#chat-new-messages').hidden=false;
                }
                updateContacts(conversationId,false).catch(error=>say(error.message));
            });
            socket.addEventListener('close',()=>{
                socket=null;
                if(!poller)poller=setInterval(()=>{
                    updateContacts(conversationId,false).catch(error=>say(error.message));
                    if(conversationId&&!popup.hidden)refreshMessages(false).catch(error=>say(error.message));
                },3000);
                clearTimeout(reconnectTimer);
                reconnectTimer=setTimeout(connectSocket,reconnectDelay);
                reconnectDelay=Math.min(reconnectDelay*2,15000);
            });
            socket.addEventListener('error',()=>say('Mất kết nối chat. Đang thử kết nối lại...'));
        };
        const markConversationRead=()=>{
            if(!conversationId||popup.hidden||document.hidden||!nearHistoryEnd())return;
            if(socket?.readyState===WebSocket.OPEN)sendSocket({type:'read',conversationId});
            else fetch('/chat/'+conversationId+'/read',{method:'POST',credentials:'same-origin',headers:csrfHeaders()}).then(()=>updateContacts(conversationId,false)).catch(()=>{});
            $('#chat-new-messages').hidden=true;
        };
        history.addEventListener('scroll',markConversationRead,{passive:true});
        document.addEventListener('visibilitychange',()=>{if(!document.hidden){updateContacts(conversationId,false).catch(()=>{});markConversationRead();}});
        $('#chat-new-messages').addEventListener('click',()=>{history.scrollTop=history.scrollHeight;markConversationRead();});
        const refreshMessages = async (scrollToEnd = false) => {
            if(!conversationId||popup.hidden)return;
            const selectedId=conversationId, oldTop=history.scrollTop, wasNearEnd=nearHistoryEnd();
            const response=await fetch('/chat/'+selectedId+'/messages',{credentials:'same-origin'});
            if(!response.ok)throw new Error('Không thể tải tin nhắn.');
            const items=await response.json();if(String(selectedId)!==String(conversationId))return;
            for(const item of items){const old=history.querySelector('[data-id="'+item.id+'"]');if(old){if(item.read)old.querySelector('small').textContent=old.querySelector('small').textContent.replace('Đã gửi','Đã đọc');}else history.append(message(item));}
            if(scrollToEnd||wasNearEnd)history.scrollTop=history.scrollHeight;else history.scrollTop=oldTop;
            if(items.length===50)$('#chat-load-older').hidden=false;
            markConversationRead();await updateContacts(conversationId,false);
        };
        $('#chat-load-older').addEventListener('click',async event=>{
            const button=event.currentTarget, before=history.querySelector('[data-id]')?.dataset.id, id=conversationId;if(!before||!id)return;
            button.disabled=true;
            try{const response=await fetch('/chat/'+id+'/messages?beforeId='+before,{credentials:'same-origin'});if(!response.ok)throw new Error('Không tải được tin nhắn cũ.');
                const items=await response.json();if(id!==conversationId)return;const height=history.scrollHeight,top=history.scrollTop;
                const nodes=items.filter(item=>!history.querySelector('[data-id="'+item.id+'"]')).map(message);history.prepend(...nodes);history.scrollTop=top+history.scrollHeight-height;
                button.hidden=items.length<50;
            }catch(error){say(error.message);}finally{button.disabled=false;}
        });
        const select = async c => {
            conversationId=c.id;peer.textContent=c.participantName;form.hidden=false;history.replaceChildren();history.dataset.lastMessage='';
            $('#chat-load-older').hidden=true;$('#chat-new-messages').hidden=true;
            $('#chat-documents').hidden=false;
            window.dispatchEvent(new CustomEvent('skillshare:conversation',{detail:{id:c.id}}));
            if(orderLink){orderLink.hidden=!c.orderId;orderLink.href=c.orderId?'/orders/'+c.orderId+'/progress':'#';}
            $$('.chat-contact',contacts).forEach(button=>button.classList.toggle('active',button.dataset.id===String(c.id)));
            try {
                sendSocket({type:'subscribe',conversationId});
                await refreshMessages(true);say('');
            } catch(error) {
                say(error.message);
            }
        }
        ;
        const open=async(preferred)=> {
            popup.hidden=false;fab.setAttribute('aria-expanded','true');
            try {
                const qs=new URLSearchParams(location.search);
                const list=await updateContacts(preferred||qs.get('conversationId')||conversationId,true);
                if(!list.length){connectSocket();say('');return;}
                connectSocket();say('');
            } catch(error) {
                say(error.message);
            }
        }
        ;
        const close=()=> {
            if(fullInbox)return;
            popup.hidden=true;fab.setAttribute('aria-expanded','false');
        }
        ;
        fab.addEventListener('click',()=>popup.hidden?open():close());
        $('#chat-close').addEventListener('click',close);
        $$('[data-open-chat]').forEach(button=>button.addEventListener('click',event=>{event.preventDefault();open(button.dataset.conversationId);}));
        $('#chat-support-start')?.addEventListener('click',async()=>{
            if(currentRole==='ADMIN')return;
            try{
                const response=await fetch('/chat/support/start',{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
                if(!response.ok)throw new Error('Không thể mở yêu cầu hỗ trợ.');
                const result=await response.json();await open(result.conversationId);
            }catch(error){say(error.message);}
        });
        $$('form[data-chat-start]').forEach(chatForm=>chatForm.addEventListener('submit',async event=>{
            event.preventDefault();
            const button=$('button[type="submit"]',chatForm);button.disabled=true;
            try{
                const response=await fetch(chatForm.action,{method:'POST',credentials:'same-origin',headers:csrfHeaders()});
                if(!response.ok)throw new Error('Không thể mở cuộc trò chuyện với Provider.');
                const result=await response.json();await open(result.conversationId);
            }catch(error){await open();say(error.message);}
            finally{button.disabled=false;}
        }));
        const adminSearch=$('#admin-chat-user-search'), adminResults=$('#admin-chat-user-results');
        if(adminSearch&&adminResults) {
            let searchTimer=null, requestSequence=0;
            const status=$('#admin-chat-user-status');
            adminSearch.addEventListener('input',()=>{
                clearTimeout(searchTimer);
                const query=adminSearch.value.trim();
                adminResults.replaceChildren();
                if(query.length<2&&!/^\d+$/.test(query)){status.textContent='Nhập tên/email hoặc ID.';return;}
                status.textContent='Đang tìm...';
                searchTimer=setTimeout(async()=>{
                    const sequence=++requestSequence;
                    try {
                        const response=await fetch('/chat/admin/users?q='+encodeURIComponent(query),{credentials:'same-origin'});
                        if(!response.ok)throw new Error('Không thể tìm người dùng.');
                        const users=await response.json();
                        if(sequence!==requestSequence)return;
                        adminResults.replaceChildren();
                        users.forEach(user=>{
                            const row=document.createElement('div');row.className='wk-list-row';
                            const label=document.createElement('span');label.textContent=user.name+' · '+user.role+' · '+user.email;
                            const button=document.createElement('button');button.type='button';button.className='wk-btn primary small';
                            button.textContent='Mở hội thoại';
                            button.addEventListener('click',async()=>{
                                button.disabled=true;
                                try {
                                    const result=await fetch('/chat/admin/users/'+user.id+'/start',{
                                        method:'POST',credentials:'same-origin',headers:csrfHeaders()
                                    });
                                    if(!result.ok)throw new Error(result.status===409?'Hội thoại đang do Admin khác phụ trách.':'Không thể mở hội thoại.');
                                    const conversation=await result.json();
                                    if(popup.hidden)await open(conversation.conversationId);
                                    else {await updateContacts(conversation.conversationId,true);connectSocket();}
                                    status.textContent='Đã mở hội thoại với '+user.name+'.';
                                }catch(error){status.textContent=error.message;}
                                finally{button.disabled=false;}
                            });
                            row.append(label,button);adminResults.append(row);
                        });
                        status.textContent=users.length?'Chọn người dùng để mở hội thoại.':'Không tìm thấy người dùng đang hoạt động.';
                    }catch(error){if(sequence===requestSequence)status.textContent=error.message;}
                },250);
            });
        }
        $$('[data-claim-support],[data-release-support]').forEach(button=>button.addEventListener('click',async()=>{
            const action=button.hasAttribute('data-claim-support')?'claim':'release';
            button.disabled=true;
            try {
                const response=await fetch('/chat/support/'+button.dataset.conversationId+'/'+action,{
                    method:'POST',credentials:'same-origin',headers:csrfHeaders()
                });
                if(!response.ok)throw new Error('Không thể cập nhật phân công. Hội thoại có thể đã được người khác nhận.');
                button.textContent=action==='claim'?'Đã tiếp nhận':'Đã trả về hàng đợi';
                button.disabled=true;
            }catch(error){say(error.message);button.disabled=false;}
        }));
        $$('[data-assignment-history]').forEach(button=>button.addEventListener('click',async()=>{
            const target=$$('[data-assignment-history-list]').find(row=>row.dataset.conversationId===button.dataset.conversationId);
            if(!target)return;
            try {
                const response=await fetch('/chat/support/'+button.dataset.conversationId+'/assignment-history',{credentials:'same-origin'});
                if(!response.ok)throw new Error('Không thể tải lịch sử phân công.');
                const records=await response.json();
                target.textContent=records.map(record=>record.time.replace('T',' ')+' · '+record.admin+' · '+record.action).join(' | ')
                    ||'Chưa có thay đổi phân công được ghi nhận.';
                target.hidden=false;
            }catch(error){target.textContent=error.message;target.hidden=false;}
        }));
        form.addEventListener('submit',async event=>{
            event.preventDefault();if(!conversationId)return;
            const input=$('input[name="content"]',form),content=input.value.trim();if(!content)return;
            const button=$('button[type="submit"]',form);if(button.disabled)return;button.disabled=true;
            const payload=messagePending&&messagePending.content===content&&messagePending.conversationId===conversationId?messagePending:
                {conversationId,content,clientKey:crypto.randomUUID()};messagePending=payload;
            try{
                // HTTP is the durable write/ack path; committed messages are pushed by WebSocket to every tab.
                const response=await fetch('/chat/'+conversationId+'/send-ajax',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json',...csrfHeaders()},body:JSON.stringify(payload)});
                if(!response.ok)throw new Error('Không gửi được tin nhắn. Nội dung được giữ để thử lại.');
                if(input.value.trim()===content)input.value='';messagePending=null;
                await refreshMessages();say('');
            }catch(error){say(error.message);}finally{button.disabled=false;input.focus();}
        });
        window.addEventListener('skillshare:notification',event=>{
            if(['CHAT_MESSAGE','WORK_DOCUMENT','SUPPORT_REQUEST'].includes(event.detail.notificationType))updateContacts(conversationId,false).catch(()=>{});
        });
        const query=new URLSearchParams(location.search);
        updateContacts(null,false).then(()=>{
            connectSocket();
            if(fullInbox||query.get('chat')==='open')open(query.get('conversationId'));
        }).catch(error=>say(error.message));
    }
    const campaignForm = $('#admin-notification-campaigns');
    if (campaignForm) {
        const audience=$('#campaign-audience'), picker=$('#campaign-recipient-picker');
        const search=$('#campaign-recipient-search'), results=$('#campaign-recipient-results');
        const selectedView=$('#campaign-selected-recipients'), previewButton=$('#campaign-preview');
        const sendButton=$('#campaign-send'), previewText=$('#campaign-preview-result');
        const sendStatus=$('#campaign-send-status');
        const selected=new Map();
        const newRequestKey=()=>window.crypto?.randomUUID?window.crypto.randomUUID():
            'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{
                const r=Math.random()*16|0;return(c==='x'?r:(r&3|8)).toString(16);
            });
        let requestKey=newRequestKey(), sending=false, revision=0;
        let pendingPayload=null;
        let previewed=null, searchTimer=null, requestSequence=0;
        const recipientIds=()=>[...selected.keys()];
        const invalidatePreview=()=>{revision++;previewed=null;sendButton.disabled=true;previewText.textContent='';};
        const renderSelected=()=>{
            selectedView.replaceChildren();
            selected.forEach((user,id)=>{
                const chip=document.createElement('span');chip.className='wk-pill blue';
                chip.append(document.createTextNode(user.name+' · '+user.email+' '));
                const remove=document.createElement('button');remove.type='button';remove.className='link-button';
                remove.setAttribute('aria-label','Bỏ '+user.name);remove.textContent='×';
                remove.addEventListener('click',()=>{selected.delete(id);renderSelected();invalidatePreview();});
                chip.append(remove);selectedView.append(chip);
            });
        };
        audience.addEventListener('change',()=>{
            picker.hidden=audience.value!=='SELECTED';
            invalidatePreview();
        });
        ['#campaign-title','#campaign-type','#campaign-content','#campaign-target-url']
            .forEach(selector=>$(selector).addEventListener('input',invalidatePreview));
        search.addEventListener('input',()=>{
            clearTimeout(searchTimer);
            const query=search.value.trim();
            results.replaceChildren();
            requestSequence++;
            if(query.length<2&&!/^\d+$/.test(query))return;
            searchTimer=setTimeout(async()=>{
                const sequence=++requestSequence;
                try {
                    const response=await fetch('/admin/notifications/recipients?q='+encodeURIComponent(query),{credentials:'same-origin'});
                    if(!response.ok)throw new Error('Không thể tìm người nhận.');
                    const users=await response.json();
                    if(sequence!==requestSequence)return;
                    users.forEach(user=>{
                        if(selected.has(user.id))return;
                        const row=document.createElement('div');row.className='wk-list-row';
                        const label=document.createElement('span');label.textContent=user.name+' · '+user.role+' · '+user.email+' · #'+user.id;
                        const add=document.createElement('button');add.type='button';add.className='wk-btn small';
                        add.textContent='Chọn';
                        add.addEventListener('click',()=>{
                            selected.set(user.id,user);renderSelected();results.replaceChildren();invalidatePreview();
                        });
                        row.append(label,add);results.append(row);
                    });
                    if(!results.childElementCount){const empty=document.createElement('p');empty.className='wk-card-desc';empty.textContent='Không có tài khoản phù hợp chưa được chọn.';results.append(empty);}
                } catch(error) {
                    const message=document.createElement('p');message.className='error';message.textContent=error.message;results.replaceChildren(message);
                }
            },250);
        });
        previewButton.addEventListener('click',async()=>{
            const previewRevision=revision;
            previewButton.disabled=true;sendStatus.textContent='';
            try{
                const response=await fetch('/admin/notifications/preview',{
                    method:'POST',credentials:'same-origin',
                    headers:{'Content-Type':'application/json',...csrfHeaders()},
                    body:JSON.stringify({audience:audience.value,recipientIds:audience.value==='SELECTED'?recipientIds():[]})
                });
                if(!response.ok)throw new Error((await response.text())||'Không thể xác định người nhận.');
                const preview=await response.json();
                if(previewRevision!==revision)return;
                previewed=preview;
                previewed.audience=audience.value;
                previewed.ids=recipientIds().join(',');
                previewText.textContent='Sẽ gửi đến '+previewed.recipientCount+' tài khoản đủ điều kiện.';
                sendButton.disabled=previewed.recipientCount===0;
            }catch(error){invalidatePreview();sendStatus.textContent=error.message;}
            finally{previewButton.disabled=false;}
        });
        sendButton.addEventListener('click',async()=>{
            if(sending)return;
            const ids=recipientIds();
            if(!previewed||previewed.audience!==audience.value||previewed.ids!==ids.join(',')){
                invalidatePreview();sendStatus.textContent='Danh sách đã thay đổi. Hãy xem trước số người nhận lại.';return;
            }
            const count=previewed.recipientCount;
            if(!window.confirm('Gửi thông báo này đến '+count+' tài khoản? Thao tác sẽ được ghi lịch sử và không thể thu hồi.'))return;
            const draft={title:$('#campaign-title').value,content:$('#campaign-content').value,
                type:$('#campaign-type').value,audience:audience.value,
                recipientIds:audience.value==='SELECTED'?ids:[],targetUrl:$('#campaign-target-url').value};
            if(!draft.title.trim()||!draft.content.trim()){
                sendStatus.textContent='Hãy nhập tiêu đề và nội dung.';return;
            }
            // Preserve the same key after a lost response or a manual page reload.
            const serialized=JSON.stringify(draft);
            try {
                const saved=JSON.parse(sessionStorage.getItem('skillshare-campaign-pending')||'null');
                if(saved?.payload===serialized)requestKey=saved.key;
                else if(pendingPayload!==serialized)requestKey=newRequestKey();
                sessionStorage.setItem('skillshare-campaign-pending',JSON.stringify({payload:serialized,key:requestKey}));
            } catch (_) { if(pendingPayload!==serialized)requestKey=newRequestKey(); }
            pendingPayload=serialized;
            sending=true;sendButton.disabled=true;previewButton.disabled=true;
            const controls=[...campaignForm.querySelectorAll('input,select,textarea,button')];
            const previousDisabled=controls.map(control=>control.disabled);
            controls.forEach(control=>control.disabled=true);
            try{
                const response=await fetch('/admin/notifications/send',{
                    method:'POST',credentials:'same-origin',
                    headers:{'Content-Type':'application/json',...csrfHeaders()},
                    body:JSON.stringify({...draft,requestKey})
                });
                if(!response.ok)throw new Error((await response.text())||'Không thể tạo chiến dịch.');
                const result=await response.json();
                sendStatus.textContent='Đã lưu chiến dịch #'+result.campaignId+'; đang phân phối theo hàng đợi.';
                try { sessionStorage.removeItem('skillshare-campaign-pending'); } catch (_) { }
                window.location.reload();
            }catch(error){
                sendStatus.textContent=error.message;
                controls.forEach((control,index)=>control.disabled=previousDisabled[index]);
                sendButton.disabled=false;previewButton.disabled=false;
            }finally{sending=false;}
        });
        const loadCampaignDetails=async(id,page=0,append=false)=>{
            const panel=$('#campaign-detail-panel');panel.hidden=false;
            try{
                const response=await fetch('/admin/notifications/campaigns/'+id+'?page='+page,{credentials:'same-origin'});
                if(!response.ok)throw new Error('Không thể tải chi tiết chiến dịch.');
                const detail=await response.json();
                if(!append){
                    panel.replaceChildren();
                    const heading=document.createElement('h2');heading.className='wk-card-title';
                    heading.textContent='#'+detail.campaign.id+' · '+detail.campaign.title;panel.append(heading);
                    const body=document.createElement('p');body.className='wk-card-desc';body.textContent=detail.content;panel.append(body);
                    const stats=document.createElement('p');stats.className='wk-card-desc';
                    stats.textContent='Người nhận: '+detail.campaign.recipientCount+' · Đã đọc: '+detail.campaign.readCount+
                        ' · Chưa đọc: '+detail.campaign.unreadCount+' · Thất bại: '+detail.campaign.failedCount+
                        ' · Đã lưu hộp thư: '+detail.campaign.storedCount+' · Chờ xử lý: '+detail.campaign.queuedCount+
                        ' · Đã phát tới kết nối: '+detail.campaign.websocketSentCount+' · Offline: '+detail.campaign.offlineCount;
                    panel.append(stats);
                    const meta=document.createElement('p');meta.className='wk-card-desc';
                    meta.textContent='Người gửi: '+detail.campaign.sender+' · Tạo: '+detail.campaign.createdAt.replace('T',' ')+
                        ' · Gửi: '+detail.campaign.sentAt.replace('T',' ')+' · '+detail.campaign.status;
                    panel.append(meta);
                    if(detail.targetUrl){const target=document.createElement('p');target.textContent='Điều hướng: '+detail.targetUrl;panel.append(target);}
                    const auditResponse=await fetch('/admin/notifications/campaigns/'+id+'/audit',{credentials:'same-origin'});
                    if(auditResponse.ok){
                        const entries=await auditResponse.json();
                        const log=document.createElement('p');log.className='wk-card-desc';
                        log.textContent='Nhật ký: '+entries.map(entry=>entry.createdAt.replace('T',' ')+' · '+entry.actor+' · '+
                            (entry.action==='SEND'?'Gửi':'Thử lại')+' · '+entry.recipientCount+' người nhận').join(' | ');
                        panel.append(log);
                    }
                }else panel.querySelector('[data-campaign-next-page]')?.remove();
                detail.recipients.forEach(user=>{
                    const row=document.createElement('div');row.className='wk-list-row';
                    const label=document.createElement('span');
                    label.textContent=user.name+' · '+user.email+' · '+(user.read?'Đã đọc':'Chưa đọc')+
                        ' · '+({QUEUED:'Chờ lưu',STORED:'Đã lưu hộp thư',ONLINE:'Đã phát tới kết nối',OFFLINE:'Đã lưu, không có kết nối',
                            FAILED:'Lỗi lưu thông báo',WS_FAILED:'Đã lưu, lỗi phát realtime'}[user.deliveryStatus]||user.deliveryStatus)+
                        (user.error?' · '+user.error:'');
                    row.append(label);panel.append(row);
                });
                const shown=document.createElement('p');shown.className='wk-card-desc';
                shown.textContent='Hiển thị '+Math.min((page+1)*detail.pageSize,detail.totalRows)+' / '+detail.totalRows+' người nhận.';
                panel.append(shown);
                if(page+1<Math.ceil(detail.totalRows/detail.pageSize)){
                    const more=document.createElement('button');more.type='button';more.className='wk-btn';
                    more.dataset.campaignNextPage='';more.textContent='Tải thêm người nhận';
                    more.addEventListener('click',()=>loadCampaignDetails(id,page+1,true));panel.append(more);
                }
            }catch(error){const message=document.createElement('p');message.className='error';message.textContent=error.message;panel.append(message);}
        };
        $$('[data-campaign-detail]').forEach(button=>button.addEventListener('click',()=>{
            loadCampaignDetails(button.dataset.campaignId);
        }));
        $$('[data-campaign-retry]').forEach(button=>button.addEventListener('click',async()=>{
            if(!window.confirm('Thử lại các trường hợp phân phối đang lỗi/chờ xử lý?'))return;
            button.disabled=true;
            try{
                const response=await fetch('/admin/notifications/campaigns/'+button.dataset.campaignId+'/retry',{
                    method:'POST',credentials:'same-origin',headers:csrfHeaders()
                });
                if(!response.ok)throw new Error('Không có mục cần thử lại hoặc thao tác thất bại.');
                window.location.reload();
            }catch(error){sendStatus.textContent=error.message;button.disabled=false;}
        }));
    }
})();
