import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_theme.dart';
import '../../domain/ai_media_repository.dart';

class AiMediaGeneratorDialog extends ConsumerStatefulWidget {
  final String? initialPrompt;
  final ValueChanged<String>? onAssetGenerated;

  const AiMediaGeneratorDialog({
    super.key,
    this.initialPrompt,
    this.onAssetGenerated,
  });

  @override
  ConsumerState<AiMediaGeneratorDialog> createState() => _AiMediaGeneratorDialogState();
}

class _AiMediaGeneratorDialogState extends ConsumerState<AiMediaGeneratorDialog> {
  late TextEditingController _promptController;
  late TextEditingController _headlineController;
  late TextEditingController _subtextController;
  late TextEditingController _badgeController;

  String _selectedTemplate = 'COUPON_CARD';
  String _selectedColor = '#6366F1';
  bool _isGenerating = false;
  String? _generatedUrl;

  final List<Map<String, String>> _templates = [
    {'id': 'COUPON_CARD', 'label': 'Coupon Voucher'},
    {'id': 'LEAD_MAGNET_COVER', 'label': 'Lead Magnet Cover'},
    {'id': 'PROMO_BANNER', 'label': 'Promo Banner'},
    {'id': 'CERTIFICATE', 'label': 'VIP Certificate'},
  ];

  final List<Map<String, dynamic>> _colorPresets = [
    {'hex': '#6366F1', 'color': Color(0xFF6366F1), 'name': 'Indigo'},
    {'hex': '#10B981', 'color': Color(0xFF10B981), 'name': 'Emerald'},
    {'hex': '#F43F5E', 'color': Color(0xFFF43F5E), 'name': 'Rose'},
    {'hex': '#F59E0B', 'color': Color(0xFFF59E0B), 'name': 'Amber'},
    {'hex': '#06B6D4', 'color': Color(0xFF06B6D4), 'name': 'Cyan'},
  ];

  @override
  void initState() {
    super.initState();
    _promptController = TextEditingController(text: widget.initialPrompt ?? 'Exclusive 25% Off Summer Deal');
    _headlineController = TextEditingController(text: 'Special VIP Offer for {{username}}');
    _subtextController = TextEditingController(text: 'Unlock instant access by presenting this card.');
    _badgeController = TextEditingController(text: 'LIMITED PERK');
  }

  @override
  void dispose() {
    _promptController.dispose();
    _headlineController.dispose();
    _subtextController.dispose();
    _badgeController.dispose();
    super.dispose();
  }

  Color _parseHex(String hex) {
    try {
      final clean = hex.replaceAll('#', '');
      return Color(int.parse('FF$clean', radix: 16));
    } catch (_) {
      return const Color(0xFF6366F1);
    }
  }

  Future<void> _handleGenerate() async {
    setState(() => _isGenerating = true);
    try {
      final repo = ref.read(aiMediaRepositoryProvider);
      final asset = await repo.generateBrandedAsset(
        AiMediaGenerateOptions(
          templateType: _selectedTemplate,
          prompt: _promptController.text.trim(),
          headline: _headlineController.text.trim(),
          subtext: _subtextController.text.trim(),
          badgeText: _badgeController.text.trim(),
          accentColor: _selectedColor,
        ),
      );

      setState(() {
        _isGenerating = false;
        _generatedUrl = asset.downloadUrl;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('AI branded asset synthesized and stored in S3!'),
            backgroundColor: AppTheme.success,
            behavior: SnackBarBehavior.floating,
          ),
        );
      }

      widget.onAssetGenerated?.call(asset.downloadUrl);
    } catch (e) {
      if (mounted) {
        setState(() => _isGenerating = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Generation failed: $e'),
            backgroundColor: AppTheme.error,
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final accent = _parseHex(_selectedColor);

    return Dialog(
      backgroundColor: AppTheme.cardDark,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppTheme.borderDark),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 820, maxHeight: 850),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: AppTheme.primary.withValues(alpha: 0.15),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: const Icon(Icons.auto_awesome, color: AppTheme.primaryLight, size: 22),
                        ),
                        const SizedBox(width: 12),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('AI Branded Media Studio', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: AppTheme.textLight)),
                              SizedBox(height: 2),
                              Text('Synthesize high-resolution promotional graphics & coupons', style: TextStyle(color: AppTheme.textMuted, fontSize: 13), overflow: TextOverflow.ellipsis),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: AppTheme.textMuted),
                    onPressed: () => Navigator.of(context).pop(_generatedUrl),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Two-column layout: Configuration on left, Live preview on right
              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final isWide = constraints.maxWidth > 650;
                    return SingleChildScrollView(
                      child: isWide
                          ? Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Expanded(flex: 5, child: _buildForm(accent)),
                                const SizedBox(width: 24),
                                Expanded(flex: 5, child: _buildPreview(accent)),
                              ],
                            )
                          : Column(
                              children: [
                                _buildPreview(accent),
                                const SizedBox(height: 20),
                                _buildForm(accent),
                              ],
                            ),
                    );
                  },
                ),
              ),

              const SizedBox(height: 16),
              // Footer CTA Buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton(
                    onPressed: () => Navigator.of(context).pop(_generatedUrl),
                    child: Text(_generatedUrl != null ? 'Done' : 'Cancel'),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton.icon(
                    onPressed: _isGenerating ? null : _handleGenerate,
                    icon: _isGenerating
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.auto_awesome, size: 16),
                    label: Text(_isGenerating ? 'Synthesizing...' : 'Synthesize Asset (PNG)'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildForm(Color accent) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Template Style', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 6),
        DropdownButtonFormField<String>(
          initialValue: _selectedTemplate,
          decoration: const InputDecoration(contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 10)),
          dropdownColor: AppTheme.cardDark,
          items: _templates.map((t) => DropdownMenuItem(value: t['id'], child: Text(t['label']!))).toList(),
          onChanged: (v) => setState(() => _selectedTemplate = v ?? 'COUPON_CARD'),
        ),
        const SizedBox(height: 14),

        const Text('Headline (Dynamic token: {{username}})', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 6),
        TextField(
          controller: _headlineController,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(hintText: 'Special VIP Offer for {{username}}'),
        ),
        const SizedBox(height: 14),

        const Text('Subtext / Incentive', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 6),
        TextField(
          controller: _subtextController,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(hintText: 'Unlock instant access by presenting this card.'),
        ),
        const SizedBox(height: 14),

        const Text('Badge / Tagline', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 6),
        TextField(
          controller: _badgeController,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(hintText: 'LIMITED PERK'),
        ),
        const SizedBox(height: 14),

        const Text('Accent Theme Color', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 8),
        Row(
          children: _colorPresets.map((preset) {
            final isSelected = _selectedColor == preset['hex'];
            return GestureDetector(
              onTap: () => setState(() => _selectedColor = preset['hex'] as String),
              child: Container(
                margin: const EdgeInsets.only(right: 10),
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: preset['color'] as Color,
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: isSelected ? Colors.white : Colors.transparent,
                    width: 2.5,
                  ),
                  boxShadow: isSelected ? [BoxShadow(color: (preset['color'] as Color).withValues(alpha: 0.5), blurRadius: 8)] : null,
                ),
                child: isSelected ? const Icon(Icons.check, size: 16, color: Colors.white) : null,
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildPreview(Color accent) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Live Visual Preview', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.textLight)),
        const SizedBox(height: 8),
        Container(
          width: double.infinity,
          height: 220,
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF0F172A), Color(0xFF181830)],
            ),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppTheme.borderDark),
            boxShadow: const [BoxShadow(color: Colors.black45, blurRadius: 12, offset: Offset(0, 4))],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: accent.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: accent),
                    ),
                    child: Text(
                      '⚡ ${_badgeController.text.toUpperCase()}',
                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: accent),
                    ),
                  ),
                  const Text('AUTOFLOW AI', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppTheme.textMuted)),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _headlineController.text.isNotEmpty ? _headlineController.text : 'VIP Offer',
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _subtextController.text.isNotEmpty ? _subtextController.text : 'Claim now',
                    style: const TextStyle(fontSize: 11, color: Color(0xFFCBD5E1)),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: const Color(0xFF0F172A).withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: accent.withValues(alpha: 0.6)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('CODE: AUTOFLOW-VIP', style: TextStyle(fontSize: 11, fontFamily: 'monospace', fontWeight: FontWeight.bold, color: Colors.white)),
                    Icon(Icons.qr_code_2, size: 16, color: accent),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (_generatedUrl != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppTheme.success.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppTheme.success.withValues(alpha: 0.3)),
            ),
            child: Row(
              children: [
                const Icon(Icons.check_circle, size: 16, color: AppTheme.success),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Asset URL ready: $_generatedUrl',
                    style: const TextStyle(fontSize: 11, color: AppTheme.textLight),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }
}
