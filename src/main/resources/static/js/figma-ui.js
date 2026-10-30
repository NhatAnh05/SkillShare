'use strict';

/* Skill Rental Figma enhancements. Original app.js owns chat/review/order modals. */
(() => {
    const calendar = document.getElementById('booking-calendar');
    const dateField = document.getElementById('booking-date');
    const slotField = document.getElementById('booking-time');
    const bookingForm = document.getElementById('booking-form');

    if (calendar && dateField && slotField && bookingForm) {
        const calendarMonth = document.getElementById('calendar-month');
        const days = document.getElementById('calendar-days');
        const previous = document.getElementById('calendar-prev');
        const next = document.getElementById('calendar-next');
        const premium = document.getElementById('booking-premium');
        const summary = document.querySelector('.booking-summary');
        const serviceField = document.getElementById('service');
        const quantityField = document.getElementById('booking-quantity') ||
            document.getElementById('quantity');
        const durationField = document.getElementById('durationHours');
        const money = amount => new Intl.NumberFormat('vi-VN', {
            maximumFractionDigits: 2, minimumFractionDigits: 0
        }).format(amount) + 'đ';
        let selectedPrice = Number(summary?.dataset.unitPrice || 0);
        let selectedPricingType = summary?.dataset.pricingType || 'FIXED';
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        let shown = new Date(today.getFullYear(), today.getMonth(), 1);

        function isoDate(date) {
            return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        }

        function renderCalendar() {
            calendarMonth.textContent = new Intl.DateTimeFormat('vi-VN', {
                month: 'long', year: 'numeric'
            }).format(shown);
            days.replaceChildren();
            const offset = (shown.getDay() + 6) % 7;
            for (let empty = 0; empty < offset; empty++) {
                days.append(document.createElement('span'));
            }
            const count = new Date(shown.getFullYear(), shown.getMonth() + 1, 0).getDate();
            for (let day = 1; day <= count; day++) {
                const date = new Date(shown.getFullYear(), shown.getMonth(), day);
                const value = isoDate(date);
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = String(day);
                button.setAttribute('aria-label', `Chọn ngày ${day}/${shown.getMonth() + 1}`);
                button.disabled = date < today;
                button.classList.toggle('is-selected', dateField.value === value);
                button.addEventListener('click', () => {
                    dateField.value = value;
                    renderCalendar();
                    updateSlots();
                });
                days.append(button);
            }
            previous.disabled = shown.getFullYear() === today.getFullYear() &&
                shown.getMonth() === today.getMonth();
        }

        function updateSlots() {
            const currentLocalDate = isoDate(today);
            const isToday = dateField.value === currentLocalDate;
            document.querySelectorAll('.time-slots button[data-start]').forEach(button => {
                const [hour, minute] = button.dataset.start.split(':').map(Number);
                const now = new Date();
                button.disabled = !dateField.value ||
                    (isToday && (hour < now.getHours() ||
                        (hour === now.getHours() && minute <= now.getMinutes())));
                if (button.disabled && slotField.value === button.dataset.start) {
                    slotField.value = '';
                }
                button.classList.toggle('is-selected', slotField.value === button.dataset.start);
            });
        }

        previous.addEventListener('click', () => {
            shown = new Date(shown.getFullYear(), shown.getMonth() - 1, 1);
            renderCalendar();
        });
        next.addEventListener('click', () => {
            shown = new Date(shown.getFullYear(), shown.getMonth() + 1, 1);
            renderCalendar();
        });
        document.querySelectorAll('.time-slots button[data-start]').forEach(button => {
            button.addEventListener('click', () => {
                if (button.disabled) return;
                slotField.value = button.dataset.start;
                updateSlots();
            });
        });

        function updatePrice() {
            if (serviceField) {
                const selected = serviceField.options[serviceField.selectedIndex];
                selectedPrice = Number(selected?.dataset.price || 0);
                selectedPricingType = selected?.dataset.pricingType || 'FIXED';
                document.getElementById('summary-service').textContent =
                    selected?.value ? selected.textContent.trim() : 'Chọn dịch vụ';
                document.getElementById('summary-provider').textContent =
                    selected?.dataset.providerName || summary.dataset.serviceProvider;
                document.getElementById('summary-pricing-type').textContent =
                    selectedPricingType === 'HOURLY' ? 'Theo giờ' : 'Cố định';
                document.getElementById('summary-unit-price').textContent =
                    selected?.value ? money(selectedPrice) + (selectedPricingType === 'HOURLY' ? ' / giờ' : '') : '—';
            }
            const quantity = Number(quantityField?.value || summary.dataset.cartQuantity || 1);
            const duration = Number(durationField?.value || summary.dataset.defaultDuration || 2);
            const base = selectedPricingType === 'HOURLY'
                ? selectedPrice * duration * quantity
                : selectedPrice * quantity;
            const extra = premium?.checked ? base * .25 : 0;
            document.getElementById('summary-quantity').textContent = String(quantity);
            document.getElementById('summary-duration').textContent = `${duration} giờ`;
            document.getElementById('summary-duration-row').hidden = selectedPricingType !== 'HOURLY';
            document.getElementById('duration-group').hidden = selectedPricingType !== 'HOURLY';
            document.getElementById('booking-base').textContent = money(base);
            document.getElementById('booking-extra').textContent = money(extra);
            document.getElementById('booking-total').textContent = money(base + extra);
        }
        premium?.addEventListener('change', updatePrice);
        serviceField?.addEventListener('change', updatePrice);
        quantityField?.addEventListener('input', updatePrice);
        durationField?.addEventListener('change', updatePrice);
        bookingForm.addEventListener('submit', event => {
            if (!dateField.value || !slotField.value) {
                event.preventDefault();
                alert('Vui lòng chọn ngày và khung giờ trước khi tiếp tục.');
            }
        });
        if (dateField.value) {
            const [year, month] = dateField.value.split('-').map(Number);
            shown = new Date(year, month - 1, 1);
        }
        renderCalendar();
        updateSlots();
        updatePrice();
    }

    const newPassword = document.getElementById('new-password');
    const confirmPassword = document.getElementById('confirm-password');
    const meter = document.querySelector('.password-meter');
    if (newPassword && confirmPassword && meter) {
        const label = document.getElementById('password-strength-label');
        const match = document.getElementById('password-match');

        function updatePasswordFeedback() {
            const value = newPassword.value;
            const score = value ? [
                value.length >= 8,
                value.length >= 12,
                /[A-Z]/.test(value) && /[a-z]/.test(value),
                /\d/.test(value) && /[^A-Za-z0-9]/.test(value)
            ].filter(Boolean).length : 0;
            const levels = ['Nhập mật khẩu', 'Yếu', 'Trung bình', 'Mạnh', 'Rất mạnh'];
            meter.dataset.score = String(score);
            label.textContent = levels[score];
            match.textContent = confirmPassword.value && confirmPassword.value === value ? '✓' : '';
            match.setAttribute('aria-label', match.textContent ? 'Mật khẩu xác nhận khớp' : '');
        }
        newPassword.addEventListener('input', updatePasswordFeedback);
        confirmPassword.addEventListener('input', updatePasswordFeedback);
        updatePasswordFeedback();

        const session = document.getElementById('current-session-agent');
        if (session) {
            const browser = /Edg\//.test(navigator.userAgent) ? 'Edge' :
                /Chrome\//.test(navigator.userAgent) ? 'Chrome' :
                    /Firefox\//.test(navigator.userAgent) ? 'Firefox' : 'Trình duyệt';
            session.textContent = `${browser} · Phiên đang sử dụng (chỉ hiển thị trên thiết bị này)`;
        }
    }
})();
