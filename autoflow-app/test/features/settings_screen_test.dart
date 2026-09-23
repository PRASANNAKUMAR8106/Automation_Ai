import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';
import 'package:autoflow_app/features/settings/presentation/settings_screen.dart';

void main() {
  Widget createTestWidget(Widget child) {
    return ProviderScope(
      child: MaterialApp(
        theme: AppTheme.darkTheme,
        home: Scaffold(body: child),
      ),
    );
  }

  group('SettingsScreen Widget & Interaction Tests', () {
    testWidgets('SettingsScreen renders profile, channels hub, and masked secret tokens', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 1000));
      await tester.pumpWidget(createTestWidget(const SettingsScreen()));
      await tester.pumpAndSettle();

      // Verify Headers & Overview
      expect(find.text('Settings & Channels Hub'), findsOneWidget);
      expect(find.text('Organization & Workspace'), findsOneWidget);
      expect(find.text('Omnichannel Connections'), findsOneWidget);
      expect(find.text('API & Webhook Secret Tokens'), findsOneWidget);
      expect(find.text('AutoFlow Pro Subscription'), findsOneWidget);

      // Verify Social Channels
      expect(find.text('INSTAGRAM'), findsOneWidget);
      expect(find.text('WHATSAPP'), findsOneWidget);
      expect(find.text('TELEGRAM'), findsOneWidget);
      expect(find.text('CONNECTED'), findsWidgets);

      // Verify Masked API Keys
      expect(find.text('Production Webhook Dispatcher'), findsOneWidget);
      expect(find.textContaining('af_live_'), findsWidgets);
      expect(find.textContaining('••••••••••••'), findsWidgets);
      expect(find.text('New Secret'), findsOneWidget);
    });

    testWidgets('Clicking New Secret button opens modal dialog', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 1000));
      await tester.pumpWidget(createTestWidget(const SettingsScreen()));
      await tester.pumpAndSettle();

      final newSecretBtn = find.text('New Secret');
      expect(newSecretBtn, findsOneWidget);

      await tester.tap(newSecretBtn);
      await tester.pumpAndSettle();

      // Verify dialog is shown
      expect(find.text('Generate New API / Webhook Secret'), findsOneWidget);
      expect(find.text('Key Label / Integration Name'), findsOneWidget);
      expect(find.text('Generate Key'), findsOneWidget);
    });
  });
}
