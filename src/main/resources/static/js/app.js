'use strict';
(() => {
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
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
    // Chat dạng popup chung; không cần điều hướng tới /chat.
    const fab = $('#chat-fab'), popup = $('#chat-popup');
    if (fab && popup) {
        const contacts = $('#chat-contacts'), history = $('#chat-history'), peer = $('#chat-peer');
        const form = $('#chat-send-form'), feedback = $('#chat-feedback');
        let conversationId = null, poller = null;
        const say = text => {
            feedback.hidden = !text;feedback.textContent=text;
        }
        ;
        const message = item => {
            const block=document.createElement('div');block.className='chat-message'+(item.own?' own':'');
            const body=document.createElement('span');body.textContent=item.content;
            const time=document.createElement('small');time.textContent=item.time;
            block.append(body,time);return block;
        }
        ;
        const refreshMessages = async () => {
            if (!conversationId || popup.hidden) return;
            const response=await fetch('/chat/'+conversationId+'/messages', {
                credentials:'same-origin'
            }
            );
            if (!response.ok)throw new Error('Không thể tải tin nhắn.');
            const items=await response.json();
            if (history.dataset.lastMessage === String(items.at(-1)?.id||0)) return;
            history.replaceChildren(...items.map(message));history.dataset.lastMessage=String(items.at(-1)?.id||0);
            history.scrollTop=history.scrollHeight;
        }
        ;
        const select = async c => {
            conversationId=c.id;peer.textContent=c.providerName;form.hidden=false;history.dataset.lastMessage='';
            $$('.chat-contact',contacts).forEach(button=>button.classList.toggle('active',button.dataset.id===String(c.id)));
            try {
                await refreshMessages();say('');
            } catch(error) {
                say(error.message);
            }
        }
        ;
        const loadContacts=async(preferred)=> {
            const response=await fetch('/chat/conversations', {
                credentials:'same-origin'
            }
            );
            if(!response.ok)throw new Error('Không thể tải danh sách trò chuyện.');
            const list=await response.json();contacts.replaceChildren();
            for(const c of list) {
                const button=document.createElement('button');button.type='button';button.className='chat-contact';button.dataset.id=c.id;button.textContent=c.providerName;button.addEventListener('click',()=>select(c));contacts.append(button);
            }
            if(list.length) {
                const chosen=list.find(c=>String(c.id)===String(preferred))||list[0];await select(chosen);
            } else {
                peer.textContent='Chưa có cuộc trò chuyện';history.replaceChildren();form.hidden=true;
            }
        }
        ;
        const open=async()=> {
            popup.hidden=false;fab.setAttribute('aria-expanded','true');
            try {
                const qs=new URLSearchParams(location.search);await loadContacts(qs.get('conversationId')||conversationId);say('');
            } catch(error) {
                say(error.message);
            }
            clearInterval(poller);poller=setInterval(()=>refreshMessages().catch(error=>say(error.message)),3000);
        }
        ;
        const close=()=> {
            popup.hidden=true;fab.setAttribute('aria-expanded','false');clearInterval(poller);poller=null;
        }
        ;
        fab.addEventListener('click',()=>popup.hidden?open():close());
        $('#chat-close').addEventListener('click',close);
        $$('[data-open-chat]').forEach(button=>button.addEventListener('click',open));
        form.addEventListener('submit',async event=> {
            event.preventDefault();if(!conversationId)return;
            const input=$('input[name="content"]',form);const content=input.value.trim();if(!content)return;
            const button=$('button[type="submit"]',form);button.disabled=true;
            try {
                const response=await fetch('/chat/'+conversationId+'/send-ajax', {
                    method:'POST',credentials:'same-origin',headers: {
                        'Content-Type':'application/json',...csrfHeaders()
                    }
                    ,body:JSON.stringify( {
                        content
                    }
                    )
                }
                );
                if(!response.ok)throw new Error('Không gửi được tin nhắn.');
                input.value='';history.dataset.lastMessage='';await refreshMessages();say('');
            } catch(error) {
                say(error.message);
            } finally {
                button.disabled=false;input.focus();
            }
        }
        );
        if(new URLSearchParams(location.search).get('chat')==='open')open();
    }
}
)();
