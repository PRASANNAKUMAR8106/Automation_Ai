import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/presentation/auth_controller.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authStateNotifierProvider);
    final user = authState.user;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Welcome Header
            Text(
              'Welcome back, ${user?.firstName ?? "Creator"} 👋',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 6),
            const Text(
              'Here is your social automation performance over the last 30 days.',
              style: TextStyle(color: AppTheme.textMuted, fontSize: 14),
            ),
            const SizedBox(height: 24),

            // Key Metric Cards Grid
            LayoutBuilder(
              builder: (context, constraints) {
                int crossAxisCount = constraints.maxWidth > 900 ? 4 : (constraints.maxWidth > 550 ? 2 : 1);
                return GridView.count(
                  crossAxisCount: crossAxisCount,
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  childAspectRatio: 1.8,
                  children: const [
                    _MetricCard(title: 'Comments Processed', value: '1,428', icon: Icons.comment_outlined, change: '+18.4% this week'),
                    _MetricCard(title: 'DMs Dispatched', value: '1,392', icon: Icons.send_outlined, change: '+22.1% this week'),
                    _MetricCard(title: 'Leads Captured', value: '384', icon: Icons.person_add_alt_outlined, change: '+9.2% this week'),
                    _MetricCard(title: 'Active Workflows', value: '4 / 20', icon: Icons.bolt_outlined, change: 'Pro Plan Quota'),
                  ],
                );
              },
            ),
            const SizedBox(height: 32),

            // Channel Integration Status Banner
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppTheme.primary.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.hub_outlined, color: AppTheme.primaryLight, size: 28),
                    ),
                    const SizedBox(width: 16),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Connected Social Channels', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                          SizedBox(height: 4),
                          Text(
                            'Connect your official Instagram Professional or WhatsApp Business account to activate automated triggers.',
                            style: TextStyle(color: AppTheme.textMuted, fontSize: 13),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 16),
                    OutlinedButton.icon(
                      onPressed: () {},
                      icon: const Icon(Icons.add_link, size: 18),
                      label: const Text('Connect Channel'),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
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
