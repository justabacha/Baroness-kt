#!/usr/bin/env ts-node
/**
* Migration Runner Script
*
* This script runs SQL migration files against the database.
* It reads migrations from the migrations/ directory and executes them in order.
*
* Usage:
*   npm run migrate              # Run all pending migrations
*   npm run migrate:up 001       # Run specific migration
*
* Environment Variables Required:
*   DATABASE_URL - PostgreSQL connection string
    */

import fs from 'fs';
import path from 'path';
import pg from 'pg';
import dotenv from 'dotenv';

// Load environment variables
dotenv.config();

const { Pool } = pg;

// Create PostgreSQL pool directly for migrations
const pgPool = new Pool({
connectionString: process.env.DATABASE_URL || '',
max: 20,
idleTimeoutMillis: 60000,
connectionTimeoutMillis: 10000,
});

const MIGRATIONS_DIR = path.join(__dirname, '..', 'migrations');

interface Migration {
filename: string;
filepath: string;
version: string;
}

/**
* Get all migration files sorted by version
  */
  function getMigrations(): Migration[] {
  const files = fs.readdirSync(MIGRATIONS_DIR)
  .filter(f => f.endsWith('.sql'))
  .sort();

return files.map(filename => ({
filename,
filepath: path.join(MIGRATIONS_DIR, filename),
version: filename.split('_')[0]
}));
}

/**
* Execute a single migration file
  */
  async function runMigration(migration: Migration): Promise<void> {
  console.log(`Running migration: ${migration.filename}`);

const sql = fs.readFileSync(migration.filepath, 'utf-8');

const client = await pgPool.connect();
try {
await client.query('BEGIN');
await client.query(sql);
await client.query('COMMIT');
console.log(`✓ Migration ${migration.filename} completed`);
} catch (error) {
await client.query('ROLLBACK');
console.error(`✗ Migration ${migration.filename} failed:`, error);
throw error;
} finally {
client.release();
}
}

/**
* Run all migrations
  */
  async function runAllMigrations(): Promise<void> {
  const migrations = getMigrations();

if (migrations.length === 0) {
console.log('No migration files found.');
return;
}

console.log(`Found ${migrations.length} migration files.`);

for (const migration of migrations) {
try {
await runMigration(migration);
} catch (error) {
console.error('Migration failed. Stopping execution.');
process.exit(1);
}
}

console.log('All migrations completed successfully.');
}

/**
* Run a specific migration by version
  */
  async function runSpecificMigration(version: string): Promise<void> {
  const migrations = getMigrations();
  const migration = migrations.find(m => m.version === version);

if (!migration) {
console.error(`Migration ${version} not found.`);
process.exit(1);
}

await runMigration(migration);
}

/**
* Main execution
  */
  async function main(): Promise<void> {
  const args = process.argv.slice(2);

if (args.length === 0) {
await runAllMigrations();
} else if (args[0] === 'up' && args[1]) {
await runSpecificMigration(args[1]);
} else {
console.log('Usage:');
console.log('  npm run migrate              # Run all migrations');
console.log('  npm run migrate:up <version> # Run specific migration');
process.exit(1);
}

await pgPool.end();
}

main().catch(error => {
console.error('Fatal error:', error);
process.exit(1);
});

============================================
import { createClient } from '@supabase/supabase-js';
import pg from 'pg';

const { Pool } = pg;

export const databaseConfig = {
url: process.env.DATABASE_URL || '',
supabaseUrl: process.env.SUPABASE_URL || '',
supabaseAnonKey: process.env.SUPABASE_ANON_KEY || '',
supabaseServiceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY || '',
};

// Supabase client for application queries
export const supabase = createClient(databaseConfig.supabaseUrl, databaseConfig.supabaseAnonKey, {
db: {
schema: 'app_auth', // Use custom auth schema instead of Supabase's built-in auth
},
});

// Supabase admin client (service role) for privileged operations
export const supabaseAdmin = createClient(
databaseConfig.supabaseUrl,
databaseConfig.supabaseServiceRoleKey,
{
db: {
schema: 'app_auth', // Use custom auth schema instead of Supabase's built-in auth
},
}
);

// PostgreSQL pool for raw SQL (migrations, complex queries)
export const pgPool = new Pool({
connectionString: databaseConfig.url,
max: 20,
idleTimeoutMillis: 30000,
connectionTimeoutMillis: process.env.NODE_ENV === 'test' ? 30000 : 10000,
});

// Health check function
export async function checkDatabaseConnection(): Promise<boolean> {
try {
const client = await pgPool.connect();
await client.query('SELECT 1');
client.release();
return true;
} catch (error) {
console.error('Database connection failed:', error);
return false;
}
}

=============================
-- Migration 001: Create all schemas
-- This migration creates all database schemas as per DDS §4
-- Note: 'auth' is renamed to 'app_auth' to avoid conflict with Supabase's built-in auth schema

CREATE SCHEMA IF NOT EXISTS app_auth;
CREATE SCHEMA IF NOT EXISTS wallet;
CREATE SCHEMA IF NOT EXISTS trading;
CREATE SCHEMA IF NOT EXISTS pricing;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS compliance;
CREATE SCHEMA IF NOT EXISTS referral;
CREATE SCHEMA IF NOT EXISTS admin;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS notifications;
CREATE SCHEMA IF NOT EXISTS events;
CREATE SCHEMA IF NOT EXISTS reporting;

-- Grant usage on schemas to appropriate roles (adjust based on Supabase setup)
-- Supabase typically handles this via the dashboard, but we ensure schemas exist

==========================
-- Migration 002: Auth schema tables
-- Creates tables for authentication and authorization as per DDS §5.1-5.8
-- Note: Schema renamed to 'app_auth' to avoid Supabase conflict

-- app_auth.users
CREATE TABLE IF NOT EXISTS app_auth.users (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
email VARCHAR(255) NOT NULL,
phone VARCHAR(20),
password_hash VARCHAR(255) NOT NULL,
full_name VARCHAR(100),
is_verified BOOLEAN NOT NULL DEFAULT FALSE,
is_active BOOLEAN NOT NULL DEFAULT TRUE,
kyc_status VARCHAR(20) NOT NULL DEFAULT 'none' CHECK (kyc_status IN ('none','pending','approved','rejected')),
self_exclusion_until TIMESTAMPTZ,
last_login_at TIMESTAMPTZ,
deleted_at TIMESTAMPTZ,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- app_auth.sessions
CREATE TABLE IF NOT EXISTS app_auth.sessions (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID NOT NULL,
token_hash VARCHAR(255) NOT NULL,
ip_address INET,
user_agent TEXT,
expires_at TIMESTAMPTZ NOT NULL,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT app_auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_auth.users(id) ON DELETE CASCADE
);

-- app_auth.mfa_tokens
CREATE TABLE IF NOT EXISTS app_auth.mfa_tokens (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID NOT NULL,
secret VARCHAR(255) NOT NULL,
is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
backup_codes TEXT[],
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT app_auth_mfa_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_auth.users(id) ON DELETE CASCADE,
CONSTRAINT app_auth_mfa_tokens_user_id_unique UNIQUE (user_id)
);

-- app_auth.password_reset_tokens
CREATE TABLE IF NOT EXISTS app_auth.password_reset_tokens (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID NOT NULL,
token_hash VARCHAR(255) NOT NULL,
expires_at TIMESTAMPTZ NOT NULL,
used_at TIMESTAMPTZ,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT app_auth_password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_auth.users(id) ON DELETE CASCADE
);

-- app_auth.roles
CREATE TABLE IF NOT EXISTS app_auth.roles (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(50) NOT NULL UNIQUE,
description TEXT,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- app_auth.permissions
CREATE TABLE IF NOT EXISTS app_auth.permissions (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
name VARCHAR(100) NOT NULL UNIQUE,
resource VARCHAR(50) NOT NULL,
action VARCHAR(50) NOT NULL,
description TEXT,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- app_auth.user_roles
CREATE TABLE IF NOT EXISTS app_auth.user_roles (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID NOT NULL,
role_id UUID NOT NULL,
assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT app_auth_user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_auth.users(id) ON DELETE CASCADE,
CONSTRAINT app_auth_user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES app_auth.roles(id) ON DELETE CASCADE,
CONSTRAINT app_auth_user_roles_user_role_unique UNIQUE (user_id, role_id)
);

-- app_auth.role_permissions
CREATE TABLE IF NOT EXISTS app_auth.role_permissions (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
role_id UUID NOT NULL,
permission_id UUID NOT NULL,
granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT app_auth_role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES app_auth.roles(id) ON DELETE CASCADE,
CONSTRAINT app_auth_role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES app_auth.permissions(id) ON DELETE CASCADE,
CONSTRAINT app_auth_role_permissions_role_permission_unique UNIQUE (role_id, permission_id)
);

-- Indexes for app_auth schema
CREATE UNIQUE INDEX IF NOT EXISTS app_auth_users_email_idx ON app_auth.users(email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS app_auth_users_phone_idx ON app_auth.users(phone) WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS app_auth_sessions_user_id_idx ON app_auth.sessions(user_id);
CREATE INDEX IF NOT EXISTS app_auth_sessions_token_hash_idx ON app_auth.sessions(token_hash);
CREATE INDEX IF NOT EXISTS app_auth_password_reset_tokens_user_id_idx ON app_auth.password_reset_tokens(user_id);

===================================
-- Migration 004: Trading schema tables
-- Creates tables for trading operations as per DDS §5.12-5.15

-- trading.assets
CREATE TABLE IF NOT EXISTS trading.assets (
symbol VARCHAR(20) PRIMARY KEY,
name VARCHAR(100) NOT NULL,
asset_type VARCHAR(20) NOT NULL CHECK (asset_type IN ('forex','commodity','crypto','index')),
is_active BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- trading.binary_contracts
CREATE TABLE IF NOT EXISTS trading.binary_contracts (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id UUID NOT NULL,
asset_symbol VARCHAR(20) NOT NULL,
stake_amount NUMERIC(16,4) NOT NULL CHECK (stake_amount > 0),
direction VARCHAR(10) NOT NULL CHECK (direction IN ('call','put')),
entry_price NUMERIC(12,6) NOT NULL CHECK (entry_price > 0),
expiry_price NUMERIC(12,6),
strike_price NUMERIC(12,6) NOT NULL CHECK (strike_price > 0),
payout_ratio NUMERIC(4,2) NOT NULL CHECK (payout_ratio > 0),
potential_payout NUMERIC(16,4) NOT NULL CHECK (potential_payout > 0),
entry_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
expiry_time TIMESTAMPTZ NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active','settling','won','lost','draw','cancelled')),
settlement_reason VARCHAR(50),
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT trading_binary_contracts_user_id_fkey FOREIGN KEY (user_id) REFERENCES app_auth.users(id) ON DELETE RESTRICT,
CONSTRAINT trading_binary_contracts_asset_symbol_fkey FOREIGN KEY (asset_symbol) REFERENCES trading.assets(symbol)
);

-- trading.contract_events
CREATE TABLE IF NOT EXISTS trading.contract_events (
id BIGSERIAL PRIMARY KEY,
contract_id UUID NOT NULL,
event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('created','price_update','extended','settled','cancelled')),
event_data JSONB NOT NULL,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT trading_contract_events_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES trading.binary_contracts(id) ON DELETE CASCADE
);

-- trading.asset_config
CREATE TABLE IF NOT EXISTS trading.asset_config (
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
asset_symbol VARCHAR(20) NOT NULL,
min_stake NUMERIC(16,4) NOT NULL CHECK (min_stake > 0),
max_stake NUMERIC(16,4) NOT NULL CHECK (max_stake > 0),
min_duration_seconds INTEGER NOT NULL CHECK (min_duration_seconds > 0),
max_duration_seconds INTEGER NOT NULL CHECK (max_duration_seconds > 0),
payout_ratio NUMERIC(4,2) NOT NULL CHECK (payout_ratio > 0),
is_tradable BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
CONSTRAINT trading_asset_config_asset_symbol_fkey FOREIGN KEY (asset_symbol) REFERENCES trading.assets(symbol) ON DELETE CASCADE,
CONSTRAINT trading_asset_config_asset_symbol_unique UNIQUE (asset_symbol)
);

-- Indexes for trading schema
CREATE INDEX IF NOT EXISTS trading_contracts_user_id_idx ON trading.binary_contracts(user_id);
CREATE INDEX IF NOT EXISTS trading_contracts_asset_symbol_idx ON trading.binary_contracts(asset_symbol);
CREATE INDEX IF NOT EXISTS trading_contracts_expiry_idx ON trading.binary_contracts(expiry_time) WHERE status = 'active';
CREATE INDEX IF NOT EXISTS trading_contracts_status_idx ON trading.binary_contracts(status);

DO $$
BEGIN
IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='trading' AND table_name='binary_contracts' AND column_name='entry_time') THEN
IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relname = 'trading_contracts_entry_time_idx' AND n.nspname = 'trading') THEN
CREATE INDEX trading_contracts_entry_time_idx ON trading.binary_contracts(entry_time);
END IF;
ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='trading' AND table_name='binary_contracts' AND column_name='purchase_time') THEN
IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relname = 'trading_contracts_purchase_time_idx' AND n.nspname = 'trading') THEN
CREATE INDEX trading_contracts_purchase_time_idx ON trading.binary_contracts(purchase_time);
END IF;
END IF;
END $$;

CREATE INDEX IF NOT EXISTS trading_contract_events_contract_id_idx ON trading.contract_events(contract_id);

====================
-- Migration 032: Cleanup redundant columns and rogue constraints in binary_contracts
-- Removes entry_price as it is redundant with strike_price as per WP-10 §4.2
-- Drops rogue constraints that survived column renames

DO $$
BEGIN
-- Drop redundant column
IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='trading' AND table_name='binary_contracts' AND column_name='entry_price') THEN
ALTER TABLE trading.binary_contracts DROP COLUMN entry_price;
END IF;

    -- Drop rogue constraints on contract_type
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_direction_check;
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS trading_binary_contracts_direction_check;

    -- Drop rogue constraints on payout_rate
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_payout_ratio_check;
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS trading_binary_contracts_payout_ratio_check;

    -- Drop rogue constraints on status
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_status_check;
    -- Note: trading_binary_contracts_status_check is the new correct one

    -- Drop rogue constraints on stake
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_stake_amount_check;

    -- Drop rogue constraints on strike_price
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_strike_price_check;

    -- Drop rogue constraints on potential_payout
    ALTER TABLE trading.binary_contracts DROP CONSTRAINT IF EXISTS binary_contracts_potential_payout_check;

END $$;
