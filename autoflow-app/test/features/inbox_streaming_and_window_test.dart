import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:autoflow_app/features/contacts_crm/data/crm_repository.dart';
import 'package:autoflow_app/features/inbox/presentation/inbox_screen.dart';

void main() {
  group('Phase 18 Live Chat Streaming & Meta 24-Hour Compliance Tests', () {
    test('MessagingWindowStatusModel parses JSON and computes remaining time correctly', () {
      // 1. Active 24h Window
      final active = MessagingWindowStatusModel.fromJson({
        'conversationId': 'conv-101',
        'channel': 'INSTAGRAM',
        'windowStatus': 'ACTIVE_24H',
        'remainingSeconds': 73800, // 20h 30m
        'canSendFreeform': true,
        'canSendHumanAgent': true,
        'policyDescription': 'Within 24h session window.',
      });

      expect(active.windowStatus, equals('ACTIVE_24H'));
      expect(active.canSendFreeform, isTrue);
      expect(active.canSendHumanAgent, isTrue);
      expect(active.formattedRemainingTime, equals('20h 30m left'));

      // 2. Human Agent Extended 7d Window
      final extended = MessagingWindowStatusModel.fromJson({
        'conversationId': 'conv-102',
        'channel': 'INSTAGRAM',
        'windowStatus': 'HUMAN_AGENT_EXTENDED_7D',
        'remainingSeconds': 432000, // 120h
        'canSendFreeform': false,
        'canSendHumanAgent': true,
        'policyDescription': 'Requires Meta HUMAN_AGENT tag.',
      });

      expect(extended.windowStatus, equals('HUMAN_AGENT_EXTENDED_7D'));
      expect(extended.canSendFreeform, isFalse);
      expect(extended.canSendHumanAgent, isTrue);
      expect(extended.formattedRemainingTime, equals('120h 0m left'));

      // 3. Expired Window
      final expired = MessagingWindowStatusModel.fromJson({
        'conversationId': 'conv-103',
        'channel': 'WHATSAPP',
        'windowStatus': 'EXPIRED',
        'remainingSeconds': 0,
        'canSendFreeform': false,
        'canSendHumanAgent': false,
        'policyDescription': 'Window has expired.',
      });

      expect(expired.windowStatus, equals('EXPIRED'));
      expect(expired.canSendFreeform, isFalse);
      expect(expired.canSendHumanAgent, isFalse);
      expect(expired.formattedRemainingTime, equals('Window Closed'));

      // 4. Unrestricted Window (Telegram)
      final unrestricted = MessagingWindowStatusModel.fromJson({
        'conversationId': 'conv-104',
        'channel': 'TELEGRAM',
        'windowStatus': 'UNRESTRICTED',
        'remainingSeconds': -1,
        'canSendFreeform': true,
        'canSendHumanAgent': true,
        'policyDescription': 'Unrestricted messaging window.',
      });

      expect(unrestricted.windowStatus, equals('UNRESTRICTED'));
      expect(unrestricted.formattedRemainingTime, equals('Unrestricted'));
    });

    test('CrmRepository Phase 18 API methods handle window, resolve, and typing gracefully', () async {
      final repo = CrmRepository();

      // Window Status fallback
      final status = await repo.getWindowStatus('conv-1');
      expect(status.conversationId, equals('conv-1'));
      expect(status.canSendFreeform, isTrue);

      // Resolve conversation
      final resolved = await repo.resolveConversation('conv-1', true);
      expect(resolved, isTrue);

      final reopened = await repo.resolveConversation('conv-1', false);
      expect(reopened, isFalse);

      // Typing indicator and reply with tag (should not throw)
      await expectLater(repo.sendTyping('conv-1', true), completes);
      await expectLater(repo.sendTyping('conv-1', false), completes);
      await expectLater(
        repo.sendReply('conv-1', 'Test human agent reply', humanAgentTag: true),
        completes,
      );

      // Get messages fallback
      final messages = await repo.getMessages('conv-1');
      expect(messages, isA<List>());
    });

    testWidgets('InboxScreen displays compliance badge and toggles conversation resolve', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1280, 800));

      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: InboxScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. Verify thread list contains contacts
      expect(find.text('Sneha Kapoor'), findsWidgets);
      expect(find.text('Vikram Rathore'), findsOneWidget);
      expect(find.text('Devin Vance'), findsOneWidget);

      // 2. Check 24-hour compliance badge for active Instagram thread
      expect(find.byKey(const Key('messaging_window_badge')), findsOneWidget);
      expect(find.textContaining('24h Window Active'), findsOneWidget);

      // 3. Verify Human Agent tag checkbox is present on Instagram
      expect(find.byKey(const Key('human_agent_tag_checkbox')), findsOneWidget);
      await tester.tap(find.byKey(const Key('human_agent_tag_checkbox')));
      await tester.pumpAndSettle();

      // 4. Test Resolve / Reopen button
      final resolveButton = find.byKey(const Key('resolve_button'));
      expect(resolveButton, findsOneWidget);
      expect(find.text('Resolve'), findsOneWidget);

      // Tap Resolve
      await tester.tap(resolveButton);
      await tester.pumpAndSettle();

      // Should now show Reopen and RESOLVED chip
      expect(find.text('Reopen'), findsOneWidget);
      expect(find.text('RESOLVED'), findsOneWidget);

      // Tap Reopen
      await tester.tap(resolveButton);
      await tester.pumpAndSettle();
      expect(find.text('Resolve'), findsOneWidget);

      // 5. Send an agent reply
      await tester.enterText(find.byType(TextField).last, 'Hello! Here is your custom proposal.');
      await tester.tap(find.byKey(const Key('send_reply_button')));
      await tester.pumpAndSettle();

      expect(find.text('Hello! Here is your custom proposal.'), findsWidgets);

      // 6. Select Devin Vance (thread 4 - Expired)
      await tester.tap(find.text('Devin Vance'));
      await tester.pumpAndSettle();

      // Verify expired badge is rendered
      expect(find.text('24h Window Closed'), findsOneWidget);

      // Attempt to send without Human Agent Tag -> should trigger SnackBar warning
      await tester.enterText(find.byType(TextField).last, 'Trying to send after expiration');
      await tester.tap(find.byKey(const Key('send_reply_button')));
      await tester.pumpAndSettle();

      expect(find.textContaining('Meta 24-hour window has expired'), findsOneWidget);
    });
  });
}
