import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:autoflow_app/app/app.dart';

void main() {
  testWidgets('AutoFlow App smoke test mounts correctly', (WidgetTester tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: AutoFlowApp(),
      ),
    );

    // Initial pump to mount router and widgets
    await tester.pumpAndSettle();

    // Verify AutoFlow AI branding appears on login screen
    expect(find.text('AutoFlow AI'), findsOneWidget);
    expect(find.text('Log In to Workspace'), findsOneWidget);
  });
}
