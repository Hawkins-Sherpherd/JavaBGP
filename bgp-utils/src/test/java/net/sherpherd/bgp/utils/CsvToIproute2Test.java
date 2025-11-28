package net.sherpherd.bgp.utils;

import org.junit.Test;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CsvToIproute2Test {

    @Test
    public void testCsvToIproute2ScriptGeneration() throws Exception {
        // locate CSV resource inside test resources
        String resourcePath = "/output.csv";
        URL res = getClass().getResource(resourcePath);
        assertNotNull("Test CSV resource must exist: " + resourcePath, res);

        // resolve to filesystem path
        String csvPath = Paths.get(res.toURI()).toFile().getAbsolutePath();

        // create output script path next to the CSV resource
        File csvFile = new File(csvPath);
        File outScript = new File(csvFile.getParentFile(), "iproute2test.sh");
        
        // *** 修复1：使用 prepareOutputFile 来准备输出文件 ***
        prepareOutputFile(outScript.getAbsolutePath());

        // run conversion: read CSV prefixes and write iproute2 commands with nexthop 192.168.0.1
        CSVProvider csv = new CSVProvider(csvPath);
        Iproute2ScriptProvider out = new Iproute2ScriptProvider(outScript.getAbsolutePath());
        
        // *** 修复2：初始化 writer ***
        out.initializeWriter();

        String[] route;
        int written = 0;
        while ((route = csv.getNextRoute()) != null) {
            // assume prefix is first column
            String prefix = route[0].trim();
            if (prefix.isEmpty()) continue;
            // only handle IPv4 prefixes in this test
            if (!Analysis.isValidIPv4Cidr(prefix)) continue;
            out.addRoute(prefix, "192.168.0.1");
            written++;
        }

        out.close();

        assertTrue("Script file should be created", outScript.exists());
        String content = new String(Files.readAllBytes(outScript.toPath()), "UTF-8");
        assertTrue("Script should contain ip route add commands", content.contains("ip route add") || content.contains("ip -6 route add"));
        // if any routes were written, check that at least one prefix appears in the script
        if (written > 0) {
            // check one of the prefixes appears
            String firstPrefix = Files.readAllLines(Paths.get(csvPath)).get(1).split(",")[0].replaceAll("\"", "").trim();
            assertTrue("Script should contain the first prefix", content.contains(firstPrefix));
        }
    }
    
    @Test
    public void testCsvToIproute2ScriptGenerationIPv6() throws Exception {
        // locate CSV resource inside test resources
        String resourcePath = "/output.csv";
        URL res = getClass().getResource(resourcePath);
        assertNotNull("Test CSV resource must exist: " + resourcePath, res);

        // resolve to filesystem path
        String csvPath = Paths.get(res.toURI()).toFile().getAbsolutePath();

        // create output script path next to the CSV resource
        File csvFile = new File(csvPath);
        File outScript = new File(csvFile.getParentFile(), "iproute2test6.sh");
        
        // *** 修复1：使用 prepareOutputFile 来准备输出文件 ***
        prepareOutputFile(outScript.getAbsolutePath());

        // run conversion: read CSV prefixes and write iproute2 commands with nexthop 2001:db8::1
        CSVProvider csv = new CSVProvider(csvPath);
        Iproute2ScriptProvider out = new Iproute2ScriptProvider(outScript.getAbsolutePath());
        
        // *** 修复2：初始化 writer ***
        out.initializeWriter();

        String[] route;
        int written = 0;
        while ((route = csv.getNextRoute()) != null) {
            // assume prefix is first column
            String prefix = route[0].trim();
            if (prefix.isEmpty()) continue;
            // only handle IPv6 prefixes in this test
            if (!Analysis.isValidIPv6Cidr(prefix)) continue;
            out.addRoute(prefix, "2001:db8::1");
            written++;
        }

        out.close();

        assertTrue("Script file should be created", outScript.exists());
        String content = new String(Files.readAllBytes(outScript.toPath()), "UTF-8");
        assertTrue("Script should contain ip -6 route add commands", content.contains("ip -6 route add"));
        // if any routes were written, check that at least one IPv6 prefix appears in the script
        if (written > 0) {
            // verify the script contains the IPv6 nexthop
            assertTrue("Script should contain the IPv6 nexthop", content.contains("2001:db8::1"));
        }
    }
    
    /**
     * 准备输出文件：如果不存在则创建，如果存在则覆盖
     */
    private static void prepareOutputFile(String path) {
        File file = new File(path);
        
        // 如果文件已存在，删除它（准备覆盖写入）
        if (file.exists()) {
            if (!file.delete()) {
                // Windows文件锁定问题的变通方案：尝试多次删除
                System.gc(); // 强制垃圾回收，释放可能的文件句柄
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                
                if (!file.delete()) {
                    System.err.println("警告: 无法删除已存在的输出文件，尝试覆盖: " + path);
                    // 不抛出异常，让后续写入操作尝试覆盖
                    return;
                }
            }
            System.out.println("已删除现有输出文件: " + path);
        }
        
        // 确保父目录存在
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new RuntimeException("无法创建输出文件的父目录: " + parentDir.getAbsolutePath());
            }
            System.out.println("已创建输出目录: " + parentDir.getAbsolutePath());
        }
        
        // 创建空文件
        try {
            if (!file.createNewFile()) {
                System.err.println("警告: 文件已存在，将被覆盖: " + path);
            } else {
                System.out.println("已创建输出文件: " + path);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("创建输出文件失败: " + path + " - " + e.getMessage(), e);
        }
    }
}