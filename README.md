# TikTok Ads

Source preview for Despia V3. This snapshot requires the runtime update currently awaiting release and an approved extension-enabled native build. Uploading these files does not change an installed app.

A native advertiser SDK extension using TikTok App Events SDK 1.7.2 on iOS and 1.7.1 on Android, independently of AppsFlyer. It sends custom and standard events and purchases through the SDK. It does not display Pangle ads.

Part of Despia V3 Extensions. First-party V4 support comes through the new package registry.

Source preview. Requires the upcoming approved V3 extension build release. Verify campaign attribution in your advertising account before launch.

## Web frameworks and app builders

Use the Despia SDK with React, Vue, Svelte, Next.js, TanStack or HTML5. Projects built with Lovable, Base44, Replit and other app builders need custom JavaScript and static file hosting. Claude Code, Codex and Cursor can edit the same extension files.

## Install

1. Copy manifest.json, ios/ and android/ into public/despia/tiktokads/ in your web app.
2. Add "tiktokads" to public/despia/extensions.json: `{ "extensions": ["tiktokads"] }`. This minimal index requires the no-app-identity runtime change in the build; legacy indexes remain compatible.
3. Replace the native configuration placeholders in TikTokAdsExtension.swift and TikTokAdsExtension.java with the app SDK configuration provided by TikTok. Never embed a server-side Marketing API credential. Do not log this configuration or put it in a page URL.
4. Approve the source and dependency closure, rebuild and install the native app.

The Swift package is pinned to commit 2a3a35560c2c0547f74869c58160946e29c0932c. Android uses com.github.tiktok:tiktok-business-android-sdk:1.7.1 from JitPack, plus the vendor's Android lifecycle, Billing and Install Referrer dependencies. Approval fetches and pins them. Automatic event logging, payment tracking and enhanced data postback are requested off in native configuration. No SDK is initialized on app launch by this extension.

## JavaScript SDK

Install the published SDK in your web app:

```sh
npm install despia-native
```

Import `despia` directly from `despia-native`. The examples use SDK 1.0.28. Run native calls from browser event handlers or mounted client hooks; importing the SDK during server rendering is safe. The installed Despia V3 app must include the approved extension.

Pass the route's exact output variable as the second argument. The SDK resolves an object whose matching property contains the full native envelope: `ok`, `code`, `error` and `data`, with runtime metadata. A native `ok: false` answer does not reject the SDK promise. Check `ok` before reading `data`. A single watched value becomes `undefined` after 30 seconds without an answer. Await calls using the same output variable sequentially.

## Call from the page

Apply the user's collection choice before sending events. Encode JSON query values with `encodeURIComponent(JSON.stringify(...))`.

```javascript
import despia from 'despia-native';

const configuredReply = await despia('extension://tiktokads/configure', ['tiktokadsConfigureResult']);
const configured = configuredReply.tiktokadsConfigureResult;
if (!configured?.ok) throw new Error(configured?.error ?? 'No native answer');

const consentReply = await despia('extension://tiktokads/consent?enabled=true', ['tiktokadsConsentResult']);
const consent = consentReply.tiktokadsConsentResult;
if (!consent?.ok) throw new Error(consent?.error ?? 'No native answer');
if (!consent.data.enabled) throw new Error('Event collection is disabled');

const loggedReply = await despia('extension://tiktokads/event?name=LaunchAPP', ['tiktokadsEventResult']);
const logged = loggedReply.tiktokadsEventResult;
if (!logged?.ok) throw new Error(logged?.error ?? 'No native answer');

const properties = { content_id: 'example-product' };
const purchaseReply = await despia(
  `extension://tiktokads/purchase?amount=12.50&currency=USD&properties=${encodeURIComponent(JSON.stringify(properties))}`,
  ['tiktokadsPurchaseResult']
);
const purchase = purchaseReply.tiktokadsPurchaseResult;
if (!purchase?.ok) throw new Error(purchase?.error ?? 'No native answer');
```

| Route | Parameters | Successful data | SDK output variable |
| --- | --- | --- | --- |
| configure | None | configured, enabled. iOS checks native configuration without starting the SDK; Android initializes with automatic transmission off. | `tiktokadsConfigureResult` |
| status | None | configured, enabled, sdk | `tiktokadsStatusResult` |
| consent | enabled boolean required | iOS: enabled. Android: configured, enabled. | `tiktokadsConsentResult` |
| event | name required, optional properties JSON object string, value numeric string, event_id string | accepted: true | `tiktokadsEventResult` |
| purchase | amount numeric string and currency uppercase three-letter code required, optional properties, event_id | accepted: true. Uses TikTok Purchase event. | `tiktokadsPurchaseResult` |
| user | Optional id, omit to log out | accepted: true | `tiktokadsUserResult` |
| flush | None | accepted: true. Requests SDK transmission, not a server receipt. | `tiktokadsFlushResult` |

Properties are a JSON object and preserve nested ecommerce objects, arrays and null values for the SDK. The route limits the encoded properties string to 4096 characters. Explicit value and amount parameters must be finite numbers. Read manifest.json for each string length limit. Errors include not_configured, already_configured, consent_required, bad_request and sdk_error. Android can also return busy while initialization is in progress. On iOS, the first opt-in checks the call lifetime before and after local SDK initialization. An expired request cannot enable bridge event submission.

## Privacy and platform behavior

This extension never requests ATT. TikTok's current iOS SDK does not automatically show that dialog. Use Despia's existing trackingconsent:// call separately, with the tracking_usage purpose description enabled in the native build. Collection consent is separate from ATT. Reapply the app's saved choice after every process launch.

On iOS, configured means the native values are present, not that the SDK has started or the vendor account has accepted them. The first consent call with enabled=true initializes the SDK with automatic event, payment and enhanced postback logging disabled. The pinned SDK completes its local initialization synchronously; its later remote configuration is separate. Local initialization can fail, and it is not a vendor account validation. An SDK already initialized by another integration is left unchanged and returns already_configured at this first opt-in. A false choice before initialization makes no SDK call. Initialization and the consent update run together on the main actor without an asynchronous gap. A later false request disables tracking on its next main-actor turn. Read enabled before sending events.

The adapter refuses bridge event calls while disabled. iOS uses TikTok setTrackingEnabled(false), which can cache SDK events for later according to the vendor API. Android has startTrack but no matching stopTrack API in 1.7.1. On a false consent call this adapter destroys its SDK instance and requires configure again before collection resumes. Neither path erases events already transmitted or guarantees cancellation of an in-flight request. Review the vendor's collection and retention behavior for your app.

Android Advertising ID collection is disabled in this starter. Native configuration can be adapted to the app's permitted collection model and base app Advertising ID permission. The SDK includes Android install referrer support. iOS SKAdNetwork support uses the SDK's defaults; configure campaign conversion settings in TikTok. Avoid competing conversion-value owners or duplicated purchase events.

SKAdNetworkItems under ios.app.info_plist is customer-editable and starts empty. This Apple key describes the ad networks used by a source app displaying ads. It is not an Android setting or a substitute for the advertised app's SDK and campaign configuration. Add only current identifiers your own integration requires, then approve and rebuild. Existing app-configured purpose descriptions take priority. The runtime update under review also permits a required description from the approved manifest when the app configuration has none.

An existing initialized TikTok SDK is left to its original integration: iOS checks ownership at the first true consent call; Android checks it during configure. Adapt the native code if the app deliberately shares that SDK. The SDK can use app-wide lifecycle and exception handling, so test it with the app's other native integrations.

Current V3 iOS target is 16.6 and later. Android module minSdk24 and compileSdk36. Billing is a vendor dependency, not a purchase UI supplied by this extension; review its merged permission and the base billing configuration during approval.

[Despia V3 Extensions documentation](https://setup.despia.com/experiments/v3-extensions) and [extension skill](https://github.com/despia-legacy/extension-skill).

Vendor sources: [TikTok iOS1.7.2](https://github.com/tiktok/tiktok-business-ios-sdk/releases/tag/1.7.2), [TikTok Android1.7.1](https://github.com/tiktok/tiktok-business-android-sdk/releases/tag/1.7.1), [official Android dependency integration](https://github.com/tiktok/tiktok-business-react-native-sdk/blob/main/android/build.gradle).

## License

[Despia V3 Extension Source-Available Commercial License](LICENSE).
You may inspect the source. Using or modifying this extension requires a valid
Despia commercial license and a valid Despia project for the particular app.
The standard price is **USD 249 per application per operating system**.
iOS and Android require separate licenses. See [Despia](https://despia.com).
Third-party SDKs keep their own licenses. Experimental V3 extensions do not
include a commitment to maintenance or a V4 port.

## Source and licensing

Browse, clone or fork this source to inspect it. Public GitHub forks remain public. For private development, clone into a private workspace or private repository. Modification, building, execution and deployment require a valid Despia commercial license and a valid Despia project for the application. The standard price is USD 249 per application per operating system. Licensed users may customize the extension or use it as a reference for new functionality in that licensed app. Keep the license and applicable notices. See [LICENSE](LICENSE) for the terms.
