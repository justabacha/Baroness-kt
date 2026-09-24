import dotenv from 'dotenv';
import postgres from 'postgres';
dotenv.config();

const sql = postgres(process.env.SUPABASE_DB_URL, { onnotice: () => {} });
async function check() {
  const rows = await sql`
    SELECT tablename, policyname, roles, cmd, qual, with_check
    FROM pg_policies
    WHERE tablename IN ('profiles', 'friday_messages', 'friday_memories', 'chat_sync_pipe', 'access_keys')
  `;
  console.log('Existing policies:', rows);
  await sql.end();
}
check();
