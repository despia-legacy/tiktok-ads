package despia.ext.tiktokads;

import com.despia.nativeext.*;
import com.tiktok.TikTokBusinessSdk;
import com.tiktok.appevents.base.TTBaseEvent;
import org.json.*;

public final class TikTokAdsExtension implements NativeExtension {
    // App SDK configuration only. Never embed a server Marketing API credential.
    private static final String APP_ID = "REPLACE_WITH_APP_ID";
    private static final String TIKTOK_APP_ID = "REPLACE_WITH_TIKTOK_APP_ID";
    private static final String APP_SDK_ACCESS_TOKEN = "REPLACE_WITH_APP_SDK_ACCESS_TOKEN";
    private ExtContext ctx;
    private boolean configured, enabled, configuring;
    private static void onMain(Runnable action) { new android.os.Handler(android.os.Looper.getMainLooper()).post(action); }
    @Override public void onCreate(ExtContext context) { ctx = context; }
    @Override public void handle(ExtCall call, ExtResult.Callback reply) {
        try {
            if (call.route.equals("status")) {
                reply.reply(ExtResult.ok(answer(configured, enabled).put("sdk", "1.7.1"))); return;
            }
            if (call.route.equals("configure")) {
                if (configured) { reply.reply(ExtResult.ok(answer(configured, enabled))); return; }
                if (APP_ID.startsWith("REPLACE_") || TIKTOK_APP_ID.startsWith("REPLACE_") || APP_SDK_ACCESS_TOKEN.startsWith("REPLACE_")) {
                    reply.reply(ExtResult.error("not_configured", "Set native app SDK configuration before approval")); return;
                }
                if (configuring) { reply.reply(ExtResult.error("busy", "SDK initialization is in progress")); return; }
                if (TikTokBusinessSdk.isInitialized()) {
                    reply.reply(ExtResult.error("already_configured", "Another integration already owns TikTok SDK")); return;
                }
                TikTokBusinessSdk.TTConfig config = new TikTokBusinessSdk.TTConfig(ctx.app(), APP_SDK_ACCESS_TOKEN)
                    .setAppId(APP_ID).setTTAppId(TIKTOK_APP_ID).disableAutoStart().disableAutoEvents()
                    .disableAutoIapTrack().disableAdvertiserIDCollection().disableAutoEnhancedDataPostbackEvent().disableMonitor();
                configuring = true;
                TikTokBusinessSdk.initializeSdk(config, new TikTokBusinessSdk.TTInitCallback() {
                    @Override public void success() { onMain(() -> {
                        configuring = false; configured = true;
                        try { reply.reply(call.isActive() ? ExtResult.ok(answer(true, false)) : ExtResult.error("timeout", "SDK initialization completed after the route expired")); }
                        catch (JSONException ex) { reply.reply(ExtResult.error("sdk_error", "Could not serialize SDK state")); }
                    }); }
                    @Override public void fail(int code, String message) { onMain(() -> {
                        configuring = false; TikTokBusinessSdk.destroy();
                        reply.reply(ExtResult.error("sdk_error", "TikTok SDK initialization failed"));
                    }); }
                });
                return;
            }
            if (!configured) { reply.reply(ExtResult.error("not_configured", "Call configure first")); return; }
            if (call.route.equals("consent")) {
                enabled = Boolean.TRUE.equals(call.params.get("enabled"));
                if (enabled) TikTokBusinessSdk.startTrack();
                else { TikTokBusinessSdk.destroy(); configured = false; }
                reply.reply(ExtResult.ok(answer(configured, enabled))); return;
            }
            if (!enabled) { reply.reply(ExtResult.error("consent_required", "Enable event collection first")); return; }
            switch (call.route) {
                case "event": case "purchase": {
                    JSONObject values = properties(call.params.get("properties"));
                    String name = (String)call.params.get("name");
                    if (call.route.equals("purchase")) {
                        name = "Purchase";
                        values.put("value", number(call.params.get("amount")));
                        values.put("currency", currency(call.params.get("currency")));
                    } else if (call.params.containsKey("value")) values.put("value", number(call.params.get("value")));
                    TikTokBusinessSdk.trackTTEvent(new TTBaseEvent(name, values, (String)call.params.get("event_id")));
                    break;
                }
                case "flush": TikTokBusinessSdk.flush(); break;
                case "user":
                    if (call.params.containsKey("id")) TikTokBusinessSdk.identify((String)call.params.get("id"), null, null, null);
                    else TikTokBusinessSdk.logout();
                    break;
                default: reply.reply(ExtResult.error("not_registered", call.route)); return;
            }
            reply.reply(ExtResult.ok(new JSONObject().put("accepted", true)));
        } catch (JSONException | IllegalArgumentException ex) {
            reply.reply(ExtResult.error("bad_request", "Expected finite numbers, ISO currency and a JSON properties object"));
        } catch (RuntimeException ex) { configuring = false; reply.reply(ExtResult.error("sdk_error", "TikTok SDK rejected the operation")); }
    }
    private static JSONObject properties(Object raw) throws JSONException {
        return new JSONObject(raw instanceof String ? (String)raw : "{}");
    }
    private static double number(Object raw) {
        double value = Double.parseDouble(String.valueOf(raw));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite number required");
        return value;
    }
    private static String currency(Object raw) {
        String value = String.valueOf(raw);
        if (!value.matches("[A-Z]{3}")) throw new IllegalArgumentException("ISO currency required");
        return value;
    }
    private static JSONObject answer(boolean configured, boolean enabled) throws JSONException {
        return new JSONObject().put("configured", configured).put("enabled", enabled);
    }
}
