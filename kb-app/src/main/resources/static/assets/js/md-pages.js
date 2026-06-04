marked.setOptions({ breaks: true, gfm: true });

let mdSessionId = null;
let mdSending = false;
let mdCurrentConfig = null;

function initMdPage() {
  requireAuth();
  const usernameEl = document.getElementById('sidebar-username');
  if (usernameEl) usernameEl.textContent = localStorage.getItem('username') || '';
  return loadMdSpaces();
}

async function loadMdSpaces() {
  const res = await apiFetch('/spaces');
  const spaces = res.data || [];
  const sel = document.getElementById('space-select');
  sel.innerHTML = '';
  spaces.forEach(s => {
    const option = document.createElement('option');
    option.value = s.id;
    option.textContent = s.name;
    sel.appendChild(option);
  });
  const saved = localStorage.getItem('current_space_id');
  if (saved && spaces.find(s => s.id === saved)) sel.value = saved;
  else if (spaces.length) sel.value = spaces[0].id;
  if (sel.value) setCurrentSpace(sel.value, sel.options[sel.selectedIndex]?.text || '');
  sel.addEventListener('change', () => {
    setCurrentSpace(sel.value, sel.options[sel.selectedIndex]?.text || '');
    mdSessionId = null;
    window.onMdSpaceChanged?.();
  });
  window.onMdSpaceChanged?.();
}

async function loadMdDocuments() {
  const spaceId = document.getElementById('space-select').value;
  if (!spaceId) return;
  const res = await apiFetch(`/spaces/${spaceId}/md-documents?size=50`);
  renderMdDocumentList(res.data?.content || []);
}

function renderMdDocumentList(docs) {
  const list = document.getElementById('doc-list');
  if (!list) return;
  if (!docs.length) {
    list.innerHTML = '<div class="text-muted text-center py-4 small">暂无 Markdown 文档</div>';
    return;
  }
  list.innerHTML = docs.map(d => {
    const name = mdDocumentName(d);
    return `
    <div class="md-doc-item">
      <div class="d-flex align-items-center justify-content-between gap-2">
        <div class="md-doc-title" title="${escAttr(name)}">${escHtml(name)}</div>
        <span class="badge ${statusClass(d.status)}">${statusText(d.status)}</span>
      </div>
      <div class="text-muted small mt-1">${d.chunkCount || 0} chunks</div>
    </div>`;
  }).join('');
}

function initMdQaPage(config) {
  mdCurrentConfig = config;
  window.onMdSpaceChanged = () => {
    startNewMdChat(false);
    refreshMdSessionList();
  };
  initMdPage();

  document.getElementById('btn-new-chat')?.addEventListener('click', () => startNewMdChat(true));
  document.getElementById('btn-send').addEventListener('click', () => sendMdQuestion(config));
  document.getElementById('question-input').addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMdQuestion(config);
    }
  });
}

async function sendMdQuestion(config) {
  const spaceId = document.getElementById('space-select').value;
  const input = document.getElementById('question-input');
  const question = input.value.trim();
  if (!spaceId || !question || mdSending) return;

  mdSending = true;
  input.value = '';
  document.getElementById('btn-send').disabled = true;
  document.getElementById('citation-panel')?.classList.add('d-none');
  addMdMessage('user', question);
  const bubble = addMdMessage('assistant', config.loading, false);
  const previousSessionId = mdSessionId;
  const requestSessionId = mdSessionId || createUuid();

  try {
    if (config.stream) {
      await sendMdStreamQuestion(spaceId, question, bubble, requestSessionId);
      mdSessionId = requestSessionId;
    } else {
      const topK = parseInt(document.getElementById('topk-select').value);
      const modelProvider = selectedModelProvider();
      const res = await apiFetch(`/spaces/${spaceId}/md-qa/${config.endpoint}`, {
        method: 'POST',
        body: JSON.stringify({ question, sessionId: previousSessionId, topK, modelProvider })
      });
      const data = res.data || {};
      mdSessionId = data.sessionId;
      bubble.innerHTML = marked.parse(data.answer || '');
      showMdCitations(data.citations || []);
    }
    await refreshMdSessionList();
  } catch (e) {
    if (!previousSessionId) mdSessionId = null;
    bubble.textContent = '问答失败：' + e.message;
  } finally {
    mdSending = false;
    document.getElementById('btn-send').disabled = false;
    input.focus();
  }
}

async function sendMdStreamQuestion(spaceId, question, bubble, sessionId) {
  const token = localStorage.getItem('access_token');
  const topK = parseInt(document.getElementById('topk-select').value);
  const modelProvider = selectedModelProvider();
  // 流式端点可配置：标准流式 ask/stream、Agentic 流式 ask/agentic/stream
  const streamEndpoint = mdCurrentConfig?.streamEndpoint || 'ask/stream';
  const res = await fetch(`/api/v1/spaces/${spaceId}/md-qa/${streamEndpoint}`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify({ question, sessionId, topK, modelProvider })
  });
  if (!res.ok) throw new Error(await readStreamError(res));

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let answer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split(/\r?\n\r?\n/);
    buffer = parts.pop() || '';
    for (const part of parts) {
      const tokenText = part.split(/\r?\n/)
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).replace(/^ /, ''))
        .join('\n');
      if (tokenText && tokenText !== '[DONE]') {
        answer += tokenText;
        bubble.innerHTML = marked.parse(answer);
        bubble.closest('.md-messages')?.scrollTo(0, bubble.closest('.md-messages').scrollHeight);
      }
    }
  }
  if (buffer.trim()) {
    answer += buffer.replace(/^data:\s?/gm, '');
    bubble.innerHTML = marked.parse(answer);
  }
}

async function refreshMdSessionList() {
  const spaceId = document.getElementById('space-select')?.value;
  const list = document.getElementById('sessions-list');
  if (!spaceId || !list) return;
  try {
    const res = await apiFetch(`/spaces/${spaceId}/qa/sessions`);
    renderMdSessionList(res.data || []);
  } catch (e) {
    list.innerHTML = '<div class="text-center text-muted p-3" style="font-size:.78rem">加载会话失败</div>';
    console.warn('加载问答会话失败', e);
  }
}

function renderMdSessionList(sessions) {
  const list = document.getElementById('sessions-list');
  if (!list) return;
  if (!sessions.length) {
    list.innerHTML = '<div class="text-center text-muted p-3" style="font-size:.78rem">暂无问答记录</div>';
    return;
  }
  list.innerHTML = sessions.map(s => {
    const active = s.id === mdSessionId ? ' active' : '';
    return `<div class="session-item${active}" onclick="switchMdSession('${s.id}')">
      <div class="session-actions">
        <button onclick="renameMdSession(event,'${s.id}')" title="重命名"><i class="bi bi-pencil"></i></button>
        <button class="btn-del" onclick="deleteMdSession(event,'${s.id}')" title="删除"><i class="bi bi-x"></i></button>
      </div>
      <div class="session-title" title="${escAttr(s.title)}">${escHtml(s.title || '未命名会话')}</div>
      <div class="session-time">${formatMdTime(s.updatedAt || s.createdAt)} · ${s.messageCount || 0} 条</div>
    </div>`;
  }).join('');
}

async function switchMdSession(id) {
  const spaceId = document.getElementById('space-select').value;
  if (!spaceId || mdSending) return;
  mdSessionId = id;
  document.getElementById('chat-messages').innerHTML = '';
  document.getElementById('citation-panel')?.classList.add('d-none');
  renderMdSessionListFromDom();
  try {
    const res = await apiFetch(`/spaces/${spaceId}/qa/sessions/${id}/messages`);
    const messages = res.data || [];
    if (messages.length) {
      messages.forEach(m => addMdMessage(m.role, m.content));
    } else {
      addMdWelcome();
    }
  } catch (e) {
    addMdMessage('assistant', '加载历史消息失败：' + e.message);
  }
  document.getElementById('question-input').focus();
}

async function renameMdSession(event, id) {
  event.stopPropagation();
  const oldTitle = event.currentTarget.closest('.session-item')?.querySelector('.session-title')?.textContent || '';
  const title = prompt('请输入新的会话标题', oldTitle || '');
  if (title === null) return;
  const trimmed = title.trim();
  if (!trimmed) {
    showToast('会话标题不能为空', 'error');
    return;
  }
  const spaceId = document.getElementById('space-select').value;
  try {
    await apiFetch(`/spaces/${spaceId}/qa/sessions/${id}/title`, {
      method: 'PATCH',
      body: JSON.stringify({ title: trimmed })
    });
    await refreshMdSessionList();
  } catch (e) {
    showToast('重命名失败：' + e.message, 'error');
  }
}

async function deleteMdSession(event, id) {
  event.stopPropagation();
  const spaceId = document.getElementById('space-select').value;
  try {
    await apiFetch(`/spaces/${spaceId}/qa/sessions/${id}`, { method: 'DELETE' });
  } catch (e) {
    showToast('删除失败：' + e.message, 'error');
    return;
  }
  if (mdSessionId === id) startNewMdChat(false);
  await refreshMdSessionList();
}

function startNewMdChat(focusInput) {
  mdSessionId = null;
  document.getElementById('chat-messages').innerHTML = '';
  document.getElementById('citation-panel')?.classList.add('d-none');
  addMdWelcome();
  renderMdSessionListFromDom();
  if (focusInput) document.getElementById('question-input').focus();
}

function addMdWelcome() {
  if (mdCurrentConfig?.welcome) addMdMessage('assistant', mdCurrentConfig.welcome);
}

function renderMdSessionListFromDom() {
  document.querySelectorAll('.session-item').forEach(el => {
    el.classList.toggle('active', el.getAttribute('onclick') === `switchMdSession('${mdSessionId}')`);
  });
}

async function readStreamError(res) {
  const err = await res.json().catch(() => null);
  return err?.message || 'Request failed';
}

function addMdMessage(role, content, markdown = true) {
  const container = document.getElementById('chat-messages');
  const row = document.createElement('div');
  row.className = `d-flex mb-3 ${role === 'user' ? 'justify-content-end' : 'justify-content-start'}`;
  const bubble = document.createElement('div');
  bubble.className = `chat-bubble ${role}`;
  if (markdown && role !== 'user') bubble.innerHTML = marked.parse(content);
  else bubble.textContent = content;
  row.appendChild(bubble);
  container.appendChild(row);
  container.scrollTop = container.scrollHeight;
  return bubble;
}

function showMdCitations(citations) {
  const panel = document.getElementById('citation-panel');
  const list = document.getElementById('citation-list');
  if (!panel || !list) return;
  if (!citations.length) {
    panel.classList.add('d-none');
    return;
  }
  panel.classList.remove('d-none');
  list.innerHTML = citations.map(c => `
    <div class="citation-card" title="${escAttr(c.excerpt || '')}">
      <div class="fw-semibold">${escHtml(c.documentTitle || 'Markdown 文档')}</div>
      <div class="text-muted text-truncate" style="max-width:220px">${escHtml(c.section || c.excerpt || '')}</div>
    </div>`).join('');
}

function statusClass(status) {
  return { PENDING: 'badge-pending', PROCESSING: 'badge-processing', READY: 'badge-ready', FAILED: 'badge-failed' }[status] || 'bg-secondary';
}

function statusText(status) {
  return { PENDING: '待处理', PROCESSING: '处理中', READY: '就绪', FAILED: '失败' }[status] || status;
}

function mdDocumentName(document) {
  return document.originalFilename || document.title || '未命名 Markdown';
}

function selectedModelProvider() {
  return document.getElementById('model-select')?.value || undefined;
}

function escHtml(s) {
  return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escAttr(s) {
  return escHtml(s).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function createUuid() {
  if (crypto?.randomUUID) return crypto.randomUUID();
  return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, c =>
    (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16));
}

function formatMdTime(iso) {
  if (!iso) return '';
  const d = new Date(iso), now = new Date();
  const diff = (now - d) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
  if (diff < 172800) return '昨天';
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}
