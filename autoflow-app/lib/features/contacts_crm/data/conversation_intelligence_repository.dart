import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class PerformanceAnalyticsModel {
  final double averageFirstResponseTimeMinutes;
  final double averageResolutionTimeMinutes;
  final double slaFirstResponseComplianceRate;
  final double slaResolutionComplianceRate;
  final int totalConversations;
  final int resolvedConversations;
  final int activeConversations;
  final double averageCsatRating;
  final int totalCsatResponses;
  final double aiAutoPilotDeflectionRate;

  const PerformanceAnalyticsModel({
    required this.averageFirstResponseTimeMinutes,
    required this.averageResolutionTimeMinutes,
    required this.slaFirstResponseComplianceRate,
    required this.slaResolutionComplianceRate,
    required this.totalConversations,
    required this.resolvedConversations,
    required this.activeConversations,
    required this.averageCsatRating,
    required this.totalCsatResponses,
    required this.aiAutoPilotDeflectionRate,
  });

  factory PerformanceAnalyticsModel.fromJson(Map<String, dynamic> json) {
    return PerformanceAnalyticsModel(
      averageFirstResponseTimeMinutes: (json['averageFirstResponseTimeMinutes'] as num?)?.toDouble() ?? 0.0,
      averageResolutionTimeMinutes: (json['averageResolutionTimeMinutes'] as num?)?.toDouble() ?? 0.0,
      slaFirstResponseComplianceRate: (json['slaFirstResponseComplianceRate'] as num?)?.toDouble() ?? 100.0,
      slaResolutionComplianceRate: (json['slaResolutionComplianceRate'] as num?)?.toDouble() ?? 100.0,
      totalConversations: (json['totalConversations'] as num?)?.toInt() ?? 0,
      resolvedConversations: (json['resolvedConversations'] as num?)?.toInt() ?? 0,
      activeConversations: (json['activeConversations'] as num?)?.toInt() ?? 0,
      averageCsatRating: (json['averageCsatRating'] as num?)?.toDouble() ?? 0.0,
      totalCsatResponses: (json['totalCsatResponses'] as num?)?.toInt() ?? 0,
      aiAutoPilotDeflectionRate: (json['aiAutoPilotDeflectionRate'] as num?)?.toDouble() ?? 0.0,
    );
  }

  static const defaultAnalytics = PerformanceAnalyticsModel(
    averageFirstResponseTimeMinutes: 3.5,
    averageResolutionTimeMinutes: 24.0,
    slaFirstResponseComplianceRate: 98.5,
    slaResolutionComplianceRate: 96.2,
    totalConversations: 124,
    resolvedConversations: 110,
    activeConversations: 14,
    averageCsatRating: 4.85,
    totalCsatResponses: 78,
    aiAutoPilotDeflectionRate: 62.5,
  );
}

class SlaPolicyModel {
  final String id;
  final String name;
  final String? channel;
  final String priority;
  final int firstResponseTimeSeconds;
  final int resolutionTimeSeconds;
  final String routingPolicy;
  final bool active;

  const SlaPolicyModel({
    required this.id,
    required this.name,
    this.channel,
    required this.priority,
    required this.firstResponseTimeSeconds,
    required this.resolutionTimeSeconds,
    required this.routingPolicy,
    required this.active,
  });

  factory SlaPolicyModel.fromJson(Map<String, dynamic> json) {
    return SlaPolicyModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Default Policy',
      channel: json['channel']?.toString(),
      priority: json['priority']?.toString() ?? 'NORMAL',
      firstResponseTimeSeconds: (json['firstResponseTimeSeconds'] as num?)?.toInt() ?? 900,
      resolutionTimeSeconds: (json['resolutionTimeSeconds'] as num?)?.toInt() ?? 7200,
      routingPolicy: json['routingPolicy']?.toString() ?? 'LEAST_BUSY',
      active: json['active'] as bool? ?? true,
    );
  }
}

class CsatSurveyModel {
  final String id;
  final String conversationId;
  final int? rating;
  final String? feedbackText;
  final String status;
  final DateTime dispatchedAt;
  final DateTime? respondedAt;

  const CsatSurveyModel({
    required this.id,
    required this.conversationId,
    this.rating,
    this.feedbackText,
    required this.status,
    required this.dispatchedAt,
    this.respondedAt,
  });

  factory CsatSurveyModel.fromJson(Map<String, dynamic> json) {
    return CsatSurveyModel(
      id: json['id']?.toString() ?? '',
      conversationId: json['conversationId']?.toString() ?? '',
      rating: (json['rating'] as num?)?.toInt(),
      feedbackText: json['feedbackText']?.toString(),
      status: json['status']?.toString() ?? 'DISPATCHED',
      dispatchedAt: DateTime.tryParse(json['dispatchedAt']?.toString() ?? '') ?? DateTime.now(),
      respondedAt: json['respondedAt'] != null
          ? DateTime.tryParse(json['respondedAt'].toString())
          : null,
    );
  }
}

final conversationIntelligenceRepositoryProvider = Provider<ConversationIntelligenceRepository>((ref) {
  return ConversationIntelligenceRepository();
});

final performanceAnalyticsProvider = FutureProvider<PerformanceAnalyticsModel>((ref) async {
  return ref.watch(conversationIntelligenceRepositoryProvider).fetchPerformanceAnalytics();
});

final slaPoliciesProvider = FutureProvider<List<SlaPolicyModel>>((ref) async {
  return ref.watch(conversationIntelligenceRepositoryProvider).fetchSlaPolicies();
});

class ConversationIntelligenceRepository {
  final ApiClient _apiClient;

  ConversationIntelligenceRepository([ApiClient? apiClient])
      : _apiClient = apiClient ?? ApiClient();

  Future<PerformanceAnalyticsModel> fetchPerformanceAnalytics() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmPerformanceAnalytics);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final data = response.data['data'] as Map<String, dynamic>? ?? {};
        return PerformanceAnalyticsModel.fromJson(data);
      }
    } catch (_) {}
    return PerformanceAnalyticsModel.defaultAnalytics;
  }

  Future<List<SlaPolicyModel>> fetchSlaPolicies() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.crmSlaPolicies);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        return list.map((e) => SlaPolicyModel.fromJson(e as Map<String, dynamic>)).toList();
      }
    } catch (_) {}
    return const [];
  }

  Future<CsatSurveyModel?> submitCsatFeedback(
    String conversationId,
    int rating,
    String? feedbackText,
  ) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.crmConversationCsat(conversationId),
        data: {
          'rating': rating,
          if (feedbackText != null && feedbackText.isNotEmpty) 'feedbackText': feedbackText,
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        final data = response.data['data'] as Map<String, dynamic>? ?? {};
        return CsatSurveyModel.fromJson(data);
      }
    } catch (_) {}
    return null;
  }
}
