# ShutterSoundZero

[한국어](README.md) · [日本語](README.ja.md)

ShutterSoundZero changes the Galaxy camera setting (CSC) that forces the shutter sound to play. On phones that honor this setting, the camera can follow the phone’s sound mode. Take a photo with the default camera to check the result. Camera apps, firmware, sound mode, and system volume can all affect what you hear.

## Requirements and installation

- A Galaxy device running Android 11 or later. Initial setup requires Wi-Fi, Developer options, and wireless debugging. You can do it on the phone without a PC, USB cable, or separate ADB app.
- Download the official APK from [GitHub Releases](https://github.com/hoons1994/ShutterSoundZero/releases/latest). This guide describes the current source tree. Check the release notes for the features and languages included in a published APK.
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

- **No code entry notification:** Allow app notifications and check the **Initial setup** notification channel. If the code is hard to enter from brief pop-ups, try detailed pop-ups for ShutterSoundZero.
- **Wireless debugging is blocked:** Check Wi-Fi and the security settings on your phone. If you temporarily disable Auto Blocker, turn wireless debugging off and re-enable Auto Blocker when finished.
- **The code was accepted but setup failed:** Keep Wi-Fi and wireless debugging on and tap **Retry connection and setup**. If an old pairing has expired, use **Reconnect with a six-digit code** in the connection error dialog.
- **Credential errors keep returning:** Check your phone’s date and time first. If reconnecting does not help, **Reset connection credentials** creates a new ADB key when you start pairing. You will need a new six-digit code.
- **The shutter still sounds:** Check the phone’s sound mode and system volume, then try the default camera. In Sound mode, zero system volume can also make the shutter inaudible.
- **Send a report:** In Settings, open **Still having trouble?** Describe what happened and review **Diagnostics to send**. **Send by email** opens a draft containing your description and that report. Review it in your email app before sending. Leave pairing codes and personal information out of your description.
