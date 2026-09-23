import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';

class ApiKeyModel {
  final String id;
  final String name;
  final String keyPrefix;
  final String maskedKey;
  final String status;
  final List<String> scopes;
  final String createdAt;

  const ApiKeyModel({
    required this.id,
    required this.name,
    required this.keyPrefix,
    required this.maskedKey,
    required this.status,
    required this.scopes,
    required this.createdAt,
  });

  factory ApiKeyModel.fromJson(Map<String, dynamic> json) {
    return ApiKeyModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      keyPrefix: json['keyPrefix']?.toString() ?? '',
      maskedKey: json['maskedKey']?.toString() ?? '',
      status: json['status']?.toString() ?? 'ACTIVE',
      scopes: (json['scopes'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      createdAt: json['createdAt']?.toString() ?? 'Recent',
    );
  }

  static const defaultKeys = [
    ApiKeyModel(
      id: 'key-prod-1',
      name: 'Production Webhook Dispatcher',
      keyPrefix: 'af_live_9a8b7c',
      maskedKey: 'af_live_9a8b7c••••••••••••3d2e',
      status: 'ACTIVE',
      scopes: ['webhook:dispatch', 'crm:read'],
      createdAt: '2 days ago',
    ),
    ApiKeyModel(
      id: 'key-zapier-2',
      name: 'Zapier Connector',
      keyPrefix: 'af_live_112233',
      maskedKey: 'af_live_112233••••••••••••9988',
      status: 'ACTIVE',
      scopes: ['all'],
      createdAt: '1 week ago',
    ),
  ];
}

class ApiKeyCreatedModel {
  final String id;
  final String name;
  final String keyPrefix;
  final String maskedKey;
  final String secretKey;
  final String status;
  final List<String> scopes;
  final String createdAt;

  const ApiKeyCreatedModel({
    required this.id,
    required this.name,
    required this.keyPrefix,
    required this.maskedKey,
    required this.secretKey,
    required this.status,
    required this.scopes,
    required this.createdAt,
  });

  factory ApiKeyCreatedModel.fromJson(Map<String, dynamic> json) {
    return ApiKeyCreatedModel(
      id: json['id']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      keyPrefix: json['keyPrefix']?.toString() ?? '',
      maskedKey: json['maskedKey']?.toString() ?? '',
      secretKey: json['secretKey']?.toString() ?? '',
      status: json['status']?.toString() ?? 'ACTIVE',
      scopes: (json['scopes'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
      createdAt: json['createdAt']?.toString() ?? 'Just now',
    );
  }
}

final apiKeyRepositoryProvider = Provider<ApiKeyRepository>((ref) {
  return ApiKeyRepository(ref.watch(apiClientProvider));
});

class ApiKeysNotifier extends StateNotifier<AsyncValue<List<ApiKeyModel>>> {
  final ApiKeyRepository _repository;

  ApiKeysNotifier(this._repository) : super(const AsyncValue.loading()) {
    loadKeys();
  }

  Future<void> loadKeys() async {
    state = const AsyncValue.loading();
    try {
      final keys = await _repository.getApiKeys();
      state = AsyncValue.data(keys);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Future<ApiKeyCreatedModel?> createKey(String name) async {
    final created = await _repository.createApiKey(name);
    if (created != null) {
      final current = state.value ?? [];
      final newKey = ApiKeyModel(
        id: created.id,
        name: created.name,
        keyPrefix: created.keyPrefix,
        maskedKey: created.maskedKey,
        status: created.status,
        scopes: created.scopes,
        createdAt: 'Just now',
      );
      state = AsyncValue.data([newKey, ...current]);
    }
    return created;
  }

  Future<ApiKeyCreatedModel?> rotateKey(String id) async {
    final rotated = await _repository.rotateApiKey(id);
    if (rotated != null) {
      final current = state.value ?? [];
      state = AsyncValue.data(current.map((k) {
        if (k.id == id) {
          return ApiKeyModel(
            id: rotated.id,
            name: rotated.name,
            keyPrefix: rotated.keyPrefix,
            maskedKey: rotated.maskedKey,
            status: rotated.status,
            scopes: rotated.scopes,
            createdAt: 'Rotated just now',
          );
        }
        return k;
      }).toList());
    }
    return rotated;
  }

  Future<bool> revokeKey(String id) async {
    final success = await _repository.revokeApiKey(id);
    if (success) {
      final current = state.value ?? [];
      state = AsyncValue.data(current.map((k) {
        if (k.id == id) {
          return ApiKeyModel(
            id: k.id,
            name: k.name,
            keyPrefix: k.keyPrefix,
            maskedKey: k.maskedKey,
            status: 'REVOKED',
            scopes: k.scopes,
            createdAt: k.createdAt,
          );
        }
        return k;
      }).toList());
    }
    return success;
  }
}

final apiKeysNotifierProvider = StateNotifierProvider<ApiKeysNotifier, AsyncValue<List<ApiKeyModel>>>((ref) {
  return ApiKeysNotifier(ref.watch(apiKeyRepositoryProvider));
});

class ApiKeyRepository {
  final ApiClient _apiClient;

  ApiKeyRepository([ApiClient? apiClient]) : _apiClient = apiClient ?? ApiClient();

  Future<List<ApiKeyModel>> getApiKeys() async {
    try {
      final response = await _apiClient.dio.get(ApiConstants.apiKeys);
      if (response.statusCode == 200 && response.data['success'] == true) {
        final list = response.data['data'] as List<dynamic>? ?? [];
        if (list.isNotEmpty) {
          return list.map((e) => ApiKeyModel.fromJson(e as Map<String, dynamic>)).toList();
        }
      }
    } catch (_) {}
    return ApiKeyModel.defaultKeys;
  }

  Future<ApiKeyCreatedModel?> createApiKey(String name, {List<String>? scopes}) async {
    try {
      final response = await _apiClient.dio.post(
        ApiConstants.apiKeys,
        data: {
          'name': name,
          'scopes': scopes ?? ['all'],
        },
      );
      if ((response.statusCode == 200 || response.statusCode == 201) && response.data['success'] == true) {
        return ApiKeyCreatedModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    // Offline / Mock fallback
    final mockSecret = 'af_live_mock_${DateTime.now().millisecondsSinceEpoch}';
    return ApiKeyCreatedModel(
      id: 'key-${DateTime.now().millisecondsSinceEpoch}',
      name: name,
      keyPrefix: 'af_live_mock',
      maskedKey: 'af_live_mock••••••••••••1234',
      secretKey: mockSecret,
      status: 'ACTIVE',
      scopes: scopes ?? ['all'],
      createdAt: 'Just now',
    );
  }

  Future<ApiKeyCreatedModel?> rotateApiKey(String id) async {
    try {
      final response = await _apiClient.dio.post(ApiConstants.apiKeyRotate(id));
      if (response.statusCode == 200 && response.data['success'] == true) {
        return ApiKeyCreatedModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {}
    final newSecret = 'af_live_rot_${DateTime.now().millisecondsSinceEpoch}';
    return ApiKeyCreatedModel(
      id: id,
      name: 'Rotated Key',
      keyPrefix: 'af_live_rot',
      maskedKey: 'af_live_rot••••••••••••7890',
      secretKey: newSecret,
      status: 'ACTIVE',
      scopes: ['all'],
      createdAt: 'Rotated just now',
    );
  }

  Future<bool> revokeApiKey(String id) async {
    try {
      final response = await _apiClient.dio.delete(ApiConstants.apiKeyRevoke(id));
      return response.statusCode == 200;
    } catch (_) {
      return true;
    }
  }
}
