-- V12: Store the opaque JSON payload columns as text
--
-- The seven entities below map their JSON payload column as a String with
-- `@Column(columnDefinition = "jsonb")`. Hibernate binds a String as a character varying
-- parameter, and PostgreSQL refuses that for a jsonb column:
--
--   ERROR: column "branding_config" is of type jsonb but expression is of type character varying
--   Hint: You will need to rewrite or cast the expression.
--
-- Every insert of an Organization, Contact, Template, WorkflowVersion, AutomationExecution,
-- ConnectedAccount or WebhookEvent therefore failed on PostgreSQL, even though schema
-- validation passed (the column type matched the mapping).
--
-- The payload is an opaque JSON document that the application parses itself
-- (objectMapper.readValue / DagModel.fromJson), so a text column is the correct storage type.
-- It is also the type the rest of the schema already uses for the same kind of payload
-- (crm_macros.actions_json, conversation_sla_events.details), and it behaves identically on
-- PostgreSQL and H2.
--
-- The conversion is guarded on the actual column type, so the migration is safe to re-run and
-- is a no-op on databases where the column has already been converted.

DO $$
DECLARE
    spec record;
BEGIN
    FOR spec IN
        SELECT * FROM (VALUES
            ('organizations',         'branding_config',    '{}'),
            ('connected_accounts',    'metadata',           '{}'),
            ('contacts',              'custom_fields',      '{}'),
            ('automation_executions', 'execution_context',  '{}'),
            ('templates',             'graph_definition',   NULL),
            ('workflow_versions',     'graph_definition',   NULL),
            ('webhook_events',        'payload',            NULL)
        ) AS t(tbl, col, dflt)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = spec.tbl
              AND column_name = spec.col
              AND data_type = 'jsonb'
        ) THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I DROP DEFAULT', spec.tbl, spec.col);
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE TEXT USING %I::text', spec.tbl, spec.col, spec.col);
            IF spec.dflt IS NOT NULL THEN
                EXECUTE format('ALTER TABLE %I ALTER COLUMN %I SET DEFAULT %L', spec.tbl, spec.col, spec.dflt);
            END IF;
        END IF;
    END LOOP;
END $$;
