import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/responsive_layout.dart';
import '../../auth/presentation/auth_controller.dart';

class AppShell extends ConsumerWidget {
  final Widget child;

  const AppShell({super.key, required this.child});

  static int _calculateSelectedIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    if (location.startsWith('/dashboard')) return 0;
    if (location.startsWith('/workflows')) return 1;
    if (location.startsWith('/inbox')) return 2;
    if (location.startsWith('/contacts')) return 3;
    if (location.startsWith('/influencer')) return 4;
    if (location.startsWith('/billing')) return 5;
    if (location.startsWith('/settings')) return 6;
    return 0;
  }

  void _onItemTapped(int index, BuildContext context) {
    switch (index) {
      case 0:
        context.go('/dashboard');
        break;
      case 1:
        context.go('/workflows');
        break;
      case 2:
        context.go('/inbox');
        break;
      case 3:
        context.go('/contacts');
        break;
      case 4:
        context.go('/influencer');
        break;
      case 5:
        context.go('/billing');
        break;
      case 6:
        context.go('/settings');
        break;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final selectedIndex = _calculateSelectedIndex(context);
    final isDesktop = ResponsiveLayout.isDesktop(context);
    final authState = ref.watch(authStateNotifierProvider);
    final user = authState.user;

    return Scaffold(
      body: Row(
        children: [
          // Desktop / Tablet Navigation Rail
          if (isDesktop)
            NavigationRail(
              backgroundColor: AppTheme.surfaceDark,
              selectedIndex: selectedIndex,
              onDestinationSelected: (idx) => _onItemTapped(idx, context),
              labelType: NavigationRailLabelType.all,
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(colors: [AppTheme.primary, AppTheme.secondary]),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: const Icon(Icons.auto_awesome, color: Colors.white, size: 20),
                    ),
                    const SizedBox(width: 8),
                    const Text('AutoFlow', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
                  ],
                ),
              ),
              destinations: const [
                NavigationRailDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: Text('Overview')),
                NavigationRailDestination(icon: Icon(Icons.schema_outlined), selectedIcon: Icon(Icons.schema), label: Text('Workflows')),
                NavigationRailDestination(icon: Icon(Icons.inbox_outlined), selectedIcon: Icon(Icons.inbox), label: Text('Inbox')),
                NavigationRailDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: Text('Contacts')),
                NavigationRailDestination(icon: Icon(Icons.discount_outlined), selectedIcon: Icon(Icons.discount), label: Text('Referrals')),
                NavigationRailDestination(icon: Icon(Icons.credit_card_outlined), selectedIcon: Icon(Icons.credit_card), label: Text('Billing')),
                NavigationRailDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: Text('Settings')),
              ],
            ),

          // Main Screen Area with Persistent Top Header
          Expanded(
            child: Column(
              children: [
                // Top Global Header
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
                  decoration: const BoxDecoration(
                    color: AppTheme.surfaceDark,
                    border: Border(bottom: BorderSide(color: AppTheme.borderDark)),
                  ),
                  child: Row(
                    children: [
                      // Active Workspace Selector Badge
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: AppTheme.cardDark,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: AppTheme.borderDark),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.business, size: 16, color: AppTheme.primaryLight),
                            const SizedBox(width: 8),
                            Text(
                              user?.activeOrganizationName ?? 'My Workspace',
                              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                            ),
                          ],
                        ),
                      ),
                      const Spacer(),

                      // Quick Action CTA
                      ElevatedButton.icon(
                        onPressed: () => context.go('/workflows'),
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('Create Workflow'),
                      ),
                      const SizedBox(width: 16),

                      // User Profile Menu
                      PopupMenuButton<String>(
                        tooltip: 'Account Menu',
                        child: CircleAvatar(
                          radius: 18,
                          backgroundColor: AppTheme.primary,
                          child: Text(
                            (user?.firstName?.isNotEmpty == true) ? user!.firstName![0].toUpperCase() : 'U',
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                          ),
                        ),
                        onSelected: (val) {
                          if (val == 'settings') {
                            context.go('/settings');
                          } else if (val == 'logout') {
                            ref.read(authStateNotifierProvider.notifier).logout();
                            context.go('/login');
                          }
                        },
                        itemBuilder: (context) => [
                          PopupMenuItem(
                            enabled: false,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(user?.displayName ?? '', style: const TextStyle(fontWeight: FontWeight.bold)),
                                Text(user?.email ?? '', style: const TextStyle(fontSize: 12, color: AppTheme.textMuted)),
                              ],
                            ),
                          ),
                          const PopupMenuDivider(),
                          const PopupMenuItem(
                            value: 'settings',
                            child: Row(
                              children: [
                                Icon(Icons.settings_outlined, size: 18),
                                SizedBox(width: 8),
                                Text('Settings'),
                              ],
                            ),
                          ),
                          const PopupMenuItem(
                            value: 'logout',
                            child: Row(
                              children: [
                                Icon(Icons.logout, size: 18, color: AppTheme.error),
                                SizedBox(width: 8),
                                Text('Log Out', style: TextStyle(color: AppTheme.error)),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),

                // Nested Route Child
                Expanded(child: child),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: isDesktop
          ? null
          : BottomNavigationBar(
              currentIndex: selectedIndex,
              onTap: (idx) => _onItemTapped(idx, context),
              backgroundColor: AppTheme.surfaceDark,
              selectedItemColor: AppTheme.primaryLight,
              unselectedItemColor: AppTheme.textMuted,
              type: BottomNavigationBarType.fixed,
              items: const [
                BottomNavigationBarItem(icon: Icon(Icons.dashboard_outlined), label: 'Overview'),
                BottomNavigationBarItem(icon: Icon(Icons.schema_outlined), label: 'Workflows'),
                BottomNavigationBarItem(icon: Icon(Icons.inbox_outlined), label: 'Inbox'),
                BottomNavigationBarItem(icon: Icon(Icons.people_outline), label: 'Contacts'),
                BottomNavigationBarItem(icon: Icon(Icons.discount_outlined), label: 'Referrals'),
                BottomNavigationBarItem(icon: Icon(Icons.credit_card_outlined), label: 'Billing'),
                BottomNavigationBarItem(icon: Icon(Icons.settings_outlined), label: 'Settings'),
              ],
            ),
    );
  }
}
