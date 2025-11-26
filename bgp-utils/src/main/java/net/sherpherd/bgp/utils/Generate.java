// Generate.java - 添加所需方法
package net.sherpherd.bgp.utils;

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
}