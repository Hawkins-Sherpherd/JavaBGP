// DataProvider.java - 修改为抽象类并添加子类
package net.sherpherd.bgp.utils;

import java.io.*;
import java.util.*;
import org.javamrt.mrt.BGPFileReader;
import org.javamrt.mrt.MRTRecord;

public abstract class DataProvider {
    protected String path;
    protected static boolean verbose = false; // 全局 verbose 开关

    public DataProvider(String path) {
        this.path = path;
    }

    public static void setVerbose(boolean verbose) {
        DataProvider.verbose = verbose;
    }

    public abstract String[] getNextRoute();
    public abstract void setRoute(int index, String[] routeData);
    public abstract String[] getRoute(int index);
}

class CSVProvider extends DataProvider {
    private BufferedReader reader;
    private List<String[]> routes;
    private int currentLine;
    private String[] headers;
    private int prefixIndex = -1;
    private int aspathIndex = -1;
    private int communityIndex = -1;

    public CSVProvider(String path) {
        super(path);
        this.routes = new ArrayList<>();
        this.currentLine = 0;
        initialize();
    }

    private void initialize() {
        try {
            reader = new BufferedReader(new FileReader(path));
            // 读取头部
            String headerLine = reader.readLine();
            if (headerLine != null) {
                headers = parseCSVLine(headerLine);
                // 查找必要的列索引
                for (int i = 0; i < headers.length; i++) {
                    String header = headers[i].trim().toLowerCase();
                    if ("prefix".equals(header)) {
                        prefixIndex = i;
                    } else if ("aspath".equals(header) || "as_path".equals(header)) {
                        aspathIndex = i;
                    } else if ("community".equals(header)) {
                        communityIndex = i;
                    }
                }
                
                // 验证列结构
                if (prefixIndex == -1 || aspathIndex == -1) {
                    throw new IllegalArgumentException("CSV文件必须包含prefix和aspath列");
                }
                
                // 验证列组合
                boolean validStructure = (communityIndex == -1) || 
                                       (headers.length == 3 && prefixIndex != -1 && aspathIndex != -1 && communityIndex != -1);
                if (!validStructure) {
                    throw new IllegalArgumentException("CSV文件必须由prefix和aspath列组成，或者由prefix、aspath和community列组成");
                }
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化CSVProvider失败: " + e.getMessage());
            }
        }
    }

    @Override
    public String[] getNextRoute() {
        if (currentLine < routes.size()) {
            return routes.get(currentLine++);
        }
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = parseCSVLine(line);
                if (fields.length < headers.length) {
                    // 填充缺失的字段
                    String[] paddedFields = new String[headers.length];
                    System.arraycopy(fields, 0, paddedFields, 0, fields.length);
                    for (int i = fields.length; i < headers.length; i++) {
                        paddedFields[i] = "";
                    }
                    fields = paddedFields;
                }
                
                // 输入验证
                String prefix = fields[prefixIndex].trim();
                String aspath = fields[aspathIndex].trim();
                
                if (!isValidCIDR(prefix)) {
                    if (verbose) {
                        System.err.println("忽略无效CIDR: " + prefix);
                    }
                    continue;
                }
                
                if (!isValidAsPath(aspath)) {
                    if (verbose) {
                        System.err.println("忽略无效AS_PATH: " + aspath);
                    }
                    continue;
                }
                
                routes.add(fields);
                currentLine++;
                return fields;
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("读取CSV文件失败: " + e.getMessage());
            }
        }
        
        return null;
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        if (index == -1) {
            // -1 表示追加到末尾
            routes.add(routeData);
        } else if (index >= 0 && index < routes.size()) {
            routes.set(index, routeData);
        } else if (index == routes.size()) {
            routes.add(routeData);
        } else {
            throw new IndexOutOfBoundsException("索引超出范围: " + index);
        }
    }

    @Override
    public String[] getRoute(int index) {
        if (index >= 0 && index < routes.size()) {
            return routes.get(index);
        }
        return null;
    }

    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        
        return result.toArray(new String[0]);
    }

    private boolean isValidCIDR(String cidr) {
        return Analysis.isValidIPv4Cidr(cidr) || Analysis.isValidIPv6Cidr(cidr);
    }

    private boolean isValidAsPath(String aspath) {
        if (aspath == null || aspath.trim().isEmpty()) {
            return false;
        }
        
        String[] asns = aspath.trim().split("\\s+");
        final long MAX_ASN = 0xFFFFFFFFL; // 32位无符号整数最大值
        
        for (String asn : asns) {
            if (!asn.matches("\\d+")) {
                return false;
            }
            try {
                long value = Long.parseLong(asn);
                if (value < 0 || value > MAX_ASN) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
}

class RawTextProvider extends DataProvider {
    private BufferedReader reader;
    private List<String> routes;
    private int currentLine;

    public RawTextProvider(String path) {
        super(path);
        this.routes = new ArrayList<>();
        this.currentLine = 0;
        initialize();
    }

    private void initialize() {
        try {
            reader = new BufferedReader(new FileReader(path));
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化RawTextProvider失败: " + e.getMessage());
            }
        }
    }

    @Override
    public String[] getNextRoute() {
        if (currentLine < routes.size()) {
            return new String[]{routes.get(currentLine++)};
        }
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                // 输入验证：必须是合法的CIDR
                if (!isValidCIDR(line)) {
                    if (verbose) {
                        System.err.println("忽略无效CIDR: " + line);
                    }
                    continue;
                }
                
                routes.add(line);
                currentLine++;
                return new String[]{line};
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("读取文本文件失败: " + e.getMessage());
            }
        }
        
        return null;
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        if (routeData == null || routeData.length == 0) {
            throw new IllegalArgumentException("路由数据不能为空");
        }
        
        if (index == -1) {
            // -1 表示追加到末尾
            routes.add(routeData[0]);
        } else if (index >= 0 && index < routes.size()) {
            routes.set(index, routeData[0]);
        } else if (index == routes.size()) {
            routes.add(routeData[0]);
        } else {
            throw new IndexOutOfBoundsException("索引超出范围: " + index);
        }
    }

    @Override
    public String[] getRoute(int index) {
        if (index >= 0 && index < routes.size()) {
            return new String[]{routes.get(index)};
        }
        return null;
    }

    private boolean isValidCIDR(String cidr) {
        return Analysis.isValidIPv4Cidr(cidr) || Analysis.isValidIPv6Cidr(cidr);
    }
}

class Iproute2ScriptProvider extends DataProvider {
    private List<String> scripts;
    private PrintWriter writer;

    public Iproute2ScriptProvider(String path) {
        super(path);
        this.scripts = new ArrayList<>();
        initialize();
    }

    private void initialize() {
        try {
            writer = new PrintWriter(new FileWriter(path, false));
            // 写入脚本头
            writer.println("#!/bin/bash");
            writer.println("# Auto-generated iproute2 script");
            writer.println();
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化Iproute2ScriptProvider失败: " + e.getMessage());
            }
        }
    }

    @Override
    public String[] getNextRoute() {
        // 根据需求，返回空值
        return null;
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        // 不需要实现索引功能，因为脚本是按顺序生成的
    }

    @Override
    public String[] getRoute(int index) {
        // 根据需求，返回空值
        return null;
    }

    public void addRoute(String prefix, String nexthop) {
        // 输入验证：prefix必须是合法的IPv4/IPv6 CIDR
        if (!isValidCIDR(prefix)) {
            throw new IllegalArgumentException("无效的prefix: " + prefix);
        }
        
        // 输入验证：nexthop必须是合法的IPv4/IPv6地址（不带前缀长度）
        if (!isValidIPAddress(nexthop)) {
            throw new IllegalArgumentException("无效的nexthop: " + nexthop);
        }
        
        // 检查地址家族是否相同
        boolean prefixIsIPv4 = Analysis.isValidIPv4Cidr(prefix);
        boolean nexthopIsIPv4 = isIPv4Address(nexthop);
        
        if (prefixIsIPv4 != nexthopIsIPv4) {
            throw new IllegalArgumentException("prefix和nexthop必须属于相同的地址家族");
        }
        
        String command;
        if (prefixIsIPv4) {
            command = String.format("ip route add %s via %s", prefix, nexthop);
        } else {
            command = String.format("ip -6 route add %s via %s", prefix, nexthop);
        }
        
        scripts.add(command);
        writer.println(command);
        writer.flush();
    }

    public void close() {
        if (writer != null) {
            writer.close();
        }
    }

    private boolean isValidCIDR(String cidr) {
        return Analysis.isValidIPv4Cidr(cidr) || Analysis.isValidIPv6Cidr(cidr);
    }

    /**
     * 验证是否为合法的IPv4或IPv6地址（不带CIDR前缀）
     */
    private boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty() || ip.contains("/")) {
            return false;
        }
        return isIPv4Address(ip) || isIPv6Address(ip);
    }

    /**
     * 验证是否为合法的IPv4地址
     */
    private boolean isIPv4Address(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        try {
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 验证是否为合法的IPv6地址
     */
    private boolean isIPv6Address(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
            return addr.getAddress().length == 16;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}

// 在 DataProvider 类的末尾添加 MRTProvider 子类
class MRTProvider extends DataProvider {
    private BGPFileReader mrtReader;
    private InputStream inputStream;
    private MRTRecord currentRecord;
    private boolean initialized = false;

    public MRTProvider(String path) {
        super(path);
        initialize();
    }

    private void initialize() {
        try {
            inputStream = new BufferedInputStream(new FileInputStream(path));
            mrtReader = new BGPFileReader(inputStream);
            initialized = true;
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化MRTProvider失败: " + e.getMessage());
            }
            initialized = false;
        }
    }

    @Override
    public String[] getNextRoute() {
        if (!initialized || mrtReader == null) {
            return null;
        }

        try {
            currentRecord = mrtReader.readNext();
            if (currentRecord == null) {
                return null;
            }

            String[] route = getRouteFromMRTRecord(currentRecord);
            String prefix = route[0];
            String asPath = route[1];

            // 过滤默认路由和无效条目
            if (prefix.equals("0.0.0.0/0") || prefix.equals("::/0") || 
                !isValidCIDR(prefix) || !isValidAsPath(asPath)) {
                if (verbose) {
                    System.err.println("跳过无效路由: prefix=" + prefix + " AS_PATH=" + asPath);
                }
                return getNextRoute(); // 递归获取下一个有效路由
            }

            return route;
        } catch (Exception e) {
            if (verbose) {
                System.err.println("读取MRT记录失败: " + e.getMessage());
            }
            return null;
        }
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        // MRTProvider 是只读的，不支持设置路由
        throw new UnsupportedOperationException("MRTProvider 不支持设置路由");
    }

    @Override
    public String[] getRoute(int index) {
        // MRTProvider 不支持按索引获取路由
        throw new UnsupportedOperationException("MRTProvider 不支持按索引获取路由");
    }

    public void close() {
        try {
            if (mrtReader != null) {
                mrtReader.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("关闭MRTProvider失败: " + e.getMessage());
            }
        }
    }

    private String[] getRouteFromMRTRecord(MRTRecord record) {
        if (record == null) return new String[]{"", ""};
        
        String prefix = "";
        String asPath = "";
        
        try {
            // 尝试使用 getPrefix() 和 getASPath() 方法
            Object prefixObj = record.getPrefix();
            Object asPathObj = record.getASPath();
            
            if (prefixObj != null) prefix = prefixObj.toString();
            if (asPathObj != null) asPath = asPathObj.toString();
        } catch (Exception e) {
            // 回退到字符串解析
            String recordStr = record.toString();
            String[] parts = recordStr.split("\\|");
            if (parts.length > 5) prefix = parts[5];
            if (parts.length > 6) asPath = parts[6];
        }
        
        return new String[]{prefix != null ? prefix.trim() : "", 
                           asPath != null ? asPath.trim() : ""};
    }

    private boolean isValidCIDR(String cidr) {
        return Analysis.isValidIPv4Cidr(cidr) || Analysis.isValidIPv6Cidr(cidr);
    }

    private boolean isValidAsPath(String aspath) {
        if (aspath == null || aspath.trim().isEmpty()) {
            return false;
        }
        
        String[] asns = aspath.trim().split("\\s+");
        final long MAX_ASN = 0xFFFFFFFFL;
        
        for (String asn : asns) {
            if (!asn.matches("\\d+")) {
                return false;
            }
            try {
                long value = Long.parseLong(asn);
                if (value < 0 || value > MAX_ASN) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
}