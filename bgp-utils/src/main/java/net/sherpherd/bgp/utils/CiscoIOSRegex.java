package net.sherpherd.bgp.utils;
import java.util.ArrayList;
import java.util.List;

public final class CiscoIOSRegex {

    public static boolean matches(String aspath, String regex) {
        if (aspath == null || regex == null) return false;

        List<String> pathTokens = tokenizeAspath(aspath);
        List<String> regexTokens = tokenizeRegex(regex);

        int n = pathTokens.size();
        int m = regexTokens.size();

        // anchor checks
        boolean anchorStart = regex.startsWith("^");
        boolean anchorEnd   = regex.endsWith("$");

        // sliding match start positions
        int start = anchorStart ? 0 : 0;
        int end   = anchorStart ? 0 : (n - m);

        for (int i = start; i <= end; i++) {
            if (i < 0 || i + m > n) continue;

            boolean ok = true;
            for (int j = 0; j < m; j++) {
                if (!regexTokens.get(j).equals(pathTokens.get(i + j))) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                if (anchorEnd && (i + m != n)) continue;
                return true;
            }
        }

        return false;
    }

    // ---------------- Helpers ----------------

    /** AS_PATH → space tokens */
    private static List<String> tokenizeAspath(String aspath) {
        List<String> list = new ArrayList<>();
        for (String t : aspath.trim().split("\\s+")) {
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    /**
     * Regex tokenization based on Cisco IOS simplified rules:
     * ^X_Y_Z$ → ["X", "Y", "Z"]
     */
    private static List<String> tokenizeRegex(String regex) {
        String r = regex;

        if (r.startsWith("^")) r = r.substring(1);
        if (r.endsWith("$"))  r = r.substring(0, r.length() - 1);

        List<String> list = new ArrayList<>();
        for (String t : r.split("_")) {
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    // ---------------- simple test ----------------
    public static void main(String[] args) {
        System.out.println(matches("4242423947 9808", "9808$"));                  // true
        System.out.println(matches("4242423947 9808", "_9808$"));                 // true
        System.out.println(matches("4242423947 309808", "9808$"));                // false
        System.out.println(matches("4242423947 4242423010 9808", "_4242423010_"));// true
        System.out.println(matches("4242423947 309808 40100", "_9808_"));          // false
        System.out.println(matches("4242423947 309808 9808", "^4242423947"));     // true
        System.out.println(matches("4242423947 309808 9808", "^4242423947_"));    // true
    }
}
