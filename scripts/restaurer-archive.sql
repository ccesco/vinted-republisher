-- US-26 : recharge le data.json d'une archive dans une base vide créée par Liquibase (application démarrée une fois,
-- puis arrêtée). Le fichier doit être copié d'abord dans le conteneur, en /tmp/data.json (voir README).
-- Tout ou rien : une seule transaction ; refusée si la version du schéma diffère de celle de l'archive.
BEGIN;
-- Les tables sont rechargées dans un ordre quelconque (celui des clés du JSON) : les clés étrangères ne sont pas vérifiées pendant le
-- rechargement (les lignes viennent d'une base où elles l'étaient). Réservé au superutilisateur, comme l'utilisateur
-- du conteneur PostgreSQL.
SET LOCAL session_replication_role = replica;
DO $$
DECLARE
    archive jsonb := pg_read_file('/tmp/data.json')::jsonb;
    current_version text := (SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1);
    archived_table text;
    key_column text;
    sequence_name text;
BEGIN
    IF archive->>'formatVersion' IS DISTINCT FROM '1' THEN
        RAISE EXCEPTION 'Format d''archive % inconnu', archive->>'formatVersion';
    END IF;
    IF archive->>'schemaVersion' IS DISTINCT FROM current_version THEN
        RAISE EXCEPTION 'Archive au schéma %, base au schéma % : démarrez la version de l''application de l''archive',
            archive->>'schemaVersion', current_version;
    END IF;
    FOR archived_table IN SELECT jsonb_object_keys(archive->'tables') LOOP
        EXECUTE format('INSERT INTO %I SELECT * FROM jsonb_populate_recordset(NULL::%I, $1)', archived_table, archived_table)
            USING archive->'tables'->archived_table;
        -- Les identifiants générés reprennent après le plus grand identifiant rechargé.
        FOR key_column IN SELECT c.column_name FROM information_schema.columns c
                           WHERE c.table_schema = current_schema() AND c.table_name = archived_table LOOP
            sequence_name := pg_get_serial_sequence(quote_ident(archived_table), key_column);
            IF sequence_name IS NOT NULL THEN
                EXECUTE format('SELECT setval(%L, coalesce((SELECT max(%I) FROM %I), 0) + 1, false)',
                               sequence_name, key_column, archived_table);
            END IF;
        END LOOP;
    END LOOP;
END
$$;
COMMIT;
