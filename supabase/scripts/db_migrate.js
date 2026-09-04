const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
const postgres = require('postgres');
const fs = require('fs');

const sql = postgres(process.env.SUPABASE_DB_URL, {
  onnotice: () => {}
});

const MIGRATIONS_DIR = path.resolve(__dirname, '../migrations');

async function ensureMigrationTable() {
  await sql`
    CREATE TABLE IF NOT EXISTS migration_history (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );
  `;
}

async function applyMigration(fileName, recordInHistory = true) {
  const filePath = path.join(MIGRATIONS_DIR, fileName);
  const content = fs.readFileSync(filePath, 'utf8');

  console.log(`⁛ Applying migration: ${fileName}...`);
  try {
    await sql.begin(async (tx) => {
      await tx.unsafe(content);
      if (recordInHistory) {
        await tx`
          INSERT INTO migration_history (name) VALUES (${fileName})
          ON CONFLICT (name) DO NOTHING;
        `;
      }
    });
    console.log(`√√ Finished ${fileName}`);
  } catch (err) {
    console.error(`[ERROR] Failed to apply ${fileName}:`, err.message);
    throw err;
  }
}

async function run() {
  const args = process.argv.slice(2);

  try {
    await ensureMigrationTable();

    const files = fs.readdirSync(MIGRATIONS_DIR)
      .filter(f => f.endsWith('.sql'))
      .sort();

    if (args[0] === 'up' && args[1]) {
      const version = args[1];
      const target = files.find(f => f.startsWith(version));

      if (!target) {
        console.error(`[ERROR] X No migration found for version: ${version}`);
        process.exit(1);
      }

      console.log(`► Running version ${version}: ${target} (Manual Override)`);
      await applyMigration(target, false);
      return;
    }

    const applied = await sql`SELECT name FROM migration_history`;
    const appliedNames = applied.map(r => r.name);
    const pending = files.filter(f => !appliedNames.includes(f));

    if (pending.length === 0) {
      console.log('√√ Database is up to date.');
      return;
    }

    console.log(`↺ ${pending.length} migrations to apply.`);
    for (const file of pending) {
      await applyMigration(file);
    }
    console.log('√√ All migrations applied successfully.');

  } catch (err) {

  } finally {
    await sql.end();
  }
}

run();
