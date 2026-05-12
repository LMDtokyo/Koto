package run.koto.desktop.ui.screens.settings

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes

/**
 * Thin wrapper around FileKit's native file picker. On Windows this routes
 * through the platform's `IFileOpenDialog` (the same Common Item Dialog
 * File Explorer / Discord / Telegram open); on macOS through `NSOpenPanel`;
 * on Linux through the XDG Desktop Portal.
 *
 * Going through FileKit beats hand-rolling JNA + COM because the library
 * already gets the boring details right — STA apartment, COMDLG_FILTERSPEC
 * lifetime, UTF-16 NUL termination, HWND parenting — and is what every
 * other Compose Multiplatform app reaches for.
 */
suspend fun pickImageBytes(): ByteArray? =
    FileKit.openFilePicker(type = FileKitType.Image)?.readBytes()
