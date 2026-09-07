const postgres = require('postgres');
require('dotenv').config();

const sql = postgres(process.env.SUPABASE_DB_URL);

async function check() {
    try {
        console.log("Checking latest messages...");
        const messages = await sql`SELECT * FROM messages ORDER BY created_at DESC LIMIT 5`;
        console.log("Messages Table:", messages);

        const fridayMessages = await sql`SELECT * FROM friday_messages ORDER BY created_at DESC LIMIT 5`;
        console.log("Friday Messages Table:", fridayMessages);

        const syncPipe = await sql`SELECT * FROM chat_sync_pipe ORDER BY created_at DESC LIMIT 5`;
        console.log("Sync Pipe Table:", syncPipe);

    } catch (err) {
        console.error("Error querying database:", err);
    } finally {
        await sql.end();
    }
}

check();
