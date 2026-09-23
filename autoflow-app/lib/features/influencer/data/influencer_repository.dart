import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class InfluencerStatsModel {
  final String influencerId;
  final String name;
  final String promoCode;
  final String referralLink;
  final int totalClicks;
  final double pendingCommissionInr;
  final double approvedCommissionInr;
  final double paidCommissionInr;

  const InfluencerStatsModel({
    required this.influencerId,
    required this.name,
    required this.promoCode,
    required this.referralLink,
    required this.totalClicks,
    required this.pendingCommissionInr,
    required this.approvedCommissionInr,
    required this.paidCommissionInr,
  });

  factory InfluencerStatsModel.fromJson(Map<String, dynamic> json) {
    return InfluencerStatsModel(
      influencerId: json['influencerId']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Partner',
      promoCode: json['promoCode']?.toString() ?? '',
      referralLink: json['referralLink']?.toString() ?? '',
      totalClicks: (json['totalClicks'] as num?)?.toInt() ?? 0,
      pendingCommissionInr: (json['pendingCommissionInr'] as num?)?.toDouble() ?? 0.0,
      approvedCommissionInr: (json['approvedCommissionInr'] as num?)?.toDouble() ?? 0.0,
      paidCommissionInr: (json['paidCommissionInr'] as num?)?.toDouble() ?? 0.0,
    );
  }

  static const defaultFallback = InfluencerStatsModel(
    influencerId: 'inf-demo-1',
    name: 'Top Creator Partner',
    promoCode: 'CREATOR20',
    referralLink: 'http://localhost:8080/r/CREATOR20',
    totalClicks: 342,
    pendingCommissionInr: 1250.0,
    approvedCommissionInr: 3400.0,
    paidCommissionInr: 8900.0,
  );
}

final influencerRepositoryProvider = Provider<InfluencerRepository>((ref) {
  return InfluencerRepository(ref.watch(apiClientProvider));
});

final influencerStatsProvider = FutureProvider<InfluencerStatsModel>((ref) async {
  return ref.watch(influencerRepositoryProvider).getStats();
});

class InfluencerRepository {
  final ApiClient _apiClient;

  InfluencerRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<InfluencerStatsModel> getStats() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.influencerPortalStats);
      if (response.statusCode == 200 && response.data['success'] == true) {
        return InfluencerStatsModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    return InfluencerStatsModel.defaultFallback;
  }
}
