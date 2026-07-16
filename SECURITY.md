# Security Policy

## Supported Versions

We release patches for security vulnerabilities in the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 3.x.x   | :white_check_mark: |
| > 3.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Aura Hi-Res Player, please report it responsibly:

1. **Do NOT** create a public GitHub issue
2. Email us at: a private GitHub security advisory
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fixes

## Security Best Practices

### For Developers

- **Never commit sensitive files**: API keys, tokens, and credentials should never be committed to version control
- **Use environment variables**: Store sensitive configuration in environment variables or secure properties files
- **Regular updates**: Keep dependencies updated to patch security vulnerabilities
- **Code review**: All code changes should be reviewed before merging

### For Users

- **Download from official sources**: Only download APKs from official releases or trusted sources
- **Keep the app updated**: Install updates promptly to receive security patches
- **Review permissions**: Be aware of the permissions the app requests

## Sensitive Information

The following files contain sensitive information and should never be committed:

- `google-services.json` - Firebase configuration with API keys
- `local.properties` - Local development configuration
- `*.keystore` / `*.jks` - App signing keys
- `secrets.properties` - API keys and secrets
- `**/assets/po_token.html` - YouTube authentication tokens

## Data Privacy

Aura Hi-Res Player is committed to user privacy:

- **No analytics, no tracking**: the published app ships no active analytics and sends us no crash reports. The Firebase libraries are linked in the `gms` variant but are never initialised, because the Firebase Gradle plugins only apply when a `google-services.json` is present — and none exists in this repository or in the release pipeline. Firebase Analytics is never called in code either. The `foss` variant has no crash backend at all (its crash reporter is a no-op). A developer who adds their own `google-services.json` to a `gms` build enables Crashlytics in *their own* build only.
- **Local storage**: your library, listening history, playlists, searches and preferences stay on the device. We have no account system for them.
- **What we do receive**: license/demo checks send a license key and a device identifier (`ANDROID_ID`, or a random fallback ID) to our license server — required for the subscription. AI playlist prompts and song-recognition fingerprints reach our relay only when you use those features.
- **What third parties receive**: the app is a YouTube Music client, so YouTube (Google) receives your playback requests and IP. Lyrics, metadata and optional integrations (Last.fm, ListenBrainz, Spotify, SponsorBlock, AI, translation) are documented in [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
- **Open source**: All code is available for review

## Contact

For security-related questions or to report vulnerabilities:

- Email: a private GitHub security advisory
- GitHub: Create a private security advisory

Thank you for helping keep Aura Hi-Res Player secure!
