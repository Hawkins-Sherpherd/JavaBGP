// Generate.java - 适配新版本 CiscoIOSRegex
package net.sherpherd.bgp.utils;

import java.util.*;
import java.io.File;
import java.io.IOException;

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
                // 聚合失败，使用原始前缀
                System.err.println("路由聚合失败，使用原始路由: " + e.getMessage());
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
                        System.out.println("更新较短AS_PATH: prefix=" + prefix + " AS_PATH=" + asPath);
                    }
                }
            } else {
                // 新前缀，直接添加
                routeMap.put(prefix, route);
                if (Main.verbose) {
                    System.out.println("添加新路由: prefix=" + prefix + " AS_PATH=" + asPath);
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
            System.out.println("MRT到CSV转换完成，共处理 " + outputRoutes.size() + " 条路由");
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
                    System.err.println("跳过无效路由: prefix=" + prefix + " AS_PATH=" + asPath);
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
                        System.out.println("更新较短AS_PATH: prefix=" + prefix + " AS_PATH=" + asPath);
                    }
                }
            } else {
                // 新前缀，直接添加
                routeMap.put(prefix, route);
                if (Main.verbose) {
                    System.out.println("添加新路由: prefix=" + prefix + " AS_PATH=" + asPath);
                }
            }
        }
        
        // 处理聚合
        List<String[]> outputRoutes;
        if (aggregate) {
            // 复用聚合方法，设置固定AS_PATH
            outputRoutes = aggregateRoutesWithFixedASPath(routeMap.values(), "0");
            if (Main.verbose) {
                System.out.println("路由聚合完成，从 " + routeMap.size() + " 条路由聚合为 " + outputRoutes.size() + " 条路由，AS_PATH固定为0");
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
            System.out.println("CSV到CSV转换完成，共处理 " + outputRoutes.size() + " 条路由");
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
            System.err.println("路由聚合失败，使用原始路由: " + e.getMessage());
            // 如果聚合失败，使用原始路由但AS_PATH仍设置为固定值
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
            System.out.println("已创建输出目录: " + parentDir.getAbsolutePath());
        }
    }
    
    // 不删除现有文件，让写入操作自然覆盖
    if (file.exists()) {
        if (Main.verbose) {
            System.out.println("输出文件已存在，将在写入时覆盖: " + path);
        }
    } else {
        try {
            if (!file.createNewFile()) {
                throw new RuntimeException("无法创建输出文件: " + path);
            }
            if (Main.verbose) {
                System.out.println("已创建输出文件: " + path);
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
            System.err.println("路由聚合失败，使用原始路由: " + e.getMessage());
            result.addAll(routes);
        }
        
        return result;
    }
}