// Generate.java - 适配新版本 CiscoIOSRegex
package net.sherpherd.bgp.utils;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

public class Generate {
    
    /**
     * 从CSVProvider中获取路由数据，并将prefix列的数据按行输出到RawTextProvider中
     */
    public static void generateRawRouteFromCSVToRawText(CSVProvider in, RawTextProvider out) {
        generateRawRouteFromCSVToRawText(in, out, null, false);
    }
    
    /**
     * 从CSVProvider中获取路由数据，并将prefix列的数据按行输出到RawTextProvider中
     * @param in 输入的CSVProvider
     * @param out 输出的RawTextProvider
     * @param aspath_regex 用于匹配AS_PATH的正则表达式列表，可为null
     * @param aggregate 是否对路由进行聚合
     */
    public static void generateRawRouteFromCSVToRawText(CSVProvider in, RawTextProvider out, 
                                                       List<String> aspath_regex, boolean aggregate) {
        // 检查输入文件
        checkInputFile(in.path);
        
        // 准备输出文件
        prepareOutputFile(out.path);
        
        String[] route;
        List<String> prefixes = new ArrayList<>();
        
        while ((route = in.getNextRoute()) != null) {
            // 检查AS_PATH匹配
            if (aspath_regex != null && !aspath_regex.isEmpty() && route.length > 1) {
                String asPath = route[1];
                boolean matched = false;
                for (String regex : aspath_regex) {
                    // 使用 CiscoIOSRegex 静态方法进行匹配
                    if (CiscoIOSRegex.matches(asPath, regex)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    continue; // 不匹配任何正则表达式，跳过该路由
                }
            }
            
            // 获取prefix列的数据
            String prefix = route[0]; // 假设prefix是第一列
            prefixes.add(prefix);
        }
        
        // 如果需要进行聚合
        if (aggregate) {
            try {
                // 创建临时CSV文件进行聚合处理
                List<String> aggregatedPrefixes = aggregatePrefixes(prefixes);
                for (String prefix : aggregatedPrefixes) {
                    out.setRoute(-1, new String[]{prefix});
                }
            } catch (IOException e) {
                System.err.println(I18nManager.getString("debug.aggregate.failed", e.getMessage()));
                for (String prefix : prefixes) {
                    out.setRoute(-1, new String[]{prefix});
                }
            }
        } else {
            // 不进行聚合，直接输出
            for (String prefix : prefixes) {
                out.setRoute(-1, new String[]{prefix});
            }
        }
        
        // 写入文件
        try {
            out.writeToFile();
        } catch (IOException e) {
            throw new RuntimeException("写入输出文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从RawTextProvider中获取路由数据，并通过Iproute2ScriptProvider逐行生成路由添加脚本
     * @param in 输入的RawTextProvider
     * @param out 输出的Iproute2ScriptProvider
     * @param nexthop 下一跳地址
     */
    public static void generateScriptFromRawTextToIproute2(RawTextProvider in, Iproute2ScriptProvider out, String nexthop) {
        // 检查输入文件
        checkInputFile(in.path);
        
        // 准备输出文件
        prepareOutputFile(out.path);
        
        // 初始化输出Provider的writer
        try {
            out.initializeWriter();
        } catch (IOException e) {
            throw new RuntimeException("初始化输出文件失败: " + e.getMessage(), e);
        }
        
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            String prefix = route[0];
            out.addRoute(prefix, nexthop);
        }
        
        // 关闭输出文件
        out.close();
    }
    
    /**
     * 从MRTProvider中读取路由数据并写入CSVProvider，实现MRT到CSV的转换
     * 保持最短AS_PATH的原则，并过滤无效路由
     */
    public static void generateRouteFromMRTToCSV(MRTProvider in, CSVProvider out) {
        generateRouteFromMRTToCSV(in, out, null, false);
    }
    
    /**
     * 从MRTProvider中读取路由数据并写入CSVProvider，实现MRT到CSV的转换
     * 保持最短AS_PATH的原则，并过滤无效路由
     * @param in 输入的MRTProvider
     * @param out 输出的CSVProvider
     * @param aspath_regex 用于匹配AS_PATH的正则表达式列表，可为null
     * @param aggregate 是否对路由进行聚合
     */
    public static void generateRouteFromMRTToCSV(MRTProvider in, CSVProvider out, 
                                                List<String> aspath_regex, boolean aggregate) {
        if (in == null || out == null) {
            throw new IllegalArgumentException("MRTProvider 和 CSVProvider 不能为 null");
        }
        
        // 检查输入文件
        checkInputFile(in.path);
        
        // 准备输出文件
        prepareOutputFile(out.path);
        
        // 用于存储前缀到最短AS_PATH的映射
        Map<String, String[]> routeMap = new LinkedHashMap<>();
        
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            if (route.length < 2) continue;
            
            String prefix = route[0];
            String asPath = route[1];
            
            // 检查AS_PATH匹配
            if (aspath_regex != null && !aspath_regex.isEmpty()) {
                boolean matched = false;
                for (String regex : aspath_regex) {
                    // 使用 CiscoIOSRegex 静态方法进行匹配
                    if (CiscoIOSRegex.matches(asPath, regex)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    continue; // 不匹配任何正则表达式，跳过该路由
                }
            }
            
            // 如果前缀已存在，比较AS_PATH长度，保留较短的
            if (routeMap.containsKey(prefix)) {
                String[] existingRoute = routeMap.get(prefix);
                String existingAsPath = existingRoute.length > 1 ? existingRoute[1] : "";
                
                if (Analysis.isShorterAsPath(asPath, existingAsPath)) {
                    routeMap.put(prefix, route);
                    if (Main.verbose) {
                        System.out.println(I18nManager.getString("debug.aspath.update", prefix, asPath));
                    }
                }
            } else {
                routeMap.put(prefix, route);
                if (Main.verbose) {
                    System.out.println(I18nManager.getString("debug.route.added", prefix, asPath));
                }
            }
        }
        
        // 处理聚合
        List<String[]> outputRoutes;
        if (aggregate) {
            outputRoutes = aggregateRoutes(routeMap.values());
        } else {
            outputRoutes = new ArrayList<>(routeMap.values());
        }
        
        // 将处理后的路由写入CSVProvider
        for (String[] routeData : outputRoutes) {
            out.setRoute(-1, routeData); // 使用-1表示追加到末尾
        }
        
        // 写入文件
        try {
            out.writeToFile();
        } catch (IOException e) {
            throw new RuntimeException("写入输出文件失败: " + e.getMessage(), e);
        }
        
        // 关闭MRTProvider资源
        in.close();
        
        if (Main.verbose) {
            System.out.println(I18nManager.getString("debug.mrt2csv.complete", outputRoutes.size()));
        }
    }

    /**
     * 从CSVProvider中读取路由数据并写入另一个CSVProvider，实现CSV到CSV的转换
     * 支持AS_PATH正则过滤和路由聚合
     */
    public static void generateRouteFromCSVToCSV(CSVProvider in, CSVProvider out) {
        generateRouteFromCSVToCSV(in, out, null, false);
    }

    /**
     * 从CSVProvider中读取路由数据并写入另一个CSVProvider，实现CSV到CSV的转换
     * 支持AS_PATH正则过滤和路由聚合
     * @param in 输入的CSVProvider
     * @param out 输出的CSVProvider
     * @param aspath_regex 用于匹配AS_PATH的正则表达式列表，可为null
     * @param aggregate 是否对路由进行聚合。如果为true，则输出的AS_PATH固定为0
     */
    public static void generateRouteFromCSVToCSV(CSVProvider in, CSVProvider out, 
                                            List<String> aspath_regex, boolean aggregate) {
        if (in == null || out == null) {
            throw new IllegalArgumentException("CSVProvider 输入和输出不能为 null");
        }
        
        // 检查输入文件
        checkInputFile(in.path);
        
        // 准备输出文件
        prepareOutputFile(out.path);
        
        // 用于存储前缀到路由数据的映射（保持最短AS_PATH原则）
        Map<String, String[]> routeMap = new LinkedHashMap<>();
        
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            if (route.length < 2) continue;
            
            String prefix = route[0];
            String asPath = route.length > 1 ? route[1] : "";
            
            // 验证路由数据的有效性
            if (!Analysis.isValidCIDR(prefix) || !Analysis.isValidAsPath(asPath)) {
                if (Main.verbose) {
                    System.err.println(I18nManager.getString("debug.invalid.route", prefix, asPath));
                }
                continue;
            }
            
            // 检查AS_PATH匹配 - 只要有一条正则匹配即通过
            if (aspath_regex != null && !aspath_regex.isEmpty()) {
                boolean matched = false;
                for (String regex : aspath_regex) {
                    // 使用 CiscoIOSRegex 静态方法进行匹配
                    if (CiscoIOSRegex.matches(asPath, regex)) {
                        matched = true;
                        break; // 有一条匹配就退出循环
                    }
                }
                if (!matched) {
                    continue; // 不匹配任何正则表达式，跳过该路由
                }
            }
            
            // 保持最短AS_PATH原则（复用MRT到CSV的逻辑）
            if (routeMap.containsKey(prefix)) {
                String[] existingRoute = routeMap.get(prefix);
                String existingAsPath = existingRoute.length > 1 ? existingRoute[1] : "";
                
                if (Analysis.isShorterAsPath(asPath, existingAsPath)) {
                    routeMap.put(prefix, route);
                    if (Main.verbose) {
                        System.out.println(I18nManager.getString("debug.aspath.update", prefix, asPath));
                    }
                }
            } else {
                routeMap.put(prefix, route);
                if (Main.verbose) {
                    System.out.println(I18nManager.getString("debug.route.added", prefix, asPath));
                }
            }
        }
        
        List<String[]> outputRoutes;
        if (aggregate) {
            outputRoutes = aggregateRoutesWithFixedASPath(routeMap.values(), "0");
            if (Main.verbose) {
                System.out.println(I18nManager.getString("debug.aggregate.complete", routeMap.size(), outputRoutes.size()));
            }
        } else {
            // 不聚合，直接使用处理后的路由
            outputRoutes = new ArrayList<>(routeMap.values());
        }
        
        // 将处理后的路由写入输出CSVProvider
        for (String[] routeData : outputRoutes) {
            out.setRoute(-1, routeData); // 使用-1表示追加到末尾
        }
        
        // 写入文件
        try {
            out.writeToFile();
        } catch (IOException e) {
            throw new RuntimeException("写入输出文件失败: " + e.getMessage(), e);
        }
        
        if (Main.verbose) {
            System.out.println(I18nManager.getString("debug.csv2csv.complete", outputRoutes.size()));
        }
    }

    /**
     * 对路由进行聚合，并将AS_PATH设置为固定值
     */
    private static List<String[]> aggregateRoutesWithFixedASPath(Collection<String[]> routes, String fixedASPath) {
        // 分离IPv4和IPv6路由
        List<String[]> ipv4Routes = new ArrayList<>();
        List<String[]> ipv6Routes = new ArrayList<>();
        
        for (String[] route : routes) {
            if (route.length > 0) {
                String prefix = route[0];
                if (Analysis.isValidIPv4Cidr(prefix)) {
                    ipv4Routes.add(route);
                } else if (Analysis.isValidIPv6Cidr(prefix)) {
                    ipv6Routes.add(route);
                }
            }
        }
        
        List<String[]> result = new ArrayList<>();
        
        try {
            // 聚合IPv4路由
            if (!ipv4Routes.isEmpty()) {
                List<String> ipv4Prefixes = new ArrayList<>();
                for (String[] route : ipv4Routes) {
                    ipv4Prefixes.add(route[0]);
                }
                
                List<String> aggregatedIPv4 = aggregatePrefixes(ipv4Prefixes);
                for (String prefix : aggregatedIPv4) {
                    // 创建新的路由数据，AS_PATH设置为固定值
                    String[] aggregatedRoute = createRouteWithFixedASPath(ipv4Routes.get(0), prefix, fixedASPath);
                    result.add(aggregatedRoute);
                }
            }
            
            // 聚合IPv6路由
            if (!ipv6Routes.isEmpty()) {
                List<String> ipv6Prefixes = new ArrayList<>();
                for (String[] route : ipv6Routes) {
                    ipv6Prefixes.add(route[0]);
                }
                
                List<String> aggregatedIPv6 = aggregatePrefixes(ipv6Prefixes);
                for (String prefix : aggregatedIPv6) {
                    // 创建新的路由数据，AS_PATH设置为固定值
                    String[] aggregatedRoute = createRouteWithFixedASPath(ipv6Routes.get(0), prefix, fixedASPath);
                    result.add(aggregatedRoute);
                }
            }
        } catch (IOException e) {
            System.err.println(I18nManager.getString("debug.aggregate.failed", e.getMessage()));
            for (String[] route : routes) {
                String[] fixedRoute = createRouteWithFixedASPath(route, route[0], fixedASPath);
                result.add(fixedRoute);
            }
        }
        
        return result;
    }

    /**
     * 创建带有固定AS_PATH的新路由数据
     */
    private static String[] createRouteWithFixedASPath(String[] templateRoute, String prefix, String fixedASPath) {
        String[] newRoute = Arrays.copyOf(templateRoute, templateRoute.length);
        newRoute[0] = prefix;
        
        // 设置AS_PATH为固定值
        if (newRoute.length > 1) {
            newRoute[1] = fixedASPath;
        }
        
        return newRoute;
    }
    
    /**
     * 检查输入文件是否存在
     * @param path 文件路径
     * @throws RuntimeException 如果文件不存在
     */
    private static void checkInputFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("输入文件不存在: " + path);
        }
        if (!file.isFile()) {
            throw new RuntimeException("输入路径不是文件: " + path);
        }
        if (!file.canRead()) {
            throw new RuntimeException("输入文件不可读: " + path);
        }
    }
    
    /**
     * 准备输出文件：如果不存在则创建，如果存在则覆盖
     * @param path 文件路径
     * @throws RuntimeException 如果文件创建失败
     */
    /**
 * 准备输出文件：如果不存在则创建，如果存在则直接覆盖
 * @param path 文件路径
 */
private static void prepareOutputFile(String path) {
    File file = new File(path);
    
    // 确保父目录存在
    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
        if (!parentDir.mkdirs()) {
            throw new RuntimeException("无法创建输出文件的父目录: " + parentDir.getAbsolutePath());
        }
        if (Main.verbose) {
            System.out.println(I18nManager.getString("debug.output.dir.created", parentDir.getAbsolutePath()));
        }
    }
    
    // 不删除现有文件，让写入操作自然覆盖
    if (file.exists()) {
        if (Main.verbose) {
            System.out.println(I18nManager.getString("debug.output.exists", path));
        }
    } else {
        try {
            if (!file.createNewFile()) {
                throw new RuntimeException("无法创建输出文件: " + path);
            }
            if (Main.verbose) {
                System.out.println(I18nManager.getString("debug.output.created", path));
            }
        } catch (IOException e) {
            throw new RuntimeException("创建输出文件失败: " + path + " - " + e.getMessage(), e);
        }
    }
}
    
    /**
     * 对前缀列表进行聚合
     */
    private static List<String> aggregatePrefixes(List<String> prefixes) throws IOException {
        // 分离IPv4和IPv6前缀
        List<String> ipv4Prefixes = new ArrayList<>();
        List<String> ipv6Prefixes = new ArrayList<>();
        
        for (String prefix : prefixes) {
            if (Analysis.isValidIPv4Cidr(prefix)) {
                ipv4Prefixes.add(prefix);
            } else if (Analysis.isValidIPv6Cidr(prefix)) {
                ipv6Prefixes.add(prefix);
            }
        }
        
        List<String> result = new ArrayList<>();
        
        // 聚合IPv4前缀
        if (!ipv4Prefixes.isEmpty()) {
            // 创建临时CSV文件进行聚合
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("ipv4_aggregate", ".csv");
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tempFile)) {
                writer.write("prefix\n");
                for (String prefix : ipv4Prefixes) {
                    writer.write(prefix + "\n");
                }
            }
            
            List<String> aggregatedIPv4 = Analysis.ipv4Aggregate(tempFile.toString());
            result.addAll(aggregatedIPv4);
            
            // 删除临时文件
            java.nio.file.Files.deleteIfExists(tempFile);
        }
        
        // 聚合IPv6前缀
        if (!ipv6Prefixes.isEmpty()) {
            // 创建临时CSV文件进行聚合
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("ipv6_aggregate", ".csv");
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(tempFile)) {
                writer.write("prefix\n");
                for (String prefix : ipv6Prefixes) {
                    writer.write(prefix + "\n");
                }
            }
            
            List<String> aggregatedIPv6 = Analysis.ipv6Aggregate(tempFile.toString());
            result.addAll(aggregatedIPv6);
            
            // 删除临时文件
            java.nio.file.Files.deleteIfExists(tempFile);
        }
        
        return result;
    }
    
    /**
     * 从RawTextProvider中读取路由前缀，计算其对于公网可路由地址空间的补集，输出到RawTextProvider
     * IPv4公网可路由地址空间：除去私有地址、保留地址等不可在公网路由的部分
     * IPv6公网可路由地址空间：2000::/3
     * @param in 输入的RawTextProvider
     * @param out 输出的RawTextProvider
     */
    public static void generateInvertedRoutes(RawTextProvider in, RawTextProvider out) {
        checkInputFile(in.path);
        prepareOutputFile(out.path);
        
        List<String> ipv4Prefixes = new ArrayList<>();
        List<String> ipv6Prefixes = new ArrayList<>();
        
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            String prefix = route[0];
            if (Analysis.isValidIPv4Cidr(prefix)) {
                ipv4Prefixes.add(prefix);
            } else if (Analysis.isValidIPv6Cidr(prefix)) {
                ipv6Prefixes.add(prefix);
            }
        }
        
        List<String> invertedPrefixes = new ArrayList<>();
        
        try {
            if (!ipv4Prefixes.isEmpty()) {
                List<String> ipv4Inverted = invertIPv4Routes(ipv4Prefixes);
                invertedPrefixes.addAll(ipv4Inverted);
                if (Main.verbose) {
                    System.out.println(I18nManager.getString("debug.invert.ipv4.complete", ipv4Prefixes.size(), ipv4Inverted.size()));
                }
            }
            
            if (!ipv6Prefixes.isEmpty()) {
                List<String> ipv6Inverted = invertIPv6Routes(ipv6Prefixes);
                invertedPrefixes.addAll(ipv6Inverted);
                if (Main.verbose) {
                    System.out.println(I18nManager.getString("debug.invert.ipv6.complete", ipv6Prefixes.size(), ipv6Inverted.size()));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("取反路由失败: " + e.getMessage(), e);
        }
        
        for (String prefix : invertedPrefixes) {
            out.setRoute(-1, new String[]{prefix});
        }
        
        try {
            out.writeToFile();
        } catch (IOException e) {
            throw new RuntimeException("写入输出文件失败: " + e.getMessage(), e);
        }
        
        if (Main.verbose) {
            System.out.println(I18nManager.getString("debug.invert.complete", invertedPrefixes.size()));
        }
    }
    
    /**
     * 计算IPv4路由的补集（相对于公网可路由地址空间）
     * 公网可路由IPv4地址空间包括：
     * - 1.0.0.0/8 到 9.0.0.0/8 (除去 10.0.0.0/8 私有)
     * - 11.0.0.0/8 到 100.0.0.0/8 (除去若干保留段)
     * - 101.0.0.0/8 到 126.0.0.0/8
     * - 128.0.0.0/8 到 169.253.0.0/16 (除去 127.0.0.0/8 回环, 169.254.0.0/16 链路本地)
     * - 170.0.0.0/8 到 172.15.0.0/12
     * - 172.32.0.0/8 到 191.0.0.0/8 (除去 172.16.0.0/12 私有)
     * - 192.0.1.0/24 到 192.167.255.255 (除去若干保留段)
     * - 192.169.0.0/8 到 223.255.255.255 (除去 192.168.0.0/16 私有)
     */
    private static List<String> invertIPv4Routes(List<String> prefixes) throws IOException {
        List<long[]> inputRanges = new ArrayList<>();
        for (String prefix : prefixes) {
            long[] range = ipv4CidrToRange(prefix);
            if (range != null) {
                inputRanges.add(range);
            }
        }
        
        if (inputRanges.isEmpty()) {
            return getPublicIPv4Space();
        }
        
        Collections.sort(inputRanges, Comparator.comparingLong(a -> a[0]));
        
        List<long[]> mergedInput = new ArrayList<>();
        long curS = inputRanges.get(0)[0];
        long curE = inputRanges.get(0)[1];
        for (int i = 1; i < inputRanges.size(); i++) {
            long s = inputRanges.get(i)[0];
            long e = inputRanges.get(i)[1];
            if (s <= curE + 1) {
                if (e > curE) curE = e;
            } else {
                mergedInput.add(new long[] {curS, curE});
                curS = s;
                curE = e;
            }
        }
        mergedInput.add(new long[] {curS, curE});
        
        List<long[]> publicSpace = getPublicIPv4Ranges();
        
        List<long[]> invertedRanges = new ArrayList<>();
        for (long[] publicRange : publicSpace) {
            List<long[]> currentRanges = new ArrayList<>();
            currentRanges.add(publicRange);
            
            for (long[] inputRange : mergedInput) {
                List<long[]> newRanges = new ArrayList<>();
                for (long[] current : currentRanges) {
                    newRanges.addAll(subtractRange(current, inputRange));
                }
                currentRanges = newRanges;
            }
            
            invertedRanges.addAll(currentRanges);
        }
        
        List<String> result = new ArrayList<>();
        for (long[] range : invertedRanges) {
            result.addAll(rangeToIPv4Cidrs(range[0], range[1]));
        }
        
        return result;
    }
    
    /**
     * 获取公网可路由IPv4地址空间范围列表
     */
    private static List<long[]> getPublicIPv4Ranges() {
        List<long[]> ranges = new ArrayList<>();
        
        ranges.add(new long[]{ipV4ToLong("1.0.0.0"), ipV4ToLong("9.255.255.255")});
        
        ranges.add(new long[]{ipV4ToLong("11.0.0.0"), ipV4ToLong("100.63.255.255")});
        ranges.add(new long[]{ipV4ToLong("100.128.0.0"), ipV4ToLong("126.255.255.255")});
        
        ranges.add(new long[]{ipV4ToLong("128.0.0.0"), ipV4ToLong("169.253.255.255")});
        ranges.add(new long[]{ipV4ToLong("170.0.0.0"), ipV4ToLong("172.15.255.255")});
        ranges.add(new long[]{ipV4ToLong("172.32.0.0"), ipV4ToLong("191.255.255.255")});
        
        ranges.add(new long[]{ipV4ToLong("192.0.1.0"), ipV4ToLong("192.167.255.255")});
        ranges.add(new long[]{ipV4ToLong("192.169.0.0"), ipV4ToLong("223.255.255.255")});
        
        return ranges;
    }
    
    /**
     * 获取完整的公网可路由IPv4地址空间CIDR列表
     */
    private static List<String> getPublicIPv4Space() throws IOException {
        List<String> result = new ArrayList<>();
        for (long[] range : getPublicIPv4Ranges()) {
            result.addAll(rangeToIPv4Cidrs(range[0], range[1]));
        }
        return result;
    }
    
    /**
     * 计算IPv6路由的补集（相对于2000::/3）
     */
    private static List<String> invertIPv6Routes(List<String> prefixes) throws IOException {
        List<BigInteger[]> inputRanges = new ArrayList<>();
        for (String prefix : prefixes) {
            BigInteger[] range = ipv6CidrToRange(prefix);
            if (range != null) {
                inputRanges.add(range);
            }
        }
        
        BigInteger[] publicSpace = getPublicIPv6Range();
        
        if (inputRanges.isEmpty()) {
            return rangeToIPv6Cidrs(publicSpace[0], publicSpace[1]);
        }
        
        Collections.sort(inputRanges, (a, b) -> a[0].compareTo(b[0]));
        
        List<BigInteger[]> mergedInput = new ArrayList<>();
        BigInteger curS = inputRanges.get(0)[0];
        BigInteger curE = inputRanges.get(0)[1];
        for (int i = 1; i < inputRanges.size(); i++) {
            BigInteger s = inputRanges.get(i)[0];
            BigInteger e = inputRanges.get(i)[1];
            if (s.compareTo(curE.add(BigInteger.ONE)) <= 0) {
                if (e.compareTo(curE) > 0) curE = e;
            } else {
                mergedInput.add(new BigInteger[] {curS, curE});
                curS = s;
                curE = e;
            }
        }
        mergedInput.add(new BigInteger[] {curS, curE});
        
        List<BigInteger[]> currentRanges = new ArrayList<>();
        currentRanges.add(publicSpace);
        
        for (BigInteger[] inputRange : mergedInput) {
            List<BigInteger[]> newRanges = new ArrayList<>();
            for (BigInteger[] current : currentRanges) {
                newRanges.addAll(subtractIPv6Range(current, inputRange));
            }
            currentRanges = newRanges;
        }
        
        List<String> result = new ArrayList<>();
        for (BigInteger[] range : currentRanges) {
            result.addAll(rangeToIPv6Cidrs(range[0], range[1]));
        }
        
        return result;
    }
    
    /**
     * 获取公网可路由IPv6地址空间范围 (2000::/3)
     */
    private static BigInteger[] getPublicIPv6Range() {
        BigInteger start = new BigInteger("20000000000000000000000000000000", 16);
        BigInteger end = new BigInteger("3fffffffffffffffffffffffffffffff", 16);
        return new BigInteger[] {start, end};
    }
    
    private static long[] ipv4CidrToRange(String cidr) {
        try {
            String[] parts = cidr.split("/");
            String ip = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            long ipNum = ipV4ToLong(ip);
            long mask = prefix == 0 ? 0L : (~0L) << (32 - prefix) & 0xffffffffL;
            long start = ipNum & mask;
            long size = (prefix == 32) ? 1L : (1L << (32 - prefix));
            long end = start + size - 1;
            return new long[] {start, end};
        } catch (Exception ex) {
            return null;
        }
    }
    
    private static long ipV4ToLong(String ip) {
        String[] oct = ip.split("\\.");
        long res = 0;
        for (int i = 0; i < 4; i++) {
            res = (res << 8) | (Integer.parseInt(oct[i]) & 0xff);
        }
        return res & 0xffffffffL;
    }
    
    private static String longToIPv4(long v) {
        return String.format("%d.%d.%d.%d", (v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
    }
    
    private static List<String> rangeToIPv4Cidrs(long start, long end) {
        List<String> out = new ArrayList<>();
        long cur = start;
        while (cur <= end) {
            long maxSize = cur & -cur;
            if (maxSize == 0) maxSize = 1L << 32;
            long rem = end - cur + 1;
            long block = maxSize;
            while (block > rem) {
                block >>= 1;
            }
            int prefixLen = 32 - (int)(Math.log(block) / Math.log(2));
            out.add(longToIPv4(cur) + "/" + prefixLen);
            cur += block;
        }
        return out;
    }
    
    private static List<long[]> subtractRange(long[] range, long[] subtract) {
        List<long[]> result = new ArrayList<>();
        
        if (subtract[1] < range[0] || subtract[0] > range[1]) {
            result.add(range);
            return result;
        }
        
        if (subtract[0] > range[0]) {
            result.add(new long[] {range[0], subtract[0] - 1});
        }
        
        if (subtract[1] < range[1]) {
            result.add(new long[] {subtract[1] + 1, range[1]});
        }
        
        return result;
    }
    
    private static BigInteger[] ipv6CidrToRange(String cidr) {
        try {
            String[] parts = cidr.split("/");
            String ip = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            BigInteger ipNum = ipV6ToBigInt(ip);
            BigInteger allOnes = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
            BigInteger mask;
            if (prefix == 0) {
                mask = BigInteger.ZERO;
            } else {
                mask = allOnes.shiftRight(128 - prefix).shiftLeft(128 - prefix);
            }
            BigInteger start = ipNum.and(mask);
            BigInteger size = (prefix == 128) ? BigInteger.ONE : BigInteger.ONE.shiftLeft(128 - prefix);
            BigInteger end = start.add(size).subtract(BigInteger.ONE);
            return new BigInteger[] {start, end};
        } catch (Exception ex) {
            return null;
        }
    }
    
    private static BigInteger ipV6ToBigInt(String ip) throws java.net.UnknownHostException {
        byte[] bytes = java.net.InetAddress.getByName(ip).getAddress();
        return new BigInteger(1, bytes);
    }
    
    private static String bigIntToIPv6(BigInteger v) throws java.net.UnknownHostException {
        byte[] bytes = toFixedLength(v.toByteArray(), 16);
        java.net.InetAddress addr = java.net.InetAddress.getByAddress(bytes);
        return addr.getHostAddress();
    }
    
    private static byte[] toFixedLength(byte[] src, int length) {
        byte[] dest = new byte[length];
        int srcPos = Math.max(0, src.length - length);
        int destPos = Math.max(0, length - src.length);
        int copyLen = Math.min(src.length, length);
        System.arraycopy(src, srcPos, dest, destPos, copyLen);
        return dest;
    }
    
    private static List<String> rangeToIPv6Cidrs(BigInteger start, BigInteger end) {
        List<String> out = new ArrayList<>();
        BigInteger cur = start;
        BigInteger one = BigInteger.ONE;
        while (cur.compareTo(end) <= 0) {
            int lowestSet;
            if (cur.equals(BigInteger.ZERO)) {
                lowestSet = 128;
            } else {
                lowestSet = cur.getLowestSetBit();
            }
            BigInteger maxBlock = one.shiftLeft(lowestSet);
            BigInteger rem = end.subtract(cur).add(one);
            BigInteger block = maxBlock;
            while (block.compareTo(rem) > 0) {
                block = block.shiftRight(1);
            }
            int log2 = block.bitLength() - 1;
            int prefixLen = 128 - log2;
            try {
                out.add(bigIntToIPv6(cur) + "/" + prefixLen);
            } catch (java.net.UnknownHostException e) {
                out.add(cur.toString(16) + "/" + prefixLen);
            }
            cur = cur.add(block);
        }
        return out;
    }
    
    private static List<BigInteger[]> subtractIPv6Range(BigInteger[] range, BigInteger[] subtract) {
        List<BigInteger[]> result = new ArrayList<>();
        
        if (subtract[1].compareTo(range[0]) < 0 || subtract[0].compareTo(range[1]) > 0) {
            result.add(range);
            return result;
        }
        
        if (subtract[0].compareTo(range[0]) > 0) {
            result.add(new BigInteger[] {range[0], subtract[0].subtract(BigInteger.ONE)});
        }
        
        if (subtract[1].compareTo(range[1]) < 0) {
            result.add(new BigInteger[] {subtract[1].add(BigInteger.ONE), range[1]});
        }
        
        return result;
    }
    
    /**
     * 对路由进行聚合（只聚合前缀，保留第一条路由的其他信息）
     */
    private static List<String[]> aggregateRoutes(Collection<String[]> routes) {
        // 分离IPv4和IPv6路由
        List<String[]> ipv4Routes = new ArrayList<>();
        List<String[]> ipv6Routes = new ArrayList<>();
        
        for (String[] route : routes) {
            if (route.length > 0) {
                String prefix = route[0];
                if (Analysis.isValidIPv4Cidr(prefix)) {
                    ipv4Routes.add(route);
                } else if (Analysis.isValidIPv6Cidr(prefix)) {
                    ipv6Routes.add(route);
                }
            }
        }
        
        List<String[]> result = new ArrayList<>();
        
        try {
            // 聚合IPv4路由
            if (!ipv4Routes.isEmpty()) {
                List<String> ipv4Prefixes = new ArrayList<>();
                for (String[] route : ipv4Routes) {
                    ipv4Prefixes.add(route[0]);
                }
                
                List<String> aggregatedIPv4 = aggregatePrefixes(ipv4Prefixes);
                for (String prefix : aggregatedIPv4) {
                    // 使用第一条IPv4路由的其他信息作为模板
                    String[] templateRoute = ipv4Routes.get(0);
                    String[] aggregatedRoute = Arrays.copyOf(templateRoute, templateRoute.length);
                    aggregatedRoute[0] = prefix;
                    result.add(aggregatedRoute);
                }
            }
            
            // 聚合IPv6路由
            if (!ipv6Routes.isEmpty()) {
                List<String> ipv6Prefixes = new ArrayList<>();
                for (String[] route : ipv6Routes) {
                    ipv6Prefixes.add(route[0]);
                }
                
                List<String> aggregatedIPv6 = aggregatePrefixes(ipv6Prefixes);
                for (String prefix : aggregatedIPv6) {
                    // 使用第一条IPv6路由的其他信息作为模板
                    String[] templateRoute = ipv6Routes.get(0);
                    String[] aggregatedRoute = Arrays.copyOf(templateRoute, templateRoute.length);
                    aggregatedRoute[0] = prefix;
                    result.add(aggregatedRoute);
                }
            }
        } catch (IOException e) {
            System.err.println(I18nManager.getString("debug.aggregate.failed", e.getMessage()));
            result.addAll(routes);
        }
        
        return result;
    }
}