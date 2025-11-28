// Analysis.java 保持不变
package net.sherpherd.bgp.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
// import java.util.regex.Pattern;
// import java.util.regex.PatternSyntaxException;

public class Analysis {

    public static List<String> ipv4Aggregate(String csvPath) throws IOException {
        List<long[]> ranges = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();
            if (header == null) return Collections.emptyList();
            String[] cols = splitCsvLine(header);
            int prefixIdx = 0;
            for (int i = 0; i < cols.length; i++) {
                if ("prefix".equalsIgnoreCase(cols[i].trim())) {
                    prefixIdx = i;
                    break;
                }
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = splitCsvLine(line);
                if (parts.length <= prefixIdx) continue;
                String pref = parts[prefixIdx].trim();
                pref = stripQuotes(pref);
                if (!isValidIPv4Cidr(pref)) continue;
                long[] se = cidrToRange(pref);
                if (se != null) ranges.add(se);
            }
        }

        if (ranges.isEmpty()) return Collections.emptyList();

        // merge ranges
        Collections.sort(ranges, Comparator.comparingLong(a -> a[0]));
        List<long[]> merged = new ArrayList<>();
        long curS = ranges.get(0)[0];
        long curE = ranges.get(0)[1];
        for (int i = 1; i < ranges.size(); i++) {
            long s = ranges.get(i)[0];
            long e = ranges.get(i)[1];
            if (s <= curE + 1) {
                if (e > curE) curE = e;
            } else {
                merged.add(new long[] {curS, curE});
                curS = s; curE = e;
            }
        }
        merged.add(new long[] {curS, curE});

        // convert merged ranges to minimal CIDR blocks
        List<String> result = new ArrayList<>();
        for (long[] r : merged) {
            result.addAll(rangeToCidrs(r[0], r[1]));
        }

        return result;
    }

    public static long ipv4Summary(String csvPath) throws IOException {
        List<String> cidrs = ipv4Aggregate(csvPath);
        long total = 0L;
        for (String c : cidrs) {
            String[] parts = c.split("/");
            int prefix = Integer.parseInt(parts[1]);
            long block = 1L << (32 - prefix);
            total += block;
        }
        return total;
    }

    // --- helpers ---
    private static String[] splitCsvLine(String line) {
        // simple split, handles quoted commas roughly by removing surrounding quotes first
        // For our CSV it is expected to be simple
        return line.split(",");
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static boolean isValidIPv4Cidr(String s) {
        if (s == null) return false;
        String[] parts = s.split("/");
        if (parts.length != 2) return false;
        String ip = parts[0];
        String[] oct = ip.split("\\.");
        if (oct.length != 4) return false;
        try {
            for (String o : oct) {
                int v = Integer.parseInt(o);
                if (v < 0 || v > 255) return false;
            }
            int p = Integer.parseInt(parts[1]);
            if (p < 0 || p > 32) return false;
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    private static long[] cidrToRange(String cidr) {
        try {
            String[] parts = cidr.split("/");
            String ip = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            long ipNum = ipToLong(ip);
            long mask = prefix == 0 ? 0L : (~0L) << (32 - prefix) & 0xffffffffL;
            long start = ipNum & mask;
            long size = (prefix == 32) ? 1L : (1L << (32 - prefix));
            long end = start + size - 1;
            return new long[] {start, end};
        } catch (Exception ex) {
            return null;
        }
    }

    private static long ipToLong(String ip) {
        String[] oct = ip.split("\\.");
        long res = 0;
        for (int i = 0; i < 4; i++) {
            res = (res << 8) | (Integer.parseInt(oct[i]) & 0xff);
        }
        return res & 0xffffffffL;
    }

    private static String longToIp(long v) {
        return String.format("%d.%d.%d.%d", (v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
    }

    private static List<String> rangeToCidrs(long start, long end) {
        List<String> out = new ArrayList<>();
        long cur = start;
        while (cur <= end) {
            long maxSize = cur & -cur; // largest power-of-two block aligned at cur
            if (maxSize == 0) maxSize = 1L << 32; // safety
            long rem = end - cur + 1;
            long block = maxSize;
            while (block > rem) {
                block >>= 1;
            }
            int prefixLen = 32 - (int)(Math.log(block) / Math.log(2));
            out.add(longToIp(cur) + "/" + prefixLen);
            cur += block;
        }
        return out;
    }

    // --- IPv6 helpers and methods ---
    public static boolean isValidIPv6Cidr(String s) {
        if (s == null) return false;
        String[] parts = s.split("/");
        if (parts.length != 2) return false;
        String ip = parts[0];
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.getAddress().length != 16) return false;
            int p = Integer.parseInt(parts[1]);
            if (p < 0 || p > 128) return false;
        } catch (UnknownHostException | NumberFormatException ex) {
            return false;
        }
        return true;
    }

    private static BigInteger[] ipv6CidrToRange(String cidr) {
        try {
            String[] parts = cidr.split("/");
            String ip = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            BigInteger ipNum = ip6ToBigInt(ip);
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

    private static BigInteger ip6ToBigInt(String ip) throws UnknownHostException {
        byte[] bytes = InetAddress.getByName(ip).getAddress();
        return new BigInteger(1, bytes);
    }

    private static String bigIntToIp6(BigInteger v) throws UnknownHostException {
        byte[] bytes = toFixedLength(v.toByteArray(), 16);
        InetAddress addr = InetAddress.getByAddress(bytes);
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

    private static List<String> rangeToIpv6Cidrs(BigInteger start, BigInteger end) {
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
            int log2 = block.bitLength() - 1; // since block is power of two
            int prefixLen = 128 - log2;
            try {
                out.add(bigIntToIp6(cur) + "/" + prefixLen);
            } catch (UnknownHostException e) {
                out.add(cur.toString(16) + "/" + prefixLen);
            }
            cur = cur.add(block);
        }
        return out;
    }

    public static List<String> ipv6Aggregate(String csvPath) throws IOException {
        List<BigInteger[]> ranges = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();
            if (header == null) return Collections.emptyList();
            String[] cols = splitCsvLine(header);
            int prefixIdx = 0;
            for (int i = 0; i < cols.length; i++) {
                if ("prefix".equalsIgnoreCase(cols[i].trim())) {
                    prefixIdx = i;
                    break;
                }
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = splitCsvLine(line);
                if (parts.length <= prefixIdx) continue;
                String pref = parts[prefixIdx].trim();
                pref = stripQuotes(pref);
                if (!isValidIPv6Cidr(pref)) continue;
                BigInteger[] se = ipv6CidrToRange(pref);
                if (se != null) ranges.add(se);
            }
        }

        if (ranges.isEmpty()) return Collections.emptyList();

        // merge ranges
        Collections.sort(ranges, new Comparator<BigInteger[]>() {
            @Override
            public int compare(BigInteger[] a, BigInteger[] b) {
                return a[0].compareTo(b[0]);
            }
        });
        List<BigInteger[]> merged = new ArrayList<>();
        BigInteger curS = ranges.get(0)[0];
        BigInteger curE = ranges.get(0)[1];
        for (int i = 1; i < ranges.size(); i++) {
            BigInteger s = ranges.get(i)[0];
            BigInteger e = ranges.get(i)[1];
            if (s.compareTo(curE.add(BigInteger.ONE)) <= 0) {
                if (e.compareTo(curE) > 0) curE = e;
            } else {
                merged.add(new BigInteger[] {curS, curE});
                curS = s; curE = e;
            }
        }
        merged.add(new BigInteger[] {curS, curE});

        // convert merged ranges to minimal CIDR blocks
        List<String> result = new ArrayList<>();
        for (BigInteger[] r : merged) {
            result.addAll(rangeToIpv6Cidrs(r[0], r[1]));
        }

        return result;
    }

//     /**
//  * 判断BGP AS_PATH属性是否匹配对应的正则表达式
//  * @param aspath AS_PATH字符串，用空格隔开的32位无符号整数
//  * @param regex 思科IOS风格的正则表达式
//  * @return 如果aspath匹配regex则返回true，否则返回false
//  * @throws IllegalArgumentException 如果传入参数不合法
//  */
// public static boolean aspathRegex(String aspath, String regex) {
//     // 参数空值检查
//     if (aspath == null) {
//         throw new IllegalArgumentException("aspath不能为null");
//     }
//     if (regex == null) {
//         throw new IllegalArgumentException("regex不能为null");
//     }
    
//     // 检查aspath格式：用空格隔开的32位无符号整数
//     validateAsPath(aspath);
    
//     try {
//         // 使用 CiscoIOSRegex 类进行匹配
//         CiscoIOSRegex ciscoRegex = new CiscoIOSRegex(regex);
//         return ciscoRegex.matchesExact(aspath);
//     } catch (Exception e) {
//         throw new IllegalArgumentException("无效的正则表达式或匹配错误: " + regex, e);
//     }
// }

// /**
//  * 验证AS_PATH格式
//  */
// private static void validateAsPath(String aspath) {
//     if (aspath.isEmpty()) {
//         return; // 空路径是合法的
//     }
    
//     String[] asNumbers = aspath.split(" ");
    
//     for (String asNumStr : asNumbers) {
//         try {
//             long asNum = Long.parseLong(asNumStr);
//             // 检查是否为32位无符号整数 (0 到 4294967295)
//             if (asNum < 0 || asNum > 4294967295L) {
//                 throw new IllegalArgumentException(
//                     "AS号码超出32位无符号整数范围: " + asNumStr);
//             }
//         } catch (NumberFormatException e) {
//             throw new IllegalArgumentException(
//                 "无效的AS号码格式: " + asNumStr, e);
//         }
//     }
// }

// // 可以删除以下不再使用的方法：
// // private static String convertCiscoRegexToJava(String ciscoRegex)
// // private static String convertCiscoRegexToJavaPrecise(String ciscoRegex)

    /**
     * 返回聚合后网络可容纳的 /64 大小网络块数量
     */
    public static BigInteger ipv6Summary(String csvPath) throws IOException {
        List<String> cidrs = ipv6Aggregate(csvPath);
        BigInteger total = BigInteger.ZERO;
        BigInteger size64 = BigInteger.ONE.shiftLeft(64);
        for (String c : cidrs) {
            String[] parts = c.split("/");
            int prefix = Integer.parseInt(parts[1]);
            BigInteger blockSize = BigInteger.ONE.shiftLeft(128 - prefix);
            BigInteger num64 = blockSize.divide(size64);
            total = total.add(num64);
        }
        return total;
    }

    // ========== 新增方法 ==========

    /**
     * 检查是否为有效的CIDR表示法（IPv4或IPv6）
     */
    public static boolean isValidCIDR(String cidr) {
        return isValidIPv4Cidr(cidr) || isValidIPv6Cidr(cidr);
    }

    /**
     * 检查是否为有效的AS_PATH
     */
    public static boolean isValidAsPath(String aspath) {
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

    /**
     * 检查是否为有效的IPv4地址（不含CIDR前缀）
     */
    public static boolean isValidIPv4Address(String ip) {
        if (ip == null || ip.isEmpty() || ip.contains("/")) {
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
     * 检查是否为有效的IPv6地址（不含CIDR前缀）
     */
    public static boolean isValidIPv6Address(String ip) {
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

    /**
     * 检查是否为有效的IP地址（IPv4或IPv6，不含CIDR前缀）
     */
    public static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty() || ip.contains("/")) {
            return false;
        }
        return isValidIPv4Address(ip) || isValidIPv6Address(ip);
    }

    /**
     * 判断AS_PATH a是否比AS_PATH b更短
     */
    public static boolean isShorterAsPath(String a, String b) {
        if (a == null || a.trim().isEmpty()) return false;
        if (b == null || b.trim().isEmpty()) return true;
        
        String asA = a.trim();
        String asB = b.trim();
        
        int lengthA = asA.split("\\s+").length;
        int lengthB = asB.split("\\s+").length;
        
        return lengthA < lengthB;
    }
}