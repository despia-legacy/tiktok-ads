import Foundation
import TikTokBusinessSDK

// Customer-specific app SDK configuration. Replace placeholders before approval.
// Never put a server-side Marketing API credential in an app extension.
enum TikTokAdsConfiguration {
    static let appID = "REPLACE_WITH_APP_ID"
    static let tikTokAppID = "REPLACE_WITH_TIKTOK_APP_ID"
    static let appSDKAccessToken = "REPLACE_WITH_APP_SDK_ACCESS_TOKEN"
}

@MainActor
final class TikTokAdsExtension: NativeExtension {
    private var configured = false
    private var configuring = false
    private var enabled = false
    required init() {}
    func didLaunch(_ context: NativeExtContext) {}
    func handle(_ call: NativeExtCall) async -> NativeExtResult {
        do {
            if call.route == "status" { return .ok(["configured": configured, "enabled": enabled, "sdk": "1.7.2"]) }
            if call.route == "configure" {
                if configured { return .ok(["configured": true, "enabled": enabled]) }
                guard !configuring else { return .error(code: "busy", message: "SDK initialization is in progress") }
                guard !TikTokBusiness.isInitialized() else {
                    return .error(code: "already_configured", message: "Another integration already owns TikTok SDK")
                }
                guard !TikTokAdsConfiguration.appID.hasPrefix("REPLACE_"),
                      !TikTokAdsConfiguration.tikTokAppID.hasPrefix("REPLACE_"),
                      !TikTokAdsConfiguration.appSDKAccessToken.hasPrefix("REPLACE_"),
                      let config = TikTokConfig(accessToken: TikTokAdsConfiguration.appSDKAccessToken,
                          appId: TikTokAdsConfiguration.appID, tiktokAppId: TikTokAdsConfiguration.tikTokAppID) else {
                    return .error(code: "not_configured", message: "Set native app SDK configuration before approval")
                }
                config.disableTracking()
                config.disableAutomaticTracking()
                config.disablePaymentTracking()
                config.disableAutoEnhancedDataPostbackEvent()
                configuring = true
                defer { configuring = false }
                do { try await TikTokBusiness.initializeSdk(config) }
                catch { return .error(code: "sdk_error", message: "TikTok SDK initialization failed") }
                configured = true
                guard call.isActive else { return .error(code: "timeout", message: "SDK initialization completed after the route expired") }
                return .ok(["configured": true, "enabled": false])
            }
            guard configured else { return .error(code: "not_configured", message: "Call configure first") }
            if call.route == "consent" {
                enabled = call.params["enabled"] as? Bool ?? false
                TikTokBusiness.setTrackingEnabled(enabled)
                return .ok(["enabled": enabled])
            }
            guard enabled else { return .error(code: "consent_required", message: "Enable event collection first") }
            switch call.route {
            case "event", "purchase":
                var values = try TikTokAdsValues.properties(call.params["properties"] as? String)
                let name: String
                if call.route == "purchase" {
                    values["value"] = try TikTokAdsValues.number(call.params["amount"] as? String ?? "")
                    values["currency"] = try TikTokAdsValues.currency(call.params["currency"] as? String ?? "")
                    name = "Purchase"
                } else {
                    name = call.params["name"] as? String ?? ""
                    if let value = call.params["value"] as? String { values["value"] = try TikTokAdsValues.number(value) }
                }
                TikTokBusiness.trackTTEvent(TikTokBaseEvent(eventName: name, properties: values, eventId: call.params["event_id"] as? String))
            case "flush": TikTokBusiness.explicitlyFlush()
            case "user":
                if let id = call.params["id"] as? String {
                    TikTokBusiness.identify(withExternalID: id, externalUserName: nil, phoneNumber: nil, email: nil)
                } else { TikTokBusiness.logout() }
            default: return .error(code: "not_registered", message: call.route)
            }
            return .ok(["accepted": true])
        } catch { return .error(code: "bad_request", message: "Expected finite numbers, ISO currency and a JSON properties object") }
    }
}

enum TikTokAdsValues {
    static func number(_ text: String) throws -> Double {
        guard let value = Double(text), value.isFinite else { throw NSError(domain: "tiktokads", code: 1) }
        return value
    }
    static func currency(_ text: String) throws -> String {
        guard text.range(of: "^[A-Z]{3}$", options: .regularExpression) != nil else { throw NSError(domain: "tiktokads", code: 2) }
        return text
    }
    static func properties(_ text: String?) throws -> [String: Any] {
        guard let text = text else { return [:] }
        guard let data = text.data(using: .utf8),
              let values = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "tiktokads", code: 3)
        }
        return values
    }
}
