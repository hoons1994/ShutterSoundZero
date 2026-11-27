# ShutterSoundZero

[한국어](README.md) · [日本語](README.ja.md)

This guide covers **v1.6.2**, which adds pairing notification recovery and an automatic update check when the app opens. [Release notes](https://github.com/hoons1994/ShutterSoundZero/releases/tag/v1.6.2)

ShutterSoundZero changes the Galaxy camera setting (CSC) that forces the shutter sound to play. On phones that honor this setting, the camera can follow the phone’s sound mode. Take a photo with the default camera to check the result. Camera apps, firmware, sound mode, and system volume can all affect what you hear.

## Requirements and installation

- A Galaxy device running Android 11 or later. Initial setup requires Wi-Fi, Developer options, and wireless debugging. You can do it on the phone without a PC, USB cable, or separate ADB app.
- Download the official APK from [GitHub Releases](https://github.com/hoons1994/ShutterSoundZero/releases/latest). Official versions from v1.5.4 onward use the same package ID and signing key and can be updated in place. Version v1.5.5 was withdrawn. If you are moving from v1.5.3 or earlier, uninstall the old app, install the current version, and complete initial setup again.
- The app follows your language settings. Korean is the default, with Japanese and English translations. Language support does not establish compatibility with a particular regional model, carrier, or One UI build.

Choose **Settings → Appearance → Language** to change the language for ShutterSoundZero. **Follow system settings** is selected by default. You can also choose **한국어**, **日本語**, or **English**.

## Initial setup

1. Connect to Wi-Fi and open the app. On Android 13 or later, allow notifications so you can enter the pairing code. On Android 17 or later, allow local network access when prompted.
2. If Developer options are off, tap **Open About phone**, open Software information, and tap Build number seven times. Your phone may ask you to unlock it.
3. Tap **Start initial setup**. Turn on wireless debugging and open the option to pair using a code. Leave the six-digit code visible.
4. In the ShutterSoundZero notification, tap **Enter code** and enter the six digits. If the code changes, use the new one. After connecting, the app obtains permission to change the setting, disables forced shutter sound, and tries to turn wireless debugging off. If debugging stays on, turn it off yourself.

## Reapply or restore the camera setting

If a software update resets the camera setting but the connection is still valid, turn on wireless debugging and choose **Settings → Camera settings → Reapply shutter sound setting**. To return to the original behavior, choose **Restore default shutter sound** and confirm. The app checks the value written to the device. It cannot listen for the shutter sound.

**To change the phone’s sound mode only while the camera is open**, use **Camera sound mode routine** in Settings. Choose **Mute** or **Vibrate** and tap **Add routine**. In Modes and Routines, the preset opens as **Camera - Mute** or **Camera - Vibrate**. Check the Camera app condition, the sound mode action, and the option to return to the previous settings when the routine ends. Then save. Enable only one of the two routines. This changes how calls and notifications sound while using the camera. The CSC shutter sound setting is separate. System menu names may vary by device.

## Troubleshooting

- **No code entry notification:** Allow app notifications and check the **Initial setup** notification channel, then tap **Continue pairing** on the home screen while waiting for a code. If the code is hard to enter from brief pop-ups, try detailed pop-ups for ShutterSoundZero.
- **Wireless debugging is blocked:** Check Wi-Fi and the security settings on your phone. If you temporarily disable Auto Blocker, turn wireless debugging off and re-enable Auto Blocker when finished.
- **The code was accepted but setup failed:** Keep Wi-Fi and wireless debugging on and tap **Retry connection and setup**. If an old pairing has expired, use **Reconnect with a six-digit code** in the connection error dialog.
- **Credential errors keep returning:** Check your phone’s date and time first. If reconnecting does not help, **Reset connection credentials** creates a new ADB key when you start pairing. You will need a new six-digit code.
- **The shutter still sounds:** Check the phone’s sound mode and system volume, then try the default camera. In Sound mode, zero system volume can also make the shutter inaudible.
- **Send a report:** In Settings, open **Still having trouble?** Describe what happened and review **Diagnostics to send**. **Send by email** opens a draft containing your description and that report. Review it in your email app before sending. Leave pairing codes and personal information out of your description.

## If you dismiss the pairing notification

While waiting for a code, dismissing the code entry notification while SSZ or a Settings screen opened from SSZ is visible shows a confirmation notification with actions to restore it or cancel setup. If you restore the code entry notification and dismiss it again, the app asks again. Dismissing the confirmation itself does not trigger another notification. No extra recovery prompt is posted while the app's task is minimized to the phone's home screen.

If the confirmation is gone or does not appear, open SSZ and tap **Continue pairing**. This restores the notification and opens Developer options with wireless debugging highlighted. The highlight may vary by device. It preserves your saved connection credentials and whether you were applying or restoring the camera setting.

Leave the current pairing code window open. If you closed it, open a new code window in Wireless debugging and use the new code once the app finds the connection and offers **Enter code**. Dismissing a notification alone does not require reinstalling the app or resetting credentials. Allow notifications and the **Initial setup** channel if they are blocked. The recovery button is hidden while a code is being submitted and after setup completes or is cancelled.

Android or One UI may suppress repeated heads-up pop-ups. Check the notification shade or use **Continue pairing** if a pop-up does not appear.

## App updates

When the app opens, it checks GitHub for the latest stable release. If a newer version is available, a notice on the home screen links to that release. Navigating between screens in the same session does not repeat the automatic check, and the app does not periodically check while closed.

You can also check manually under **App updates** in Settings. An automatic check failure does not show an error pop-up on the home screen; retry from Settings or open GitHub Releases directly. The app does not download or install APKs. You choose when to download and install an update.
