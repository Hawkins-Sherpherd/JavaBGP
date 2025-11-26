// Generate.java - 添加 generateRouteFromMRTToCSV 方法
package net.sherpherd.bgp.utils;

import java.util.*;
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
     * @param aspath_regex 用于匹配AS_PATH的正则表达式列表，可为null
     * @param aggregate 是否对路由进行聚合
     */
    public static void generateRawRouteFromCSVToRawText(CSVProvider in, RawTextProvider out, 
                                                       List<String> aspath_regex, boolean aggregate) {
        String[] route;
        List<String> prefixes = new ArrayList<>();
        
        while ((route = in.getNextRoute()) != null) {
            // 检查AS_PATH匹配
            if (aspath_regex != null && !aspath_regex.isEmpty() && route.length > 1) {
                String asPath = route[1];
                boolean matched = false;
                for (String regex : aspath_regex) {
                    if (Analysis.aspathRegex(asPath, regex)) {
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
    }
    
    /**
     * 从RawTextProvider中获取路由数据，并通过Iproute2ScriptProvider逐行生成路由添加脚本
     */
    public static void generateScriptFromRawTextToIproute2(RawTextProvider in, Iproute2ScriptProvider out, String nexthop) {
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            String prefix = route[0];
            out.addRoute(prefix, nexthop);
        }
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
     * @param aspath_regex 用于匹配AS_PATH的正则表达式列表，可为null
     * @param aggregate 是否对路由进行聚合
     */
    public static void generateRouteFromMRTToCSV(MRTProvider in, CSVProvider out, 
                                                List<String> aspath_regex, boolean aggregate) {
        if (in == null || out == null) {
            throw new IllegalArgumentException("MRTProvider 和 CSVProvider 不能为 null");
        }
        
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
                    if (Analysis.aspathRegex(asPath, regex)) {
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
                
                if (isShorterAsPath(asPath, existingAsPath)) {
                    routeMap.put(prefix, route);
                    if (DataProvider.verbose) {
                        System.out.println("更新较短AS_PATH: prefix=" + prefix + " AS_PATH=" + asPath);
                    }
                }
            } else {
                // 新前缀，直接添加
                routeMap.put(prefix, route);
                if (DataProvider.verbose) {
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
        
        // 关闭MRTProvider资源
        in.close();
        
        if (DataProvider.verbose) {
            System.out.println("MRT到CSV转换完成，共处理 " + outputRoutes.size() + " 条路由");
        }
    }
    
    /**
     * 判断AS_PATH a是否比AS_PATH b更短
     */
    private static boolean isShorterAsPath(String a, String b) {
        if (a == null || a.trim().isEmpty()) return false;
        if (b == null || b.trim().isEmpty()) return true;
        
        String asA = a.trim();
        String asB = b.trim();
        
        int lengthA = asA.split("\\s+").length;
        int lengthB = asB.split("\\s+").length;
        
        return lengthA < lengthB;
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