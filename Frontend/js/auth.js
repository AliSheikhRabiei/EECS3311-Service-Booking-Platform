/**
 * auth.js — Session management helpers
 *
 * The token returned by POST /auth/login is stored in localStorage.
 * Every protected API call reads it from there via api.js.
 * localStorage persists across page refreshes until logout or expiry.
 */

/** Returns the currently stored user object, or null fields if not logged in. */
function getUser() {
    return {
        token:  localStorage.getItem('token'),
        userId: localStorage.getItem('userId'),
        role:   localStorage.getItem('role'),
        name:   localStorage.getItem('name'),
        email:  localStorage.getItem('email'),
    };
}

/**
 * Call at the top of every protected page.
 * If no token → redirect to login.
 * If wrong role → redirect to login.
 * Returns the user object if auth is valid.
 */
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

/** Renders the user's name in navbar elements with class .user-name-display */
function renderNavbar(user) {
    document.querySelectorAll('.user-name-display').forEach(el => {
        el.textContent = user.name + ' (' + user.role + ')';
    });
}

/** Calls POST /auth/logout then clears localStorage and goes to login. */
function logout() {
    api.post('/auth/logout', {})
        .catch(() => {}) // ignore errors – clear session regardless
        .finally(() => {
            localStorage.clear();
            window.location.href = 'login.html';
        });
}

/** Tab switching: show the panel matching tabId, mark btn active. */
function switchTab(tabId) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    const panel = document.getElementById(tabId);
    const btn   = document.querySelector(`[data-tab="${tabId}"]`);
    if (panel) panel.classList.add('active');
    if (btn)   btn.classList.add('active');
}

/** Show an alert div by id with a message. */
function showAlert(id, message, type = 'error') {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = message;
    el.className = `alert alert-${type} show`;
    setTimeout(() => el.classList.remove('show'), 5000);
}

/** Returns a status badge HTML string. */
function badge(status) {
    const cls = status.toLowerCase().replace('_payment','').replace('_', '-');
    return `<span class="badge badge-${cls}">${status}</span>`;
}

/** Formats an ISO datetime string to a readable short format. */
function fmtDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString([], {
        dateStyle: 'medium', timeStyle: 'short'
    });
}

/** Formats just the date part. */
function fmtSlot(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
         + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
