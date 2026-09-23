import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';
import 'package:autoflow_app/features/dashboard/data/funnel_repository.dart';
import 'package:autoflow_app/features/workflow_builder/domain/ai_media_repository.dart';
import 'package:autoflow_app/features/workflow_builder/domain/workflow_node_model.dart';
import 'package:autoflow_app/features/workflow_builder/presentation/widgets/ai_media_generator_dialog.dart';
import 'package:autoflow_app/features/workflow_builder/presentation/widgets/node_config_drawer.dart';
import 'package:autoflow_app/features/workflows/presentation/workflow_funnel_dialog.dart';

class FakeFunnelRepository extends FunnelRepository {
  @override
  Future<WorkflowFunnelModel> getWorkflowFunnel(String workflowId) async {
    return WorkflowFunnelModel.sampleFallback(workflowId, 'Test Funnel');
  }
}

class FakeAiMediaRepository extends AiMediaRepository {
  @override
  Future<AiMediaAssetModel> generateBrandedAsset(AiMediaGenerateOptions options) async {
    return AiMediaAssetModel(
      id: 'mock-asset-123',
      fileName: 'ai_asset_mock.png',
      mimeType: 'image/png',
      fileSizeBytes: 45678,
      downloadUrl: 'https://s3.autoflow.ai/tenants/media/mock-asset-123.png',
    );
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
  group('Phase 17 - Funnel Analytics & AI Media Studio Tests', () {
    test('FunnelRepository model serialization and sample fallback', () async {
      final sample = WorkflowFunnelModel.sampleFallback('wf-test');
      expect(sample.workflowId, 'wf-test');
      expect(sample.totalRuns, 248);
      expect(sample.successfulRuns, 194);
      expect(sample.overallConversionRate, 78.2);
      expect(sample.steps.length, 4);

      final step1 = sample.steps.first;
      expect(step1.stepIndex, 1);
      expect(step1.reachedCount, 248);
      expect(step1.conversionPercentage, 100.0);
    });

    test('AiMediaRepository model serialization', () async {
      final repo = FakeAiMediaRepository();
      final asset = await repo.generateBrandedAsset(
        const AiMediaGenerateOptions(prompt: 'Test Promo'),
      );
      expect(asset.id, 'mock-asset-123');
      expect(asset.mimeType, 'image/png');
      expect(asset.downloadUrl, contains('mock-asset-123.png'));
    });

    testWidgets('WorkflowFunnelDialog renders KPI cards, progress bars, and bottleneck notice',
        (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      await tester.pumpWidget(
        createTestApp(
          const WorkflowFunnelDialog(
            workflowId: 'wf-101',
            workflowName: 'Instagram VIP Lead Funnel',
          ),
          overrides: [
            funnelRepositoryProvider.overrideWithValue(FakeFunnelRepository()),
          ],
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Conversion Funnel Intelligence'), findsOneWidget);
      expect(find.text('Instagram VIP Lead Funnel'), findsOneWidget);

      // Verify KPI metrics
      expect(find.text('Total Triggers'), findsOneWidget);
      expect(find.text('Conversions'), findsOneWidget);
      expect(find.text('Funnel Rate'), findsOneWidget);
      expect(find.text('248'), findsWidgets);
      expect(find.text('78.2%'), findsOneWidget);

      // Verify bottleneck alert
      expect(find.textContaining('Primary Bottleneck Detected'), findsOneWidget);

      // Verify step cards
      expect(find.text('Instagram Comment Trigger ("GUIDE")'), findsOneWidget);
      expect(find.text('Public Comment Reply'), findsOneWidget);
      expect(find.text('Send DM with Lead Magnet Asset'), findsOneWidget);
      expect(find.byType(LinearProgressIndicator), findsNWidgets(4));
    });

    testWidgets('AiMediaGeneratorDialog renders template options and live preview',
        (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));

      await tester.pumpWidget(
        createTestApp(
          const AiMediaGeneratorDialog(
            initialPrompt: 'Summer VIP Voucher',
          ),
          overrides: [
            aiMediaRepositoryProvider.overrideWithValue(FakeAiMediaRepository()),
          ],
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('AI Branded Media Studio'), findsOneWidget);
      expect(find.text('Template Style'), findsOneWidget);
      expect(find.text('Live Visual Preview'), findsOneWidget);
      expect(find.text('Synthesize Asset (PNG)'), findsOneWidget);

      // Enter headline
      await tester.enterText(find.byType(TextField).at(1), '50% Summer Discount for @alex');
      await tester.pumpAndSettle();

      expect(find.text('50% Summer Discount for @alex'), findsWidgets);

      // Tap Synthesize
      await tester.tap(find.text('Synthesize Asset (PNG)'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Asset URL ready:'), findsOneWidget);
    });

    testWidgets('NodeConfigDrawer configures actionSendMediaAsset properties',
        (tester) async {
      final node = WorkflowNodeModel(
        id: 'node-media',
        type: NodeType.actionSendMediaAsset,
        label: 'Deliver Media Asset',
        category: NodeCategory.action,
        config: {'media_url': 'https://example.com/guide.png', 'asset_type': 'IMAGE'},
        position: const Offset(100, 100),
      );

      bool configChanged = false;

      await tester.pumpWidget(
        createTestApp(
          NodeConfigDrawer(
            node: node,
            onConfigChanged: () => configChanged = true,
            onClose: () {},
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Configure Node'), findsOneWidget);
      expect(find.text('Media / Asset URL'), findsOneWidget);
      expect(find.text('Open AI Media Studio'), findsOneWidget);
      expect(find.text('https://example.com/guide.png'), findsOneWidget);

      // Modify URL to dynamic token
      await tester.enterText(find.byType(TextField).at(1), '{{lastGeneratedMediaUrl}}');
      await tester.pumpAndSettle();

      await tester.tap(find.text('Save Properties'));
      await tester.pumpAndSettle();

      expect(configChanged, isTrue);
      expect(node.config['media_url'], '{{lastGeneratedMediaUrl}}');
      expect(node.config['asset_type'], 'IMAGE');
    });
  });
}
