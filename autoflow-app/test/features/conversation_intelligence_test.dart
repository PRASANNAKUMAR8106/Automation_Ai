import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:autoflow_app/features/contacts_crm/data/conversation_intelligence_repository.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/performance_telemetry_dialog.dart';
import 'package:autoflow_app/features/inbox/presentation/inbox_screen.dart';

void main() {
  group('Phase 21 Conversation Intelligence, SLA & CSAT Unit Tests', () {
    test('PerformanceAnalyticsModel parses JSON correctly', () {
      final json = {
        'averageFirstResponseTimeMinutes': 4.5,
        'averageResolutionTimeMinutes': 32.0,
        'slaFirstResponseComplianceRate': 97.5,
        'slaResolutionComplianceRate': 94.0,
        'totalConversations': 200,
        'resolvedConversations': 180,
        'activeConversations': 20,
        'averageCsatRating': 4.75,
        'totalCsatResponses': 95,
        'aiAutoPilotDeflectionRate': 65.0,
      };

      final model = PerformanceAnalyticsModel.fromJson(json);

      expect(model.averageFirstResponseTimeMinutes, 4.5);
      expect(model.averageResolutionTimeMinutes, 32.0);
      expect(model.slaFirstResponseComplianceRate, 97.5);
      expect(model.slaResolutionComplianceRate, 94.0);
      expect(model.totalConversations, 200);
      expect(model.resolvedConversations, 180);
      expect(model.activeConversations, 20);
      expect(model.averageCsatRating, 4.75);
      expect(model.totalCsatResponses, 95);
      expect(model.aiAutoPilotDeflectionRate, 65.0);
    });

    test('SlaPolicyModel parses JSON correctly', () {
      final json = {
        'id': 'policy-1',
        'name': 'WhatsApp Priority SLA',
        'channel': 'WHATSAPP',
        'priority': 'URGENT',
        'firstResponseTimeSeconds': 300,
        'resolutionTimeSeconds': 3600,
        'routingPolicy': 'ROUND_ROBIN',
        'active': true,
        'whatsappTemplateEnabled': true,
      };

      final model = SlaPolicyModel.fromJson(json);

      expect(model.id, 'policy-1');
      expect(model.name, 'WhatsApp Priority SLA');
      expect(model.channel, 'WHATSAPP');
      expect(model.priority, 'URGENT');
      expect(model.firstResponseTimeSeconds, 300);
      expect(model.resolutionTimeSeconds, 3600);
      expect(model.routingPolicy, 'ROUND_ROBIN');
      expect(model.active, true);
      expect(model.whatsappTemplateEnabled, true);
    });

    test('CsatSurveyModel parses JSON correctly', () {
      final json = {
        'id': 'survey-1',
        'conversationId': 'conv-123',
        'rating': 5,
        'feedbackText': 'Resolved my inquiry in 2 minutes!',
        'status': 'COMPLETED',
        'dispatchedAt': '2026-10-02T10:00:00Z',
        'respondedAt': '2026-10-02T10:05:00Z',
      };

      final model = CsatSurveyModel.fromJson(json);

      expect(model.id, 'survey-1');
      expect(model.conversationId, 'conv-123');
      expect(model.rating, 5);
      expect(model.feedbackText, 'Resolved my inquiry in 2 minutes!');
      expect(model.status, 'COMPLETED');
      expect(model.respondedAt, isNotNull);
    });

    test('ConversationIntelligenceRepository returns default analytics when offline', () async {
      final repo = ConversationIntelligenceRepository();
      final analytics = await repo.fetchPerformanceAnalytics();

      expect(analytics, isNotNull);
      expect(analytics.averageFirstResponseTimeMinutes, greaterThan(0));
      expect(analytics.slaFirstResponseComplianceRate, greaterThan(90));
      expect(analytics.averageCsatRating, greaterThan(4.0));
      expect(analytics.aiAutoPilotDeflectionRate, greaterThan(50));
    });
  });

  group('Phase 21 Performance Telemetry Widget Tests', () {
    testWidgets('PerformanceTelemetryDialog renders KPI cards, metrics, and close button', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            performanceAnalyticsProvider.overrideWith(
              (ref) => Future.value(PerformanceAnalyticsModel.defaultAnalytics),
            ),
          ],
          child: const MaterialApp(
            home: Scaffold(
              body: PerformanceTelemetryDialog(),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Conversation Intelligence & SLA Telemetry'), findsOneWidget);
      expect(find.textContaining('First Response Time'), findsOneWidget);
      expect(find.textContaining('Resolution Time'), findsOneWidget);
      expect(find.textContaining('Customer Satisfaction (CSAT)'), findsOneWidget);
      expect(find.textContaining('AI Auto-Pilot Deflection'), findsOneWidget);
      expect(find.text('Total Conversations: '), findsOneWidget);
      expect(find.text('Close'), findsOneWidget);
    });

    testWidgets('InboxScreen contains SLA & Performance Telemetry button and opens dialog', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 900));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            performanceAnalyticsProvider.overrideWith(
              (ref) => Future.value(PerformanceAnalyticsModel.defaultAnalytics),
            ),
          ],
          child: const MaterialApp(
            home: InboxScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      final telemetryButton = find.byKey(const Key('performance_telemetry_button'));
      expect(telemetryButton, findsOneWidget);

      await tester.tap(telemetryButton);
      await tester.pumpAndSettle();

      expect(find.byType(PerformanceTelemetryDialog), findsOneWidget);
      expect(find.text('Conversation Intelligence & SLA Telemetry'), findsOneWidget);
    });
  });
}
