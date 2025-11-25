package net.sherpherd.bgp.utils;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class MRTToolsTest {

    @Test
    public void testMrtToCSVFromResources() throws Exception {
        // locate resource inside test resources
        String resourcePath = "/net/sherpherd/bgp/utils/resources/updates.20251125.0000";
        InputStream in = getClass().getResourceAsStream(resourcePath);
        assertNotNull("Test MRT resource must exist: " + resourcePath, in);

        // copy resource to a temp file (MRTTools expects a file path)
        File tmpMrt = Files.createTempFile("updates-", ".mrt").toFile();
        tmpMrt.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpMrt)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        } finally {
            in.close();
        }

        // output CSV path inside test resources as requested
        File outCsv = Paths.get("src/test/java/net/sherpherd/bgp/utils/resources/output.csv").toFile();
        // ensure parent exists
        outCsv.getParentFile().mkdirs();
        if (outCsv.exists()) outCsv.delete();

        // run the conversion
        MRTTools.mrtToCSV(tmpMrt.getAbsolutePath(), outCsv.getAbsolutePath());

        // assert output exists and has expected header
        assertTrue("Output CSV should be created", outCsv.exists());
        String content = new String(Files.readAllBytes(outCsv.toPath()), "UTF-8");
        assertTrue("CSV should contain header 'prefix,as_path'", content.contains("prefix,as_path"));
        // parse lines and validate each data row
        String[] lines = content.split("\\r?\\n");
        assertTrue("CSV should contain at least header", lines.length >= 1);
        // determine column indexes from header (robust to additional columns)
        String header = lines[0];
        String[] headerParts = header.split(",");
        int prefixIdx = -1, asIdx = -1;
        for (int i = 0; i < headerParts.length; i++) {
            String h = headerParts[i].trim();
            if (prefixIdx < 0 && h.equalsIgnoreCase("prefix")) prefixIdx = i;
            if (asIdx < 0 && h.equalsIgnoreCase("as_path")) asIdx = i;
        }
        assertTrue("header must contain prefix column", prefixIdx >= 0);
        assertTrue("header must contain as_path column", asIdx >= 0);

        // skip first line (header)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) continue;
            line = line.trim();
            if (line.isEmpty()) continue;
            // print raw CSV line for debugging / inspection
            System.out.println("CSV raw line " + i + ": " + line);
            java.util.List<String> fields = parseCsvLine(line);
            while (fields.size() <= Math.max(prefixIdx, asIdx)) fields.add("");
            String prefix = unquote(fields.get(prefixIdx).trim());
            String asPath = unquote(fields.get(asIdx).trim());
            // also print parsed fields
            System.out.println("Parsed: prefix=" + prefix + "  AS_PATH=" + asPath);

            assertTrue("prefix should be a valid IPv4 or IPv6 CIDR: " + prefix, isValidCIDR(prefix));
            assertTrue("as_path should be a valid AS_PATH: " + asPath, isValidAsPath(asPath));
        }
    }

    private static String unquote(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return s;
    }

    private static boolean isValidCIDR(String s) {
        if (s == null || s.isEmpty()) return false;
        String[] parts = s.split("/");
        if (parts.length != 2) return false;
        String ip = parts[0];
        String pre = parts[1];
        try {
            int prefix = Integer.parseInt(pre);
            // try IPv4
            String[] oct = ip.split("\\.");
            if (oct.length == 4) {
                if (prefix < 0 || prefix > 32) return false;
                for (String o : oct) {
                    int v = Integer.parseInt(o);
                    if (v < 0 || v > 255) return false;
                }
                return true;
            }
            // try IPv6
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            if (addr.getAddress().length == 16) {
                if (prefix < 0 || prefix > 128) return false;
                return true;
            }
        } catch (Exception ex) {
            return false;
        }
        return false;
    }

    private static boolean isValidAsPath(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return false;
        String[] toks = trimmed.split("\\s+");
        final long MAX = 0xFFFFFFFFL;
        for (String t : toks) {
            if (!t.matches("\\d+")) return false;
            try {
                long v = Long.parseLong(t);
                if (v < 0 || v > MAX) return false;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private static java.util.List<String> parseCsvLine(String line) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
