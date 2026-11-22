# Offline validation (stable v1.2.2)

- Java AutoPolicy stress: 214,009 assertions; WakeReason stress: 100,009; BootPolicy stress: 700,004; Brightness prearm stress: 519. Captured in offline regression on the exact source before packaging.
- Exact installed APK SHA-256: `BB88AFC137213F2A58559C19F0DCDA444D189153172D07F4BCAA13BA4F7A684D`.
- Confirmed running on firmware `LOKMAT_APPLLP_7_PRO_C30_EN_V1.0_20260921`.
- Three wrist-raise tests showed the clock; three power-button tests did not.
- Optional reboot startup and media guard tested on the device in the current development iteration.
- This is the stable root/logcat implementation; the exploratory Vector/LSPosed direct wake hook is **not** included or required.
- Limits: No reproducible controlled battery measurement. New installations need overlay permission and Magisk root. Notification warning is an Android System channel and is managed separately.
