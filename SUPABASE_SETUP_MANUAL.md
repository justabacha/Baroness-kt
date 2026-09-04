# Supabase CLI Setup Manual for Baroness-kt (Windows/CMD)

Follow these precise steps to enable the AI agent to manage, debug, and update your backend database directly from the terminal.

---

### Step 1: Install the Supabase CLI
Since this is a Kotlin project, we will install the CLI **globally** so you don't need a `package.json` file.

**Windows (CMD / PowerShell):**
```cmd
npm install supabase -g
```
*Note: After this, you can just run `supabase login` instead of `npx supabase login`.*

---

### Step 2: Initialize & Link your Project
Open your **CMD** (Command Prompt) in the root of `C:/Baroness_Core/Baroness-kt` and run:

1.  **Initialize local config:**
    ```cmd
    npx supabase init
    ```
2.  **Login to your Supabase account:**
    ```cmd
    npx supabase login
    ```
3.  **Link your remote database:**
    ```cmd
    # Use your project's Reference ID (Dashboard -> Settings -> General)
    # This will ask for your Database Password.
    npx supabase link --project-ref your-project-id-here
    ```

---

### Step 3: Configure Environment Secrets
I have created a `.env_example` file in your root folder.

1.  **Duplicate the file**: Copy `.env_example` and rename the copy to `.env`.
2.  **Fill in the values**:
    *   `SUPABASE_PROJECT_ID`: Your Reference ID.
    *   `SUPABASE_DB_URL`: Use the **Transaction Pooler** URI if the direct one fails.
        *   Find this in: `Settings` -> `Database` -> `Connection string` -> `Mode: Transaction`.
        *   It usually looks like: `postgresql://postgres.[REF]:[PASS]@aws-0-[REGION].pooler.supabase.com:6543/postgres`
    *   `SUPABASE_SERVICE_ROLE_KEY`: Your service role key (Project Settings -> API).

*Note: The `.gitignore` has been updated to ensure your `.env` is never uploaded.*

---

### Step 4: Using the Node.js Bridge (New & Improved)
We have moved the database tools to `supabase/scripts/` to keep the root clean. You can now use these commands:

*   **Pull Schema**: `npm run db:dump` (Updates `supabase/schema.sql`)
*   **Apply Specific File**: `npm run db:apply path/to/file.sql`
*   **Run All Pending Migrations**: `npm run db:migrate`
*   **Re-run Specific Migration**: `npm run db:migrate 001` (Finds the file starting with 001 and executes it).

---

### Why we are doing this:
*   **Easy Debugging:** I can query the DB to see why messages are stuck or RLS is failing.
*   **Safe Migrations:** I can write SQL updates to `supabase/migrations/` for you to review and apply with one command.
*   **Consistency:** Our local Kotlin code will always stay in sync with the remote database structure.
