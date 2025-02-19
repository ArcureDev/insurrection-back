ALTER TABLE player
    ADD COLUMN roles JSONB default '{}'::jsonb;