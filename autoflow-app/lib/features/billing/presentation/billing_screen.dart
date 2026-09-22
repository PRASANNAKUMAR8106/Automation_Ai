import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';

class BillingScreen extends StatelessWidget {
  const BillingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Subscription & Entitlements', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    const Text('Manage your subscription plan, resource limits, and invoices.', style: TextStyle(color: AppTheme.textMuted, fontSize: 14)),
                  ],
                ),
                OutlinedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.receipt_long, size: 18),
                  label: const Text('Download Invoices'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Active Plan Overview Card
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.primary.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.workspace_premium, color: AppTheme.primaryLight, size: 36),
                    ),
                    const SizedBox(width: 20),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Text('Pro Plan', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
                              SizedBox(width: 10),
                              Chip(
                                label: Text('ACTIVE', style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: AppTheme.success)),
                                backgroundColor: Color(0x2010B981),
                                padding: EdgeInsets.zero,
                              ),
                            ],
                          ),
                          SizedBox(height: 4),
                          Text('₹1,499.00 / month • Renews on 22 October 2026', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
                        ],
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () {},
                      child: const Text('Change Plan'),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Resource Usage Entitlement Meters
            const Text('Current Monthly Quota Usage', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 16),
            GridView.count(
              crossAxisCount: 3,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 2.2,
              children: const [
                _QuotaCard(name: 'Active Automations', used: 4, limit: 20, unit: 'workflows'),
                _QuotaCard(name: 'Audience Contacts', used: 1428, limit: 10000, unit: 'contacts'),
                _QuotaCard(name: 'Monthly Messages', used: 18340, limit: 50000, unit: 'messages'),
                _QuotaCard(name: 'AI Smart Requests', used: 840, limit: 5000, unit: 'calls'),
                _QuotaCard(name: 'Media Storage', used: 1200, limit: 5000, unit: 'MB'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _QuotaCard extends StatelessWidget {
  final String name;
  final int used;
  final int limit;
  final String unit;

  const _QuotaCard({
    required this.name,
    required this.used,
    required this.limit,
    required this.unit,
  });

  @override
  Widget build(BuildContext context) {
    final double percent = (used / limit).clamp(0.0, 1.0);

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
                Text(name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                Text('$used / $limit $unit', style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
              ],
            ),
            const SizedBox(height: 10),
            LinearProgressIndicator(
              value: percent,
              backgroundColor: AppTheme.surfaceDark,
              color: percent > 0.85 ? AppTheme.warning : AppTheme.primaryLight,
              minHeight: 6,
              borderRadius: BorderRadius.circular(4),
            ),
            const SizedBox(height: 4),
            Text('${(percent * 100).toStringAsFixed(1)}% consumed', style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
          ],
        ),
      ),
    );
  }
}
