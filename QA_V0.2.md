# CNC Master v0.2 — acceptance checklist

This checklist is intentionally practical: each item should be verified on a real Android device before publishing the signed v0.2 release.

## Navigation

- Open every home card and return with the top-left arrow.
- Open every home card and return with the Android system back gesture/button.
- Verify Back exits the app only when already on the home screen.
- Repeat navigation after rotating/reopening the app.

## Smart cutting advisor

- 30ХГСА, Ø35.5, rough, stable: all returned values must be positive and plausible.
- Л63, small diameter: verify high-RPM warning logic where applicable.
- Finish with R0.8: verify vibration warning.
- Clear diameter field: screen must not crash.
- Enter comma instead of decimal point: calculation must still work.

## Threads

- M12×1: d2 ≈ 11.350, external minor ≈ 10.773, internal minor ≈ 10.917.
- M12 coarse pitch button: 1.75 mm.
- Verify several ISO 965 fine rows against the embedded reference table.
- Verify 1/4-20 UNC and 1/4-28 UNF inch/mm conversions.
- Verify BSPP is labeled parallel / 55°.
- Verify BSPT is labeled tapered / 55°.
- Verify NPT is labeled tapered / 60°.
- Verify app warns not to treat BSPT and NPT as interchangeable.

## Fanuc 0i-TF

- M12×1 external, Z-18: generated G76 final X should be around 10.773 basic-profile value.
- G76 first line contains packed finish/chamfer/nose-angle P word.
- G92 list ends at the same final X.
- Enter safe G97 RPM and verify it appears in both templates.
- Copy G76 and G92 and paste into a notes app; text must be complete.
- Select left-hand thread: warning must explicitly say CNC Master does not guess M03/M04.
- Clear/invalid fields: app must not crash.
- Never test generated code on a workpiece without Single Block / Dry Run / safe positions and machine-manual verification.

## Diagnostics

- Switch through every diagnostic chip.
- Each symptom must show description, probable causes, first checks and actions.
- Verify no card overflows horizontally on the target phone.

## Tool journal

- Add a record with insert, material, Vc, f, ap, count and result.
- Close/reopen app: record must still exist.
- Add multiple records.
- Delete one record and verify others remain.
- Empty numeric fields must not crash the app.

## My parts

- Save part name, material, machine, program, cycle and notes.
- Close/reopen app: card must still exist.
- Check theoretical parts/hour calculation for a known cycle time.
- Delete one card and verify others remain.

## Updates

- Manual "Check" works when current version is latest.
- New manifest version shows the update card/dialog.
- APK download progress moves from 0 to 100%.
- Wrong SHA-256 must abort installation.
- Correct APK opens Android package installer.
- First install-from-this-source permission flow returns correctly to installation.

## Release gate

Before publishing v0.2:

1. Unit tests green.
2. Android lint green.
3. Debug build green.
4. Manual device checklist completed.
5. Permanent release signing secrets configured.
6. Manual GitHub Actions release with version `0.2.0`.
7. Install signed v0.2 and verify future in-app update path with a test `0.2.1` release before broad distribution.
