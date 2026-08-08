"""
rename_to_shipyard.py - 批量替换 "master" -> "shipyard" 关键字
只改代码/文档里需要改的，保留:
- 真实邮箱 470842725@qq.com
- .git/ 内部文件(git 自己管)
- node_modules / target / dist / coverage / bin 等构建产物
- frozen-world / localai-deploy 旧项目残留

用法:
  python scripts/rename_to_shipyard.py          # dry-run, 不写文件
  python scripts/rename_to_shipyard.py --apply  # 真跑
"""

import sys
import re
from pathlib import Path

# 替换规则 - 长前缀先匹配,避免子串冲突
REPLACEMENTS = [
    # 1. 邮箱组合 (4 个不同的 maintainer 邮箱)
    (r'master@master-platform\.dev', 'team@shipyard.dev'),
    (r'conduct@master-platform\.dev', 'conduct@shipyard.dev'),
    (r'security@master-platform\.dev', 'security@shipyard.dev'),
    (r'maintainers@master-platform\.dev', 'maintainers@shipyard.dev'),

    # 2. 域名 / URL
    (r'master-platform\.dev', 'shipyard.dev'),
    (r'master\.local', 'shipyard.local'),

    # 3. Docker 镜像引用
    (r'ghcr\.io/yourname/master', 'ghcr.io/yourname/shipyard'),
    (r'yourname/master', 'yourname/shipyard'),

    # 4. 配对术语
    (r'master-worker', 'shipyard-worker'),

    # 5. Java 包名
    (r'com\.master', 'com.shipyard'),

    # 6. Maven artifactId
    (r'master-backend', 'shipyard-backend'),

    # 7. 文件名
    (r'master\.key', 'shipyard.key'),
    (r'master\.crt', 'shipyard.crt'),
    (r'master\.pem', 'shipyard.pem'),

    # 8. 全大写
    (r'\bMASTER\b', 'SHIPYARD'),

    # 9. 首字母大写
    (r'\bMaster\b', 'Shipyard'),

    # 10. 全小写
    (r'\bmaster\b', 'shipyard'),
]

# 要处理的扩展名
EXTS = {
    '.md', '.html', '.yml', '.yaml', '.xml', '.json',
    '.java', '.go', '.ts', '.tsx', '.vue', '.js',
    '.properties', '.sql', '.txt', '.sh', '.ps1',
    '.bat', '.cmd',
    # 无扩展名但要处理的文件
    'Makefile', '.gitignore', '.dockerignore', '.env.example',
    'CODEOWNERS', 'Dockerfile.master', 'Dockerfile.worker',
}

# 排除的目录
EXCLUDE_DIRS = {
    '.git', 'node_modules', 'target', 'dist', 'coverage',
    'bin', '.gradle', 'build', '.idea', '.vscode',
    'frozen-world', 'frozen-world-audio', 'frozen-world-sprites',
    'localai-deploy', '__pycache__',
}

# 排除的文件 (即使扩展名匹配)
EXCLUDE_FILES = {
    'rename_to_shipyard.py',  # 自己
    'build_spec_html.py',      # 自己生成的工具
    'LICENSE',                 # Apache 2.0 全文里如果有引用, 但实际上没有 master 字样
    'package-lock.json',       # 锁文件不要动
    'pnpm-lock.yaml',
    'yarn.lock',
    'mvnw', 'mvnw.cmd',
}

# 真实邮箱 - 单独保护
PROTECTED_EMAILS = {
    '470842725@qq.com',
}


def should_process(path: Path) -> bool:
    """判断文件是否需要处理"""
    # 排除目录
    for part in path.parts:
        if part in EXCLUDE_DIRS:
            return False
    # 排除特定文件
    if path.name in EXCLUDE_FILES:
        return False
    # 扩展名匹配
    if path.suffix in EXTS or path.name in EXTS:
        return True
    return False


def process_file(path: Path, apply: bool) -> tuple[int, list[tuple[str, str]]]:
    """处理单个文件, 返回 (替换数, 替换样例)"""
    content = path.read_text(encoding='utf-8', errors='replace')
    original = content
    changes = []

    for pattern, replacement in REPLACEMENTS:
        new_content, n = re.subn(pattern, replacement, content)
        if n > 0:
            # 检查是否误伤了真实邮箱
            for email in PROTECTED_EMAILS:
                if email in new_content and email in original:
                    # 真实邮箱保留(因为 pattern 不会匹配 qq.com 邮箱)
                    pass
            content = new_content
            # 记录前几个替换的上下文
            if len(changes) < 3:
                changes.append((pattern, replacement))

    if apply and content != original:
        path.write_text(content, encoding='utf-8')

    # 数总替换数(用单个大正则)
    combined = '|'.join(f'({p})' for p, _ in REPLACEMENTS)
    total = len(re.findall(combined, original))

    return total, changes


def main():
    apply_mode = '--apply' in sys.argv
    workspace = Path(__file__).parent.parent.resolve()

    mode = 'APPLY' if apply_mode else 'DRY-RUN'
    print(f'=== {mode} mode ===')
    print(f'Workspace: {workspace}')
    print()

    # 收集所有要处理的文件
    files = []
    for path in workspace.rglob('*'):
        if path.is_file() and should_process(path):
            files.append(path)

    print(f'Found {len(files)} files to process')
    print()

    total_replacements = 0
    changed_files = 0

    for f in sorted(files):
        n, samples = process_file(f, apply=apply_mode)
        if n > 0:
            rel = f.relative_to(workspace)
            print(f'  {rel}: {n} replacements')
            total_replacements += n
            changed_files += 1

    print()
    print(f'=== Summary ===')
    print(f'  Total replacements: {total_replacements}')
    print(f'  Changed files: {changed_files} / {len(files)}')
    print()

    if not apply_mode:
        print('[DRY-RUN] No files were modified. Run with --apply to commit changes.')
    else:
        print('[APPLIED] Files modified. Run `git diff` to review.')


if __name__ == '__main__':
    main()
