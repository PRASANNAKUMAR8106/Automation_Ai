import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/auth_repository.dart';
import '../domain/user_model.dart';

class AuthState {
  final bool isLoading;
  final bool isAuthenticated;
  final UserModel? user;
  final String? errorMessage;

  const AuthState({
    this.isLoading = false,
    this.isAuthenticated = false,
    this.user,
    this.errorMessage,
  });

  AuthState copyWith({
    bool? isLoading,
    bool? isAuthenticated,
    UserModel? user,
    String? errorMessage,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      isAuthenticated: isAuthenticated ?? this.isAuthenticated,
      user: user ?? this.user,
      errorMessage: errorMessage,
    );
  }
}

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;

  AuthNotifier(this._repository) : super(const AuthState()) {
    checkAuthStatus();
  }

  Future<void> checkAuthStatus() async {
    state = state.copyWith(isLoading: true);
    try {
      final user = await _repository.getCurrentUser();
      if (user != null) {
        state = AuthState(isAuthenticated: true, user: user);
      } else {
        state = const AuthState(isAuthenticated: false);
      }
    } catch (_) {
      state = const AuthState(isAuthenticated: false);
    }
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final user = await _repository.login(email: email, password: password);
      state = AuthState(isAuthenticated: true, user: user);
      return true;
    } on DioException catch (e) {
      final msg = e.response?.data?['error']?['message'] ?? 'Login failed. Check your credentials.';
      state = state.copyWith(isLoading: false, errorMessage: msg.toString());
      return false;
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
      return false;
    }
  }

  Future<bool> register({
    required String email,
    required String password,
    required String firstName,
    String? lastName,
    required String organizationName,
    String? referralCode,
  }) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final user = await _repository.register(
        email: email,
        password: password,
        firstName: firstName,
        lastName: lastName,
        organizationName: organizationName,
        referralCode: referralCode,
      );
      state = AuthState(isAuthenticated: true, user: user);
      return true;
    } on DioException catch (e) {
      final msg = e.response?.data?['error']?['message'] ?? 'Registration failed.';
      state = state.copyWith(isLoading: false, errorMessage: msg.toString());
      return false;
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
      return false;
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    state = const AuthState(isAuthenticated: false);
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) => AuthRepository());

final authStateNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repo = ref.watch(authRepositoryProvider);
  return AuthNotifier(repo);
});
