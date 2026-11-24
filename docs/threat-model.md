# garlic-launcher Threat Model

This document describes which security properties garlic-launcher is designed to
provide, which behaviours are deliberate design decisions, and which risks are
accepted or still open. Please read it before filing a security report.

---

## 1. Deployment Model

garlic-launcher turns an Android device (Android 7.1.1 or newer) into an
unattended digital signage player together with garlic-player. It runs as the
**device owner** (set once with `dpm set-device-owner`, or by the launcher
itself on a rooted device) and as the home launcher. It

- starts garlic-player, keeps it in the foreground (watchdog) and pins it in
  lock task mode (kiosk);
- installs and updates garlic-player and itself;
- reboots the device, switches the screen off and on, and enters deep standby
  on supported hardware;
- takes configuration, SMIL content and an APK from a USB stick or SD card.

The device usually hangs in a public place. Passers-by may touch the screen and
reach USB ports. The operator administers it locally in the service mode or
remotely through garlic-player and the CMS.

---

## 2. Assets

| Asset | Description |
| --- | --- |
| Kiosk integrity | Only the player can be used |
| Device control | Reboot, screen, standby, installation of apps as device owner |
| Player UUID and content URL | Identify the player to the CMS, see the garlic-hub threat model |
| Service password | Opens the service mode (leaves the strict kiosk mode) |
| Wi-Fi credentials | Set from `config.xml` |

---

## 3. What garlic-launcher Is Designed to Protect Against

- **Leaving the kiosk by touch**: the launcher is the persistent home activity,
  the player runs in lock task mode. In the strict kiosk mode the user
  restrictions `DISALLOW_CONFIG_CREDENTIALS`, `DISALLOW_ADD_USER`,
  `DISALLOW_REMOVE_USER`, `DISALLOW_MODIFY_ACCOUNTS` and `DISALLOW_FUN` are set.
  The service mode is reached only with the service password; the password is
  optional and off by default.
- **Player not running**: a watchdog brings the player back to the foreground,
  the launcher starts it after boot.
- **Service password at rest**: stored as PBKDF2 hash with a random salt, never
  in plain text.
- **Installation of foreign apps**: every installation runs through
  `InstallAppReceiver`, whether the APK comes from the player, the automatic
  download of garlic-player (HTTPS from `garlic-player.com`) or a USB stick.
  The APK is installed only when it is signed with exactly the certificate of
  the launcher. Launcher and player share the signing key, so updates of both
  are accepted and every other APK is rejected. Android additionally verifies
  updates against the certificate of the installed version and refuses
  downgrades.
- **No root needed**: the device owner API replaces root; on rooted devices the
  launcher uses `su` only for the actions the API does not offer.

---

## 4. Design Decisions

### 4.1 Local broadcasts and the settings provider without permission

garlic-player is a separate app and talks to the launcher by broadcasts
(`CommandReceiver`, `InstallAppReceiver`, `PlayerClosedReceiver`) and by the
content provider `SettingsProvider` (content URL, UUID, launcher version, check
of the service password). These components are exported **without a
permission**. Any app installed on the device can therefore reboot it, switch
the screen off, start deep standby, stop the player, have an APK installed that
is signed with the garlic key (an update of the launcher or the player, see
section 3), read the content URL and the player UUID, and test passwords against
the service password.

A signature permission would close this, but players already deployed,
including the legacy player, do not request it. Android would drop their
broadcasts silently, and they could no longer reboot the device or update
themselves. Launcher 1.x therefore keeps these components open and keeps the
names of their actions and extras unchanged.

This is acceptable only because the device is a dedicated kiosk: no user can
install apps, and the only apps are the launcher, the player and tools the
operator installs (see 4.3). Every app the operator adds is fully trusted.

### 4.2 Configuration and installation from USB storage

A USB stick or SD card with `SMIL/`, `config.xml` or `garlic-player.apk` in its
root is applied when it is mounted: the content is copied and the content URL
set, Wi-Fi, volume, brightness, standby and reboot times are set, or the APK is
installed. The APK must be signed with the garlic key (see section 3); the
configuration has no authentication. Physical access to a USB port or card slot
is therefore equivalent to administrative access.

**Operator requirement**: mount the device so that its ports and card slots are
not reachable by the public.

### 4.3 Remote access tools

Packages whose name starts with `com.anydesk` are added to the lock task
packages, so a remote support session can run in the kiosk. Such a tool is
installed and configured by the operator and is fully trusted.

---

## 5. Accepted Risks and Known Weaknesses

- Exported components without permission, see 4.1.
- No authentication for the USB configuration, see 4.2.
- The service password hash uses PBKDF2 with HMAC-SHA1 and 1000 iterations,
  below current recommendations. The provider allows unlimited password tests
  by local apps.
- `InstallAppReceiver` reads the APK file again after the certificate check.
  An app that sends the path of a file it controls could replace the file in
  between. This needs an installed app, see 4.1.
- On rooted devices commands run through `su`; root weakens every property of
  the platform and is not recommended.

---

## 6. Out of Scope

- Attacks requiring an additional app installed on the device by the operator
  or with the operator's credentials (see 4.1).
- Attacks requiring physical access to USB ports, card slots or the device
  internals (see 4.2).
- Vulnerabilities of Android, of the device firmware, of rooted images or of
  third party apps such as remote access tools.
- Denial of service by switching the device off or disconnecting the network.

---

## 7. In Scope

- Leaving the kiosk or the strict kiosk mode by touch or a connected keyboard
  without the service password.
- Reaching the device owner functions from the network without an installed app
  or physical access.
- Bypassing the service password other than by the documented provider check.
- Installation of an app other than through the documented ways in section 4,
  or of an APK not signed with the garlic key.

---

## 8. Reporting

See [SECURITY.md](../SECURITY.md) for the reporting process.
