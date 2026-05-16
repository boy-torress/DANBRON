require('dotenv').config();

const required = ['SUPABASE_URL', 'SUPABASE_SERVICE_ROLE', 'JWT_SECRET'];
const optionalAi = ['GROQ_API_KEY', 'OPENROUTER_API_KEY', 'ANTHROPIC_API_KEY'];
const failures = [];

for (const key of required) {
  const value = process.env[key];
  if (!value || value.includes('your-') || value.includes('replace-')) {
    failures.push(`${key} missing or placeholder`);
  }
}

if (process.env.SUPABASE_KEY && !process.env.SUPABASE_SERVICE_ROLE) {
  failures.push('Use SUPABASE_SERVICE_ROLE on the backend; do not rely on anon SUPABASE_KEY for server writes');
}

if ((process.env.JWT_SECRET || '').length < 32) {
  failures.push('JWT_SECRET must be at least 32 characters');
}

const cors = process.env.CORS_ORIGIN || '';
if (!cors || cors.trim() === '*') {
  failures.push('CORS_ORIGIN must be explicit in production');
}

if (!optionalAi.some((key) => process.env[key] && !process.env[key].includes('your-'))) {
  failures.push('At least one AI provider key is required');
}

if (failures.length) {
  console.error('Production check failed:');
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log('Production check passed.');
