// 侧边栏导航组件
document.addEventListener('DOMContentLoaded', () => {
    // 标注当前页激活链接
    const path = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('#sidebar .nav-link').forEach(a => {
        const href = a.getAttribute('href')?.split('/').pop();
        if (href === path) a.classList.add('active');
    });

    // 移动端汉堡菜单
    const toggler = document.getElementById('sidebarToggler');
    const sidebar = document.getElementById('sidebar');
    if (toggler && sidebar) {
        toggler.addEventListener('click', () => sidebar.classList.toggle('show'));
        document.addEventListener('click', e => {
            if (!sidebar.contains(e.target) && !toggler.contains(e.target)) {
                sidebar.classList.remove('show');
            }
        });
    }
});

// 生成侧边栏 HTML（各页面共用）
function renderSidebar() {
    return `
<div id="sidebar">
  <div class="brand"><i class="bi bi-mortarboard-fill"></i> IELTS 学习</div>
  <nav class="nav flex-column py-2">
    <a class="nav-link" href="index.html"><i class="bi bi-house-door"></i> 首页</a>
    <a class="nav-link" href="study.html"><i class="bi bi-play-circle"></i> 今日学习</a>
    <a class="nav-link" href="stats.html"><i class="bi bi-bar-chart-line"></i> 学习统计</a>

    <div class="nav-section">跨技能内容</div>
    <a class="nav-link" href="words.html"><i class="bi bi-alphabet"></i> 单词</a>
    <a class="nav-link" href="phrases.html"><i class="bi bi-quote"></i> 短语</a>
    <a class="nav-link" href="paraphrases.html"><i class="bi bi-arrow-left-right"></i> 同义替换</a>
    <a class="nav-link" href="pronunciation.html"><i class="bi bi-mic"></i> 发音要点</a>
    <a class="nav-link" href="grammar-points.html"><i class="bi bi-book"></i> 语法要点</a>
    <a class="nav-link" href="grammar-exercises.html"><i class="bi bi-pencil-square"></i> 语法练习</a>

    <div class="nav-section">四大技能</div>
    <a class="nav-link" href="speaking.html"><i class="bi bi-chat-dots"></i> 口语话题</a>
    <a class="nav-link" href="listening.html"><i class="bi bi-headphones"></i> 听力练习</a>
    <a class="nav-link" href="reading.html"><i class="bi bi-file-text"></i> 阅读练习</a>
    <a class="nav-link" href="writing.html"><i class="bi bi-pencil"></i> 写作任务</a>
  </nav>
</div>`;
}
