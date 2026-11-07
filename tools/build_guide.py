"""Render the Italian offline manual using only the Python standard library."""
from pathlib import Path
import base64
import html
import re
import unicodedata

ROOT = Path(__file__).resolve().parents[1]


def slug(text):
    plain = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode()
    return re.sub(r'[^a-z0-9]+', '-', plain.lower()).strip('-')


def inline(text):
    text = html.escape(text)
    text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', text)
    return re.sub(r'\[([^]]+)\]\((https?://[^)]+)\)', r'<a href="\2">\1</a>', text)


def main():
    source = (ROOT / 'docs/GUIDA.md').read_text(encoding='utf-8')
    body, nav, paragraph = [], [], []
    listing = None

    def flush():
        if paragraph:
            body.append('<p>' + inline(' '.join(paragraph)) + '</p>')
            paragraph.clear()

    for line in source.splitlines() + ['']:
        heading = re.match(r'^(#{1,3}) (.+)$', line)
        item = re.match(r'^(- |\d+\. )(.+)$', line)
        kind = ('ul' if item.group(1) == '- ' else 'ol') if item else None
        if listing and kind != listing:
            body.append('</' + listing + '>')
            listing = None
        if heading:
            flush()
            level, title = len(heading.group(1)), heading.group(2)
            anchor = slug(title)
            body.append(f'<h{level} id="{anchor}">{inline(title)}</h{level}>')
            if level == 2:
                nav.append(f'<a href="#{anchor}">{inline(title)}</a>')
        elif item:
            flush()
            if not listing:
                listing = kind
                body.append('<' + kind + '>')
            body.append('<li>' + inline(item.group(2)) + '</li>')
        elif line.strip():
            paragraph.append(line.strip())
        else:
            flush()
    template = (ROOT / 'docs/guide-template.html').read_text(encoding='utf-8')
    icon = base64.b64encode((ROOT / 'web/public/stagepilot-icon.png').read_bytes()).decode()
    rendered = template.replace('{{ICON}}', 'data:image/png;base64,' + icon).replace('{{NAV}}', ''.join(nav)).replace('{{BODY}}', ''.join(body))
    for path in ['web/public/guida.html', 'app/src/main/assets/guida.html']:
        (ROOT / path).write_text(rendered, encoding='utf-8')


if __name__ == '__main__':
    main()
