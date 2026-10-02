import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_theme.dart';
import '../../crm/data/agent_productivity_repository.dart';

class MacroRunnerDialog extends ConsumerStatefulWidget {
  final String conversationId;
  final VoidCallback onMacroApplied;

  const MacroRunnerDialog({
    super.key,
    required this.conversationId,
    required this.onMacroApplied,
  });

  @override
  ConsumerState<MacroRunnerDialog> createState() => _MacroRunnerDialogState();
}

class _MacroRunnerDialogState extends ConsumerState<MacroRunnerDialog> {
  bool _isExecuting = false;
  String? _executionStatus;

  @override
  Widget build(BuildContext context) {
    final macrosAsync = ref.watch(crmMacrosProvider);

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      backgroundColor: AppTheme.surfaceDark,
      child: Container(
        width: 580,
        height: 480,
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: Colors.amber.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.bolt_rounded, color: Colors.amber, size: 24),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Execute Multi-Action Macro',
                        style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        'Atomically run reply, tag, priority, and resolution bundles in 1 click',
                        style: TextStyle(color: AppTheme.textMuted, fontSize: 12),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close, color: AppTheme.textMuted),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_executionStatus != null)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.green.withValues(alpha: 0.15),
                  border: Border.all(color: Colors.green.withValues(alpha: 0.4)),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.check_circle_rounded, color: Colors.green, size: 18),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _executionStatus!,
                        style: const TextStyle(color: Colors.green, fontSize: 12, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: macrosAsync.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (err, _) => Center(child: Text('Error loading macros: $err', style: const TextStyle(color: Colors.redAccent))),
                data: (macros) {
                  if (macros.isEmpty) {
                    return const Center(
                      child: Text('No macros configured for this workspace', style: TextStyle(color: AppTheme.textMuted)),
                    );
                  }

                  return ListView.separated(
                    itemCount: macros.length,
                    separatorBuilder: (context, index) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      final macro = macros[index];
                      return Container(
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppTheme.cardDark,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppTheme.cardDark),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    macro.name,
                                    style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold),
                                  ),
                                ),
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.amber.shade700,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                    textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
                                  ),
                                  onPressed: _isExecuting ? null : () async {
                                    final navigator = Navigator.of(context);
                                    setState(() => _isExecuting = true);
                                    final repo = ref.read(agentProductivityRepositoryProvider);
                                    final result = await repo.applyMacro(widget.conversationId, macro.id);
                                    if (mounted) {
                                      setState(() {
                                        _isExecuting = false;
                                        _executionStatus = 'Executed ${result.actionsExecuted.length} actions successfully.';
                                      });
                                    }
                                    widget.onMacroApplied();
                                    Future.delayed(const Duration(milliseconds: 800), () {
                                      if (mounted) navigator.pop();
                                    });
                                  },
                                  icon: const Icon(Icons.play_arrow_rounded, size: 16),
                                  label: const Text('Apply Macro'),
                                ),
                              ],
                            ),
                            if (macro.description != null && macro.description!.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              Text(
                                macro.description!,
                                style: const TextStyle(color: AppTheme.textMuted, fontSize: 12),
                              ),
                            ],
                          ],
                        ),
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
