import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_theme.dart';
import '../auth/presentation/auth_controller.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  int _selectedNavIndex = 0;

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateNotifierProvider);
    final user = authState.user;
    final isWideScreen = MediaQuery.of(context).size.width >= 1024;

    return Scaffold(
      body: Row(
        children: [
          // Sidebar for Web / Desktop
          if (isWideScreen)
            NavigationRail(
              backgroundColor: AppTheme.surfaceDark,
              selectedIndex: _selectedNavIndex,
              onDestinationSelected: (idx) => setState(() => _selectedNavIndex = idx),
              labelType: NavigationRailLabelType.all,
              leading: Padding(
                padding: const EdgeInsets.symmetric(vertical: 20),
                child: Row(
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
                    const Text('AutoFlow', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                  ],
                ),
              ),
              destinations: const [
                NavigationRailDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: Text('Overview')),
                NavigationRailDestination(icon: Icon(Icons.schema_outlined), selectedIcon: Icon(Icons.schema), label: Text('Automations')),
                NavigationRailDestination(icon: Icon(Icons.inbox_outlined), selectedIcon: Icon(Icons.inbox), label: Text('Inbox')),
                NavigationRailDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: Text('Contacts')),
                NavigationRailDestination(icon: Icon(Icons.discount_outlined), selectedIcon: Icon(Icons.discount), label: Text('Referrals')),
                NavigationRailDestination(icon: Icon(Icons.credit_card_outlined), selectedIcon: Icon(Icons.credit_card), label: Text('Billing')),
              ],
            ),

          // Main Dashboard Content
          Expanded(
            child: Column(
              children: [
                // Top Header Bar
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                  decoration: const BoxDecoration(
                    color: AppTheme.surfaceDark,
                    border: Border(bottom: BorderSide(color: AppTheme.borderDark)),
                  ),
                  child: Row(
                    children: [
                      // Active Workspace Badge
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

                      // New Automation Action Button
                      ElevatedButton.icon(
                        onPressed: () {},
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('New Automation'),
                      ),
                      const SizedBox(width: 16),

                      // User Menu & Logout
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
                          if (val == 'logout') {
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

                // Dashboard Scrollable Area
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Welcome back, ${user?.firstName ?? "Creator"} 👋',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Here is your social automation performance over the last 30 days.',
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                        const SizedBox(height: 24),

                        // Metric Cards Grid
                        LayoutBuilder(
                          builder: (context, constraints) {
                            int crossAxisCount = constraints.maxWidth > 800 ? 4 : (constraints.maxWidth > 500 ? 2 : 1);
                            return GridView.count(
                              crossAxisCount: crossAxisCount,
                              crossAxisSpacing: 16,
                              mainAxisSpacing: 16,
                              shrinkWrap: true,
                              physics: const NeverScrollableScrollPhysics(),
                              childAspectRatio: 1.7,
                              children: const [
                                _MetricCard(title: 'Comments Processed', value: '1,428', icon: Icons.comment_outlined, change: '+18.4%'),
                                _MetricCard(title: 'DMs Dispatched', value: '1,392', icon: Icons.send_outlined, change: '+22.1%'),
                                _MetricCard(title: 'Leads Captured', value: '384', icon: Icons.person_add_alt_outlined, change: '+9.2%'),
                                _MetricCard(title: 'Active Workflows', value: '4 / 20', icon: Icons.bolt_outlined, change: 'Pro Plan'),
                              ],
                            );
                          },
                        ),
                        const SizedBox(height: 32),

                        // Channel Connection Status Banner
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(20),
                            child: Row(
                              children: [
                                const Icon(Icons.hub_outlined, color: AppTheme.primaryLight, size: 28),
                                const SizedBox(width: 16),
                                const Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text('Connected Social Channels', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                                      SizedBox(height: 4),
                                      Text('Connect Instagram or WhatsApp to start triggering automated workflows.', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                                    ],
                                  ),
                                ),
                                OutlinedButton.icon(
                                  onPressed: () {},
                                  icon: const Icon(Icons.add_link, size: 18),
                                  label: const Text('Connect Account'),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: isWideScreen
          ? null
          : BottomNavigationBar(
              currentIndex: _selectedNavIndex,
              onTap: (idx) => setState(() => _selectedNavIndex = idx),
              backgroundColor: AppTheme.surfaceDark,
              selectedItemColor: AppTheme.primaryLight,
              unselectedItemColor: AppTheme.textMuted,
              type: BottomNavigationBarType.fixed,
              items: const [
                BottomNavigationBarItem(icon: Icon(Icons.dashboard_outlined), label: 'Overview'),
                BottomNavigationBarItem(icon: Icon(Icons.schema_outlined), label: 'Automations'),
                BottomNavigationBarItem(icon: Icon(Icons.inbox_outlined), label: 'Inbox'),
                BottomNavigationBarItem(icon: Icon(Icons.people_outline), label: 'Contacts'),
              ],
            ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  final String title;
  final String value;
  final IconData icon;
  final String change;

  const _MetricCard({
    required this.title,
    required this.value,
    required this.icon,
    required this.change,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(title, style: const TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                Icon(icon, size: 18, color: AppTheme.primaryLight),
              ],
            ),
            Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 24)),
            Text(change, style: const TextStyle(color: AppTheme.success, fontSize: 12, fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }
}
