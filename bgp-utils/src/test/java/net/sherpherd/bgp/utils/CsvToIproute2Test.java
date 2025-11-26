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
        if (outScript.exists()) outScript.delete();

        // run conversion: read CSV prefixes and write iproute2 commands with nexthop 192.168.0.1
        CSVProvider csv = new CSVProvider(csvPath);
        Iproute2ScriptProvider out = new Iproute2ScriptProvider(outScript.getAbsolutePath());

        String[] route;
        int written = 0;
        while ((route = csv.getNextRoute()) != null) {
            // assume prefix is first column
            String prefix = route[0].trim();
            if (prefix.isEmpty()) continue;
            // only handle IPv4 prefixes in this test
            if (!Analysis.isValidIPv4Cidr(prefix)) continue;
            out.addRoute(prefix, "192.168.0.1/32");
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
        if (outScript.exists()) outScript.delete();

        // run conversion: read CSV prefixes and write iproute2 commands with nexthop 2001:db8::1
        CSVProvider csv = new CSVProvider(csvPath);
        Iproute2ScriptProvider out = new Iproute2ScriptProvider(outScript.getAbsolutePath());

        String[] route;
        int written = 0;
        while ((route = csv.getNextRoute()) != null) {
            // assume prefix is first column
            String prefix = route[0].trim();
            if (prefix.isEmpty()) continue;
            // only handle IPv6 prefixes in this test
            if (!Analysis.isValidIPv6Cidr(prefix)) continue;
            out.addRoute(prefix, "2001:db8::1/128");
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
}
