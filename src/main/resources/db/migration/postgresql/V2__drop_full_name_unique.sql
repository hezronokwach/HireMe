-- users.full_name used to be unique. Databases created by Hibernate ddl-auto still have that
-- constraint, under a generated name (e.g. ukin1xa1v3pcuete8f8lxa47btl), so find it by column.
-- On databases created by V1 there is nothing to drop.
DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = 'app_user.users'::regclass
          AND c.contype = 'u'
          AND array_length(c.conkey, 1) = 1
          AND a.attname = 'full_name'
    LOOP
        EXECUTE format('ALTER TABLE app_user.users DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;
