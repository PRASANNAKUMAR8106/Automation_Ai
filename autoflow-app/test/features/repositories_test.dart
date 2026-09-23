import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/features/dashboard/data/analytics_repository.dart';
import 'package:autoflow_app/features/contacts_crm/data/crm_repository.dart';
import 'package:autoflow_app/features/workflows/data/workflow_repository.dart';
import 'package:autoflow_app/features/influencer/data/influencer_repository.dart';
import 'package:autoflow_app/features/settings/data/channel_repository.dart';
import 'package:autoflow_app/features/settings/data/api_key_repository.dart';

void main() {
  group('Frontend Repository Tests', () {
    test('AnalyticsRepository returns valid overview model', () async {
      final repo = AnalyticsRepository();
      final overview = await repo.getOverview();

      expect(overview.commentsProcessed, isPositive);
      expect(overview.dmsDispatched, isPositive);
      expect(overview.leadsCaptured, isPositive);
      expect(overview.activeWorkflows, isNonNegative);
      expect(overview.maxWorkflows, equals(20));
      expect(overview.channelBreakdown, contains('INSTAGRAM'));
    });

    test('CrmRepository returns contacts list and handles tag addition', () async {
      final repo = CrmRepository();
      final contacts = await repo.getContacts();

      expect(contacts, isNotEmpty);
      expect(contacts.first.channel, isIn(['INSTAGRAM', 'WHATSAPP']));
      expect(contacts.first.leadStatus, isNotEmpty);

      // Verify no exception on add tag
      await expectLater(repo.addTag('c-1', 'vip'), completes);

      // Verify CSV export
      final csv = await repo.exportContactsCsv();
      expect(csv, contains('Contact ID,Channel,External ID'));
      expect(csv, contains('Priya Sharma'));
    });

    test('ChannelRepository retrieves connected accounts and handles disconnect', () async {
      final repo = ChannelRepository();
      final accounts = await repo.getConnectedAccounts();

      expect(accounts, isNotEmpty);
      expect(accounts.any((a) => a.channel == 'INSTAGRAM'), isTrue);
      expect(accounts.any((a) => a.channel == 'WHATSAPP'), isTrue);

      final disconnected = await repo.disconnectAccount(accounts.first.id);
      expect(disconnected, isTrue);
    });

    test('ApiKeyRepository manages keys with secret masking, rotation, and revocation', () async {
      final repo = ApiKeyRepository();
      final keys = await repo.getApiKeys();

      expect(keys, isNotEmpty);
      expect(keys.first.maskedKey, contains('••••••••••••'));

      // Test Key Creation
      final created = await repo.createApiKey('Test Automation Key');
      expect(created, isNotNull);
      expect(created!.secretKey, startsWith('af_live_'));
      expect(created.maskedKey, contains('••••••••••••'));

      // Test Key Rotation
      final rotated = await repo.rotateApiKey(keys.first.id);
      expect(rotated, isNotNull);
      expect(rotated!.secretKey, startsWith('af_live_'));

      // Test Key Revocation
      final revoked = await repo.revokeApiKey(keys.first.id);
      expect(revoked, isTrue);
    });

    test('WorkflowRepository returns active workflows list', () async {
      final repo = WorkflowRepository();
      final workflows = await repo.getWorkflows();

      expect(workflows, isNotEmpty);
      expect(workflows.first.name, isNotEmpty);
      expect(workflows.first.status, isIn(['PUBLISHED', 'DRAFT', 'PAUSED']));
    });

    test('InfluencerRepository returns partner stats with commission math', () async {
      final repo = InfluencerRepository();
      final stats = await repo.getStats();

      expect(stats.influencerId, isNotEmpty);
      expect(stats.name, isNotEmpty);
      expect(stats.promoCode, isNotEmpty);
      expect(stats.totalClicks, isNonNegative);
      expect(stats.approvedCommissionInr, isNonNegative);
    });
  });
}
