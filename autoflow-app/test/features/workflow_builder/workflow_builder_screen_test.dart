import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:autoflow_app/features/workflow_builder/presentation/workflow_builder_screen.dart';
import 'package:autoflow_app/features/workflow_builder/presentation/widgets/workflow_node_widget.dart';

void main() {
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
  });

  testWidgets('WorkflowBuilderScreen renders canvas, nodes, and action buttons', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      const MaterialApp(
        home: WorkflowBuilderScreen(),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // Verify canvas interactive viewer and initial nodes exist
    expect(find.byType(InteractiveViewer), findsOneWidget);
    expect(find.byType(WorkflowNodeWidget), findsNWidgets(3));

    // Verify toolbar items
    expect(find.text('Publish Automation'), findsOneWidget);
    expect(find.text('Add Step'), findsOneWidget);
    expect(find.byIcon(Icons.zoom_in), findsOneWidget);
    expect(find.byIcon(Icons.zoom_out), findsOneWidget);

    // Verify initial workflow name and draft status badge
    expect(find.text('Instagram Comment -> DM Lead Magnet'), findsOneWidget);
    expect(find.text('DRAFT'), findsOneWidget);
  });

  testWidgets('Publishing valid workflow transitions status to PUBLISHED', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      const MaterialApp(
        home: WorkflowBuilderScreen(),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // Tap 'Publish Automation'
    final publishBtn = find.text('Publish Automation');
    expect(publishBtn, findsOneWidget);
    await tester.ensureVisible(publishBtn);
    await tester.tap(publishBtn);
    await tester.pumpAndSettle();

    // Verify status updated to PUBLISHED
    expect(find.text('PUBLISHED'), findsOneWidget);
    expect(find.text('Workflow published successfully! Active and listening for triggers.'), findsOneWidget);
  });

  testWidgets('Tapping a node opens NodeConfigDrawer with properties', (WidgetTester tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(
      const MaterialApp(
        home: WorkflowBuilderScreen(),
      ),
    );

    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    // Tap on the trigger node
    final triggerNode = find.text('Instagram Comment Trigger');
    expect(triggerNode, findsOneWidget);
    await tester.tap(triggerNode);
    await tester.pumpAndSettle();

    // Verify drawer appears with Configure Node and keywords
    expect(find.text('Configure Node'), findsOneWidget);
    expect(find.text('Trigger Keywords (Comma separated)'), findsOneWidget);
  });
}
