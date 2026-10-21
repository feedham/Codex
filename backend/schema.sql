CREATE TABLE IF NOT EXISTS directory_members (
 id TEXT PRIMARY KEY COLLATE BINARY, first_name TEXT NOT NULL, last_name TEXT NOT NULL,
 full_name TEXT GENERATED ALWAYS AS (TRIM(first_name || ' ' || last_name)) STORED,
 member_number TEXT, cell_number TEXT,
 enrollment_date TEXT CHECK(enrollment_date IS NULL OR enrollment_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
 enrollment_source TEXT, area TEXT, chamber TEXT, biometric TEXT, gender TEXT, parent_name TEXT,
 residence TEXT, photo_asset TEXT, cnic TEXT, source_page INTEGER,
 needs_review INTEGER NOT NULL DEFAULT 0 CHECK(needs_review IN (0,1))
) STRICT;
CREATE INDEX IF NOT EXISTS idx_directory_name ON directory_members(full_name);
CREATE INDEX IF NOT EXISTS idx_directory_enrollment ON directory_members(enrollment_date,id);
CREATE INDEX IF NOT EXISTS idx_directory_cell ON directory_members(cell_number);
CREATE TABLE IF NOT EXISTS directory_metadata (
 singleton INTEGER PRIMARY KEY CHECK(singleton=1), revision TEXT NOT NULL, source_filename TEXT NOT NULL,
 source_sha256 TEXT NOT NULL, member_count INTEGER NOT NULL, imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
) STRICT;
