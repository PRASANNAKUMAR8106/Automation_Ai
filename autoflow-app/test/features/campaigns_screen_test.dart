import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';
import 'package:autoflow_app/features/campaigns/data/campaign_repository.dart';
import 'package:autoflow_app/features/campaigns/presentation/campaigns_screen.dart';

class FakeCampaignRepository extends CampaignRepository {
  List<CampaignModel> campaigns = [
    CampaignModel(
      id: 'camp-test-1',
      name: 'Black Friday VIP Drop',
      channel: 'INSTAGRAM',
      status: 'COMPLETED',
      messageTemplate: 'Hey {{name}}! VIP discount live.',
      targetTags: const ['vip'],
      targetLeadStatus: 'QUALIFIED',
      minLeadScore: 50,
      skipExpiredWindow: true,
      scheduledAt: DateTime.now().subtract(const Duration(days: 1)),
      startedAt: DateTime.now().subtract(const Duration(days: 1)),
      completedAt: DateTime.now().subtract(const Duration(hours: 23)),
      totalRecipients: 100,
      sentCount: 95,
      deliveredCount: 90,
      failedCount: 5,
      createdAt: DateTime.now().subtract(const Duration(days: 2)),
    ),
    CampaignModel(
      id: 'camp-test-2',
      name: 'Webinar Reminder',
      channel: 'WHATSAPP',
      status: 'SCHEDULED',
      messageTemplate: 'Hi {{name}}, webinar begins in 2 hours!',
      targetTags: const ['webinar'],
      targetLeadStatus: null,
      minLeadScore: 0,
      skipExpiredWindow: true,
      scheduledAt: DateTime.now().add(const Duration(hours: 4)),
      startedAt: null,
      completedAt: null,
      totalRecipients: 50,
      sentCount: 0,
      deliveredCount: 0,
      failedCount: 0,
      createdAt: DateTime.now().subtract(const Duration(hours: 1)),
    ),
  ];

  @override
  Future<List<CampaignModel>> getCampaigns({String? status, int page = 0, int size = 20}) async {
    if (status != null && status.isNotEmpty && status != 'ALL') {
      return campaigns.where((c) => c.status == status).toList();
    }
    return campaigns;
  }

  @override
  Future<AudienceEstimateModel> estimateAudience({
    required String channel,
    List<String>? targetTags,
    String? targetLeadStatus,
    int minLeadScore = 0,
  }) async {
    return AudienceEstimateModel(
      totalMatchingContacts: 42,
      eligibleWindowContacts: channel == 'TELEGRAM' ? 42 : 30,
      ineligibleWindowContacts: channel == 'TELEGRAM' ? 0 : 12,
      channel: channel,
    );
  }

  @override
  Future<CampaignModel?> createCampaign({
    required String name,
    required String channel,
    required String messageTemplate,
    String? mediaUrl,
    List<String>? targetTags,
    String? targetLeadStatus,
    int minLeadScore = 0,
    bool skipExpiredWindow = true,
    DateTime? scheduledAt,
  }) async {
    final created = CampaignModel(
      id: 'camp-created-1',
      name: name,
      channel: channel,
      status: scheduledAt == null ? 'RUNNING' : 'SCHEDULED',
      messageTemplate: messageTemplate,
      mediaUrl: mediaUrl,
      targetTags: targetTags ?? [],
      targetLeadStatus: targetLeadStatus,
      minLeadScore: minLeadScore,
      skipExpiredWindow: skipExpiredWindow,
      scheduledAt: scheduledAt,
      totalRecipients: 30,
      sentCount: 0,
      deliveredCount: 0,
      failedCount: 0,
      createdAt: DateTime.now(),
    );
    campaigns.add(created);
    return created;
  }

  @override
  Future<bool> cancelCampaign(String id) async {
    final idx = campaigns.indexWhere((c) => c.id == id);
    if (idx != -1) {
      final old = campaigns[idx];
      campaigns[idx] = CampaignModel(
        id: old.id,
        name: old.name,
        channel: old.channel,
        status: 'CANCELLED',
        messageTemplate: old.messageTemplate,
        targetTags: old.targetTags,
        minLeadScore: old.minLeadScore,
        skipExpiredWindow: old.skipExpiredWindow,
        totalRecipients: old.totalRecipients,
        sentCount: old.sentCount,
        deliveredCount: old.deliveredCount,
        failedCount: old.failedCount,
      );
    }
    return true;
  }
}

Widget createTestApp(Widget child, {List<Override> overrides = const []}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: AppTheme.darkTheme,
      home: Scaffold(body: child),
    ),
  );
}

void main() {
  group('Phase 19 - Campaign Models & Repository Tests', () {
    test('CampaignModel serialization and computed rates', () {
      final json = {
        'id': 'c-100',
        'name': 'Spring Promo',
        'channel': 'INSTAGRAM',
        'status': 'RUNNING',
        'messageTemplate': 'Spring sale is here {{name}}!',
        'mediaUrl': 'https://example.com/banner.png',
        'targetTags': ['promo', 'spring'],
        'targetLeadStatus': 'LEAD',
        'minLeadScore': 25,
        'skipExpiredWindow': true,
        'scheduledAt': '2026-03-24T10:00:00Z',
        'startedAt': '2026-03-24T10:00:05Z',
        'completedAt': null,
        'totalRecipients': 200,
        'sentCount': 150,
        'deliveredCount': 140,
        'failedCount': 10,
        'createdAt': '2026-03-24T09:00:00Z',
      };

      final model = CampaignModel.fromJson(json);
      expect(model.id, 'c-100');
      expect(model.name, 'Spring Promo');
      expect(model.channel, 'INSTAGRAM');
      expect(model.status, 'RUNNING');
      expect(model.minLeadScore, 25);
      expect(model.skipExpiredWindow, isTrue);
      expect(model.totalRecipients, 200);
      expect(model.deliveredCount, 140);

      // Delivery rate is deliveredCount / totalRecipients = 140 / 200 = 0.70
      expect(model.deliveryRate, closeTo(0.70, 0.001));

      // Progress is (sentCount + failedCount) / totalRecipients = (150 + 10) / 200 = 0.80
      expect(model.progress, closeTo(0.80, 0.001));
    });

    test('CampaignModel edge cases for zero recipients and fallback', () {
      const emptyModel = CampaignModel(
        id: 'c-zero',
        name: 'Empty',
        channel: 'TELEGRAM',
        status: 'DRAFT',
        messageTemplate: 'Hi',
        targetTags: [],
        minLeadScore: 0,
        skipExpiredWindow: false,
        totalRecipients: 0,
        sentCount: 0,
        deliveredCount: 0,
        failedCount: 0,
      );

      expect(emptyModel.deliveryRate, 0.0);
      expect(emptyModel.progress, 0.0);

      // When deliveredCount is 0 but sentCount > 0, fallback to sentCount
      const sentOnlyModel = CampaignModel(
        id: 'c-sent',
        name: 'Sent Only',
        channel: 'WHATSAPP',
        status: 'RUNNING',
        messageTemplate: 'Hi',
        targetTags: [],
        minLeadScore: 0,
        skipExpiredWindow: true,
        totalRecipients: 100,
        sentCount: 80,
        deliveredCount: 0,
        failedCount: 0,
      );
      expect(sentOnlyModel.deliveryRate, closeTo(0.80, 0.001));
    });

    test('AudienceEstimateModel serialization', () {
      final json = {
        'totalMatchingContacts': 150,
        'eligibleWindowContacts': 110,
        'ineligibleWindowContacts': 40,
        'channel': 'INSTAGRAM',
      };
      final estimate = AudienceEstimateModel.fromJson(json);
      expect(estimate.totalMatchingContacts, 150);
      expect(estimate.eligibleWindowContacts, 110);
      expect(estimate.ineligibleWindowContacts, 40);
      expect(estimate.channel, 'INSTAGRAM');
    });

    test('CampaignDetailModel serialization', () {
      final json = {
        'campaign': {
          'id': 'c-detail-1',
          'name': 'Detail Test',
          'channel': 'WHATSAPP',
          'status': 'COMPLETED',
          'messageTemplate': 'Detail template',
          'targetTags': ['test'],
          'minLeadScore': 10,
          'skipExpiredWindow': true,
          'totalRecipients': 50,
          'sentCount': 50,
          'deliveredCount': 48,
          'failedCount': 2,
        },
        'recentRecipients': [
          {
            'id': 'rec-1',
            'contactId': 'cont-1',
            'contactName': 'Alice Smith',
            'contactUsername': 'alice',
            'status': 'DELIVERED',
            'sentAt': '2026-03-24T12:00:00Z',
          }
        ],
        'deliveryRate': 96.0,
      };

      final detail = CampaignDetailModel.fromJson(json);
      expect(detail.campaign.name, 'Detail Test');
      expect(detail.recentRecipients.length, 1);
      expect(detail.recentRecipients.first.contactName, 'Alice Smith');
      expect(detail.recentRecipients.first.status, 'DELIVERED');
      expect(detail.deliveryRate, 96.0);
    });

    test('CampaignRecipientModel handles hardened statuses', () {
      final statuses = ['PROCESSING', 'SKIPPED_OPT_OUT', 'SKIPPED_POLICY', 'CANCELLED'];
      for (final s in statuses) {
        final r = CampaignRecipientModel.fromJson({'id': 'rec-$s', 'status': s});
        expect(r.status, s);
      }
    });
  });

  group('Phase 19 - CampaignsScreen Widget Tests', () {
    testWidgets('Renders KPI cards, search bar, filter chips, and campaign list', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      final fakeRepo = FakeCampaignRepository();

      await tester.pumpWidget(
        createTestApp(
          const CampaignsScreen(),
          overrides: [
            campaignRepositoryProvider.overrideWithValue(fakeRepo),
            campaignsProvider.overrideWith((ref) async => fakeRepo.campaigns),
          ],
        ),
      );

      await tester.pumpAndSettle();

      // Verify Header
      expect(find.text('Scheduled Broadcast Campaigns'), findsOneWidget);
      expect(find.textContaining('Automated omnichannel outreach'), findsOneWidget);

      // Verify 4 KPI metric cards
      expect(find.text('Total Broadcasts'), findsOneWidget);
      expect(find.text('Messages Delivered'), findsOneWidget);
      expect(find.text('Avg Delivery Rate'), findsOneWidget);
      expect(find.text('Scheduled Outreaches'), findsOneWidget);

      // Verify KPI values (2 campaigns, 90 delivered, 90.0% avg for completed, 1 scheduled)
      expect(find.text('2'), findsOneWidget);
      expect(find.text('90'), findsOneWidget);
      expect(find.text('90.0%'), findsOneWidget);
      expect(find.text('1'), findsOneWidget);

      // Verify Filter Chips
      expect(find.text('ALL'), findsOneWidget);
      expect(find.text('SCHEDULED'), findsWidgets);
      expect(find.text('RUNNING'), findsOneWidget);
      expect(find.text('COMPLETED'), findsWidgets);
      expect(find.text('CANCELLED'), findsOneWidget);

      // Verify Campaigns in List
      expect(find.text('Black Friday VIP Drop'), findsOneWidget);
      expect(find.text('Webinar Reminder'), findsOneWidget);
    });

    testWidgets('Filter chips filter the campaign list', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      final fakeRepo = FakeCampaignRepository();

      await tester.pumpWidget(
        createTestApp(
          const CampaignsScreen(),
          overrides: [
            campaignRepositoryProvider.overrideWithValue(fakeRepo),
            campaignsProvider.overrideWith((ref) async => fakeRepo.campaigns),
          ],
        ),
      );

      await tester.pumpAndSettle();

      // Filter by 'SCHEDULED'
      await tester.tap(find.widgetWithText(ChoiceChip, 'SCHEDULED'));
      await tester.pumpAndSettle();

      expect(find.text('Webinar Reminder'), findsOneWidget);
      expect(find.text('Black Friday VIP Drop'), findsNothing);

      // Filter by 'COMPLETED'
      await tester.tap(find.widgetWithText(ChoiceChip, 'COMPLETED'));
      await tester.pumpAndSettle();

      expect(find.text('Black Friday VIP Drop'), findsOneWidget);
      expect(find.text('Webinar Reminder'), findsNothing);
    });

    testWidgets('Search input filters campaigns by title', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      final fakeRepo = FakeCampaignRepository();

      await tester.pumpWidget(
        createTestApp(
          const CampaignsScreen(),
          overrides: [
            campaignRepositoryProvider.overrideWithValue(fakeRepo),
            campaignsProvider.overrideWith((ref) async => fakeRepo.campaigns),
          ],
        ),
      );

      await tester.pumpAndSettle();

      // Enter search text
      await tester.enterText(find.byType(TextField).first, 'Webinar');
      await tester.pumpAndSettle();

      expect(find.text('Webinar Reminder'), findsOneWidget);
      expect(find.text('Black Friday VIP Drop'), findsNothing);
    });

    testWidgets('CreateCampaignDialog opens, inserts variables, and schedules campaign', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 1000));

      final fakeRepo = FakeCampaignRepository();

      await tester.pumpWidget(
        createTestApp(
          const CampaignsScreen(),
          overrides: [
            campaignRepositoryProvider.overrideWithValue(fakeRepo),
            campaignsProvider.overrideWith((ref) async => fakeRepo.campaigns),
          ],
        ),
      );

      await tester.pumpAndSettle();

      // Click New Campaign button
      await tester.tap(find.byKey(const Key('new_campaign_button')));
      await tester.pumpAndSettle();

      // Verify Dialog header
      expect(find.text('Create Broadcast Campaign'), findsOneWidget);

      // Verify Channel selection exists
      expect(find.byKey(const Key('channel_selector')), findsOneWidget);

      // Verify Audience reach preview
      expect(find.textContaining('Estimated Reach:'), findsOneWidget);

      // Fill in campaign name
      await tester.enterText(find.byKey(const Key('campaign_name_input')), 'New Launch Outbound');
      await tester.pumpAndSettle();

      // Tap token chip {{name}} to insert into template
      await tester.tap(find.widgetWithText(ActionChip, '{{name}}'));
      await tester.pumpAndSettle();

      // Enter remaining template text
      await tester.enterText(find.byKey(const Key('message_template_input')), 'Hey {{name}}, check our launch!');
      await tester.pumpAndSettle();

      // Submit the dialog
      await tester.tap(find.byKey(const Key('schedule_campaign_submit_button')));
      await tester.pumpAndSettle();

      // Verify snackbar is displayed
      expect(find.textContaining('New Launch Outbound'), findsWidgets);
      expect(find.textContaining('scheduled successfully!'), findsOneWidget);
    });

    testWidgets('Tapping Cancel on scheduled campaign calls cancelCampaign and updates UI', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      final fakeRepo = FakeCampaignRepository();

      await tester.pumpWidget(
        createTestApp(
          const CampaignsScreen(),
          overrides: [
            campaignRepositoryProvider.overrideWithValue(fakeRepo),
            campaignsProvider.overrideWith((ref) async => fakeRepo.campaigns),
          ],
        ),
      );

      await tester.pumpAndSettle();

      // Find cancel button for camp-test-2 (which is SCHEDULED)
      final cancelBtn = find.byKey(const Key('cancel_button_camp-test-2'));
      expect(cancelBtn, findsOneWidget);

      await tester.tap(cancelBtn);
      await tester.pumpAndSettle();

      expect(fakeRepo.campaigns.firstWhere((c) => c.id == 'camp-test-2').status, 'CANCELLED');
    });
  });
}
