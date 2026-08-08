"""
build_spec_html.py - 把 spec markdown 转成带 Mermaid 渲染的 HTML

特性:
- GitHub 风格 CSS(亮/暗主题都好看)
- Mermaid CDN 自动渲染 sequence/flowchart
- 表格 + 代码块高亮
- 一键侧边栏目录(基于 heading 自动生成)
- 响应式(mobile + desktop)
- 离线友好(只依赖 Mermaid CDN, 其他资源内嵌)

用法:
  python build_spec_html.py <input.md> <output.html>
"""

import sys
from pathlib import Path
import markdown
from markdown.extensions.toc import TocExtension

# 强制 stdout utf-8, 避免 Windows GBK 编码报错
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
elif sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

HTML_TEMPLATE = r'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title}</title>
<style>
  :root {{
    --color-fg: #1f2328;
    --color-fg-muted: #59636e;
    --color-bg: #ffffff;
    --color-bg-soft: #f6f8fa;
    --color-border: #d1d9e0;
    --color-accent: #0969da;
    --color-accent-soft: #ddf4ff;
    --color-table-stripe: #f6f8fa;
    --color-code-bg: #f6f8fa;
    --color-blockquote-border: #d1d9e0;
    --font-stack: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans",
      Helvetica, Arial, sans-serif, "Apple Color Emoji", "Segoe UI Emoji";
    --font-mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas,
      "Liberation Mono", monospace;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{
      --color-fg: #e6edf3;
      --color-fg-muted: #9198a1;
      --color-bg: #0d1117;
      --color-bg-soft: #161b22;
      --color-border: #30363d;
      --color-accent: #58a6ff;
      --color-accent-soft: #1f6feb33;
      --color-table-stripe: #161b22;
      --color-code-bg: #161b22;
      --color-blockquote-border: #30363d;
    }}
  }}
  * {{ box-sizing: border-box; }}
  html, body {{ margin: 0; padding: 0; }}
  body {{
    font-family: var(--font-stack);
    font-size: 16px;
    line-height: 1.6;
    color: var(--color-fg);
    background: var(--color-bg);
  }}
  .layout {{
    display: grid;
    grid-template-columns: 280px 1fr;
    gap: 0;
    max-width: 1400px;
    margin: 0 auto;
  }}
  @media (max-width: 1024px) {{
    .layout {{ grid-template-columns: 1fr; }}
    .sidebar {{ display: none; }}
  }}
  .sidebar {{
    position: sticky;
    top: 0;
    height: 100vh;
    overflow-y: auto;
    padding: 24px 20px;
    background: var(--color-bg-soft);
    border-right: 1px solid var(--color-border);
    font-size: 14px;
  }}
  .sidebar h3 {{
    margin: 0 0 12px 0;
    font-size: 12px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    color: var(--color-fg-muted);
  }}
  .sidebar ul {{
    list-style: none;
    padding: 0;
    margin: 0;
  }}
  .sidebar li {{
    margin: 4px 0;
  }}
  .sidebar a {{
    color: var(--color-fg-muted);
    text-decoration: none;
    display: block;
    padding: 3px 8px;
    border-radius: 4px;
    transition: background 0.15s;
  }}
  .sidebar a:hover {{
    background: var(--color-accent-soft);
    color: var(--color-accent);
  }}
  .sidebar a.lv3 {{
    padding-left: 24px;
    font-size: 13px;
  }}
  .content {{
    padding: 32px 48px;
    min-width: 0;
  }}
  @media (max-width: 768px) {{
    .content {{ padding: 20px 16px; }}
  }}
  h1, h2, h3, h4 {{
    font-weight: 600;
    line-height: 1.25;
    margin-top: 24px;
    margin-bottom: 16px;
  }}
  h1 {{ font-size: 32px; padding-bottom: 8px; border-bottom: 1px solid var(--color-border); }}
  h2 {{ font-size: 24px; padding-bottom: 6px; border-bottom: 1px solid var(--color-border); margin-top: 40px; }}
  h3 {{ font-size: 20px; margin-top: 32px; }}
  h4 {{ font-size: 16px; margin-top: 24px; }}
  a {{ color: var(--color-accent); text-decoration: none; }}
  a:hover {{ text-decoration: underline; }}
  p, ul, ol {{ margin: 8px 0 16px 0; }}
  ul, ol {{ padding-left: 24px; }}
  li {{ margin: 4px 0; }}
  hr {{ border: 0; border-top: 1px solid var(--color-border); margin: 32px 0; }}
  table {{
    border-collapse: collapse;
    width: 100%;
    margin: 16px 0;
    font-size: 14px;
    display: block;
    overflow-x: auto;
  }}
  th, td {{
    border: 1px solid var(--color-border);
    padding: 8px 12px;
    text-align: left;
    vertical-align: top;
  }}
  th {{
    background: var(--color-bg-soft);
    font-weight: 600;
  }}
  tr:nth-child(even) td {{
    background: var(--color-table-stripe);
  }}
  code {{
    font-family: var(--font-mono);
    font-size: 85%;
    background: var(--color-code-bg);
    padding: 2px 6px;
    border-radius: 4px;
  }}
  pre {{
    background: var(--color-code-bg);
    border-radius: 6px;
    padding: 16px;
    overflow-x: auto;
    font-size: 13px;
    line-height: 1.5;
    border: 1px solid var(--color-border);
  }}
  pre code {{
    background: transparent;
    padding: 0;
    font-size: inherit;
  }}
  blockquote {{
    border-left: 3px solid var(--color-blockquote-border);
    margin: 16px 0;
    padding: 0 16px;
    color: var(--color-fg-muted);
  }}
  .mermaid {{
    background: var(--color-bg-soft);
    border: 1px solid var(--color-border);
    border-radius: 6px;
    padding: 16px;
    margin: 16px 0;
    text-align: center;
    overflow-x: auto;
  }}
  .header-meta {{
    background: var(--color-bg-soft);
    border: 1px solid var(--color-border);
    border-radius: 6px;
    padding: 12px 16px;
    margin: 16px 0 24px 0;
    font-size: 14px;
  }}
  .header-meta table {{ font-size: 14px; margin: 0; }}
  .header-meta th {{ background: transparent; width: 120px; }}
  .anchor-link {{
    opacity: 0;
    margin-left: 8px;
    font-size: 0.8em;
    color: var(--color-fg-muted);
  }}
  h1:hover .anchor-link, h2:hover .anchor-link, h3:hover .anchor-link {{
    opacity: 1;
  }}
  .toc {{
    background: var(--color-bg-soft);
    border: 1px solid var(--color-border);
    border-radius: 6px;
    padding: 16px 24px;
    margin: 16px 0 32px 0;
  }}
  .toc ul {{ padding-left: 20px; }}
  .print-hide {{ }}
  @media print {{
    .sidebar {{ display: none; }}
    .layout {{ grid-template-columns: 1fr; }}
    .content {{ padding: 0; max-width: 100%; }}
    pre {{ white-space: pre-wrap; word-wrap: break-word; }}
  }}
</style>
</head>
<body>
<div class="layout">
  <aside class="sidebar">
    <h3>📋 目录</h3>
    <ul>
{sidebar_links}
    </ul>
  </aside>
  <main class="content">
{content}
  </main>
</div>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
  // 等 DOM + mermaid 都加载完再初始化
  window.addEventListener('load', function() {{
    if (typeof mermaid === 'undefined') {{
      console.error('mermaid CDN 加载失败, 请检查网络');
      return;
    }}
    mermaid.initialize({{
      startOnLoad: true,
      theme: window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'default',
      securityLevel: 'loose',
      flowchart: {{ useMaxWidth: true, htmlLabels: true }},
      sequence: {{ useMaxWidth: true, showSequenceNumbers: true }},
    }});
    // 手动 run,把页面上所有 <pre><code class="language-mermaid"> 替换成图
    document.querySelectorAll('pre code.language-mermaid').forEach(function(block, idx) {{
      var pre = block.parentNode;
      var div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = block.textContent;
      pre.parentNode.replaceChild(div, pre);
    }});
    mermaid.run();
  }});
</script>
</body>
</html>
'''


def build_sidebar(md_text: str) -> str:
    """从 markdown 提 heading 生成侧边栏 HTML"""
    import re
    links = []
    for line in md_text.splitlines():
        m = re.match(r'^(#{2,4})\s+(.+?)\s*$', line)
        if not m:
            continue
        level = len(m.group(1))
        title = m.group(2).strip()
        # 去引号包裹
        title = title.strip('"\'`')
        # 生成 anchor id (slugify)
        anchor = re.sub(r'[^\w\u4e00-\u9fff\- ]+', '', title).strip().lower()
        anchor = re.sub(r'\s+', '-', anchor)
        css_class = 'lv3' if level == 3 else ''
        links.append(
            f'      <li><a href="#{anchor}" class="{css_class}">{title}</a></li>'
        )
    return '\n'.join(links)


def build_toc(md_text: str) -> str:
    """生成 H2 级别的内容目录(在页面顶部)"""
    import re
    items = []
    for line in md_text.splitlines():
        m = re.match(r'^##\s+(.+?)\s*$', line)
        if not m:
            continue
        title = m.group(1).strip().strip('"\'`')
        anchor = re.sub(r'[^\w\u4e00-\u9fff\- ]+', '', title).strip().lower()
        anchor = re.sub(r'\s+', '-', anchor)
        items.append(f'      <li><a href="#{anchor}">{title}</a></li>')
    if not items:
        return ''
    body = '\n'.join(items)
    return f'<div class="toc print-hide">\n  <strong>📑 内容目录</strong>\n  <ul>\n{body}\n  </ul>\n</div>'


def main():
    if len(sys.argv) < 3:
        print('用法: python build_spec_html.py <input.md> <output.html>')
        sys.exit(1)

    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])

    md_text = input_path.read_text(encoding='utf-8')

    # 用 markdown 库转 HTML
    md = markdown.Markdown(
        extensions=[
            'tables',
            'fenced_code',
            'sane_lists',
            TocExtension(toc_depth='2-3', anchorlink=False),
            'nl2br',
        ],
        extension_configs={
            'fenced_code': {},
        },
    )
    html_content = md.convert(md_text)

    # 把 GitHub 风格的 header table 拆出来, 单独样式化
    # (markdown 库会把它转成普通 table, 我们检测第一行是 "字段|值" 就套上 .header-meta 样式)
    # 简化: 不做特殊处理, GitHub CSS 已经能渲染

    # 生成侧边栏
    sidebar = build_sidebar(md_text)

    # 生成顶部 TOC
    toc = build_toc(md_text)

    # 拼到 content 前面
    html_content = toc + '\n' + html_content

    # 标题
    title = '统一构建发布平台 — 设计 Spec'

    # 渲染最终 HTML
    final_html = HTML_TEMPLATE.format(
        title=title,
        sidebar_links=sidebar,
        content=html_content,
    )

    output_path.write_text(final_html, encoding='utf-8')
    size_kb = round(output_path.stat().st_size / 1024, 1)
    print(f'[OK] HTML 写完: {output_path} ({size_kb} KB)')


if __name__ == '__main__':
    main()
