import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';
import 'workflow_repository.dart';

class TemplateModel {
  final String id;
  final String name;
  final String category;
  final String? description;
  final String? icon;
  final List<String> tags;
  final String? graphDefinition;
  final bool isFeatured;
  final DateTime? createdAt;

  const TemplateModel({
    required this.id,
    required this.name,
    required this.category,
    this.description,
    this.icon,
    this.tags = const [],
    this.graphDefinition,
    this.isFeatured = false,
    this.createdAt,
  });

  factory TemplateModel.fromJson(Map<String, dynamic> json) {
    return TemplateModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      category: json['category']?.toString() ?? 'GENERAL',
      description: json['description']?.toString(),
      icon: json['icon']?.toString(),
      tags: (json['tags'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? const [],
      graphDefinition: json['graphDefinition']?.toString(),
      isFeatured: json['isFeatured'] == true || json['featured'] == true,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
    );
  }

  static const defaultTemplates = [
    TemplateModel(
      id: 'tpl-1',
      name: 'Comment to Lead Magnet',
      category: 'LEAD_MAGNET',
      description: 'Auto-reply to post comments and send a downloadable PDF guide via direct message',
      icon: 'file-text',
      tags: ['instagram', 'lead-magnet', 'growth'],
      isFeatured: true,
    ),
    TemplateModel(
      id: 'tpl-2',
      name: 'Story Mention Promo Delivery',
      category: 'ECOMMERCE',
      description: 'Reward users who tag your brand in stories with an exclusive discount code',
      icon: 'gift',
      tags: ['instagram', 'ecommerce', 'promo'],
      isFeatured: true,
    ),
    TemplateModel(
      id: 'tpl-3',
      name: 'WhatsApp AI Customer Qualifier',
      category: 'SUPPORT',
      description: 'Deploy an AI triage agent to handle incoming WhatsApp inquiries and capture email addresses',
      icon: 'message-circle',
      tags: ['whatsapp', 'ai', 'support'],
      isFeatured: false,
    ),
    TemplateModel(
      id: 'tpl-4',
      name: 'VIP Webinar Auto-Registration',
      category: 'WEBINAR',
      description: 'Collect attendee phone number and register them to upcoming Zoom webinar',
      icon: 'video',
      tags: ['zoom', 'webinar', 'lead-gen'],
      isFeatured: false,
    ),
  ];
}

class TemplateFilter {
  final String? category;
  final String? tag;
  final bool? featured;

  const TemplateFilter({this.category, this.tag, this.featured});

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TemplateFilter &&
          runtimeType == other.runtimeType &&
          category == other.category &&
          tag == other.tag &&
          featured == other.featured;

  @override
  int get hashCode => Object.hash(category, tag, featured);
}

final templateRepositoryProvider = Provider<TemplateRepository>((ref) {
  return TemplateRepository(ref.watch(apiClientProvider));
});

final templatesProvider = FutureProvider.family<List<TemplateModel>, TemplateFilter?>((ref, filter) async {
  return ref.watch(templateRepositoryProvider).getTemplates(
        category: filter?.category,
        tag: filter?.tag,
        featured: filter?.featured,
      );
});

class TemplateRepository {
  final ApiClient _apiClient;

  TemplateRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<TemplateModel>> getTemplates({String? category, String? tag, bool? featured}) async {
    try {
      final queryParams = <String, dynamic>{};
      if (category != null && category.isNotEmpty && category != 'ALL') {
        queryParams['category'] = category;
      }
      if (tag != null && tag.isNotEmpty) {
        queryParams['tag'] = tag;
      }
      if (featured != null && featured) {
        queryParams['featured'] = 'true';
      }

      final response = await _apiClient.dio.get(
        ApiConstants.templates,
        queryParameters: queryParams.isNotEmpty ? queryParams : null,
      );

      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => TemplateModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}

    // Fallback to default templates matching filters locally
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

  Future<TemplateModel?> getTemplateById(String id) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.template(id));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return TemplateModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}

    final matches = TemplateModel.defaultTemplates.where((t) => t.id == id);
    return matches.isNotEmpty ? matches.first : null;
  }

  Future<WorkflowListItemModel?> instantiateTemplate(
    String templateId, {
    String? workflowName,
    String? description,
  }) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.templateInstantiate(templateId),
        data: {
          if (workflowName != null && workflowName.isNotEmpty) 'workflowName': workflowName,
          if (description != null && description.isNotEmpty) 'description': description,
        },
      );

      if (response.statusCode == 201 && response.data['success'] == true) {
        return WorkflowListItemModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }
}
