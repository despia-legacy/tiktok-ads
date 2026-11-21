import Foundation
func check(_ ok: @autoclosure () throws -> Bool) rethrows { if try !ok() { fatalError("validation mismatch") } }
func rejects(_ operation: () throws -> Any) { do { _ = try operation(); fatalError("invalid value accepted") } catch {} }
try check(try TikTokAdsValues.number("12.50") == 12.5)
for value in ["NaN", "Infinity", "1e999", "invalid"] { rejects { try TikTokAdsValues.number(value) } }
try check(try TikTokAdsValues.currency("USD") == "USD")
for value in ["usd", "US", "123"] { rejects { try TikTokAdsValues.currency(value) } }
try check(try TikTokAdsValues.properties("{\"text\":\"ok\",\"number\":2,\"flag\":true}").count == 3)
for value in ["[]", "null", "invalid JSON"] { rejects { try TikTokAdsValues.properties(value) } }
let nested = try TikTokAdsValues.properties("{\"contents\":[{\"content_id\":\"product\",\"quantity\":2}],\"nullable\":null}")
try check((nested["contents"] as? [[String: Any]])?.first?["content_id"] as? String == "product")
try check(nested["nullable"] is NSNull)
print("PASS: finite numbers, currency format and structured event properties")
