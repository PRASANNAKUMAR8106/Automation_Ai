import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/features/dashboard/presentation/dashboard_screen.dart';
import 'package:autoflow_app/features/contacts_crm/presentation/contacts_screen.dart';
import 'package:autoflow_app/features/inbox/presentation/inbox_screen.dart';
import 'package:autoflow_app/features/workflows/presentation/workflow_list_screen.dart';
import 'package:autoflow_app/features/influencer/presentation/influencer_dashboard_screen.dart';
import 'package:autoflow_app/features/settings/presentation/settings_screen.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';

import 'package:autoflow_app/features/contacts_crm/data/crm_repository.dart';

class FakeCrmRepository extends CrmRepository {
  @override
  Future<String> exportContactsCsv({String? search, String? tag}) async {
    return 'Contact ID,Channel,External ID\n1,INSTAGRAM,101';
  }
}

void main() {
  Widget createTestWidget(Widget child, [List<dynamic> overrides = const []]) {
    return ProviderScope(
      overrides: overrides.cast(),
      child: MaterialApp(
        theme: AppTheme.darkTheme,
        home: Scaffold(body: child),
      ),
    );
  }

  group('Screen Rendering Tests with Riverpod State', () {
    testWidgets('DashboardScreen renders welcome header and metric cards', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(const DashboardScreen()));
      await tester.pumpAndSettle();

      expect(find.textContaining('Welcome back'), findsOneWidget);
      expect(find.text('Comments Processed'), findsOneWidget);
      expect(find.text('DMs Dispatched'), findsOneWidget);
      expect(find.text('Leads Captured'), findsOneWidget);
      expect(find.text('Active Workflows'), findsOneWidget);
    });

    testWidgets('ContactsScreen renders table headers and contact rows', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(const ContactsScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Contacts & CRM Directory'), findsOneWidget);
      expect(find.text('Export CSV'), findsOneWidget);
      expect(find.text('Priya Sharma'), findsOneWidget);
      expect(find.text('INSTAGRAM'), findsWidgets);
    });

    testWidgets('InboxScreen renders conversation thread and message list', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(const InboxScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Sneha Kapoor'), findsWidgets);
      expect(find.byType(TextField), findsWidgets);
    });

    testWidgets('WorkflowListScreen renders automation list and tabs', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(const WorkflowListScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Automations & Workflows'), findsOneWidget);
      expect(find.text('New Automation'), findsOneWidget);
      expect(find.textContaining('Reel Comment GUIDE'), findsOneWidget);
    });

    testWidgets('InfluencerDashboardScreen renders promo assets and earnings', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(const InfluencerDashboardScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Influencer & Referral Portal'), findsOneWidget);
      expect(find.text('Request Payout'), findsOneWidget);
      expect(find.text('Your Active Promotion Assets'), findsOneWidget);
      expect(find.text('Link Clicks'), findsOneWidget);
      expect(find.text('Payable Balance'), findsOneWidget);
    });

    testWidgets('ContactsScreen export CSV button triggers export and gives feedback', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 800));
      await tester.pumpWidget(createTestWidget(
        const ContactsScreen(),
        [crmRepositoryProvider.overrideWithValue(FakeCrmRepository())],
      ));
      await tester.pumpAndSettle();

      final exportBtn = find.text('Export CSV');
      expect(exportBtn, findsOneWidget);

      await tester.tap(exportBtn);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.textContaining('Exported'), findsOneWidget);
    });

    testWidgets('SettingsScreen renders hub, channels, and masked tokens', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 1000));
      await tester.pumpWidget(createTestWidget(const SettingsScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Settings & Channels Hub'), findsOneWidget);
      expect(find.text('Omnichannel Connections'), findsOneWidget);
      expect(find.text('API & Webhook Secret Tokens'), findsOneWidget);
      expect(find.text('Manage Billing'), findsOneWidget);
    });
  });
}
