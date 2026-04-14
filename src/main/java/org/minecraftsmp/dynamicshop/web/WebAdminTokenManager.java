package org.minecraftsmp.dynamicshop.web;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages time-limited tokens for web admin authentication.
 * Each token is tied to a specific player's in-game name.
 * Tokens expire after 30 minutes.
 */
public class WebAdminTokenManager {

    private static final long TOKEN_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    /**
     * Holds both the expiry timestamp and the player name associated with the token.
     */
    private record TokenData(long expiry, String playerName) {}

    // token -> TokenData (expiry + player name)
    private final Map<String, TokenData> activeTokens = new ConcurrentHashMap<>();

    /**
     * Generate a new admin token tied to a specific player name.
     * Returns the token string.
     */
    public String generateToken(String playerName) {
        cleanup();
        String token = UUID.randomUUID().toString().replace("-", "");
        activeTokens.put(token, new TokenData(System.currentTimeMillis() + TOKEN_EXPIRY_MS, playerName));
        return token;
    }

    /**
     * Validate a token. Returns true if valid and not expired.
     */
    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) return false;
        TokenData data = activeTokens.get(token);
        if (data == null) return false;
        if (System.currentTimeMillis() > data.expiry()) {
            activeTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Get the player name associated with a token.
     * Returns null if the token is invalid or expired.
     */
    public String getPlayerName(String token) {
        if (token == null || token.isEmpty()) return null;
        TokenData data = activeTokens.get(token);
        if (data == null) return null;
        if (System.currentTimeMillis() > data.expiry()) {
            activeTokens.remove(token);
            return null;
        }
        return data.playerName();
    }

    /**
     * Revoke a specific token.
     */
    public void revoke(String token) {
        activeTokens.remove(token);
    }

    /**
     * Revoke all active tokens.
     */
    public void revokeAll() {
        activeTokens.clear();
    }

    /**
     * Clean up expired tokens.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        activeTokens.entrySet().removeIf(e -> now > e.getValue().expiry());
    }
}
