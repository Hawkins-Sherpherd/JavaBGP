package net.sherpherd.bgp.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class CiscoIOSRegexTest {

    @Test
    public void testMatchesEndOfPath() {
        // 匹配 1 2 3 和 3$，返回 true
        assertTrue(CiscoIOSRegex.matches("1 2 3", "3$"));
    }

    @Test
    public void testMatchesEndOfPathFalse() {
        // 匹配 1 2 3 4 和 3$，返回 false
        assertFalse(CiscoIOSRegex.matches("1 2 3 4", "3$"));
    }

    @Test
    public void testMatchesEndOfPathPartialMatch() {
        // 匹配 1 2 33 和 3$，返回 false
        assertFalse(CiscoIOSRegex.matches("1 2 33", "3$"));
    }

    @Test
    public void testMatchesMiddleOfPath() {
        // 匹配 1 2 30 3 和 _3_ 返回 true
        assertTrue(CiscoIOSRegex.matches("1 2 30 3", "_3_"));
    }

    @Test
    public void testMatchesMiddleOfPathFalse() {
        // 匹配 1 2 30 4 和 _3_ 返回 false
        assertFalse(CiscoIOSRegex.matches("1 2 30 4", "_3_"));
    }
}
