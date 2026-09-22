import 'package:dio/dio.dart';
import '../../core/constants/api_constants.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/secure_storage_service.dart';
import '../domain/user_model.dart';

class AuthRepository {
  final ApiClient _apiClient;
  final SecureStorageService _storage;

  AuthRepository([ApiClient? apiClient, SecureStorageService? storage])
      : _apiClient = apiClient ?? ApiClient(),
        _storage = storage ?? SecureStorageService();

  Future<UserModel> register({
    required String email,
    required String password,
    required String firstName,
    String? lastName,
    required String organizationName,
    String? referralCode,
  }) async {
    final response = await _apiClient.dio.post(
      ApiConstants.register,
      data: {
        'email': email,
        'password': password,
        'firstName': firstName,
        'lastName': lastName,
        'organizationName': organizationName,
        'referralCode': referralCode,
      },
    );

    final data = response.data['data'];
    final accessToken = data['accessToken'] as String;
    final refreshToken = data['refreshToken'] as String;
    final user = UserModel.fromJson(data['user'] as Map<String, dynamic>);

    await _storage.saveTokens(
      accessToken: accessToken,
      refreshToken: refreshToken,
    );

    if (user.activeOrganizationId != null) {
      await _storage.saveActiveTenantId(user.activeOrganizationId!);
    }

    return user;
  }

  Future<UserModel> login({
    required String email,
    required String password,
  }) async {
    final response = await _apiClient.dio.post(
      ApiConstants.login,
      data: {
        'email': email,
        'password': password,
      },
    );

    final data = response.data['data'];
    final accessToken = data['accessToken'] as String;
    final refreshToken = data['refreshToken'] as String;
    final user = UserModel.fromJson(data['user'] as Map<String, dynamic>);

    await _storage.saveTokens(
      accessToken: accessToken,
      refreshToken: refreshToken,
    );

    if (user.activeOrganizationId != null) {
      await _storage.saveActiveTenantId(user.activeOrganizationId!);
    }

    return user;
  }

  Future<UserModel?> getCurrentUser() async {
    final token = await _storage.getAccessToken();
    if (token == null) return null;

    try {
      final response = await _apiClient.dio.get(ApiConstants.me);
      if (response.data['success'] == true) {
        return UserModel.fromJson(response.data['data'] as Map<String, dynamic>);
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  Future<void> logout() async {
    try {
      final refreshToken = await _storage.getRefreshToken();
      if (refreshToken != null) {
        await _apiClient.dio.post(
          ApiConstants.logout,
          data: {'refreshToken': refreshToken},
        );
      }
    } catch (_) {
      // Ignore network errors during logout
    } finally {
      await _storage.clearAll();
    }
  }
}
