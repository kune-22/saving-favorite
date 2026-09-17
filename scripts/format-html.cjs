const fs = require('fs');

function formatHtml(source) {
  const tokens = source.replace(/>\s+</g, '><').match(/<!--[\s\S]*?-->|<[^>]+>|[^<]+/g) || [];
  let output = '';
  let indent = 0;
  for (const token of tokens) {
    const value = token.trim();
    if (!value) continue;
    const closing = /^<\//.test(value);
    const opening = /^<([\w-]+)/.exec(value);
    const selfClosing = /\/\s*>$/.test(value) || /^<!/.test(value) || /^<meta|^<link|^<input|^<img|^<br/.test(value);
    if (closing) indent = Math.max(0, indent - 1);
    output += `${'    '.repeat(indent)}${value}\n`;
    if (opening && !selfClosing && !closing) indent += 1;
  }
  return output.replace(/\n{3,}/g, '\n\n');
}
for (const file of process.argv.slice(2)) fs.writeFileSync(file, formatHtml(fs.readFileSync(file, 'utf8')));
