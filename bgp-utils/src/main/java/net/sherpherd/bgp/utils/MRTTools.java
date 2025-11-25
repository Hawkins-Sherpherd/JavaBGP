package net.sherpherd.bgp.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.javamrt.mrt.BGPFileReader;
import org.javamrt.mrt.MRTRecord;

public class MRTTools {
    public static void mrtToCSV(String mrt_file_location,String csv_file_location) throws Exception{
        InputStream input = new FileInputStream(mrt_file_location);
        BGPFileReader mrt = new BGPFileReader(new BufferedInputStream(input));

        File csvFile = new File(csv_file_location);
        File parent = csvFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Ensure CSV exists and has header containing prefix and as_path
        if (!csvFile.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, false))) {
                pw.println("prefix,as_path");
            }
        } else {
            // Read existing header and rest lines
            List<String> rest = new ArrayList<>();
            String header = "";
            try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
                String first = br.readLine();
                if (first != null) header = first;
                String line;
                while ((line = br.readLine()) != null) {
                    rest.add(line);
                }
            }

            String[] origParts = header.isEmpty() ? new String[0] : header.split(",", -1);
            List<String> cols = new ArrayList<>();
            for (String c : origParts) {
                String t = c.trim();
                if (!t.isEmpty()) cols.add(t);
            }
            boolean hasPrefix = cols.stream().anyMatch(s -> s.equalsIgnoreCase("prefix"));
            boolean hasAsPath = cols.stream().anyMatch(s -> s.equalsIgnoreCase("as_path"));
            if (!hasPrefix) cols.add("prefix");
            if (!hasAsPath) cols.add("as_path");

            if (cols.size() != origParts.length) {
                int added = cols.size() - origParts.length;
                File tmp = new File(csvFile.getAbsolutePath() + ".tmp");
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(tmp, false)))) {
                    pw.println(String.join(",", cols));
                    for (String l : rest) {
                        StringBuilder sb = new StringBuilder(l == null ? "" : l);
                        for (int i = 0; i < added; i++) sb.append(",");
                        pw.println(sb.toString());
                    }
                }
                Files.move(tmp.toPath(), csvFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Prepare async logger (create before loading CSV so loading logs can be shown)
        final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
        Thread loggerThread = new Thread(() -> {
            try {
                while (true) {
                    String msg = logQueue.take();
                    if ("__END__".equals(msg)) break;
                    System.out.println(msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mrt-csv-logger");
        // make logger a normal thread so console output reliably appears
        loggerThread.setDaemon(false);
        loggerThread.start();

        // Load existing CSV rows into a map keyed by prefix (keep shortest AS_PATH)
        List<String> cols;
        java.util.LinkedHashMap<String, String[]> rowsMap = new java.util.LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String headerLine = br.readLine();
            if (headerLine == null) headerLine = "prefix,as_path";
            cols = parseHeader(headerLine);
            int prefixIdx = indexOfIgnoreCase(cols, "prefix");
            int asIdx = indexOfIgnoreCase(cols, "as_path");
            String line;
            while ((line = br.readLine()) != null) {
                java.util.List<String> fields = parseCsvLine(line);
                // normalize size
                while (fields.size() < cols.size()) fields.add("");
                String prefix = prefixIdx >= 0 && prefixIdx < fields.size() ? fields.get(prefixIdx).trim() : "";
                String asVal = asIdx >= 0 && asIdx < fields.size() ? fields.get(asIdx).trim() : "";
                if (prefix.isEmpty()) continue;
                if (!rowsMap.containsKey(prefix)) {
                    rowsMap.put(prefix, fields.toArray(new String[0]));
                    // log initial load added to memory
                    logQueue.offer("Loaded in-memory: prefix=" + prefix + "  AS_PATH=" + asVal);
                } else {
                    String[] existing = rowsMap.get(prefix);
                    String existingAs = (asIdx >= 0 && asIdx < existing.length) ? existing[asIdx] : "";
                    if (isShorterAsPath(asVal, existingAs)) {
                        String[] newFields = new String[cols.size()];
                        for (int i = 0; i < newFields.length; i++) newFields[i] = "";
                        if (prefixIdx >= 0) newFields[prefixIdx] = prefix;
                        if (asIdx >= 0) newFields[asIdx] = asVal;
                        rowsMap.put(prefix, newFields);
                        // log that in-memory entry was updated during CSV load
                        logQueue.offer("Loaded updated in-memory: prefix=" + prefix + "  AS_PATH=" + asVal);
                    }
                }
            }
        }

        // Process MRT records, updating rowsMap; track which prefixes changed/added
        java.util.List<String> changed = new java.util.ArrayList<>();
        while (true) {
            MRTRecord mrtRecord = mrt.readNext();
            if (mrtRecord == null) break;
            String[] route = getRouteFromLine(mrtRecord);
            String prefix = route[0];
            String as_path = route[1];
            if (prefix == null) continue;
            String p = prefix.trim();
            if (p.isEmpty()) continue;
            // filter default routes
            if (p.equals("0.0.0.0/0") || p.equals("::/0")) continue;

            // validate prefix and as_path; skip invalid entries
            String asTrim = as_path == null ? "" : as_path.trim();
            if (!isValidCIDR(p) || !isValidAsPath(asTrim)) {
                // if logger available, record skipped entry; otherwise print
                try {
                    logQueue.offer("Skipping invalid route: prefix=" + p + "  AS_PATH=" + asTrim);
                } catch (Throwable t) {
                    System.out.println("Skipping invalid route: prefix=" + p + "  AS_PATH=" + asTrim);
                }
                continue;
            }

            int prefixIdx = indexOfIgnoreCase(cols, "prefix");
            int asIdx = indexOfIgnoreCase(cols, "as_path");
            String existingAs = null;
            if (rowsMap.containsKey(p)) {
                String[] ex = rowsMap.get(p);
                existingAs = (asIdx >= 0 && asIdx < ex.length) ? ex[asIdx] : "";
            }
            if (existingAs == null || existingAs.isEmpty()) {
                // not present -> add
                String[] newFields = new String[cols.size()];
                for (int i = 0; i < newFields.length; i++) newFields[i] = "";
                if (prefixIdx >= 0) newFields[prefixIdx] = p;
                if (asIdx >= 0) newFields[asIdx] = as_path;
                rowsMap.put(p, newFields);
                changed.add(p);
                // log addition to in-memory map
                logQueue.offer("Added to memory: prefix=" + p + "  AS_PATH=" + as_path);
            } else if (isShorterAsPath(as_path, existingAs)) {
                String[] newFields = new String[cols.size()];
                for (int i = 0; i < newFields.length; i++) newFields[i] = "";
                if (prefixIdx >= 0) newFields[prefixIdx] = p;
                if (asIdx >= 0) newFields[asIdx] = as_path;
                rowsMap.put(p, newFields);
                changed.add(p);
                // log update to in-memory AS_PATH
                logQueue.offer("Updated in memory: prefix=" + p + "  AS_PATH=" + as_path);
            }
        }

        // Rewrite CSV with final rowsMap (preserve column order)
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)))) {
            pw.println(String.join(",", cols));
            for (String key : rowsMap.keySet()) {
                String[] fields = rowsMap.get(key);
                java.util.List<String> outFields = new java.util.ArrayList<>();
                for (int i = 0; i < cols.size(); i++) {
                    String v = (i < fields.length && fields[i] != null) ? fields[i] : "";
                    outFields.add(escapeCsv(v));
                }
                pw.println(String.join(",", outFields));
            }
        } finally {
            mrt.close();
            input.close();
            // Enqueue log messages only for actually changed/added prefixes
            int asIdx = indexOfIgnoreCase(cols, "as_path");
            for (String pfx : changed) {
                String[] f = rowsMap.get(pfx);
                String asVal = (asIdx >= 0 && asIdx < f.length) ? f[asIdx] : "";
                logQueue.offer("Wrote: prefix=" + pfx + "  AS_PATH=" + asVal);
            }
            logQueue.offer("__END__");
            // wait briefly for logger to finish printing (non-blocking long wait)
            try {
                loggerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
            // try IPv6 - rely on InetAddress for parsing
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

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (needQuote) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static java.util.List<String> parseHeader(String headerLine) {
        java.util.List<String> cols = parseCsvLine(headerLine);
        for (int i = 0; i < cols.size(); i++) cols.set(i, cols.get(i).trim());
        return cols;
    }

    private static int indexOfIgnoreCase(java.util.List<String> list, String key) {
        if (list == null || key == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (key.equalsIgnoreCase(list.get(i))) return i;
        }
        return -1;
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

    private static boolean isShorterAsPath(String a, String b) {
        if (a == null) return false;
        if (b == null || b.trim().isEmpty()) return true;
        String asA = a.trim();
        String asB = b.trim();
        if (asA.isEmpty()) return false;
        int na = asA.split("\\s+").length;
        int nb = asB.isEmpty() ? Integer.MAX_VALUE : asB.split("\\s+").length;
        return na < nb;
    }

    /**
     * 从 MRTRecord 中获取 prefix 和 as_path。
     * 优先使用 mrtRecord.getPrefix() 与 mrtRecord.getASPath() 方法（如果可用），
     * 否则回退到使用 mrtRecord.toString() 并按 '|' 分割的旧解析方式。
     * 返回长度为 2 的数组：{ prefix, as_path }，任何为 null 的值将被转换为空字符串。
     */
    private static String[] getRouteFromLine(MRTRecord mrtRecord) {
        if (mrtRecord == null) return new String[] {"", ""};
        String prefix = "";
        String as_path = "";
        try {
            // 尝试直接调用 API 方法（按要求使用 getPrefix()/getASPath()），
            // 这些方法可能返回专用类型（如 Prefix/ASPath），因此安全地调用 toString()
            try {
                Object pfxObj = mrtRecord.getPrefix();
                if (pfxObj != null) prefix = pfxObj.toString();
            } catch (Throwable t) {
                // ignore, will fallback
            }
            try {
                Object asObj = mrtRecord.getASPath();
                if (asObj != null) as_path = asObj.toString();
            } catch (Throwable t) {
                // ignore, will fallback
            }
        } catch (Throwable t) {
            // ignore outer
        }

        // 如果任一为空，则回退到原始 toString() 解析（保持向后兼容）
        if ((prefix == null || prefix.isEmpty()) || (as_path == null || as_path.isEmpty())) {
            String s = mrtRecord == null ? "" : mrtRecord.toString();
            String[] parts = s.split("\\|");
            if (prefix == null || prefix.isEmpty()) prefix = parts.length > 5 ? parts[5] : "";
            if (as_path == null || as_path.isEmpty()) as_path = parts.length > 6 ? parts[6] : "";
        }
        if (prefix == null) prefix = "";
        if (as_path == null) as_path = "";
        return new String[] {prefix, as_path};
    }
}
