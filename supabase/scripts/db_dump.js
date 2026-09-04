const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });
const postgres = require('postgres');
const fs = require('fs');

const sql = postgres(process.env.SUPABASE_DB_URL);

async function dumpSchema() {
  console.log('Connecting to Supabase to pull schema...');
  try {
    const schema = await sql`
      SELECT
        table_name,
        column_name,
        data_type,
        is_nullable,
        column_default
      FROM
        information_schema.columns
      WHERE
        table_schema = 'public'
      ORDER BY
        table_name, ordinal_position;
    `;

    const policies = await sql`
      SELECT
        tablename,
        policyname,
        permissive,
        roles,
        cmd,
        qual,
        with_check
      FROM
        pg_policies
      WHERE
        schemaname = 'public';
    `;

    let output = '-- Baroness-kt Supabase Schema (Generated via Node Bridge)\n\n';

    let currentTable = '';
    schema.forEach(row => {
      if (row.table_name !== currentTable) {
        currentTable = row.table_name;
        output += `\n-- Table: ${currentTable}\n`;
      }
      output += `--   ${row.column_name}: ${row.data_type} (${row.is_nullable === 'YES' ? 'NULLABLE' : 'NOT NULL'}${row.column_default ? ', DEFAULT ' + row.column_default : ''})\n`;
    });

    output += '\n\n-- RLS Policies\n';
    policies.forEach(p => {
      output += `-- Policy: ${p.policyname} on ${p.tablename} (${p.cmd})\n`;
    });

    const schemaPath = path.resolve(__dirname, '../schema.sql');
    fs.writeFileSync(schemaPath, output);
    console.log(`[OK] Schema successfully written to ${schemaPath}`);
  } catch (err) {
    console.error('[ERROR] Error dumping schema:', err.message);
  } finally {
    await sql.end();
  }
}

dumpSchema();
