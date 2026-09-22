import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureStorageService {
  final FlutterSecureStorage _storage;

  static const String _keyAccessToken = 'af_access_token';
  static const String _keyRefreshToken = 'af_refresh_token';
  static const String _keyActiveTenantId = 'af_active_tenant_id';

  SecureStorageService([FlutterSecureStorage? storage])
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
            );

  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _keyAccessToken, value: accessToken);
    await _storage.write(key: _keyRefreshToken, value: refreshToken);
  }

  Future<String?> getAccessToken() async {
    return await _storage.read(key: _keyAccessToken);
  }

  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _keyRefreshToken);
  }

  Future<void> saveActiveTenantId(String tenantId) async {
    await _storage.write(key: _keyActiveTenantId, value: tenantId);
  }

  Future<String?> getActiveTenantId() async {
    return await _storage.read(key: _keyActiveTenantId);
  }

  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
