本项目使用的 mrt-0.0.2-RIPE-16.jar 来自 https://github.com/RIPE-NCC/java-mrt

# bgp-utils-cli
bgp-utils-cli 是一个主要用于将以 MRT 格式存储的 BGP 路由数据转储为其它格式并加以处理的命令行工具。

## 功能
- 从一种路由存储格式转储为其它格式（MRT 到 CSV，CSV 到 CSV，CSV 到纯文本）
  - 如果一条前缀有多条路径，则选取 AS_PATH 最短的一条
- 基本的思科风格正则支持，可对路由进行基于 AS_PATH 属性的过滤
- 交互模式
- 取反路由（纯文本到纯文本）

## 命令行参数 -c 中的转换类型解释
- mrt2csv：MRT 到 CSV 格式转换，用于初步的数据提取
  - 建议在这一步进行不聚合的全量提取，方便后续处理，因为这一步会相当费时（在我的平台上提取一次全表要大约10分钟，使用的数据源是 RIPE RIS 的全表转储）
- csv2csv：CSV 到 CSV 格式转换
- csv2txt：CSV 到纯文本格式
- txt2txt：纯文本到纯文本格式
- txt2iproute2：纯文本到 iproute2 脚本格式，用于生成目标平台的路由注入脚本
- invert：取反路由，适用于要对特定路由进行反向匹配，但目标平台只支持正向匹配（如路由表）的场景

---

# bgp-utils-cli
bgp-utils-cli is a command-line tool meant to extract BGP route data from MRT dump to other formats and then process them.

## Features:
- Convert one route data format to another format (MRT to CSV, CSV to CSV, CSV to TXT)
  - If one prefix has multiple path, choose one with most short AS_PATH
- Basic Cisco-style Regex support, for filtering routes based on AS_PATH
- Interactive mode
- Invert routes (TXT to TXT)

## Command-line arguments -c
- mrt2csv：MRT to CSV format conversion, for initial data extraction
  - Suggest to run this step without aggregation, as it may take a long time (about 10 minutes on my platform, using RIPE RIS full table dump)
- csv2csv：CSV to CSV format
- csv2txt：CSV to TXT format
- txt2txt：TXT to TXT format
- txt2iproute2：TXT to iproute2 script format, for generating route injection scripts for target platform
- invert：Invert routes, for matching routes in reverse way, meant for target platform which don't support reverse matching (eg. routing table)