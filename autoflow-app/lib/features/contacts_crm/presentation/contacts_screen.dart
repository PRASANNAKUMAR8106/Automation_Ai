import 'package:flutter/material.dart';
import '../../../core/theme/app_theme.dart';

class ContactsScreen extends StatefulWidget {
  const ContactsScreen({super.key});

  @override
  State<ContactsScreen> createState() => _ContactsScreenState();
}

class _ContactsScreenState extends State<ContactsScreen> {
  final List<Map<String, dynamic>> _contacts = [
    {
      'name': 'Sneha Kapoor',
      'username': '@snehak_designs',
      'channel': 'INSTAGRAM',
      'email': 'sneha@studio.design',
      'status': 'QUALIFIED',
      'tags': ['design-guide', 'warm-lead'],
      'lastActive': '3 mins ago',
    },
    {
      'name': 'Vikram Rathore',
      'username': '+91 98765 43210',
      'channel': 'WHATSAPP',
      'email': 'vikram@enterprise.in',
      'status': 'LEAD',
      'tags': ['demo-request', 'ecommerce'],
      'lastActive': '25 mins ago',
    },
    {
      'name': 'Ananya Roy',
      'username': '@ananya_fitness',
      'channel': 'INSTAGRAM',
      'email': 'ananya@fitnesshub.com',
      'status': 'CUSTOMER',
      'tags': ['coupon-claimed', 'paying-member'],
      'lastActive': '1 hour ago',
    },
    {
      'name': 'Devendra Mehta',
      'username': '@devendra_tech',
      'channel': 'INSTAGRAM',
      'email': 'devendra@techhub.io',
      'status': 'NEW',
      'tags': ['reel-commenter'],
      'lastActive': '3 hours ago',
    },
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Row
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Contacts & CRM Directory', style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 4),
                    const Text('Manage audience leads, captured emails, and interaction tags.', style: TextStyle(color: AppTheme.textMuted, fontSize: 14)),
                  ],
                ),
                OutlinedButton.icon(
                  onPressed: () {},
                  icon: const Icon(Icons.download, size: 18),
                  label: const Text('Export CSV'),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Contacts Table Card
            Expanded(
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SingleChildScrollView(
                      child: DataTable(
                        headingRowColor: WidgetStateProperty.all(AppTheme.surfaceDark),
                        columns: const [
                          DataColumn(label: Text('Contact', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Channel', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Email', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Lead Status', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Tags', style: TextStyle(fontWeight: FontWeight.bold))),
                          DataColumn(label: Text('Last Active', style: TextStyle(fontWeight: FontWeight.bold))),
                        ],
                        rows: _contacts.map((c) {
                          Color statusColor;
                          switch (c['status']) {
                            case 'CUSTOMER':
                              statusColor = AppTheme.success;
                              break;
                            case 'QUALIFIED':
                              statusColor = AppTheme.primaryLight;
                              break;
                            case 'LEAD':
                              statusColor = AppTheme.warning;
                              break;
                            default:
                              statusColor = AppTheme.textMuted;
                          }

                          return DataRow(
                            cells: [
                              DataCell(
                                Row(
                                  children: [
                                    CircleAvatar(
                                      radius: 14,
                                      backgroundColor: AppTheme.primaryDark,
                                      child: Text(
                                        (c['name'] as String)[0],
                                        style: const TextStyle(fontSize: 12, color: Colors.white),
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Text(c['name'] as String, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                                        Text(c['username'] as String, style: const TextStyle(color: AppTheme.textMuted, fontSize: 11)),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                              DataCell(
                                Text(c['channel'] as String, style: const TextStyle(fontSize: 12)),
                              ),
                              DataCell(
                                Text(c['email'] as String, style: const TextStyle(fontSize: 13)),
                              ),
                              DataCell(
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: statusColor.withValues(alpha: 0.12),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    c['status'] as String,
                                    style: TextStyle(color: statusColor, fontWeight: FontWeight.bold, fontSize: 11),
                                  ),
                                ),
                              ),
                              DataCell(
                                Wrap(
                                  spacing: 4,
                                  children: (c['tags'] as List<String>).map((t) {
                                    return Chip(
                                      label: Text(t, style: const TextStyle(fontSize: 10)),
                                      padding: EdgeInsets.zero,
                                      materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                      backgroundColor: AppTheme.surfaceDark,
                                    );
                                  }).toList(),
                                ),
                              ),
                              DataCell(
                                Text(c['lastActive'] as String, style: const TextStyle(color: AppTheme.textMuted, fontSize: 12)),
                              ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
