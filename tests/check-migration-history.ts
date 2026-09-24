import dotenv from 'dotenv';
import postgres from 'postgres';
dotenv.config();

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });
async function check() {
  const rows = await sql`SELECT name, applied_at FROM migration_history ORDER BY id ASC`;
  console.log('Applied migrations in migration_history:', rows);
  await sql.end();
}
check();
