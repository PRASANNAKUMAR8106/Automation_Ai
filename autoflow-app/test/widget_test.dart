import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:autoflow_app/app/app.dart';

void main() {
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  testWidgets('AutoFlow App mounts correctly and displays branding', (WidgetTester tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: AutoFlowApp(),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // Verify AutoFlow AI branding and form button are mounted
    expect(find.text('AutoFlow AI'), findsOneWidget);
    expect(find.byType(ElevatedButton), findsOneWidget);
  });
}
