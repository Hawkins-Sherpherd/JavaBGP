package net.sherpherd.bgp.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalysisTest {

    @Test
    public void testExactBoundaryMatch() {
        String asPath = "4242423010 4242423011 4242423947";
        assertTrue(Analysis.aspathRegex(asPath, "_4242423011_"));
    }

    @Test
    public void testNoMatchWhenDifferentNumber() {
        String asPath = "4242423010 42424230112 4242423947";
        assertFalse(Analysis.aspathRegex(asPath, "_4242423011_"));
    }

    @Test
    public void testStartBoundaryMatch() {
        String asPath = "4242423011 4242423012";
        assertTrue(Analysis.aspathRegex(asPath, "_4242423011"));
    }

    @Test
    public void testNormalizeSpaces() {
        String asPath = " 4242423010   4242423011\t4242423947 ";
        assertTrue(Analysis.aspathRegex(asPath, "_4242423011_"));
    }

    @Test
    public void testNullInputs() {
        assertFalse(Analysis.aspathRegex(null, "_4242423011_"));
        assertFalse(Analysis.aspathRegex("4242423011", null));
    }

    @Test
    public void testInvalidRegex() {
        assertFalse(Analysis.aspathRegex("4242423011 4242423012", "(unclosed"));
    }

    @Test
    public void testPlainNumberMatch() {
        // 原先测试包含非法数字，现在应判定为非法 as_path
        String asPath = "12424230114242423011 4242423011 4242423";
        assertFalse(Analysis.aspathRegex(asPath, "4242423011"));
    }

    @Test
    public void testUnderscoreMatchesEdges() {
        String asPath1 = "4242423011";
        assertTrue(Analysis.aspathRegex(asPath1, "_4242423011_"));
        String asPath2 = "4242423011 4242423012";
        assertTrue(Analysis.aspathRegex(asPath2, "_4242423011_"));
        String asPath3 = "4242423010 4242423011";
        assertTrue(Analysis.aspathRegex(asPath3, "_4242423011_"));
    }

    @Test
    public void testMaxUint32AndZero() {
        String asPath = "0 4294967295 123";
        assertTrue(Analysis.aspathRegex(asPath, "_4294967295_"));
        assertTrue(Analysis.aspathRegex(asPath, "_0_"));
    }

    @Test
    public void testInvalidTooLargeOrNegativeOrNonNumeric() {
        assertFalse(Analysis.aspathRegex("4294967296 1", "_4294967296_"));
        assertFalse(Analysis.aspathRegex("-1 1", "_-1_"));
        assertFalse(Analysis.aspathRegex("abc 1", "_abc_"));
    }
}
