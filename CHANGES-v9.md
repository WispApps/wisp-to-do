# v9: reminder picker correction

The native date/time picker now keeps its Activity context and applies the app locale with applyOverrideConfiguration. Previously it used a resources-only configuration context, which may cause a window-token failure when displaying a dialog. No device crash trace was supplied, so the exact reported exception remains unconfirmed.

Existing reminder date/time initializes the picker. Cancel leaves the previous reminder unchanged; new values are assigned only after confirming time. This shared dialog serves creation and editing.

Recovery phrase code review: create() reuses a stored phrase; otherwise SecureRandom generates 24 independently sampled tokens, each with 256 possible values (192 bits total). There is no shared fixed phrase. Identical phrases are theoretically possible; the probability for two independent generations is 2^-192. Restoring intentionally adopts the supplied phrase. This is a custom format, not BIP39. No phrase/key format was changed in this update.

Validation: source diff and archive integrity checked. Android SDK/emulator unavailable; APK compilation and phone verification are still required. On the phone, test creation and editing, date/time cancellation, and a reminder one or two minutes ahead. Do not uninstall to apply this update.
