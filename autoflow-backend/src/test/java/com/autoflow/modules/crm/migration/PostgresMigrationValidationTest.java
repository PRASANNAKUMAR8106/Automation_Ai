package com.autoflow.modules.crm.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway managed schema against a <em>real PostgreSQL</em> database.
 *
 * <p>The regular test suite runs on H2 with {@code ddl-auto: create-drop} and Flyway disabled, so
 * it never exercises the SQL migrations at all - it only proves that the entities and the Java
 * code agree with each other. Production runs PostgreSQL with {@code ddl-auto: validate} and
 * Flyway enabled, which is a completely different contract: the migration scripts must apply
 * cleanly on PostgreSQL and must produce exactly the schema the entities expect.
 *
 * <p>This test closes that gap. It is opt-in because it needs a PostgreSQL instance; enable it
 * with:
 *
 * <pre>
 *   ./gradlew test -Dautoflow.postgres.url=jdbc:postgresql://localhost:5432/autoflow_verify \
 *                   -Dautoflow.postgres.user=... -Dautoflow.postgres.password=...
 * </pre>
 *
 * <p>The CI pipeline provides a PostgreSQL service container and runs it on every push.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "autoflow.postgres.url", matches = ".+")
@DisplayName("PostgreSQL Flyway Migration Validation Tests")
class PostgresMigrationValidationTest {

    private static final String URL = System.getProperty("autoflow.postgres.url");
    private static final String USER = System.getProperty("autoflow.postgres.user", "postgres");
    private static final String PASSWORD = System.getProperty("autoflow.postgres.password", "");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TestEntityManager testEntityManager;

    /**
     * Points the slice at the PostgreSQL instance under test and asks Hibernate to validate the
     * entity mappings against the migrated schema - exactly the combination the production
     * profile uses.
     */
    @org.springframework.test.context.DynamicPropertySource
    static void postgresProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // The migrations are applied by migrateFromScratch() so that the run starts from a clean
        // database; letting the context migrate again would only be a no-op.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    /**
     * Migrates the target database from scratch before the Spring context is created.
     *
     * <p>The clean run guarantees the migrations are exercised exactly as they would be on a
     * fresh production deployment, rather than against a database that already happens to carry
     * the objects.
     */
    @BeforeAll
    static void migrateFromScratch() {
        Flyway flyway = Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    @DisplayName("Should apply every migration script successfully")
    void shouldApplyAllMigrationsSuccessfully() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).isNotEmpty();
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(Boolean.TRUE));

        List<String> versions = history.stream().map(r -> String.valueOf(r.get("version"))).toList();
        assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    }

    @Test
    @DisplayName("Should create the transactional outbox table introduced by V9")
    void shouldCreateOutboxTable() {
        Integer columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'crm_outbox_messages'",
                Integer.class);
        assertThat(columns).isNotNull().isGreaterThanOrEqualTo(14);

        Integer uniqueKeys = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uq_crm_outbox_org_idempotency'",
                Integer.class);
        assertThat(uniqueKeys).isEqualTo(1);
    }

    @Test
    @DisplayName("Should add the audit checksum columns introduced by V9")
    void shouldAddAuditChecksumColumns() {
        for (String table : List.of("messages", "conversation_internal_notes", "conversation_sla_events")) {
            Integer columns = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = 'audit_checksum'",
                    Integer.class, table);
            assertThat(columns).as("audit_checksum on " + table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should create the tables and columns introduced by V10")
    void shouldApplyV10Alignment() {
        for (String table : List.of("api_keys", "webhook_dlq_entries")) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("table " + table).isEqualTo(1);
        }

        Integer versionColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE column_name = 'version' "
                        + "AND table_name IN ('conversations', 'knowledge_chunks', 'conversation_sla_events', "
                        + "'contact_lead_score_audits', 'agent_channel_specializations', 'canned_responses', "
                        + "'crm_macros', 'conversation_internal_notes', 'crm_outbox_messages')",
                Integer.class);
        assertThat(versionColumns).isEqualTo(9);
    }

    @Test
    @DisplayName("Should validate every entity mapping against the migrated schema")
    void shouldValidateEntityMappingsAgainstMigratedSchema() {
        // The context was created with spring.jpa.hibernate.ddl-auto=validate against this very
        // database, so the successful injection of the EntityManagerFactory is itself the proof
        // that Hibernate found every mapped table and column in the migrated schema.
        assertThat(entityManagerFactory).isNotNull();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isNotEmpty();

        // And the migrated schema is usable: a real round trip through the migrated tables works.
        testEntityManager.getEntityManager().createNativeQuery("SELECT COUNT(*) FROM conversations").getSingleResult();
    }
}
