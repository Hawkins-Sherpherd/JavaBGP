// DataProvider.java - 移除文件创建逻辑，仅负责读写
package net.sherpherd.bgp.utils;

import java.io.*;
import java.util.*;
import org.javamrt.mrt.BGPFileReader;
import org.javamrt.mrt.MRTRecord;

public abstract class DataProvider {
    protected String path;
    protected static boolean verbose = false;
    final static String CSV_PREFIX_COL_HEADER = "prefix";
    final static String CSV_ASPATH_COL_HEADER = "as_path";

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

// 添加创建文件接口
interface CreateProviderFile {
    /**
     * 创建文件
     * @param filePath 文件路径
     * @return 如果文件创建成功返回true，如果文件已存在或创建失败返回false
     */
    boolean createFile(String filePath);
}

class CSVProvider extends DataProvider implements CreateProviderFile {
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

    @Override
    public boolean createFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                if (verbose) {
                    System.out.println("CSV文件已存在: " + filePath);
                }
                return false;
            }
            
            // 创建目录（如果不存在）
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (file.createNewFile()) {
                // 写入CSV列首部
                try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
                    String headerLine = CSV_PREFIX_COL_HEADER + "," + CSV_ASPATH_COL_HEADER;
                    writer.println(headerLine);
                    
                    if (verbose) {
                        System.out.println("CSV文件创建成功并写入列首部: " + filePath);
                        System.out.println("列首部: " + headerLine);
                    }
                }
                return true;
            } else {
                if (verbose) {
                    System.err.println("CSV文件创建失败: " + filePath);
                }
                return false;
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("创建CSV文件时发生IO异常: " + e.getMessage());
            }
            return false;
        }
    }

    private void initialize() {
        try {
            File file = new File(path);
            
            // 文件必须存在才能读取
            if (!file.exists()) {
                if (verbose) {
                    System.err.println("CSV文件不存在: " + path);
                }
                return;
            }
            
            // 按读取模式初始化
            reader = new BufferedReader(new FileReader(path));
            String headerLine = reader.readLine();
            if (headerLine != null) {
                headers = parseCSVLine(headerLine);
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
                
                if (prefixIndex == -1 || aspathIndex == -1) {
                    throw new IllegalArgumentException("CSV文件必须包含prefix和as_path列");
                }
                
                boolean validStructure = (communityIndex == -1) || 
                                       (headers.length == 3 && prefixIndex != -1 && aspathIndex != -1 && communityIndex != -1);
                if (!validStructure) {
                    throw new IllegalArgumentException("CSV文件必须由prefix和as_path列组成，或者由prefix、as_path和community列组成");
                }
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化CSVProvider失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将内存中的路由数据写入CSV文件
     */
    public void writeToFile() throws IOException {
        // 如果headers为空，使用默认头部
        if (headers == null) {
            headers = new String[]{CSV_PREFIX_COL_HEADER, CSV_ASPATH_COL_HEADER};
            prefixIndex = 0;
            aspathIndex = 1;
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            // 写入头部
            writer.println(String.join(",", headers));
            
            // 写入每一行数据
            for (String[] route : routes) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < route.length; i++) {
                    if (i > 0) line.append(",");
                    String field = route[i];
                    // 如果字段包含逗号、引号或换行符，需要加引号并转义
                    if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
                        line.append("\"").append(field.replace("\"", "\"\"")).append("\"");
                    } else {
                        line.append(field);
                    }
                }
                writer.println(line.toString());
            }
            
            if (verbose) {
                System.out.println("已写入 " + routes.size() + " 条路由到文件: " + path);
            }
        }
    }

    @Override
    public String[] getNextRoute() {
        if (currentLine < routes.size()) {
            return routes.get(currentLine++);
        }
        
        // 如果没有reader，直接返回null
        if (reader == null) {
            return null;
        }
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = parseCSVLine(line);
                if (fields.length < headers.length) {
                    String[] paddedFields = new String[headers.length];
                    System.arraycopy(fields, 0, paddedFields, 0, fields.length);
                    for (int i = fields.length; i < headers.length; i++) {
                        paddedFields[i] = "";
                    }
                    fields = paddedFields;
                }
                
                String prefix = fields[prefixIndex].trim();
                String aspath = fields[aspathIndex].trim();
                
                if (!Analysis.isValidCIDR(prefix)) {
                    if (verbose) {
                        System.err.println("忽略无效CIDR: " + prefix);
                    }
                    continue;
                }
                
                if (!Analysis.isValidAsPath(aspath)) {
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
}

class RawTextProvider extends DataProvider implements CreateProviderFile {
    private BufferedReader reader;
    private List<String> routes;
    private int currentLine;

    public RawTextProvider(String path) {
        super(path);
        this.routes = new ArrayList<>();
        this.currentLine = 0;
        initialize();
    }

    @Override
    public boolean createFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                if (verbose) {
                    System.out.println("文本文件已存在: " + filePath);
                }
                return false;
            }
            
            // 创建目录（如果不存在）
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (file.createNewFile()) {
                if (verbose) {
                    System.out.println("文本文件创建成功: " + filePath);
                }
                return true;
            } else {
                if (verbose) {
                    System.err.println("文本文件创建失败: " + filePath);
                }
                return false;
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("创建文本文件时发生IO异常: " + e.getMessage());
            }
            return false;
        }
    }

    private void initialize() {
        try {
            File file = new File(path);
            
            // 文件必须存在才能读取
            if (!file.exists()) {
                if (verbose) {
                    System.err.println("文本文件不存在: " + path);
                }
                return;
            }
            
            // 按读取模式初始化
            reader = new BufferedReader(new FileReader(path));
            
        } catch (IOException e) {
            if (verbose) {
                System.err.println("初始化RawTextProvider失败: " + e.getMessage());
            }
        }
    }

    /**
     * 将内存中的路由数据写入文本文件
     */
    public void writeToFile() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            for (String route : routes) {
                writer.println(route);
            }
            
            if (verbose) {
                System.out.println("已写入 " + routes.size() + " 条路由到文件: " + path);
            }
        }
    }

    @Override
    public String[] getNextRoute() {
        if (currentLine < routes.size()) {
            return new String[]{routes.get(currentLine++)};
        }
        
        // 如果没有reader，直接返回null
        if (reader == null) {
            return null;
        }
        
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                if (!Analysis.isValidCIDR(line)) {
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
        
        String route = routeData[0];
        
        if (index == -1) {
            routes.add(route);
        } else if (index >= 0 && index < routes.size()) {
            routes.set(index, route);
        } else if (index == routes.size()) {
            routes.add(route);
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

    /**
     * 关闭读取流
     */
    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                if (verbose) {
                    System.err.println("关闭reader失败: " + e.getMessage());
                }
            }
            reader = null;
        }
    }
}

class Iproute2ScriptProvider extends DataProvider implements CreateProviderFile {
    private List<String> scripts;
    private PrintWriter writer;

    public Iproute2ScriptProvider(String path) {
        super(path);
        this.scripts = new ArrayList<>();
    }

    @Override
    public boolean createFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                if (verbose) {
                    System.out.println("脚本文件已存在: " + filePath);
                }
                return false;
            }
            
            // 创建目录（如果不存在）
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            if (file.createNewFile()) {
                if (verbose) {
                    System.out.println("脚本文件创建成功: " + filePath);
                }
                return true;
            } else {
                if (verbose) {
                    System.err.println("脚本文件创建失败: " + filePath);
                }
                return false;
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("创建脚本文件时发生IO异常: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 初始化写入流（延迟初始化，由Generate类调用）
     */
    public void initializeWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new FileWriter(path, false));
            // 写入脚本头
            writer.println("#!/bin/bash");
            writer.println("# Auto-generated iproute2 script");
            writer.println();
        }
    }

    @Override
    public String[] getNextRoute() {
        return null;
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        // 不需要实现索引功能，因为脚本是按顺序生成的
    }

    @Override
    public String[] getRoute(int index) {
        return null;
    }

    public void addRoute(String prefix, String nexthop) {
        if (!Analysis.isValidCIDR(prefix)) {
            throw new IllegalArgumentException("无效的prefix: " + prefix);
        }
        
        if (!Analysis.isValidIPAddress(nexthop)) {
            throw new IllegalArgumentException("无效的nexthop: " + nexthop);
        }
        
        boolean prefixIsIPv4 = Analysis.isValidIPv4Cidr(prefix);
        boolean nexthopIsIPv4 = Analysis.isValidIPv4Address(nexthop);
        
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
        if (writer != null) {
            writer.println(command);
            writer.flush();
        }
    }

    public void close() {
        if (writer != null) {
            writer.close();
            writer = null;
            
            if (verbose) {
                System.out.println("已写入 " + scripts.size() + " 条路由命令到文件: " + path);
            }
        }
    }
}

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
            File file = new File(path);
            
            // 文件必须存在
            if (!file.exists()) {
                if (verbose) {
                    System.err.println("MRT文件不存在: " + path);
                }
                initialized = false;
                return;
            }
            
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
            while ((currentRecord = mrtReader.readNext()) != null) {
                String[] route = getRouteFromMRTRecord(currentRecord);
                String prefix = route[0];
                String asPath = route[1];

                if (prefix.equals("0.0.0.0/0") || prefix.equals("::/0") || 
                    !Analysis.isValidCIDR(prefix) || !Analysis.isValidAsPath(asPath) ||
                    asPath == null || asPath.trim().isEmpty()) {
                    if (verbose) {
                        System.err.println("跳过无效路由: prefix=" + prefix + " AS_PATH=" + asPath);
                    }
                    continue;
                }

                return route;
            }
        } catch (Exception e) {
            if (verbose) {
                System.err.println("读取MRT记录失败: " + e.getMessage());
            }
        }
        
        return null;
    }

    @Override
    public void setRoute(int index, String[] routeData) {
        throw new UnsupportedOperationException("MRTProvider 不支持设置路由");
    }

    @Override
    public String[] getRoute(int index) {
        throw new UnsupportedOperationException("MRTProvider 不支持按索引获取路由");
    }

    public void close() {
        try {
            if (mrtReader != null) {
                mrtReader.close();
                mrtReader = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            if (verbose) {
                System.err.println("关闭MRTProvider失败: " + e.getMessage());
            }
        } catch (NullPointerException e) {
            if (verbose) {
                System.err.println("MRTProvider关闭时遇到空指针，可能已部分初始化: " + e.getMessage());
            }
        }
    }

    private String[] getRouteFromMRTRecord(MRTRecord record) {
        if (record == null) return new String[]{"", ""};
        
        String prefix = "";
        String asPath = "";
        
        try {
            Object prefixObj = record.getPrefix();
            Object asPathObj = record.getASPath();
            
            if (prefixObj != null) prefix = prefixObj.toString();
            if (asPathObj != null) asPath = asPathObj.toString();
        } catch (Exception e) {
            String recordStr = record.toString();
            String[] parts = recordStr.split("\\|");
            if (parts.length > 5) prefix = parts[5];
            if (parts.length > 6) asPath = parts[6];
        }
        
        return new String[]{prefix != null ? prefix.trim() : "", 
                           asPath != null ? asPath.trim() : ""};
    }
}