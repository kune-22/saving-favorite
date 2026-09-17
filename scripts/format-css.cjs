const fs = require('fs');

function formatCss(source) {
  let output = '';
  let indent = 0;
  let inComment = false;
  let buffer = '';
  const writeLine = value => {
    const line = value.trim();
    if (line) output += `${'    '.repeat(Math.max(indent, 0))}${line}\n`;
  };
  for (let i = 0; i < source.length; i += 1) {
    const pair = source.slice(i, i + 2);
    if (pair === '/*') inComment = true;
    if (pair === '*/') inComment = false;
    const char = source[i];
    if (!inComment && char === '{') {
      writeLine(`${buffer.trim()} {`);
      buffer = '';
      indent += 1;
    } else if (!inComment && char === '}') {
      writeLine(buffer);
      buffer = '';
      indent -= 1;
      writeLine('}');
    } else if (!inComment && char === ';') {
      buffer += char;
      writeLine(buffer);
      buffer = '';
    } else if (!inComment && char === '\n') {
      if (buffer.trim()) writeLine(buffer);
      buffer = '';
    } else {
      buffer += char;
    }
  }
  writeLine(buffer);
  return output.replace(/\n{3,}/g, '\n\n');
}

for (const file of process.argv.slice(2)) {
  fs.writeFileSync(file, formatCss(fs.readFileSync(file, 'utf8')));
}
