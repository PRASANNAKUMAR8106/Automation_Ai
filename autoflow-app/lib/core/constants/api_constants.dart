class ApiConstants {
  ApiConstants._();

  // For Android Emulator use 10.0.2.2, for Web / iOS simulator / Desktop use localhost
  static const String defaultBaseUrl = 'http://localhost:8080';

  static String baseUrl = const String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: defaultBaseUrl,
  );

  // Auth Endpoints
  static const String register = '/api/v1/auth/register';
  static const String login = '/api/v1/auth/login';
  static const String refreshToken = '/api/v1/auth/refresh';
  static const String logout = '/api/v1/auth/logout';
  static const String me = '/api/v1/auth/me';
  static const String switchOrg = '/api/v1/auth/switch-org';

  // System
  static const String health = '/api/v1/health';

  // Timeouts
  static const Duration connectTimeout = Duration(seconds: 15);
  static const Duration receiveTimeout = Duration(seconds: 15);
}
