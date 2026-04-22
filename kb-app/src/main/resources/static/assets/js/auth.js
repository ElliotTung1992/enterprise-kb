async function login(username, password) {
    const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || '登录失败');
    }
    const data = await res.json();
    localStorage.setItem('access_token', data.data.accessToken);
    localStorage.setItem('refresh_token', data.data.refreshToken);
    localStorage.setItem('user_id', data.data.userId);
    localStorage.setItem('username', data.data.username);
    return data.data;
}

function clearAuthStorage() {
    ['access_token', 'refresh_token', 'user_id', 'username', 'current_space_id', 'current_space_name']
        .forEach(k => localStorage.removeItem(k));
}

function logout() {
    const rt = localStorage.getItem('refresh_token');
    if (rt) {
        fetch('/api/v1/auth/logout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${localStorage.getItem('access_token')}`
            },
            body: JSON.stringify({ refreshToken: rt })
        }).catch(() => {});
    }
    clearAuthStorage();
    window.location.href = '/login.html';
}
