import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:autoflow_app/features/crm/data/agent_productivity_repository.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/collision_warning_banner.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/canned_response_dialog.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/macro_runner_dialog.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/conversation_timeline_sheet.dart';

class MockAgentProductivityRepository extends AgentProductivityRepository {
  @override
  Future<List<CannedResponseModel>> getCannedResponses({String? category, String? search}) async {
    return [
      const CannedResponseModel(
        id: 'canned-1',
        shortcut: '#refund',
        title: 'Refund Policy & Guarantee',
        content: 'We offer a 100% money-back guarantee within 14 days of purchase.',
        category: 'BILLING',
        usageCount: 18,
      ),
    ];
  }

  @override
  Future<String> interpolateTemplate(String conversationId, String template) async {
    return template;
  }

  @override
  Future<List<CrmMacroModel>> getMacros() async {
    return [
      const CrmMacroModel(
        id: 'macro-1',
        name: 'VIP Fast Track',
        description: 'Tags as VIP and routes to Tier 2 specialist',
        actionsJson: '[{"type":"ADD_TAGS","tags":["vip"]}]',
      ),
    ];
  }

  @override
  Future<ApplyMacroResultModel> applyMacro(String conversationId, String macroId) async {
    return const ApplyMacroResultModel(
      macroId: 'macro-1',
      macroName: 'VIP Fast Track',
      success: true,
      actionsExecuted: ['Applied tag: vip'],
    );
  }

  @override
  Future<List<TimelineEventModel>> getTimeline(String conversationId) async {
    return [
      TimelineEventModel(
        id: 'evt-1',
        category: 'MESSAGE',
        eventType: 'INBOUND',
        summary: 'Inbound customer inquiry',
        actor: 'CUSTOMER',
        timestamp: DateTime.now().subtract(const Duration(minutes: 15)),
      ),
      TimelineEventModel(
        id: 'evt-2',
        category: 'SLA_EVENT',
        eventType: 'WARNING',
        summary: 'Approaching First Response Deadline',
        actor: 'SYSTEM',
        timestamp: DateTime.now().subtract(const Duration(minutes: 5)),
      ),
    ];
  }
}

void main() {
  group('Phase 22 Agent Productivity Models & Repository Tests', () {
    test('CannedResponseModel parses JSON correctly', () {
      final json = {
        'id': 'snippet-1',
        'shortcut': '#welcome',
        'title': 'Welcome greeting',
        'content': 'Hello {{contact.name}}, thanks for contacting support!',
        'category': 'SUPPORT',
        'usageCount': 42,
      };

      final model = CannedResponseModel.fromJson(json);

      expect(model.id, 'snippet-1');
      expect(model.shortcut, '#welcome');
      expect(model.title, 'Welcome greeting');
      expect(model.content, 'Hello {{contact.name}}, thanks for contacting support!');
      expect(model.category, 'SUPPORT');
      expect(model.usageCount, 42);
    });

    test('AgentPresenceModel parses JSON correctly', () {
      final json = {
        'userId': 'usr-abc',
        'userEmail': 'sarah@autoflow.ai',
        'action': 'TYPING',
        'lastActiveAt': '2026-10-02T10:00:00Z',
      };

      final model = AgentPresenceModel.fromJson(json);

      expect(model.userId, 'usr-abc');
      expect(model.userEmail, 'sarah@autoflow.ai');
      expect(model.action, 'TYPING');
      expect(model.lastActiveAt, isNotNull);
    });

    test('InternalNoteModel parses JSON correctly', () {
      final json = {
        'id': 'note-1',
        'conversationId': 'conv-123',
        'authorEmail': 'lead@autoflow.ai',
        'noteType': 'SUPERVISOR_WHISPER',
        'content': 'Escalated from tier 1, please review payment status',
        'createdAt': '2026-10-02T10:05:00Z',
      };

      final model = InternalNoteModel.fromJson(json);

      expect(model.id, 'note-1');
      expect(model.conversationId, 'conv-123');
      expect(model.authorEmail, 'lead@autoflow.ai');
      expect(model.noteType, 'SUPERVISOR_WHISPER');
      expect(model.content, 'Escalated from tier 1, please review payment status');
      expect(model.createdAt, isNotNull);
    });

    test('CrmMacroModel and ApplyMacroResultModel parse JSON correctly', () {
      final macroJson = {
        'id': 'macro-1',
        'name': 'VIP Fast Track',
        'description': 'Tags as VIP and routes to Tier 2 specialist',
        'actionsJson': '[{"type":"ADD_TAGS","tags":["vip"]}]',
      };

      final macro = CrmMacroModel.fromJson(macroJson);
      expect(macro.id, 'macro-1');
      expect(macro.name, 'VIP Fast Track');
      expect(macro.description, 'Tags as VIP and routes to Tier 2 specialist');
      expect(macro.actionsJson, '[{"type":"ADD_TAGS","tags":["vip"]}]');

      final resultJson = {
        'macroId': 'macro-1',
        'macroName': 'VIP Fast Track',
        'success': true,
        'actionsExecuted': ['Applied tag: vip'],
      };

      final result = ApplyMacroResultModel.fromJson(resultJson);
      expect(result.macroId, 'macro-1');
      expect(result.macroName, 'VIP Fast Track');
      expect(result.success, isTrue);
      expect(result.actionsExecuted, contains('Applied tag: vip'));
    });

    test('TimelineEventModel parses JSON correctly', () {
      final json = {
        'id': 'evt-1',
        'category': 'SLA_EVENT',
        'eventType': 'BREACH',
        'summary': 'First Response Time SLA Breached',
        'actor': 'SYSTEM',
        'timestamp': '2026-10-02T10:10:00Z',
      };

      final model = TimelineEventModel.fromJson(json);

      expect(model.id, 'evt-1');
      expect(model.category, 'SLA_EVENT');
      expect(model.eventType, 'BREACH');
      expect(model.summary, 'First Response Time SLA Breached');
      expect(model.actor, 'SYSTEM');
      expect(model.timestamp, isNotNull);
    });

    test('AgentProductivityRepository returns robust offline fallbacks', () async {
      final repo = AgentProductivityRepository();

      final canned = await repo.getCannedResponses();
      expect(canned.isNotEmpty, isTrue);
      expect(canned.any((c) => c.shortcut == '#refund'), isTrue);

      final macros = await repo.getMacros();
      expect(macros.length, greaterThanOrEqualTo(2));
      expect(macros.any((m) => m.name.contains('VIP')), isTrue);

      final applyResult = await repo.applyMacro('conv-1', 'macro-1');
      expect(applyResult.success, isTrue);

      final timeline = await repo.getTimeline('conv-1');
      expect(timeline.isNotEmpty, isTrue);

      final note = await repo.postInternalNote('conv-1', 'Test whisper note');
      expect(note, isNotNull);
      expect(note!.content, 'Test whisper note');
    });
  });

  group('Phase 22 Agent Collision Warning Widget Tests', () {
    testWidgets('CollisionWarningBanner renders nothing when no other viewers exist', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: CollisionWarningBanner(
              activeViewers: [],
              currentUserId: 'me',
            ),
          ),
        ),
      );

      expect(find.byType(CollisionWarningBanner), findsOneWidget);
      expect(find.textContaining('Collision Warning'), findsNothing);
      expect(find.textContaining('Agent Collision'), findsNothing);
    });

    testWidgets('CollisionWarningBanner displays viewing collision warning', (tester) async {
      final viewers = [
        AgentPresenceModel(
          userId: 'agent-alex',
          userEmail: 'alex@autoflow.ai',
          action: 'VIEWING',
          lastActiveAt: DateTime.now(),
        ),
      ];

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CollisionWarningBanner(
              activeViewers: viewers,
              currentUserId: 'me',
            ),
          ),
        ),
      );

      expect(find.textContaining('Agent Collision: alex is currently viewing this conversation'), findsOneWidget);
      expect(find.byIcon(Icons.visibility_rounded), findsOneWidget);
    });

    testWidgets('CollisionWarningBanner displays typing collision warning when agent is drafting', (tester) async {
      final viewers = [
        AgentPresenceModel(
          userId: 'agent-sarah',
          userEmail: 'sarah@autoflow.ai',
          action: 'TYPING',
          lastActiveAt: DateTime.now(),
        ),
      ];

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CollisionWarningBanner(
              activeViewers: viewers,
              currentUserId: 'me',
            ),
          ),
        ),
      );

      expect(find.textContaining('Collision Warning: sarah is actively drafting a reply'), findsOneWidget);
      expect(find.byIcon(Icons.edit_note_rounded), findsOneWidget);
    });
  });

  group('Phase 22 Dialog and Sheet Widget Tests', () {
    testWidgets('CannedResponseDialog renders snippets and triggers onInsert', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final mockRepo = MockAgentProductivityRepository();
      String? insertedText;

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            agentProductivityRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: CannedResponseDialog(
                conversationId: 'conv-1',
                onInsert: (text) {
                  insertedText = text;
                },
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Canned Responses & Snippets'), findsOneWidget);
      expect(find.textContaining('Search by shortcut'), findsOneWidget);
      expect(find.text('Insert into Reply'), findsWidgets);

      await tester.tap(find.text('Insert into Reply').first);
      await tester.pumpAndSettle();

      expect(insertedText, isNotNull);
    });

    testWidgets('MacroRunnerDialog renders available macros and executes apply', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final mockRepo = MockAgentProductivityRepository();
      bool macroCallbackFired = false;

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            agentProductivityRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: MaterialApp(
            home: Scaffold(
              body: MacroRunnerDialog(
                conversationId: 'conv-1',
                onMacroApplied: () {
                  macroCallbackFired = true;
                },
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Execute Multi-Action Macro'), findsOneWidget);
      expect(find.text('Apply Macro'), findsWidgets);

      await tester.tap(find.text('Apply Macro').first);
      await tester.pump();

      expect(macroCallbackFired, isTrue);

      await tester.pump(const Duration(milliseconds: 900));
    });

    testWidgets('ConversationTimelineSheet renders event ledger and categories', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final mockRepo = MockAgentProductivityRepository();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            agentProductivityRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: const MaterialApp(
            home: Scaffold(
              body: ConversationTimelineSheet(
                conversationId: 'conv-1',
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Unified Conversation Audit Timeline'), findsOneWidget);
      expect(find.textContaining('Inbound customer inquiry'), findsOneWidget);
      expect(find.textContaining('Approaching First Response Deadline'), findsOneWidget);
    });
  });
}
