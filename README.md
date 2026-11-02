# SurveyWise

Lightweight, offline-first Android field surveying and photo documentation.

## Product goal
Walk a building, capture photos quickly, dictate observations using Android's native speech recognition, pin observations to floor plans, and export an editable Word survey report and annotated images.

## Design principles
- Native Android, minimal dependencies and background processing.
- One-tap photo capture followed by immediate native voice dictation; visible microphone state and editable text fallback.
- Local-first: field capture, annotations, and floor plans must work without an internet connection. On-device speech recognition is preferred where supported; do not assume all devices offer offline transcription.
- No bundled AI model or mandatory account for basic capture.
- Keep original images untouched; store editable annotation objects and produce flattened annotated photos for export.
- Each observation links its photos, notes, drawing/sheet, plan pin, timestamp, and optional position confidence.
- Optional barcode scanner stays outside the primary camera flow.
- Reuse suitable BasketWise scanning/camera components only after evaluating coupling, licensing, and dependencies. Do not import the whole app.

## P0 — Rapid Capture and Survey Reports
- Project setup with site, buildings, levels, and survey metadata.
- Multi-page PDF floor plan import and sheet organization.
- Immediate capture -> native Android voice-to-text -> save observation.
- Photo annotation: text, arrows, shapes, freehand, undo/redo.
- Manual observation/photo pins and status/checklist tracking on plans.
- Offline persistence, autosave, session recovery.
- Export annotated JPG/PNG, original photos, marked-up plans, and full project archive.
- Generate DOCX report with observation IDs, photo annotations, note text, sheet and location references.
- Optional barcode scanning accessible as a secondary tool.

## P1 — Positioning and Coverage
- Position calibration against floor plan, manual position corrections.
- ARCore visual-inertial tracking (when supported), sensor diagnostics, heading indicator.
- Confidence-aware position estimates, with fallback to manually positioned pins.
- Optional Wi-Fi RTT ranging where hardware/access points support it.
- Survey coverage map (inspected, uninspected, needs revisit).
- Windows companion for reviewing/editing and authenticated project transfer.

## P2 — Stretch
- Insta360 X5 media ingestion/panoramas, investigate supported camera integration.
- Opt-in AI-assisted documentation (not present in initial UX).
- Revit/BIM coordination.
- UWB anchor-based precision positioning.
- Multiuser collaboration.

## Explicitly out of initial scope
- Equipment-tag OCR and automated equipment identification.
- Before/after photo comparison.
- Always-on AI inference.
- Pretending GPS/compass alone can provide precise room-level indoor positioning.

## First acceptance scenario
1. Import a multi-sheet floor plan.
2. Take a photo with one tap and immediately dictate a note.
3. Edit the transcribed note, annotate photo, pin observation to drawing.
4. Close/reopen app and verify all original/annotated data is preserved.
5. Export ZIP and DOCX to Windows and check all notes and annotation overlays appear correctly.

## Implementation starting point
Audit BasketWise's capture pipeline, extract only reusable low-coupling components, then establish a small standalone Kotlin/Jetpack Compose Android project with CameraX, Android speech recognition, local project persistence, and tests. Validate performance and permission flows on-device before positioning work.
