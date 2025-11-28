package net.sherpherd.bgp.utils;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class GenerateTest {

    @Test
    public void testGenerateRouteFromMRTToCSV() throws Exception {
        // 定位测试资源文件
        String resourcePath = "/updates.20251125.0000";
        InputStream in = getClass().getResourceAsStream(resourcePath);
        assertNotNull("Test MRT resource must exist: " + resourcePath, in);

        // 将资源文件复制到临时文件（MRTProvider 需要文件路径）
        File tmpMrt = Files.createTempFile("updates-", ".mrt").toFile();
        tmpMrt.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpMrt)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        } finally {
            in.close();
        }

        // 创建输出CSV文件
        File outCsv = new File(getClass().getResource(resourcePath).getPath());
        outCsv = new File(outCsv.getParent(), "output.csv");
        outCsv.getParentFile().mkdirs();
        
        // *** 修复：使用更健壮的文件删除逻辑 ***
        if (outCsv.exists()) {
            // 尝试强制关闭可能打开的流
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            
            // 尝试删除，如果失败则使用临时文件
            if (!outCsv.delete()) {
                System.err.println("警告: 无法删除现有输出文件，使用临时文件");
                outCsv = File.createTempFile("output-", ".csv", outCsv.getParentFile());
                outCsv.deleteOnExit();
            }
        }

        // 创建 MRTProvider 和 CSVProvider
        MRTProvider mrtProvider = new MRTProvider(tmpMrt.getAbsolutePath());
        CSVProvider csvProvider = new CSVProvider(outCsv.getAbsolutePath());
        
        // 设置详细模式以便调试
        DataProvider.verbose = true;

        // 执行转换
        Generate.generateRouteFromMRTToCSV(mrtProvider, csvProvider);

        // 验证输出文件存在
        assertTrue("Output CSV should be created", outCsv.exists());
        
        // 读取并验证CSV内容
        String content = new String(Files.readAllBytes(outCsv.toPath()), "UTF-8");
        assertFalse("CSV content should not be empty", content.isEmpty());
        
        // 解析CSV行
        String[] lines = content.split("\\r?\\n");
        assertTrue("CSV should contain at least header", lines.length >= 1);
        
        // 验证头部
        String header = lines[0];
        assertTrue("CSV header should contain prefix column", header.toLowerCase().contains("prefix"));
        assertTrue("CSV header should contain aspath column", 
                   header.toLowerCase().contains("as_path") || header.toLowerCase().contains("aspath"));
        
        // 解析头部获取列索引
        String[] headerParts = header.split(",");
        int prefixIdx = -1, asIdx = -1;
        for (int i = 0; i < headerParts.length; i++) {
            String h = headerParts[i].trim().toLowerCase();
            if (h.equals("prefix")) prefixIdx = i;
            if (h.equals("as_path") || h.equals("aspath")) asIdx = i;
        }
        
        assertTrue("header must contain prefix column", prefixIdx >= 0);
        assertTrue("header must contain as_path column", asIdx >= 0);

        // 验证数据行
        int dataRowCount = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            dataRowCount++;
            java.util.List<String> fields = parseCsvLine(line);
            
            // 确保有足够的字段
            while (fields.size() <= Math.max(prefixIdx, asIdx)) {
                fields.add("");
            }
            
            String prefix = unquote(fields.get(prefixIdx).trim());
            String asPath = unquote(fields.get(asIdx).trim());
            
            System.out.println("Parsed route " + dataRowCount + ": prefix=" + prefix + " AS_PATH=" + asPath);

            // 验证前缀格式
            assertTrue("prefix should be a valid CIDR: " + prefix, isValidCIDR(prefix));
            
            // 验证AS_PATH格式
            assertFalse("as_path should not be empty: " + asPath, asPath.isEmpty());
            assertTrue("as_path should be valid: " + asPath, isValidAsPath(asPath));
        }
        
        System.out.println("Successfully processed " + dataRowCount + " routes from MRT to CSV");
        
        // 验证确实处理了数据
        assertTrue("Should process at least some routes", dataRowCount > 0);
        
        // 关闭资源
        mrtProvider.close();
    }

    // 辅助方法
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
                    i++;
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