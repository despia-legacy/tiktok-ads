package despia.ext.tiktokads;
import com.despia.nativeext.*;
import org.junit.Test;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

public class TikTokAdsExtensionTest {
    private ExtResult call(TikTokAdsExtension extension, String route) {
        final ExtResult[] result = new ExtResult[1];
        extension.handle(new ExtCall("tiktokads", route, Collections.emptyMap(), null), r -> {
            assertNull("exactly one answer", result[0]); result[0] = r;
        });
        assertNotNull(result[0]); return result[0];
    }
    @Test public void noSdkUseBeforeConfiguration() throws Exception {
        TikTokAdsExtension extension = new TikTokAdsExtension();
        assertFalse(call(extension, "status").data().getBoolean("configured"));
        for (String route : new String[]{"event", "purchase", "flush", "user", "consent"}) {
            assertEquals("not_configured", call(extension, route).code());
        }
        assertEquals("not_configured", call(extension, "configure").code());
        assertFalse(call(extension, "status").data().getBoolean("configured"));
    }
    private Object invoke(String name, Class<?> type, Object value) throws Exception {
        Method method = TikTokAdsExtension.class.getDeclaredMethod(name, type);
        method.setAccessible(true); return method.invoke(null, value);
    }
    private void rejects(String name, String value) throws Exception {
        try { invoke(name, Object.class, value); fail("accepted invalid " + name); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof JSONException || expected.getCause() instanceof IllegalArgumentException); }
    }
    @Test public void finiteNumbersOnly() throws Exception {
        assertEquals(12.5, (Double)invoke("number", Object.class, "12.50"), 0.0);
        for (String value : new String[]{"NaN", "Infinity", "1e999", "invalid"}) rejects("number", value);
    }
    @Test public void structuredPropertiesRemainIntact() throws Exception {
        JSONObject input = new JSONObject("{\"contents\":[{\"content_id\":\"product\",\"quantity\":2}],\"nullable\":null,\"flag\":true}");
        JSONObject output = (JSONObject)invoke("properties", Object.class, input.toString());
        assertEquals("product", output.getJSONArray("contents").getJSONObject(0).getString("content_id"));
        assertTrue(output.isNull("nullable"));
        for (String value : new String[]{"[]", "null", "invalid JSON"}) rejects("properties", value);
    }
    @Test public void currencyFormat() throws Exception {
        assertEquals("USD", invoke("currency", Object.class, "USD"));
        rejects("currency", "usd"); rejects("currency", "US"); rejects("currency", "123");
    }
}
