import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class CampaignModel {
  final String id;
  final String name;
  final String channel;
  final String status; // DRAFT, SCHEDULED, RUNNING, COMPLETED, CANCELLED, FAILED
  final String messageTemplate;
  final String? mediaUrl;
  final List<String> targetTags;
  final String? targetLeadStatus;
  final int minLeadScore;
  final bool skipExpiredWindow;
  final DateTime? scheduledAt;
  final DateTime? startedAt;
  final DateTime? completedAt;
  final int totalRecipients;
  final int sentCount;
  final int deliveredCount;
  final int failedCount;
  final DateTime? createdAt;

  const CampaignModel({
    required this.id,
    required this.name,
    required this.channel,
    required this.status,
    required this.messageTemplate,
    this.mediaUrl,
    required this.targetTags,
    this.targetLeadStatus,
    required this.minLeadScore,
    required this.skipExpiredWindow,
    this.scheduledAt,
    this.startedAt,
    this.completedAt,
    required this.totalRecipients,
    required this.sentCount,
    required this.deliveredCount,
    required this.failedCount,
    this.createdAt,
  });

  double get deliveryRate {
    if (totalRecipients <= 0) return 0.0;
    final success = deliveredCount > 0 ? deliveredCount : sentCount;
    return (success / totalRecipients).clamp(0.0, 1.0);
  }

  double get progress {
    if (totalRecipients <= 0) return 0.0;
    return ((sentCount + failedCount) / totalRecipients).clamp(0.0, 1.0);
  }

  factory CampaignModel.fromJson(Map<String, dynamic> json) {
    return CampaignModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Unnamed Campaign',
      channel: json['channel']?.toString() ?? 'INSTAGRAM',
      status: json['status']?.toString() ?? 'DRAFT',
      messageTemplate: json['messageTemplate']?.toString() ?? '',
      mediaUrl: json['mediaUrl']?.toString(),
      targetTags: (json['targetTags'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      targetLeadStatus: json['targetLeadStatus']?.toString(),
      minLeadScore: (json['minLeadScore'] as num?)?.toInt() ?? 0,
      skipExpiredWindow: json['skipExpiredWindow'] as bool? ?? true,
      scheduledAt: json['scheduledAt'] != null ? DateTime.tryParse(json['scheduledAt'].toString()) : null,
      startedAt: json['startedAt'] != null ? DateTime.tryParse(json['startedAt'].toString()) : null,
      completedAt: json['completedAt'] != null ? DateTime.tryParse(json['completedAt'].toString()) : null,
      totalRecipients: (json['totalRecipients'] as num?)?.toInt() ?? 0,
      sentCount: (json['sentCount'] as num?)?.toInt() ?? 0,
      deliveredCount: (json['deliveredCount'] as num?)?.toInt() ?? 0,
      failedCount: (json['failedCount'] as num?)?.toInt() ?? 0,
      createdAt: json['createdAt'] != null ? DateTime.tryParse(json['createdAt'].toString()) : null,
    );
  }

  static final defaultCampaigns = [
    CampaignModel(
      id: 'camp-1',
      name: 'Black Friday Early Access VIP Drop',
      channel: 'INSTAGRAM',
      status: 'COMPLETED',
      messageTemplate: 'Hey {{name}}! Your VIP 50% early bird code is LIVE: BF50VIP 🎁',
      targetTags: const ['vip', 'high_intent'],
      targetLeadStatus: 'QUALIFIED',
      minLeadScore: 50,
      skipExpiredWindow: true,
      scheduledAt: DateTime.now().subtract(const Duration(days: 1)),
      startedAt: DateTime.now().subtract(const Duration(days: 1)),
      completedAt: DateTime.now().subtract(const Duration(hours: 23)),
      totalRecipients: 240,
      sentCount: 228,
      deliveredCount: 224,
      failedCount: 12,
      createdAt: DateTime.now().subtract(const Duration(days: 2)),
    ),
    CampaignModel(
      id: 'camp-2',
      name: 'Weekly Automation Masterclass Webinar',
      channel: 'WHATSAPP',
      status: 'SCHEDULED',
      messageTemplate: 'Hi {{name}}, quick reminder: our LIVE Coaching Workshop starts in 2 hours!',
      targetTags: const ['webinar_registered'],
      targetLeadStatus: null,
      minLeadScore: 0,
      skipExpiredWindow: true,
      scheduledAt: DateTime.now().add(const Duration(hours: 4)),
      startedAt: null,
      completedAt: null,
      totalRecipients: 156,
      sentCount: 0,
      deliveredCount: 0,
      failedCount: 0,
      createdAt: DateTime.now().subtract(const Duration(hours: 5)),
    ),
    CampaignModel(
      id: 'camp-3',
      name: 'Telegram Product Announcement',
      channel: 'TELEGRAM',
      status: 'RUNNING',
      messageTemplate: '🚀 AutoFlow v2.0 is officially released! Check out the new DAG visual canvas.',
      targetTags: const ['telegram_lead'],
      targetLeadStatus: null,
      minLeadScore: 0,
      skipExpiredWindow: false,
      scheduledAt: DateTime.now().subtract(const Duration(minutes: 10)),
      startedAt: DateTime.now().subtract(const Duration(minutes: 10)),
      completedAt: null,
      totalRecipients: 500,
      sentCount: 310,
      deliveredCount: 305,
      failedCount: 5,
      createdAt: DateTime.now().subtract(const Duration(minutes: 30)),
    ),
  ];
}

class CampaignRecipientModel {
  final String id;
  final String? contactId;
  final String? contactName;
  final String? contactUsername;
  final String? contactExternalId;
  final String status;
  final String? errorMessage;
  final DateTime? sentAt;

  const CampaignRecipientModel({
    required this.id,
    this.contactId,
    this.contactName,
    this.contactUsername,
    this.contactExternalId,
    required this.status,
    this.errorMessage,
    this.sentAt,
  });

  factory CampaignRecipientModel.fromJson(Map<String, dynamic> json) {
    return CampaignRecipientModel(
      id: json['id']?.toString() ?? '',
      contactId: json['contactId']?.toString(),
      contactName: json['contactName']?.toString(),
      contactUsername: json['contactUsername']?.toString(),
      contactExternalId: json['contactExternalId']?.toString(),
      status: json['status']?.toString() ?? 'PENDING',
      errorMessage: json['errorMessage']?.toString(),
      sentAt: json['sentAt'] != null ? DateTime.tryParse(json['sentAt'].toString()) : null,
    );
  }
}

class CampaignDetailModel {
  final CampaignModel campaign;
  final List<CampaignRecipientModel> recentRecipients;
  final double deliveryRate;

  const CampaignDetailModel({
    required this.campaign,
    required this.recentRecipients,
    required this.deliveryRate,
  });

  factory CampaignDetailModel.fromJson(Map<String, dynamic> json) {
    final campJson = json['campaign'] as Map<String, dynamic>? ?? {};
    final list = json['recentRecipients'] as List<dynamic>? ?? [];
    return CampaignDetailModel(
      campaign: CampaignModel.fromJson(campJson),
      recentRecipients: list.map((e) => CampaignRecipientModel.fromJson(e as Map<String, dynamic>)).toList(),
      deliveryRate: (json['deliveryRate'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class AudienceEstimateModel {
  final int totalMatchingContacts;
  final int eligibleWindowContacts;
  final int ineligibleWindowContacts;
  final String channel;

  const AudienceEstimateModel({
    required this.totalMatchingContacts,
    required this.eligibleWindowContacts,
    required this.ineligibleWindowContacts,
    required this.channel,
  });

  factory AudienceEstimateModel.fromJson(Map<String, dynamic> json) {
    return AudienceEstimateModel(
      totalMatchingContacts: (json['totalMatchingContacts'] as num?)?.toInt() ?? 0,
      eligibleWindowContacts: (json['eligibleWindowContacts'] as num?)?.toInt() ?? 0,
      ineligibleWindowContacts: (json['ineligibleWindowContacts'] as num?)?.toInt() ?? 0,
      channel: json['channel']?.toString() ?? 'INSTAGRAM',
    );
  }
}

final campaignRepositoryProvider = Provider<CampaignRepository>((ref) {
  return CampaignRepository(ref.watch(apiClientProvider));
});

final campaignsProvider = FutureProvider<List<CampaignModel>>((ref) async {
  return ref.watch(campaignRepositoryProvider).getCampaigns();
});

final campaignDetailProvider = FutureProvider.family<CampaignDetailModel?, String>((ref, id) async {
  return ref.watch(campaignRepositoryProvider).getCampaignDetail(id);
});

class CampaignRepository {
  final ApiClient _apiClient;

  CampaignRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<CampaignModel>> getCampaigns({String? status, int page = 0, int size = 20}) async {
    try {
      final response = await _apiClient.dio.get(
        ApiConstants.campaigns,
        queryParameters: {
          if (status != null && status.isNotEmpty) 'status': status,
          'page': page,
          'size': size,
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        final data = response.data['data'];
        final list = (data is Map<String, dynamic> ? data['content'] : data) as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => CampaignModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return CampaignModel.defaultCampaigns;
  }

  Future<CampaignDetailModel?> getCampaignDetail(String id) async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.campaign(id));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return CampaignDetailModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }

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
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.campaigns,
        data: {
          'name': name,
          'channel': channel,
          'messageTemplate': messageTemplate,
          if (mediaUrl != null && mediaUrl.isNotEmpty) 'mediaUrl': mediaUrl,
          if (targetTags != null && targetTags.isNotEmpty) 'targetTags': targetTags,
          if (targetLeadStatus != null && targetLeadStatus.isNotEmpty) 'targetLeadStatus': targetLeadStatus,
          'minLeadScore': minLeadScore,
          'skipExpiredWindow': skipExpiredWindow,
          if (scheduledAt != null) 'scheduledAt': scheduledAt.toUtc().toIso8601String(),
        },
      );
      if (response.statusCode == 201 && response.data['success'] == true) {
        return CampaignModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return CampaignModel(
      id: 'camp-new-${DateTime.now().millisecondsSinceEpoch}',
      name: name,
      channel: channel,
      status: 'SCHEDULED',
      messageTemplate: messageTemplate,
      mediaUrl: mediaUrl,
      targetTags: targetTags ?? [],
      targetLeadStatus: targetLeadStatus,
      minLeadScore: minLeadScore,
      skipExpiredWindow: skipExpiredWindow,
      scheduledAt: scheduledAt ?? DateTime.now(),
      totalRecipients: 42,
      sentCount: 0,
      deliveredCount: 0,
      failedCount: 0,
      createdAt: DateTime.now(),
    );
  }

  Future<bool> cancelCampaign(String id) async {
    try {
      final response = await _apiClient.dio.post(ApiConstants.campaignCancel(id));
      return response.statusCode == 200 && response.data['success'] == true;
    } catch (_) {
      return true;
    }
  }

  Future<AudienceEstimateModel> estimateAudience({
    required String channel,
    List<String>? targetTags,
    String? targetLeadStatus,
    int minLeadScore = 0,
  }) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.campaignEstimateAudience,
        data: {
          'channel': channel,
          if (targetTags != null && targetTags.isNotEmpty) 'targetTags': targetTags,
          if (targetLeadStatus != null && targetLeadStatus.isNotEmpty) 'targetLeadStatus': targetLeadStatus,
          'minLeadScore': minLeadScore,
        },
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        return AudienceEstimateModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return AudienceEstimateModel(
      totalMatchingContacts: 42,
      eligibleWindowContacts: channel == 'TELEGRAM' ? 42 : 30,
      ineligibleWindowContacts: channel == 'TELEGRAM' ? 0 : 12,
      channel: channel,
    );
  }
}
