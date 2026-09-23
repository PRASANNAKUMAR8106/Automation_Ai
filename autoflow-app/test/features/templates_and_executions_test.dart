import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:autoflow_app/core/theme/app_theme.dart';
import 'package:autoflow_app/features/workflows/data/template_repository.dart';
import 'package:autoflow_app/features/workflows/data/workflow_repository.dart';
import 'package:autoflow_app/features/workflows/presentation/template_gallery_dialog.dart';
import 'package:autoflow_app/features/workflows/presentation/workflow_execution_history_dialog.dart';
import 'package:autoflow_app/features/workflows/presentation/workflow_list_screen.dart';

class FakeTemplateRepository extends TemplateRepository {
  @override
  Future<List<TemplateModel>> getTemplates({String? category, String? tag, bool? featured}) async {
    return TemplateModel.defaultTemplates.where((t) {
      if (category != null && category.isNotEmpty && category != 'ALL' && !t.category.toUpperCase().contains(category.toUpperCase())) {
        return false;
      }
      if (tag != null && tag.isNotEmpty && !t.tags.any((tg) => tg.toLowerCase() == tag.toLowerCase())) {
        return false;
      }
      if (featured != null && featured && !t.isFeatured) {
        return false;
      }
      return true;
    }).toList();
  }

  @override
  Future<TemplateModel?> getTemplateById(String id) async {
    return TemplateModel.defaultTemplates.firstWhere((t) => t.id == id);
  }

  @override
  Future<WorkflowListItemModel?> instantiateTemplate(
    String templateId, {
    String? workflowName,
    String? description,
  }) async {
    return WorkflowListItemModel(
      id: 'wf-cloned-1',
      name: workflowName ?? 'Cloned Workflow',
      description: description,
      status: 'DRAFT',
      activeVersionNumber: 1,
    );
  }
}

class FakeWorkflowRepository extends WorkflowRepository {
  @override
  Future<List<WorkflowListItemModel>> getWorkflows() async {
    return WorkflowListItemModel.defaultWorkflows;
  }

  @override
  Future<List<WorkflowExecutionModel>> getExecutions(String workflowId) async {
    return [
      WorkflowExecutionModel(
        id: 'exec-1',
        workflowId: workflowId,
        workflowName: 'Lead Magnet Funnel',
        triggerType: 'TRIGGER_INSTAGRAM_COMMENT',
        status: 'SUCCESS',
        retryCount: 0,
        startedAt: DateTime.now().subtract(const Duration(minutes: 15)),
      ),
      WorkflowExecutionModel(
        id: 'exec-2',
        workflowId: workflowId,
        workflowName: 'Lead Magnet Funnel',
        triggerType: 'TRIGGER_INSTAGRAM_COMMENT',
        status: 'FAILED',
        errorMessage: 'Instagram Graph API rate limit exceeded (code 429)',
        retryCount: 1,
        startedAt: DateTime.now().subtract(const Duration(hours: 1)),
      ),
    ];
  }

  @override
  Future<WorkflowExecutionModel?> retryExecution(String executionId) async {
    return WorkflowExecutionModel(
      id: executionId,
      workflowId: 'wf-1',
      workflowName: 'Lead Magnet Funnel',
      triggerType: 'TRIGGER_INSTAGRAM_COMMENT',
      status: 'RETRYING',
      retryCount: 2,
      startedAt: DateTime.now(),
    );
  }
}

void main() {
  group('Template & Execution History Repository Tests', () {
    test('TemplateRepository retrieves default templates with category filtering', () async {
      final repo = FakeTemplateRepository();

      final allTemplates = await repo.getTemplates();
      expect(allTemplates, isNotEmpty);
      expect(allTemplates.length, greaterThanOrEqualTo(4));

      final leadMagnets = await repo.getTemplates(category: 'LEAD_MAGNET');
      expect(leadMagnets, isNotEmpty);
      expect(leadMagnets.every((t) => t.category == 'LEAD_MAGNET'), isTrue);

      final featured = await repo.getTemplates(featured: true);
      expect(featured.every((t) => t.isFeatured), isTrue);
    });

    test('TemplateRepository instantiates template into new workflow', () async {
      final repo = FakeTemplateRepository();
      final newWf = await repo.instantiateTemplate(
        'tpl-1',
        workflowName: 'Holiday Promo Automation',
        description: 'Sends holiday PDF magnet',
      );

      expect(newWf, isNotNull);
      expect(newWf!.name, equals('Holiday Promo Automation'));
      expect(newWf.status, equals('DRAFT'));
    });

    test('WorkflowRepository fetches execution logs and retries failed execution', () async {
      final repo = FakeWorkflowRepository();

      final executions = await repo.getExecutions('wf-1');
      expect(executions.length, equals(2));
      expect(executions.any((e) => e.status == 'SUCCESS'), isTrue);

      final failed = executions.firstWhere((e) => e.status == 'FAILED');
      expect(failed.errorMessage, contains('rate limit'));
      expect(failed.retryCount, equals(1));

      final retried = await repo.retryExecution(failed.id);
      expect(retried, isNotNull);
      expect(retried!.status, equals('RETRYING'));
      expect(retried.retryCount, equals(2));
    });
  });

  group('Template Marketplace & Execution Telemetry Widget Tests', () {
    Widget buildTestApp(Widget child) {
      return ProviderScope(
        overrides: [
          templateRepositoryProvider.overrideWithValue(FakeTemplateRepository()),
          workflowRepositoryProvider.overrideWithValue(FakeWorkflowRepository()),
        ],
        child: MaterialApp(
          theme: AppTheme.darkTheme,
          home: Scaffold(body: child),
        ),
      );
    }

    testWidgets('TemplateGalleryDialog renders category chips, templates, and badges', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));
      await tester.pumpWidget(buildTestApp(const TemplateGalleryDialog()));
      await tester.pumpAndSettle();

      expect(find.text('Workflow Templates Marketplace'), findsOneWidget);
      expect(find.text('All Templates'), findsOneWidget);
      expect(find.text('Lead Magnet'), findsOneWidget);
      expect(find.text('E-Commerce'), findsOneWidget);
      expect(find.text('Support & AI'), findsOneWidget);

      expect(find.text('Comment to Lead Magnet'), findsOneWidget);
      expect(find.text('Use Template'), findsWidgets);
      expect(find.text('FEATURED'), findsWidgets);
    });

    testWidgets('TemplateGalleryDialog filters by category when chip tapped', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));
      await tester.pumpWidget(buildTestApp(const TemplateGalleryDialog()));
      await tester.pumpAndSettle();

      final ecommerceChip = find.text('E-Commerce');
      expect(ecommerceChip, findsOneWidget);
      await tester.tap(ecommerceChip);
      await tester.pumpAndSettle();

      expect(find.text('Story Mention Promo Delivery'), findsOneWidget);
    });

    testWidgets('WorkflowExecutionHistoryDialog renders metrics, status chips, and retry button', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));
      await tester.pumpWidget(buildTestApp(
        const WorkflowExecutionHistoryDialog(
          workflowId: 'wf-1',
          workflowName: 'Lead Magnet Funnel',
        ),
      ));
      await tester.pumpAndSettle();

      expect(find.text('Execution Telemetry & Audit Logs'), findsOneWidget);
      expect(find.text('Workflow: Lead Magnet Funnel'), findsOneWidget);
      expect(find.text('Total Runs'), findsOneWidget);
      expect(find.text('Succeeded'), findsOneWidget);
      expect(find.text('Failed'), findsOneWidget);

      // Verify executions listed
      expect(find.text('SUCCESS'), findsOneWidget);
      expect(find.text('FAILED'), findsOneWidget);
      expect(find.textContaining('rate limit exceeded'), findsOneWidget);
      expect(find.text('Retry (1/3)'), findsOneWidget);
    });

    testWidgets('WorkflowListScreen displays Starter Templates button', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1200, 900));
      await tester.pumpWidget(buildTestApp(const WorkflowListScreen()));
      await tester.pumpAndSettle();

      expect(find.text('Starter Templates'), findsOneWidget);
      expect(find.text('New Automation'), findsOneWidget);
      expect(find.byIcon(Icons.history), findsWidgets);
    });
  });
}
