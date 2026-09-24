import dotenv from 'dotenv';
import postgres from 'postgres';
dotenv.config();

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });
async function check() {
  const rows = await sql`SELECT conname, contype FROM pg_constraint WHERE conname = 'friday_entities_owner_id_name_key'`;
  console.log('Constraint query result:', rows);
  await sql.end();
}
check();
