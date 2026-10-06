-- The entity maps created_at/updated_at as java.time.Instant, which Hibernate 7
-- expects as TIMESTAMP WITH TIME ZONE; V1 used plain TIMESTAMP, which can fail
-- ddl-auto=validate. Done as a new migration so V1's checksum is untouched.
ALTER TABLE users ALTER COLUMN created_at SET DATA TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ALTER COLUMN updated_at SET DATA TYPE TIMESTAMP WITH TIME ZONE;
