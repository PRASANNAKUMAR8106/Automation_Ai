-- V11: Align crm_macros.actions_json with the CrmMacro entity mapping
--
-- V8 created crm_macros.actions_json as JSONB, but the CrmMacro entity maps it as
--   @Column(name = "actions_json", nullable = false, columnDefinition = "text")
-- With `spring.jpa.hibernate.ddl-auto: validate` the application therefore aborts at startup
-- with:
--   Schema-validation: wrong column type encountered in column [actions_json] in table
--   [crm_macros]; found [jsonb (Types#OTHER)], but expecting [text (Types#VARCHAR)]
--
-- The macro actions are an opaque JSON document that the application parses and validates
-- itself (MacroActionValidator), so a text column is the correct storage type: it matches the
-- entity mapping and works identically on PostgreSQL and H2.
--
-- V8 is left untouched - editing an already applied migration would change its Flyway
-- checksum and abort startup on every database where it has already run.

ALTER TABLE crm_macros ALTER COLUMN actions_json DROP DEFAULT;
ALTER TABLE crm_macros ALTER COLUMN actions_json TYPE TEXT USING actions_json::text;
ALTER TABLE crm_macros ALTER COLUMN actions_json SET DEFAULT '[]';
