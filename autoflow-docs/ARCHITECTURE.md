# AutoFlow AI — Architectural Reference & Decision Records (ADR)

## ADR-001: Monorepo Organization
- **Context**: The project spans a Spring Boot backend, a cross-platform Flutter application (iOS, Android, Web), and deployment infrastructure.
- **Decision**: Adopt a unified repository structure (`autoflow-backend`, `autoflow-app`, `autoflow-infra`, `autoflow-docs`) to facilitate atomic commits, end-to-end integration testing, and shared API contract updates.

## ADR-002: Modular Monolith over Early Microservices
- **Context**: Initial operations require rapid feature velocity without the operational overhead of managing multi-cluster service meshes.
- **Decision**: Implement domain-driven boundaries inside a Spring Boot 3.3 modular monolith with clear interfaces. High-volume components (such as Webhook Ingestion or AI Generation) can be isolated and split into standalone microservices at scale without changing domain logic.

## ADR-003: Multi-Tenancy Strategy
- **Context**: Data must be strictly isolated between organization tenants.
- **Decision**: Shared Database, Shared Schema with an indexed `organization_id` column on all tenant-owned entities. Access is enforced at both the ORM layer (Hibernate filter) and database level (PostgreSQL Row-Level Security).

## ADR-004: Influencer Promo Security
- **Context**: Preventing affiliate and influencer privilege escalation.
- **Decision**: Strictly prohibit promo code and campaign mutation by non-admins. Influencers have zero write access to promo codes, commissions, or campaigns.
