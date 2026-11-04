(() => {
    'use strict';

    const grid = document.querySelector('[data-provider-grid]');
    const toggleWrap = document.querySelector('[data-provider-toggle]');
    const toggleButton = document.querySelector('[data-provider-toggle-button]');

    if (!grid || !toggleWrap || !toggleButton) return;

    const cards = Array.from(grid.querySelectorAll(':scope > .expert-card'));
    const maxRows = 3;
    let expanded = false;

    const getColumnCount = () => {
        const template = window.getComputedStyle(grid).gridTemplateColumns;
        const columns = template.trim().split(/\s+/).filter(Boolean).length;
        return Math.max(columns, 1);
    };

    const render = () => {
        const visibleLimit = getColumnCount() * maxRows;
        cards.forEach((card, index) => {
            card.hidden = !expanded && index >= visibleLimit;
        });

        const hasMore = cards.length > visibleLimit;
        toggleWrap.hidden = !hasMore;
        toggleButton.setAttribute('aria-expanded', String(expanded));
        toggleButton.textContent = expanded ? 'Thu gọn danh sách' : 'Xem thêm Provider';
    };

    toggleButton.addEventListener('click', () => {
        expanded = !expanded;
        render();
        if (!expanded) {
            grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    });

    let resizeFrame = null;
    window.addEventListener('resize', () => {
        if (resizeFrame !== null) window.cancelAnimationFrame(resizeFrame);
        resizeFrame = window.requestAnimationFrame(() => {
            render();
            resizeFrame = null;
        });
    }, { passive: true });

    render();
})();
