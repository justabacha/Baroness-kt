import dotenv from 'dotenv';
import postgres from 'postgres';
dotenv.config();

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });
async function check() {
  const rows = await sql`SELECT id, secret_key FROM access_keys`;
  console.log('access_keys rows:', rows);
  await sql.end();
}
check();
