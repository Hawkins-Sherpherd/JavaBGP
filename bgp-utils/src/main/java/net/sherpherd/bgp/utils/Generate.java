// Generate.java - 添加 generateRouteFromMRTToCSV 方法
package net.sherpherd.bgp.utils;

import java.util.*;

public class Generate {
    
    /**
     * 从CSVProvider中获取路由数据，并将prefix列的数据按行输出到RawTextProvider中
     */
    public static void generateRawRouteFromCSVToRawText(CSVProvider in, RawTextProvider out) {
        String[] route;
        while ((route = in.getNextRoute()) != null) {
            // 获取prefix列的数据
            String prefix = route[0]; // 假设prefix是第一列
            out.setRoute(-1, new String[]{prefix}); // 使用-1表示追加到末尾
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
        
        // 将处理后的路由写入CSVProvider
        for (String[] routeData : routeMap.values()) {
            out.setRoute(-1, routeData); // 使用-1表示追加到末尾
        }
        
        // 关闭MRTProvider资源
        in.close();
        
        if (DataProvider.verbose) {
            System.out.println("MRT到CSV转换完成，共处理 " + routeMap.size() + " 条路由");
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
}