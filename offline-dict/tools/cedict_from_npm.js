// Fallback: rebuild a CC-CEDICT text file from the cc-cedict npm package data.
const fs = require('fs');
const [src, out] = process.argv.slice(2);
const d = JSON.parse(fs.readFileSync(src, 'utf8').replace(/^export default /, '').replace(/;\s*$/, ''));
const lines = d.all.map(r => `${r[0]} ${r[1]} [${r[2]}] /${String(r[3]).replace(/\n/g, ' ')}/`);
fs.writeFileSync(out, '# from npm cc-cedict\n' + lines.join('\n') + '\n');
console.log(lines.length, 'entries');
