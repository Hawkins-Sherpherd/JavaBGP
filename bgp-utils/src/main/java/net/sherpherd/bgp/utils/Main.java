package net.sherpherd.bgp.utils;

import java.util.*;
import java.util.Scanner;

public class Main {   
    public static boolean verbose = false;
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runInteractiveMode();
            return;
        }
        
        if (parseArgForFlag(args, "-I")) {
            runInteractiveMode();
            return;
        }
        
        if (parseArgForFlag(args, "-h")) {
            showHelp();
            return;
        }
        
        if (parseArgForFlag(args, "-v")) {
            verbose = true;
        }
        
        String convertType = parseArgValue(args, "-c");
        String inputFile = parseArgValue(args, "-i");
        String outputFile = parseArgValue(args, "-o");
        List<String> regexList = parseArgValues(args, "-r");
        
        if (convertType == null) {
            System.err.println(I18nManager.getString("cli.error.missing.convert"));
            System.exit(1);
        }
        
        if (inputFile == null) {
            System.err.println(I18nManager.getString("cli.error.missing.input"));
            System.exit(1);
        }
        
        if (outputFile == null) {
            System.err.println(I18nManager.getString("cli.error.missing.output"));
            System.exit(1);
        }
        
        executeConversion(convertType, inputFile, outputFile, regexList, args);
    }
    
    private static void executeConversion(String convertType, String inputFile, String outputFile, 
                                          List<String> regexList, String[] args) {
        try {
            switch (convertType.toLowerCase()) {
                case "mrt2csv":
                    executeMRTToCSV(inputFile, outputFile, regexList);
                    break;
                case "csv2csv":
                    executeCSVToCSV(inputFile, outputFile, regexList);
                    break;
                case "csv2txt":
                    executeCSVToRawText(inputFile, outputFile, regexList);
                    break;
                case "txt2iproute2":
                    String nexthop = parseArgValue(args, "-n");
                    if (nexthop == null) {
                        System.err.println(I18nManager.getString("cli.error.missing.nexthop"));
                        System.exit(1);
                    }
                    executeRawTextToIproute2(inputFile, outputFile, nexthop);
                    break;
                default:
                    System.err.println(I18nManager.getString("cli.error.invalid.convert", convertType));
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println(I18nManager.getString("prompt.failed", e.getMessage()));
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static void executeMRTToCSV(String inputFile, String outputFile, List<String> regexList) throws Exception {
        MRTProvider in = new MRTProvider(inputFile);
        CSVProvider out = new CSVProvider(outputFile);
        Generate.generateRouteFromMRTToCSV(in, out, regexList, false);
        System.out.println(I18nManager.getString("prompt.complete"));
    }
    
    private static void executeCSVToCSV(String inputFile, String outputFile, List<String> regexList) throws Exception {
        CSVProvider in = new CSVProvider(inputFile);
        CSVProvider out = new CSVProvider(outputFile);
        Generate.generateRouteFromCSVToCSV(in, out, regexList, false);
        System.out.println(I18nManager.getString("prompt.complete"));
    }
    
    private static void executeCSVToRawText(String inputFile, String outputFile, List<String> regexList) throws Exception {
        CSVProvider in = new CSVProvider(inputFile);
        RawTextProvider out = new RawTextProvider(outputFile);
        Generate.generateRawRouteFromCSVToRawText(in, out, regexList, false);
        System.out.println(I18nManager.getString("prompt.complete"));
    }
    
    private static void executeRawTextToIproute2(String inputFile, String outputFile, String nexthop) throws Exception {
        RawTextProvider in = new RawTextProvider(inputFile);
        Iproute2ScriptProvider out = new Iproute2ScriptProvider(outputFile);
        Generate.generateScriptFromRawTextToIproute2(in, out, nexthop);
        System.out.println(I18nManager.getString("prompt.complete"));
    }
    
    private static boolean parseArgForFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }
    
    private static String parseArgValue(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(option)) {
                return args[i + 1];
            }
        }
        return null;
    }
    
    private static List<String> parseArgValues(String[] args, String option) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(option)) {
                values.add(args[i + 1]);
            }
        }
        return values.isEmpty() ? null : values;
    }
    
    private static void showHelp() {
        System.out.println(I18nManager.getString("cli.usage"));
        System.out.println();
        System.out.println(I18nManager.getString("cli.options"));
        System.out.println(I18nManager.getString("cli.opt.I"));
        System.out.println(I18nManager.getString("cli.opt.i"));
        System.out.println(I18nManager.getString("cli.opt.o"));
        System.out.println(I18nManager.getString("cli.opt.r"));
        System.out.println(I18nManager.getString("cli.opt.c"));
        System.out.println(I18nManager.getString("cli.opt.v"));
        System.out.println(I18nManager.getString("cli.opt.h"));
        System.out.println();
        System.out.println(I18nManager.getString("cli.examples"));
        System.out.println(I18nManager.getString("cli.ex1"));
        System.out.println(I18nManager.getString("cli.ex2"));
        System.out.println(I18nManager.getString("cli.ex3"));
        System.out.println(I18nManager.getString("cli.ex4"));
    }
    
    private static void runInteractiveMode() {
        selectLanguage();
        
        System.out.println("=== " + I18nManager.getString("app.title") + " ===");
        
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
                    processRawTextToIproute2();
                    break;
                case "5":
                    toggleVerbose();
                    break;
                case "6":
                    System.out.println(I18nManager.getString("app.goodbye"));
                    return;
                default:
                    System.out.println(I18nManager.getString("msg.invalid.selection"));
            }
        }
    }
    
    private static void selectLanguage() {
        System.out.println("\n=== Select Language / 选择语言 ===");
        System.out.println("1. English");
        System.out.println("2. 简体中文");
        System.out.print("选择 / Select (1-2): ");
        
        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                I18nManager.setLocale(Locale.US);
                break;
            case "2":
                I18nManager.setLocale(Locale.SIMPLIFIED_CHINESE);
                break;
            default:
                I18nManager.setLocale(Locale.US);
        }
    }
    
    private static void showMainMenu() {
        System.out.println("\n=== " + I18nManager.getString("menu.title") + " ===");
        System.out.println("1. " + I18nManager.getString("menu.option1"));
        System.out.println("2. " + I18nManager.getString("menu.option2"));
        System.out.println("3. " + I18nManager.getString("menu.option3"));
        System.out.println("4. " + I18nManager.getString("menu.option4"));
        System.out.println("5. " + I18nManager.getString("menu.option5", I18nManager.getString(verbose ? "msg.on" : "msg.off")));
        System.out.println("6. " + I18nManager.getString("menu.option6"));
        System.out.print(I18nManager.getString("menu.prompt"));
    }
    
    private static void processMRTToCSV() {
        System.out.println("\n=== " + I18nManager.getString("mrt.title") + " ===");
        
        String inputFile = getInput(I18nManager.getString("prompt.input"));
        String outputFile = getInput(I18nManager.getString("prompt.output"));
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print(I18nManager.getString("prompt.confirm"));
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println(I18nManager.getString("prompt.cancel"));
            return;
        }
        
        try {
            MRTProvider in = new MRTProvider(inputFile);
            CSVProvider out = new CSVProvider(outputFile);
            
            Generate.generateRouteFromMRTToCSV(in, out, aspathRegex, aggregate);
            
            System.out.println(I18nManager.getString("prompt.complete"));
        } catch (Exception e) {
            System.err.println(I18nManager.getString("prompt.failed", e.getMessage()));
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processCSVToCSV() {
        System.out.println("\n=== " + I18nManager.getString("csv.title") + " ===");
        
        String inputFile = getInput(I18nManager.getString("prompt.input"));
        String outputFile = getInput(I18nManager.getString("prompt.output"));
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print(I18nManager.getString("prompt.confirm"));
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println(I18nManager.getString("prompt.cancel"));
            return;
        }
        
        try {
            CSVProvider in = new CSVProvider(inputFile);
            CSVProvider out = new CSVProvider(outputFile);
            
            Generate.generateRouteFromCSVToCSV(in, out, aspathRegex, aggregate);
            
            System.out.println(I18nManager.getString("prompt.complete"));
        } catch (Exception e) {
            System.err.println(I18nManager.getString("prompt.failed", e.getMessage()));
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processCSVToRawText() {
        System.out.println("\n=== " + I18nManager.getString("raw.title") + " ===");
        
        String inputFile = getInput(I18nManager.getString("prompt.input"));
        String outputFile = getInput(I18nManager.getString("prompt.output"));
        List<String> aspathRegex = getASPathRegex();
        boolean aggregate = getAggregateOption();
        
        System.out.print(I18nManager.getString("prompt.confirm"));
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println(I18nManager.getString("prompt.cancel"));
            return;
        }
        
        try {
            CSVProvider in = new CSVProvider(inputFile);
            RawTextProvider out = new RawTextProvider(outputFile);
            
            Generate.generateRawRouteFromCSVToRawText(in, out, aspathRegex, aggregate);
            
            System.out.println(I18nManager.getString("prompt.complete"));
        } catch (Exception e) {
            System.err.println(I18nManager.getString("prompt.failed", e.getMessage()));
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    private static void processRawTextToIproute2() {
        System.out.println("\n=== " + I18nManager.getString("iproute2.title") + " ===");
        
        String inputFile = getInput(I18nManager.getString("prompt.input"));
        String outputFile = getInput(I18nManager.getString("prompt.output"));
        String nexthop = getInput(I18nManager.getString("prompt.nexthop"));
        
        System.out.print(I18nManager.getString("prompt.confirm"));
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println(I18nManager.getString("prompt.cancel"));
            return;
        }
        
        try {
            RawTextProvider in = new RawTextProvider(inputFile);
            Iproute2ScriptProvider out = new Iproute2ScriptProvider(outputFile);
            
            Generate.generateScriptFromRawTextToIproute2(in, out, nexthop);
            
            System.out.println(I18nManager.getString("prompt.complete"));
        } catch (Exception e) {
            System.err.println(I18nManager.getString("prompt.failed", e.getMessage()));
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
        System.out.println(I18nManager.getString("prompt.regex"));
        
        while (true) {
            System.out.print(I18nManager.getString("prompt.regex.input"));
            String regex = scanner.nextLine().trim();
            if (regex.isEmpty()) {
                break;
            }
            regexList.add(regex);
        }
        
        if (regexList.isEmpty()) {
            System.out.println(I18nManager.getString("msg.no.regex"));
        }
        
        return regexList.isEmpty() ? null : regexList;
    }
    
    private static boolean getAggregateOption() {
        System.out.print(I18nManager.getString("prompt.aggregate"));
        String choice = scanner.nextLine().trim();
        return choice.equalsIgnoreCase("y");
    }
    
    private static void toggleVerbose() {
        verbose = !verbose;
        String status = verbose ? "enabled" : "disabled";
        System.out.println(I18nManager.getString("msg.debug.toggle", status));
    }
}
