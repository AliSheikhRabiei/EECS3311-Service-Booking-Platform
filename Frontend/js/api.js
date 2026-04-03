/**
 * api.js — Backend communication layer
 *
 * WHY JS IS NEEDED HERE:
 *   HTML forms can only do GET/POST to a URL and cause a full page reload.
 *   To send JSON, read the response, and update the page without reloading,
 *   JavaScript's fetch() is required. There is no pure HTML alternative.
 *
 * Change API_URL if your backend runs on a different host/port.
 */

const API_URL = 'http://localhost:8080';

/**
 * Core fetch wrapper.
 * - Automatically adds Content-Type: application/json
 * - Automatically adds Authorization: Bearer <token> from localStorage
 * - On 401 → clears session and redirects to login
 * - On error → throws an Error with the backend's error message
 */
async function apiFetch(path, options = {}) {
    const token = localStorage.getItem('token');

    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    let response;
    try {
        response = await fetch(API_URL + path, {
            ...options,
            headers: { ...headers, ...(options.headers || {}) }
        });
    } catch (networkErr) {
        throw new Error('Cannot reach the server. Is Docker running?');
    }

    // Session expired or not logged in
    if (response.status === 401) {
        localStorage.clear();
        window.location.href = 'login.html';
        return;
    }

    // Parse body (may be empty for 204)
    let data = null;
    const text = await response.text();
    if (text) {
        try { data = JSON.parse(text); } catch { data = { message: text }; }
    }

    if (!response.ok) {
        throw new Error((data && data.error) || `Request failed (${response.status})`);
    }

    return data;
}

/** Convenience wrappers so callers don't repeat method/body setup */
const api = {
    get:    (path)         => apiFetch(path),
    post:   (path, body)   => apiFetch(path, { method: 'POST',   body: JSON.stringify(body) }),
    delete: (path)         => apiFetch(path, { method: 'DELETE' }),
};
