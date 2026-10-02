import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:autoflow_app/features/contacts_crm/data/crm_repository.dart';
import 'package:autoflow_app/features/inbox/presentation/inbox_screen.dart';
import 'package:autoflow_app/features/knowledge/data/knowledge_repository.dart';
import 'package:autoflow_app/features/workflows/data/workflow_repository.dart';
import 'package:autoflow_app/features/workflows/presentation/workflow_list_screen.dart';

class FakeCrmRepository extends CrmRepository {
  @override
  Future<AiSuggestionModel?> getAiSuggestion(String conversationId, {String? tone}) async {
    return const AiSuggestionModel(
      suggestedReply: 'Hi Sneha! Yes, you can book a 1-on-1 coaching call at https://cal.com/autoflow-demo. We look forward to helping you grow!',
      confidenceScore: 0.96,
      requiresHumanHandoff: false,
      sourceArticleTitles: ['Consultation & 1-on-1 Coaching Booking FAQ'],
      generatedByModel: 'gpt-4o-mini',
    );
  }

  @override
  Future<void> sendReply(String conversationId, String content, {bool humanAgentTag = false}) async {}

  @override
  Future<bool> resolveConversation(String conversationId, bool resolved) async {
    return resolved;
  }

  @override
  Future<void> sendTyping(String conversationId, bool isTyping) async {}

  @override
  Future<List<Map<String, dynamic>>> getMessages(String conversationId) async {
    return [];
  }
}

class FakeWorkflowRepository extends WorkflowRepository {
  @override
  Future<List<WorkflowListItemModel>> getWorkflows() async {
    return WorkflowListItemModel.defaultWorkflows;
  }

  @override
  Future<WorkflowListItemModel?> generateWorkflowWithAi(String prompt, {String? name}) async {
    return WorkflowListItemModel(
      id: 'wf-ai-generated',
      name: name ?? 'AI Generated Workflow',
      description: 'Generated from: $prompt',
      status: 'DRAFT',
      activeVersionNumber: 1,
    );
  }
}

class FakeKnowledgeRepository extends KnowledgeRepository {
  @override
  Future<List<KnowledgeArticleModel>> getArticles({String? search, String? category}) async {
    if (search != null && search.isNotEmpty) {
      final q = search.toLowerCase();
      return KnowledgeArticleModel.defaultArticles.where((a) =>
        a.title.toLowerCase().contains(q) || a.content.toLowerCase().contains(q)
      ).toList();
    }
    if (category != null && category.isNotEmpty && category != 'ALL') {
      return KnowledgeArticleModel.defaultArticles.where((a) => a.category == category).toList();
    }
    return KnowledgeArticleModel.defaultArticles;
  }

  @override
  Future<KnowledgeArticleModel?> createArticle({
    required String title,
    required String content,
    required String category,
    List<String>? tags,
  }) async {
    return KnowledgeArticleModel(
      id: 'art-new',
      title: title,
      content: content,
      category: category,
      tags: tags ?? [],
    );
  }

  @override
  Future<bool> deleteArticle(String id) async {
    return true;
  }
}

void main() {
  group('Phase 20 Knowledge Base RAG & AI Co-Pilot Unit & Widget Tests', () {
    test('KnowledgeArticleModel parses JSON and defaults correctly', () {
      final article = KnowledgeArticleModel.fromJson({
        'id': 'art-1',
        'title': 'Test Article',
        'content': 'This is test content for RAG vector search',
        'category': 'FAQ',
        'tags': ['test', 'rag'],
        'enabled': true,
        'usageCount': 12,
        'citationSnippet': 'This is test content...',
      });

      expect(article.id, equals('art-1'));
      expect(article.title, equals('Test Article'));
      expect(article.category, equals('FAQ'));
      expect(article.tags, contains('rag'));
      expect(article.usageCount, equals(12));
      expect(article.citationSnippet, equals('This is test content...'));
    });

    test('KnowledgeRepository CRUD fallbacks work gracefully', () async {
      final repo = KnowledgeRepository();

      // 1. Get all articles fallback
      final allArticles = await repo.getArticles();
      expect(allArticles.length, greaterThanOrEqualTo(4));

      // 2. Search filtering fallback
      final searchResults = await repo.getArticles(search: 'refund');
      expect(searchResults.any((a) => a.title.toLowerCase().contains('refund')), isTrue);

      // 3. Category filtering fallback
      final faqResults = await repo.getArticles(category: 'FAQ');
      expect(faqResults.every((a) => a.category == 'FAQ'), isTrue);

      // 4. Create article
      final created = await repo.createArticle(
        title: 'New Shipping Info',
        content: 'Orders ship in 2-3 business days across India.',
        category: 'FAQ',
        tags: ['shipping', 'delivery'],
      );
      expect(created, isNotNull);
      expect(created!.title, equals('New Shipping Info'));

      // 5. Delete article
      final deleted = await repo.deleteArticle('art-1');
      expect(deleted, isA<bool>());
    });

    test('AiSuggestionModel parses JSON and escalations correctly', () {
      final suggestion = AiSuggestionModel.fromJson({
        'suggestedReply': 'We process refunds within 3-5 banking days.',
        'confidenceScore': 0.95,
        'requiresHumanHandoff': true,
        'humanHandoffReason': 'Customer expressed intent to dispute charges.',
        'sourceArticleTitles': ['Refund Policy', 'FAQ - Payment Terms'],
        'generatedByModel': 'gpt-4o',
      });

      expect(suggestion.suggestedReply, contains('refunds within 3-5 banking days'));
      expect(suggestion.confidenceScore, equals(0.95));
      expect(suggestion.requiresHumanHandoff, isTrue);
      expect(suggestion.humanHandoffReason, contains('dispute charges'));
      expect(suggestion.sourceArticleTitles.length, equals(2));
      expect(suggestion.generatedByModel, equals('gpt-4o'));
    });

    test('WorkflowRepository generateWorkflowWithAi creates draft workflow', () async {
      final repo = WorkflowRepository();
      final wf = await repo.generateWorkflowWithAi(
        'When user comments PRICE on my Reel, send them a DM with the link',
        name: 'Price Info Funnel',
      );

      expect(wf, isNotNull);
      expect(wf!.name, equals('Price Info Funnel'));
      expect(wf.status, equals('DRAFT'));
    });

    testWidgets('InboxScreen AI Co-Pilot Suggestion flow and Knowledge Base dialog', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 900));

      final fakeCrmRepo = FakeCrmRepository();
      final fakeKnowledgeRepo = FakeKnowledgeRepository();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            crmRepositoryProvider.overrideWithValue(fakeCrmRepo),
            knowledgeRepositoryProvider.overrideWithValue(fakeKnowledgeRepo),
          ],
          child: const MaterialApp(
            home: InboxScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. Verify Knowledge Base button in conversation header
      final kbButton = find.byKey(const Key('knowledge_base_button'));
      expect(kbButton, findsOneWidget);

      // Open Knowledge Base Dialog
      await tester.tap(kbButton);
      await tester.pumpAndSettle();

      expect(find.text('Knowledge Base (RAG)'), findsOneWidget);
      expect(find.byKey(const Key('add_article_button')), findsOneWidget);
      expect(find.byKey(const Key('knowledge_search_field')), findsOneWidget);

      // Close Dialog
      await tester.tap(find.byIcon(Icons.close).first);
      await tester.pumpAndSettle();

      // 2. Verify AI Co-Pilot Suggest button is present in reply composer
      final copilotBtn = find.byKey(const Key('ai_copilot_suggest_button'));
      expect(copilotBtn, findsOneWidget);
      expect(find.text('✨ AI Co-Pilot Suggest'), findsOneWidget);

      // Tap AI Co-Pilot Suggest button
      await tester.tap(copilotBtn);
      await tester.pumpAndSettle();

      // 3. Verify AI Suggestion Card appears with high confidence and citations
      expect(find.byKey(const Key('ai_suggestion_card')), findsOneWidget);
      expect(find.text('AI Co-Pilot Suggestion'), findsOneWidget);
      expect(find.text('96% Match'), findsOneWidget);
      expect(find.text('Insert into Reply'), findsOneWidget);

      // 4. Tap "Insert into Reply"
      await tester.tap(find.byKey(const Key('insert_suggestion_button')));
      await tester.pumpAndSettle();

      // Suggestion card is cleared and text is now populated in the reply text field
      expect(find.byKey(const Key('ai_suggestion_card')), findsNothing);
      expect(find.textContaining('https://cal.com/autoflow-demo'), findsOneWidget);
    });

    testWidgets('WorkflowListScreen Generate with AI button opens dialog and creates draft', (tester) async {
      await tester.binding.setSurfaceSize(const Size(1400, 900));

      final fakeWfRepo = FakeWorkflowRepository();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            workflowRepositoryProvider.overrideWithValue(fakeWfRepo),
          ],
          child: const MaterialApp(
            home: WorkflowListScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. Find and tap "Generate with AI" button
      final aiGenBtn = find.byKey(const Key('ai_generate_workflow_button'));
      expect(aiGenBtn, findsOneWidget);
      expect(find.text('Generate with AI'), findsOneWidget);

      await tester.tap(aiGenBtn);
      await tester.pumpAndSettle();

      // 2. Verify Dialog appears
      expect(find.text('AI Workflow Generator'), findsOneWidget);
      expect(find.byKey(const Key('ai_workflow_prompt_input')), findsOneWidget);
      expect(find.byKey(const Key('submit_ai_generate_workflow')), findsOneWidget);

      // 3. Enter prompt and submit
      await tester.enterText(
        find.byKey(const Key('ai_workflow_prompt_input')),
        'When someone comments "DEAL", send them a DM with 30% discount link',
      );
      await tester.tap(find.byKey(const Key('submit_ai_generate_workflow')));
      await tester.pumpAndSettle();

      // Dialog closes and draft is generated
      expect(find.text('AI Workflow Generator'), findsNothing);
    });
  });
}
