-- Migration 025: PhestyDrop Manifest Table
-- Provides remote version control for Baroness in-app update engine

CREATE TABLE IF NOT EXISTS phestydrop_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_code INT4 UNIQUE NOT NULL,
    version_name TEXT NOT NULL,
    download_url TEXT NOT NULL,
    changelog TEXT,
    is_mandatory BOOLEAN DEFAULT FALSE,
    min_supported_version_code INT4 DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable Row Level Security (RLS)
ALTER TABLE phestydrop_releases ENABLE ROW LEVEL SECURITY;

-- Drop existing policy if present to allow idempotent execution
DROP POLICY IF EXISTS "Allow public read access to phestydrop_releases" ON phestydrop_releases;

-- Create policy to allow public/authenticated read access for version checking
CREATE POLICY "Allow public read access to phestydrop_releases"
ON phestydrop_releases FOR SELECT
TO anon, authenticated
USING (true);
