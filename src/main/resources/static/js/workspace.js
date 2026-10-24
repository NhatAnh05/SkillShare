(() => {
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    const sidebar = document.querySelector('.wk-sidebar');

    document.addEventListener('click', (event) => {
        const menuButton = event.target.closest('[data-wk-menu]');
        if (menuButton && sidebar) {
            sidebar.classList.toggle('open');
            return;
        }

        const sidebarLink = event.target.closest('.wk-side-item');
        if (sidebarLink && sidebar && window.matchMedia('(max-width: 820px)').matches) {
            sidebar.classList.remove('open');
        }
    });

    $$('[data-wk-confirm]').forEach((element) => {
        element.addEventListener('click', (event) => {
            const message = element.dataset.wkConfirm;
            if (message && !window.confirm(message)) {
                event.preventDefault();
            }
        });
    });

    $$('[data-matrix]').forEach((cell) => {
        cell.addEventListener('click', () => {
            cell.classList.toggle('on');
            cell.textContent = cell.classList.contains('on') ? 'Rảnh' : 'Bận';
        });
    });

    $$('[data-wallet-preset]').forEach((button) => {
        button.addEventListener('click', () => {
            const input = document.querySelector('[data-wallet-amount]');
            if (!input) {
                return;
            }

            input.value = button.dataset.walletPreset;
            input.dispatchEvent(new Event('input'));
        });
    });

    const amountInput = document.querySelector('[data-wallet-amount]');
    if (amountInput) {
        const validateWalletAmount = () => {
            const max = Number(amountInput.dataset.walletBalance || 0);
            const value = Number(amountInput.value || 0);

            if (value > max) {
                amountInput.setCustomValidity('Số tiền rút vượt số dư khả dụng.');
            } else {
                amountInput.setCustomValidity('');
            }
        };

        amountInput.addEventListener('input', validateWalletAmount);
        validateWalletAmount();
    }

    const priceInput = document.querySelector('[data-apply-price]');
    const netElement = document.querySelector('[data-apply-net]');
    const feeElement = document.querySelector('[data-apply-fee]');

    if (priceInput && netElement) {
        const updateIncomePreview = () => {
            const value = Number(priceInput.value || 0);
            const commissionRate = Number(priceInput.dataset.commissionRate || 0.05);
            const feeValue = Math.round(value * commissionRate);
            const netValue = value - feeValue;
            const formatter = new Intl.NumberFormat('vi-VN');

            if (feeElement) {
                feeElement.textContent = `-${formatter.format(feeValue)}đ`;
            }

            netElement.textContent = `${formatter.format(netValue)}đ`;
        };

        priceInput.addEventListener('input', updateIncomePreview);
        updateIncomePreview();
    }
})();
