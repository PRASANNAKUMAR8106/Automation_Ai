import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../features/auth/presentation/auth_controller.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/auth/presentation/register_screen.dart';
import '../features/billing/presentation/billing_screen.dart';
import '../features/contacts_crm/presentation/contacts_screen.dart';
import '../features/dashboard/presentation/app_shell.dart';
import '../features/dashboard/presentation/dashboard_screen.dart';
import '../features/inbox/presentation/inbox_screen.dart';
import '../features/influencer/presentation/influencer_dashboard_screen.dart';
import '../features/workflows/presentation/workflow_list_screen.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/dashboard',
    refreshListenable: _AuthStateListenable(ref),
    redirect: (context, state) {
      final authState = ref.read(authStateNotifierProvider);
      final isAuth = authState.isAuthenticated;
      final isLoggingIn = state.matchedLocation == '/login';
      final isRegistering = state.matchedLocation.startsWith('/register');
      final isReferral = state.matchedLocation.startsWith('/r/');

      if (isReferral) {
        final code = state.pathParameters['referralCode'];
        return '/register?ref=$code';
      }

      if (!isAuth && !isLoggingIn && !isRegistering) {
        return '/login';
      }

      if (isAuth && (isLoggingIn || isRegistering)) {
        return '/dashboard';
      }

      return null;
    },
    routes: [
      // Public Auth Routes
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) {
          final refCode = state.uri.queryParameters['ref'];
          return RegisterScreen(initialReferralCode: refCode);
        },
      ),
      GoRoute(
        path: '/r/:referralCode',
        builder: (context, state) {
          final refCode = state.pathParameters['referralCode'];
          return RegisterScreen(initialReferralCode: refCode);
        },
      ),

      // Protected Dashboard Shell Routes
      ShellRoute(
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(
            path: '/dashboard',
            builder: (context, state) => const DashboardScreen(),
          ),
          GoRoute(
            path: '/workflows',
            builder: (context, state) => const WorkflowListScreen(),
          ),
          GoRoute(
            path: '/inbox',
            builder: (context, state) => const InboxScreen(),
          ),
          GoRoute(
            path: '/contacts',
            builder: (context, state) => const ContactsScreen(),
          ),
          GoRoute(
            path: '/influencer',
            builder: (context, state) => const InfluencerDashboardScreen(),
          ),
          GoRoute(
            path: '/billing',
            builder: (context, state) => const BillingScreen(),
          ),
        ],
      ),
    ],
  );
});

class _AuthStateListenable extends ChangeNotifier {
  _AuthStateListenable(Ref ref) {
    ref.listen(authStateNotifierProvider, (previous, current) => notifyListeners());
  }
}
