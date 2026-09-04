const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
const postgres = require('postgres');
const fs = require('fs');

const sql = postgres(process.env.SUPABASE_DB_URL);

async function applySql() {
  const filePath = process.argv[2];
  if (!filePath) {
    console.error('[ERROR] Please provide a path to a .sql file');
    process.exit(1);
  }

  const absolutePath = path.isAbsolute(filePath) ? filePath : path.resolve(process.cwd(), filePath);
  if (!fs.existsSync(absolutePath)) {
    console.error(`[ERROR] File not found: ${absolutePath}`);
    process.exit(1);
  }

  console.log(`[PROCESS] Applying SQL from: ${filePath}...`);
  try {
    const content = fs.readFileSync(absolutePath, 'utf8');
    await sql.unsafe(content);
    console.log('√√ SQL applied successfully!');
  } catch (err) {
    console.error('[ERROR] Error applying SQL:', err.message);
  } finally {
    await sql.end();
  }
}

applySql();
