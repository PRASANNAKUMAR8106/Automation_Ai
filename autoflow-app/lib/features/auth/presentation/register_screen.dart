import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'auth_controller.dart';
import '../../../core/theme/app_theme.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  final String? initialReferralCode;

  const RegisterScreen({super.key, this.initialReferralCode});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _orgNameController = TextEditingController();
  final _referralCodeController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void initState() {
    super.initState();
    if (widget.initialReferralCode != null) {
      _referralCodeController.text = widget.initialReferralCode!;
    }
  }

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _orgNameController.dispose();
    _referralCodeController.dispose();
    super.dispose();
  }

  void _submit() async {
    if (_formKey.currentState?.validate() ?? false) {
      final success = await ref.read(authStateNotifierProvider.notifier).register(
            email: _emailController.text.trim(),
            password: _passwordController.text,
            firstName: _firstNameController.text.trim(),
            lastName: _lastNameController.text.trim().isNotEmpty ? _lastNameController.text.trim() : null,
            organizationName: _orgNameController.text.trim(),
            referralCode: _referralCodeController.text.trim().isNotEmpty ? _referralCodeController.text.trim() : null,
          );
      if (success && mounted) {
        context.go('/dashboard');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateNotifierProvider);

    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Form(
                  key: _formKey,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Header
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(
                                colors: [AppTheme.primary, AppTheme.secondary],
                              ),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Icon(
                              Icons.auto_awesome,
                              color: Colors.white,
                              size: 28,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Text(
                            'AutoFlow AI',
                            style: Theme.of(context).textTheme.displayLarge?.copyWith(fontSize: 26),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Start your 14-day free trial. No credit card required.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                      const SizedBox(height: 28),

                      if (authState.errorMessage != null) ...[
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppTheme.error.withOpacity(0.12),
                            border: Border.all(color: AppTheme.error.withOpacity(0.5)),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            authState.errorMessage!,
                            style: const TextStyle(color: AppTheme.error, fontSize: 13),
                          ),
                        ),
                        const SizedBox(height: 20),
                      ],

                      // Name Fields
                      Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text('First Name', style: TextStyle(fontWeight: FontWeight.w500)),
                                const SizedBox(height: 8),
                                TextFormField(
                                  controller: _firstNameController,
                                  decoration: const InputDecoration(hintText: 'Aarav'),
                                  validator: (v) => (v == null || v.trim().isEmpty) ? 'Required' : null,
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text('Last Name', style: TextStyle(fontWeight: FontWeight.w500)),
                                const SizedBox(height: 8),
                                TextFormField(
                                  controller: _lastNameController,
                                  decoration: const InputDecoration(hintText: 'Sharma'),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),

                      // Email Field
                      const Text('Work / Creator Email', style: TextStyle(fontWeight: FontWeight.w500)),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _emailController,
                        keyboardType: TextInputType.emailAddress,
                        decoration: const InputDecoration(
                          hintText: 'aarav@creators.com',
                          prefixIcon: Icon(Icons.email_outlined, size: 20),
                        ),
                        validator: (v) {
                          if (v == null || v.trim().isEmpty) return 'Please enter your email';
                          if (!v.contains('@')) return 'Invalid email format';
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Organization Name
                      const Text('Workspace / Brand Name', style: TextStyle(fontWeight: FontWeight.w500)),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _orgNameController,
                        decoration: const InputDecoration(
                          hintText: 'Aarav Digital',
                          prefixIcon: Icon(Icons.business_outlined, size: 20),
                        ),
                        validator: (v) => (v == null || v.trim().isEmpty) ? 'Enter workspace name' : null,
                      ),
                      const SizedBox(height: 16),

                      // Password Field
                      const Text('Password (8+ characters)', style: TextStyle(fontWeight: FontWeight.w500)),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _passwordController,
                        obscureText: _obscurePassword,
                        decoration: InputDecoration(
                          hintText: '••••••••',
                          prefixIcon: const Icon(Icons.lock_outline, size: 20),
                          suffixIcon: IconButton(
                            icon: Icon(
                              _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                              size: 20,
                            ),
                            onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                          ),
                        ),
                        validator: (v) {
                          if (v == null || v.length < 8) return 'Password must be at least 8 characters';
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Referral Code (Optional)
                      const Text('Promo / Referral Code (Optional)', style: TextStyle(fontWeight: FontWeight.w500)),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _referralCodeController,
                        textCapitalization: TextCapitalization.characters,
                        decoration: const InputDecoration(
                          hintText: 'e.g. RAHUL30',
                          prefixIcon: Icon(Icons.discount_outlined, size: 20),
                        ),
                      ),
                      const SizedBox(height: 28),

                      // Submit Button
                      ElevatedButton(
                        onPressed: authState.isLoading ? null : _submit,
                        child: authState.isLoading
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text('Create Free Workspace'),
                      ),
                      const SizedBox(height: 24),

                      // Switch to Login
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text('Already have an account? ', style: Theme.of(context).textTheme.bodyMedium),
                          GestureDetector(
                            onTap: () => context.go('/login'),
                            child: const Text(
                              'Log in',
                              style: TextStyle(
                                color: AppTheme.primaryLight,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
