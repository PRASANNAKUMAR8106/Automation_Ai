import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../data/influencer_repository.dart';

class InfluencerDashboardScreen extends ConsumerStatefulWidget {
  const InfluencerDashboardScreen({super.key});

  @override
  ConsumerState<InfluencerDashboardScreen> createState() => _InfluencerDashboardScreenState();
}

class _InfluencerDashboardScreenState extends ConsumerState<InfluencerDashboardScreen> {
  final String _promoCode = 'RAHUL30';
  final String _referralUrl = 'https://autoflow.ai/r/RAHUL30';

  void _copyToClipboard(String text, String label) {
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$label copied to clipboard!'),
        backgroundColor: AppTheme.success,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _requestPayout() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.cardDark,
        title: const Text('Request Commission Payout'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Eligible Payable Balance: ₹4,290.00', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.success)),
            SizedBox(height: 12),
            Text('Payouts are processed via direct NEFT / UPI transfer within 3-5 business days after admin review.', style: TextStyle(color: AppTheme.textMuted, fontSize: 13)),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Payout request submitted successfully! Status: PENDING REVIEW')),
              );
            },
            child: const Text('Submit Request'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final statsAsync = ref.watch(influencerStatsProvider);
    final stats = statsAsync.value ?? InfluencerStatsModel.defaultFallback;
    final promoCode = stats.promoCode.isNotEmpty ? stats.promoCode : _promoCode;
    final referralUrl = stats.referralLink.isNotEmpty ? stats.referralLink : _referralUrl;

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
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Influencer & Referral Portal', style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 4),
                      const Text('Track your referral traffic, earned commissions, and request payouts.', style: TextStyle(color: AppTheme.textMuted, fontSize: 14)),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton.icon(
                  onPressed: _requestPayout,
                  icon: const Icon(Icons.account_balance_wallet_outlined, size: 18),
                  label: const Text('Request Payout'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Promo Code & Shareable Link Banner
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Your Active Promotion Assets', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        // Promo Code Pill
                        Expanded(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              color: AppTheme.surfaceDark,
                              border: Border.all(color: AppTheme.borderDark),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.discount_outlined, color: AppTheme.primaryLight, size: 20),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Text('PROMO CODE (20% OFF FOR FANS)', style: TextStyle(color: AppTheme.textMuted, fontSize: 11)),
                                      Text(promoCode, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, letterSpacing: 1.5), overflow: TextOverflow.ellipsis),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.copy, size: 18),
                                  onPressed: () => _copyToClipboard(promoCode, 'Promo code'),
                                  tooltip: 'Copy Code',
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),

                        // Referral Link Pill
                        Expanded(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                            decoration: BoxDecoration(
                              color: AppTheme.surfaceDark,
                              border: Border.all(color: AppTheme.borderDark),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              children: [
                                const Icon(Icons.link, color: AppTheme.secondary, size: 20),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      const Text('CANONICAL REFERRAL URL', style: TextStyle(color: AppTheme.textMuted, fontSize: 11)),
                                      Text(referralUrl, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14), overflow: TextOverflow.ellipsis),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.copy, size: 18),
                                  onPressed: () => _copyToClipboard(referralUrl, 'Referral link'),
                                  tooltip: 'Copy Link',
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Performance Metrics
            GridView.count(
              crossAxisCount: 4,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 1.8,
              children: [
                _StatCard(title: 'Link Clicks', value: '${stats.totalClicks}', sub: 'All-time referral clicks'),
                _StatCard(title: 'Pending Commission', value: '₹${stats.pendingCommissionInr.toStringAsFixed(2)}', sub: 'In hold period'),
                _StatCard(title: 'Paid Payouts', value: '₹${stats.paidCommissionInr.toStringAsFixed(2)}', sub: 'Disbursed to bank'),
                _StatCard(title: 'Payable Balance', value: '₹${stats.approvedCommissionInr.toStringAsFixed(2)}', sub: 'Eligible for payout', isHighlight: true),
              ],
            ),
            const SizedBox(height: 24),

            // Commission History Table
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Commission Earnings History', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 16),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: DataTable(
                        headingRowColor: WidgetStateProperty.all(AppTheme.surfaceDark),
                        columns: const [
                          DataColumn(label: Text('Date', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Event', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Customer Paid', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Commission (30%)', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Status', style: TextStyle(fontWeight: FontWeight.bold))),
                        ],
                        rows: const [
                          DataRow(cells: [
                            DataCell(Text('22 Sep 2026')),
                            DataCell(Text('Pro Plan Monthly Subscription')),
                            DataCell(Text('₹1,199.20')),
                            DataCell(Text('₹359.76', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.success))),
                            DataCell(Text('PENDING (HOLD)', style: TextStyle(color: AppTheme.warning, fontWeight: FontWeight.bold, fontSize: 11))),
                          ]),
                          DataRow(cells: [
                            DataCell(Text('18 Sep 2026')),
                            DataCell(Text('Starter Plan Annual Subscription')),
                            DataCell(Text('₹4,790.00')),
                            DataCell(Text('₹1,437.00', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.success))),
                            DataCell(Text('PAYABLE', style: TextStyle(color: AppTheme.success, fontWeight: FontWeight.bold, fontSize: 11))),
                          ]),
                          DataRow(cells: [
                            DataCell(Text('12 Sep 2026')),
                            DataCell(Text('Pro Plan Monthly Subscription')),
                            DataCell(Text('₹1,199.20')),
                            DataCell(Text('₹359.76', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.success))),
                            DataCell(Text('PAID', style: TextStyle(color: AppTheme.info, fontWeight: FontWeight.bold, fontSize: 11))),
                          ]),
                        ],
                      ),
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

class _StatCard extends StatelessWidget {
  final String title;
  final String value;
  final String sub;
  final bool isHighlight;

  const _StatCard({
    required this.title,
    required this.value,
    required this.sub,
    this.isHighlight = false,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      color: isHighlight ? AppTheme.primaryDark.withValues(alpha: 0.3) : null,
      shape: RoundedRectangleBorder(
        side: BorderSide(color: isHighlight ? AppTheme.primary : AppTheme.borderDark),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(title, style: const TextStyle(color: AppTheme.textMuted, fontSize: 13)),
            Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 24, color: isHighlight ? AppTheme.success : null)),
            Text(sub, style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
          ],
        ),
      ),
    );
  }
}
