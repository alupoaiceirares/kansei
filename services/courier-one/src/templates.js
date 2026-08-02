import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import Handlebars from 'handlebars';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const templatesDir = path.join(__dirname, '..', 'templates');

const SUBJECTS = {
  'email-verification': 'Confirm your email',
  'password-reset': 'Reset your password',
};

const compiled = new Map();

function compile(name) {
  if (!compiled.has(name)) {
    const source = readFileSync(path.join(templatesDir, `${name}.hbs`), 'utf8');
    compiled.set(name, Handlebars.compile(source));
  }
  return compiled.get(name);
}

export function render(templateName, vars) {
  const subject = SUBJECTS[templateName];
  if (!subject) {
    throw new Error(`Unknown mail template: ${templateName}`);
  }
  return { subject, html: compile(templateName)(vars) };
}
