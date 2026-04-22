const API_BASE = '/api/v1';

async function apiFetch(path, options = {}, _retried = false) {
    const token = localStorage.getItem('access_token');
    const isFormData = options.body instanceof FormData;

    const headers = {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...(options.headers || {})
    };

    const res = await fetch(API_BASE + path, { ...options, headers });

    if (res.status === 401) {
        if (!_retried) {
            const refreshed = await tryRefreshToken();
            if (refreshed) return apiFetch(path, options, true);
        }
        clearAuthStorage();
        window.location.href = '/login.html';
        return;
    }

    if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'Request failed' }));
        throw new Error(err.message || 'Request failed');
    }

    return res.json();
}

async function tryRefreshToken() {
    const rt = localStorage.getItem('refresh_token');
    if (!rt) return false;
    try {
        const res = await fetch(API_BASE + '/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken: rt })
        });
        if (!res.ok) return false;
        const data = await res.json();
        localStorage.setItem('access_token', data.data.accessToken);
        localStorage.setItem('refresh_token', data.data.refreshToken);
        return true;
    } catch { return false; }
}

function requireAuth() {
    if (!localStorage.getItem('access_token')) {
        window.location.href = '/login.html';
        return;
    }
    document.body.style.visibility = 'visible';
}

function getCurrentSpace() {
    return localStorage.getItem('current_space_id');
}

function setCurrentSpace(id, name) {
    localStorage.setItem('current_space_id', id);
    localStorage.setItem('current_space_name', name);
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container') || createToastContainer();
    const toast = document.createElement('div');
    toast.className = `toast align-items-center text-bg-${type === 'error' ? 'danger' : 'success'} border-0`;
    toast.setAttribute('role', 'alert');
    toast.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">${message}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>`;
    container.appendChild(toast);
    const bsToast = new bootstrap.Toast(toast, { delay: 3000 });
    bsToast.show();
    toast.addEventListener('hidden.bs.toast', () => toast.remove());
}

function createToastContainer() {
    const div = document.createElement('div');
    div.id = 'toast-container';
    div.className = 'toast-container position-fixed bottom-0 end-0 p-3';
    document.body.appendChild(div);
    return div;
}
