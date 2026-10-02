import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class KnowledgeArticleModel {
  final String id;
  final String title;
  final String content;
  final String category;
  final List<String> tags;
  final bool enabled;
  final int usageCount;
  final String? citationSnippet;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  const KnowledgeArticleModel({
    required this.id,
    required this.title,
    required this.content,
    required this.category,
    this.tags = const [],
    this.enabled = true,
    this.usageCount = 0,
    this.citationSnippet,
    this.createdAt,
    this.updatedAt,
  });

  factory KnowledgeArticleModel.fromJson(Map<String, dynamic> json) {
    return KnowledgeArticleModel(
      id: json['id']?.toString() ?? '',
      title: json['title']?.toString() ?? '',
      content: json['content']?.toString() ?? '',
      category: json['category']?.toString() ?? 'GENERAL',
      tags: (json['tags'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      enabled: json['enabled'] != false,
      usageCount: (json['usageCount'] as num?)?.toInt() ?? 0,
      citationSnippet: json['citationSnippet']?.toString(),
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
      updatedAt: json['updatedAt'] != null ? DateTime.tryParse(json['updatedAt'].toString()) : null,
    );
  }

  static const defaultArticles = [
    KnowledgeArticleModel(
      id: 'kb-1',
      title: 'Consultation & 1-on-1 Coaching Booking FAQ',
      content: 'Clients can book a 30-minute discovery or 1-on-1 coaching call directly using https://cal.com/autoflow-demo. Rescheduling is allowed up to 4 hours before the session.',
      category: 'FAQ',
      tags: ['booking', 'coaching', 'calendar'],
      usageCount: 42,
    ),
    KnowledgeArticleModel(
      id: 'kb-2',
      title: 'Refund, Guarantee and Cancellation Policy',
      content: 'We offer a 14-day 100% money-back guarantee on all digital courses and coaching retainers if you are not satisfied. Please submit refund requests through support.',
      category: 'POLICY',
      tags: ['refund', 'cancellation', 'money-back'],
      usageCount: 19,
    ),
    KnowledgeArticleModel(
      id: 'kb-3',
      title: 'Pricing Tiers and Subscription Features',
      content: 'Starter Plan is \$29/mo with 3 automations and 1,000 monthly executions. Pro Plan is \$79/mo with unlimited automations, WhatsApp + Telegram AI Co-Pilot, and dedicated support.',
      category: 'PRODUCT_SPECS',
      tags: ['pricing', 'plans', 'features'],
      usageCount: 88,
    ),
    KnowledgeArticleModel(
      id: 'kb-4',
      title: 'Lead Magnet Delivery Troubleshooting',
      content: 'If an Instagram follower comments the target keyword but did not receive the DM, verify that their Instagram Privacy settings allow message requests from accounts they do not follow.',
      category: 'TROUBLESHOOTING',
      tags: ['dm', 'instagram', 'troubleshooting'],
      usageCount: 15,
    ),
  ];
}

final knowledgeRepositoryProvider = Provider<KnowledgeRepository>((ref) {
  return KnowledgeRepository(ref.watch(apiClientProvider));
});

final knowledgeArticlesProvider = FutureProvider.family<List<KnowledgeArticleModel>, String?>((ref, search) async {
  return ref.watch(knowledgeRepositoryProvider).getArticles(search: search);
});

class KnowledgeRepository {
  final ApiClient _apiClient;

  KnowledgeRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<KnowledgeArticleModel>> getArticles({String? search, String? category}) async {
    try {
      final response = await _apiClient.dio.get(
        ApiConstants.knowledgeArticles,
        queryParameters: {
          if (search != null && search.isNotEmpty) 'search': search,
          if (category != null && category.isNotEmpty) 'category': category,
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => KnowledgeArticleModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}

    // Fallback filtered mock data
    if (search != null && search.isNotEmpty) {
      final q = search.toLowerCase();
      return KnowledgeArticleModel.defaultArticles.where((a) =>
        a.title.toLowerCase().contains(q) ||
        a.content.toLowerCase().contains(q) ||
        a.tags.any((t) => t.toLowerCase().contains(q))
      ).toList();
    }
    if (category != null && category.isNotEmpty) {
      return KnowledgeArticleModel.defaultArticles.where((a) => a.category == category).toList();
    }
    return KnowledgeArticleModel.defaultArticles;
  }

  Future<KnowledgeArticleModel?> createArticle({
    required String title,
    required String content,
    required String category,
    List<String>? tags,
  }) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.knowledgeArticles,
        data: {
          'title': title,
          'content': content,
          'category': category,
          'tags': tags ?? [],
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        return KnowledgeArticleModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return KnowledgeArticleModel(
      id: 'kb-${DateTime.now().millisecondsSinceEpoch}',
      title: title,
      content: content,
      category: category,
      tags: tags ?? [],
    );
  }

  Future<KnowledgeArticleModel?> updateArticle(
    String id, {
    required String title,
    required String content,
    required String category,
    List<String>? tags,
    bool? enabled,
  }) async {
    try {
      final payload = <String, dynamic>{
        'title': title,
        'content': content,
        'category': category,
        'tags': tags ?? [],
      };
      if (enabled != null) {
        payload['enabled'] = enabled;
      }
      final response = await _apiClient.dio.put(
        ApiConstants.knowledgeArticle(id),
        data: payload,
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        return KnowledgeArticleModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }

  Future<bool> deleteArticle(String id) async {
    try {
      final response = await _apiClient.dio.delete(ApiConstants.knowledgeArticle(id));
      return response.statusCode == 200 && response.data['success'] == true;
    } catch (_) {
      return false;
    }
  }
}
