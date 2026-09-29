# Privacy

CLIProxyAPI for Android does not include analytics, advertising SDKs, crash-reporting SDKs, or developer-operated telemetry.

## Data stored on the device

Configuration, API keys, management credentials, OAuth credentials, and logs are stored in the app's private data directory. A normal signed app update preserves this data. Uninstalling the app removes it according to Android's application data rules.

## Network connections

The app listens on `127.0.0.1` by default. If the user explicitly enables LAN access, the proxy listens on all device network interfaces so other devices on the local network can connect. Users should configure an API key before enabling LAN access and use this mode only on trusted networks.

Depending on the features the user enables, the app can connect to configured AI providers for API and OAuth traffic, to upstream resources required by CLIProxyAPI, and to this repository's GitHub Releases to check for signed app and proxy core updates.

The update checker does not send API keys or OAuth credentials. Its request contains only normal HTTPS metadata and an app version user agent.

## User control

Users control which provider accounts and API keys are configured. Credentials can be removed through the management console or by clearing the app's data. The app does not upload its local configuration to the Android fork maintainer.
