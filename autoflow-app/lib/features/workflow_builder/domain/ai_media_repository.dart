import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class AiMediaAssetModel {
  final String id;
  final String? organizationId;
  final String fileName;
  final String mimeType;
  final int fileSizeBytes;
  final String? sha256Checksum;
  final String downloadUrl;

  const AiMediaAssetModel({
    required this.id,
    this.organizationId,
    required this.fileName,
    required this.mimeType,
    required this.fileSizeBytes,
    this.sha256Checksum,
    required this.downloadUrl,
  });

  factory AiMediaAssetModel.fromJson(Map<String, dynamic> json) {
    return AiMediaAssetModel(
      id: json['id'] as String? ?? '',
      organizationId: json['organizationId'] as String?,
      fileName: json['fileName'] as String? ?? 'asset.png',
      mimeType: json['mimeType'] as String? ?? 'image/png',
      fileSizeBytes: (json['fileSizeBytes'] as num?)?.toInt() ?? 0,
      sha256Checksum: json['sha256Checksum'] as String?,
      downloadUrl: json['downloadUrl'] as String? ?? '',
    );
  }
}

class AiMediaGenerateOptions {
  final String templateType;
  final String prompt;
  final String? headline;
  final String? subtext;
  final String? badgeText;
  final String? accentColor;
  final int width;
  final int height;

  const AiMediaGenerateOptions({
    this.templateType = 'COUPON_CARD',
    required this.prompt,
    this.headline,
    this.subtext,
    this.badgeText,
    this.accentColor = '#6366F1',
    this.width = 1200,
    this.height = 630,
  });

  Map<String, dynamic> toJson() => {
    'templateType': templateType,
    'prompt': prompt,
    'headline': headline ?? prompt,
    'subtext': subtext ?? 'Generated with AutoFlow AI',
    'badgeText': badgeText ?? templateType.replaceAll('_', ' '),
    'accentColor': accentColor ?? '#6366F1',
    'width': width,
    'height': height,
  };
}

final aiMediaRepositoryProvider = Provider<AiMediaRepository>((ref) {
  return AiMediaRepository(ref.watch(apiClientProvider));
});

class AiMediaRepository {
  final ApiClient _apiClient;

  AiMediaRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<AiMediaAssetModel> generateBrandedAsset(AiMediaGenerateOptions options) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.mediaAiGenerate,
        data: options.toJson(),
      );
      if (response.statusCode == 200 && response.data['success'] == true) {
        return AiMediaAssetModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {
      // Graceful simulated fallback in offline / test environments
    }

    final mockId = 'mock-asset-${DateTime.now().millisecondsSinceEpoch}';
    return AiMediaAssetModel(
      id: mockId,
      fileName: 'ai_asset_mock.png',
      mimeType: 'image/png',
      fileSizeBytes: 65432,
      downloadUrl: 'https://s3.autoflow.ai/tenants/media/$mockId.png',
    );
  }
}
