/**
 * auth.js - Shared session and UI helpers.
 */

function clearSessionData() {
    sessionStorage.clear();
}

function getUser() {
    return {
        token: sessionStorage.getItem('token'),
        userId: sessionStorage.getItem('userId'),
        role: sessionStorage.getItem('role'),
        name: sessionStorage.getItem('name'),
        email: sessionStorage.getItem('email'),
    };
}

function requireAuth(expectedRole) {
    const user = getUser();
    if (!user.token) {
        window.location.href = 'login.html';
        return null;
    }
    if (expectedRole && user.role !== expectedRole) {
        alert('Access denied. You are not a ' + expectedRole);
        window.location.href = 'login.html';
        return null;
    }
    return user;
}

function renderNavbar(user) {
    document.querySelectorAll('.user-name-display').forEach(el => {
        el.textContent = user.name + ' (' + user.role + ')';
    });
}

function logout(button) {
    const action = () => api.post('/auth/logout', {})
        .catch(() => {})
        .finally(() => {
            clearSessionData();
            window.location.href = 'login.html';
        });

    if (button) {
        withButtonLoading(button, 'Signing Out...', action);
        return;
    }
    action();
}

function getTabStorageKey() {
    return `activeTab:${window.location.pathname}`;
}

function switchTab(tabId, options = {}) {
    document.querySelectorAll('.page').forEach(page => page.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(button => button.classList.remove('active'));

    const panel = document.getElementById(tabId);
    const button = document.querySelector(`[data-tab="${tabId}"]`);
    if (panel) panel.classList.add('active');
    if (button) button.classList.add('active');

    if (options.persist !== false) {
        sessionStorage.setItem(getTabStorageKey(), tabId);
    }
}

function restoreSavedTab(defaultTab) {
    const saved = sessionStorage.getItem(getTabStorageKey());
    const target = saved && document.getElementById(saved) ? saved : defaultTab;
    if (target) {
        switchTab(target, { persist: false });
    }
    return target;
}

function showAlert(id, message, type = 'error') {
    const el = id ? document.getElementById(id) : null;
    if (el) {
        el.textContent = message;
        el.className = `alert alert-${type} show`;
        window.setTimeout(() => {
            if (el.textContent === message) {
                el.classList.remove('show');
            }
        }, 5000);
    }
    showToast(message, type);
}

function ensureToastStack() {
    let stack = document.getElementById('toastStack');
    if (!stack) {
        stack = document.createElement('div');
        stack.id = 'toastStack';
        stack.className = 'toast-stack';
        document.body.appendChild(stack);
    }
    return stack;
}

function showToast(message, type = 'info') {
    if (!message) return;

    const stack = ensureToastStack();
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <div class="toast-copy">${escapeHtml(message)}</div>
        <button type="button" class="toast-close" aria-label="Dismiss notification">x</button>
    `;

    toast.querySelector('.toast-close').addEventListener('click', () => removeToast(toast));
    stack.appendChild(toast);
    window.setTimeout(() => toast.classList.add('show'), 10);
    window.setTimeout(() => removeToast(toast), 4200);
}

function removeToast(toast) {
    if (!toast || !toast.parentNode) return;
    toast.classList.remove('show');
    window.setTimeout(() => {
        if (toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    }, 180);
}

function setButtonLoading(button, loadingText) {
    if (!button) return () => {};

    const previousHtml = button.innerHTML;
    const previousDisabled = button.disabled;
    button.disabled = true;
    button.classList.add('btn-loading');
    button.innerHTML = `<span class="spinner spinner-inline" aria-hidden="true"></span>${escapeHtml(loadingText || 'Working...')}`;

    return () => {
        button.innerHTML = previousHtml;
        button.disabled = previousDisabled;
        button.classList.remove('btn-loading');
    };
}

async function withButtonLoading(button, loadingText, task) {
    const restore = setButtonLoading(button, loadingText);
    try {
        return await task();
    } finally {
        restore();
    }
}

async function copyText(value, successMessage = 'Copied to clipboard.') {
    if (!value) return;

    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(value);
        } else {
            const textarea = document.createElement('textarea');
            textarea.value = value;
            textarea.setAttribute('readonly', '');
            textarea.style.position = 'absolute';
            textarea.style.left = '-9999px';
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
        }
        showToast(successMessage, 'success');
    } catch (err) {
        showToast('Copy failed. Please copy it manually.', 'error');
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/\r?\n/g, ' ');
}

function escapeMultilineHtml(value) {
    return escapeHtml(value).replace(/\r?\n/g, '<br>');
}

function badge(status) {
    const safeStatus = String(status ?? 'UNKNOWN');
    const cls = safeStatus.toLowerCase()
        .replace('_payment', '')
        .replace(/_/g, '-')
        .replace(/[^a-z0-9-]/g, '');
    return `<span class="badge badge-${cls || 'pending'}">${escapeHtml(safeStatus)}</span>`;
}

function renderBookingProgress(status) {
    const safeStatus = String(status ?? 'UNKNOWN');
    if (safeStatus === 'REJECTED' || safeStatus === 'CANCELLED') {
        return `<div class="progress-terminal progress-terminal-${safeStatus.toLowerCase()}">${escapeHtml(safeStatus)}</div>`;
    }

    const steps = ['REQUESTED', 'CONFIRMED', 'PAID', 'COMPLETED'];
    const normalized = safeStatus === 'PENDING_PAYMENT' ? 'CONFIRMED' : safeStatus;
    const activeIndex = Math.max(0, steps.indexOf(normalized));

    return `
        <div class="progress-track" aria-label="Booking progress ${escapeAttr(safeStatus)}">
            ${steps.map((step, index) => `
                <div class="progress-step ${index <= activeIndex ? 'done' : ''}">
                    <span class="progress-node">${index + 1}</span>
                    <span class="progress-label">${step.replace('_', ' ')}</span>
                </div>
            `).join('')}
        </div>
    `;
}

function fmtDate(iso) {
    if (!iso) return '-';
    return new Date(iso).toLocaleString([], {
        dateStyle: 'medium',
        timeStyle: 'short',
    });
}

function fmtSlot(iso) {
    if (!iso) return '-';
    const d = new Date(iso);
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
        + ' '
        + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function fmtSlotRange(startIso, endIso) {
    if (!startIso || !endIso) return '-';
    const start = new Date(startIso);
    const end = new Date(endIso);
    return `${start.toLocaleDateString([], { month: 'short', day: 'numeric' })} ${start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - ${end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}
