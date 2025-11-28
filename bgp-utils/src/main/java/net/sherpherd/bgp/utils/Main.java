package net.sherpherd.bgp.utils;

import java.util.*;
import java.util.Scanner;

public class Main {   
    public static boolean verbose = false;
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== BGP 路由处理工具 ===");
        
        while (true) {
            showMainMenu();
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    processMRTToCSV();
                    break;
                case "2":
                    processCSVToCSV();
                    break;
                case "3":
                    processCSVToRawText();
                    break;
                case "4":
                    processCSVToIproute2();
                    break;
                case "5":
                    toggleVerbose();
                    break;
                case "6":
                    System.out.println("感谢使用，再见！");
                    return;
                default:
                    System.out.println("无效选择，请重新输入。");
            }
        }
    }
    
    private static void showMainMenu() {
        System.out.println("\n=== 主菜单 ===");
        System.out.println("1. 从 MRT 文件提取路由到 CSV");
        System.out.println("2. 从 CSV 文件提取路由到 CSV");
        System.out.println("3. 从 CSV 文件提取路由到纯文本");
        System.out.println("4. 从 CSV 文件生成 iproute2 路由添加脚本");
        System.out.println("5. 启用调试输出（当前模式：" + (verbose ? "开启" : "关闭") + "）");
        System.out.println("6. 退出程序");
        System.out.print("请选择操作 (1-6): ");
    }
    
    private static void processMRTToCSV() {
        System.out.println("\n=== 从 MRT 文件提取路由到 CSV ===");
        
        String inputFile = getInput("输入文件: ");
        String outputFile = getInput("输出文件: ");
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print("确认执行? (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("操作已取消。");
            return;
        }
        
        try {
            MRTProvider in = new MRTProvider(inputFile);
            CSVProvider out = new CSVProvider(outputFile);
            
            Generate.generateRouteFromMRTToCSV(in, out, aspathRegex, aggregate);
            
            System.out.println("操作完成！");
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processCSVToCSV() {
        System.out.println("\n=== 从 CSV 文件提取路由到 CSV ===");
        
        String inputFile = getInput("输入文件: ");
        String outputFile = getInput("输出文件: ");
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print("确认执行? (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("操作已取消。");
            return;
        }
        
        try {
            CSVProvider in = new CSVProvider(inputFile);
            CSVProvider out = new CSVProvider(outputFile);
            
            Generate.generateRouteFromCSVToCSV(in, out, aspathRegex, aggregate);
            
            System.out.println("操作完成！");
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processCSVToRawText() {
        System.out.println("\n=== 从 CSV 文件提取路由到纯文本 ===");
        
        String inputFile = getInput("输入文件: ");
        String outputFile = getInput("输出文件: ");
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print("确认执行? (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("操作已取消。");
            return;
        }
        
        try {
            CSVProvider in = new CSVProvider(inputFile);
            RawTextProvider out = new RawTextProvider(outputFile);
            
            Generate.generateRawRouteFromCSVToRawText(in, out, aspathRegex, aggregate);
            
            System.out.println("操作完成！");
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processCSVToIproute2() {
        System.out.println("\n=== 从 CSV 文件生成 iproute2 路由添加脚本 ===");
        
        String inputFile = getInput("输入文件: ");
        String outputFile = getInput("输出文件: ");
        String nexthop = getInput("下一跳地址: ");
        
        System.out.print("确认执行? (y/N): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("操作已取消。");
            return;
        }
        
        try {
            RawTextProvider in = new RawTextProvider(inputFile);
            Iproute2ScriptProvider out = new Iproute2ScriptProvider(outputFile);
            
            Generate.generateScriptFromRawTextToIproute2(in, out, nexthop);
            
            System.out.println("操作完成！");
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static String getInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
    
    private static List<String> getASPathRegex() {
        List<String> regexList = new ArrayList<>();
        System.out.println("AS_PATH 正则表达式 (输入空行结束):");
        
        while (true) {
            System.out.print("正则表达式: ");
            String regex = scanner.nextLine().trim();
            if (regex.isEmpty()) {
                break;
            }
            regexList.add(regex);
        }
        
        return regexList.isEmpty() ? null : regexList;
    }
    
    private static boolean getAggregateOption() {
        System.out.print("是否聚合路由? (y/N): ");
        String choice = scanner.nextLine().trim();
        return choice.equalsIgnoreCase("y");
    }
    
    private static void toggleVerbose() {
        verbose = !verbose;
        System.out.println("调试输出已" + (verbose ? "开启" : "关闭"));
    }
}