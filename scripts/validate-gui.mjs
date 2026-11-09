import { readFile, access } from 'node:fs/promises';

const fail = (message) => {
  console.error(`FAIL: ${message}`);
  process.exitCode = 1;
};
const pass = (message) => console.log(`PASS: ${message}`);

const htmlPath = new URL('../prototype/index.html', import.meta.url);
const cssPath = new URL('../prototype/styles.css', import.meta.url);
const appPath = new URL('../prototype/app.js', import.meta.url);

let html;
let css;
let app;
try {
  [html, css, app] = await Promise.all([
    readFile(htmlPath, 'utf8'),
    readFile(cssPath, 'utf8'),
    readFile(appPath, 'utf8')
  ]);
  pass('Prototype HTML, CSS, and JavaScript files exist and are readable');
} catch (error) {
  fail(`Could not read prototype source files: ${error.message}`);
  process.exit(1);
}

const ids = [...html.matchAll(/\\bid="([^"]+)"/g)].map((match) => match[1]);
const uniqueIds = new Set(ids);
const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index);
if (duplicates.length) fail(`Duplicate HTML IDs: ${[...new Set(duplicates)].join(', ')}`);
else pass('HTML element IDs are unique');

const jsSelectors = [...app.matchAll(/\\$\\('#([^']+)'/g)].map((match) => match[1]);
const missingIds = [...new Set(jsSelectors)].filter((id) => !uniqueIds.has(id) && !id.startsWith('suggestion-'));
if (missingIds.length) fail(`JavaScript references missing HTML IDs: ${missingIds.join(', ')}`);
else pass('All static JavaScript ID selectors resolve to HTML elements');

const requiredHtml = [
  ['main configuration view', 'id="mainView"'],
  ['changed blocks view', 'id="changedView"'],
  ['hitbox editor view', 'id="hitboxView"'],
  ['advanced adhesion editor view', 'id="advancedView"'],
  ['block search input', 'id="blockSearch"'],
  ['contextual adhesion Configure button', 'id="adhesionConfigure"'],
  ['movement options', 'data-group="movement"'],
  ['scheduled tick options', 'data-group="ticks"'],
  ['adhesion options', 'data-group="adhesion"']
];
for (const [label, token] of requiredHtml) {
  if (html.includes(token)) pass(`HTML contains ${label}`);
  else fail(`HTML is missing ${label}`);
}

const requiredInteractions = [
  ['keyboard down navigation', "event.key === 'ArrowDown'"],
  ['keyboard up navigation', "event.key === 'ArrowUp'"],
  ['Tab completion', "event.key === 'Tab'"],
  ['Enter selection', "event.key === 'Enter'"],
  ['Escape dismissal', "event.key === 'Escape'"],
  ['browser-local persistence', 'localStorage.setItem']
];
for (const [label, token] of requiredInteractions) {
  if (app.includes(token)) pass(`JavaScript contains ${label}`);
  else fail(`JavaScript is missing ${label}`);
}

for (const [label, source] of [['styles', css], ['application script', app]]) {
  if (source.trim().length > 100) pass(`Prototype ${label} is not empty`);
  else fail(`Prototype ${label} is unexpectedly small or empty`);
}

if (process.exitCode) {
  console.error('GUI prototype validation failed.');
} else {
  console.log('GUI prototype validation completed successfully.');
}
