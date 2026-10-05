# Vessel BPBC：Java + COPT

《Vessel Service Planning in Seaports》的**基础 BPBC**复现。目标是用少量、清楚的代码看懂并运行论文方法。9 个核心 Java 文件，约 750 行；另有独立穷举验证代码。没有 Maven 或 GUI。

实现两层列生成、Benders 割和完整分支搜索，范围对应作者的 **BPBC_NONE**，不包含 DCG、LBL、VF、WS、PH 五项加速策略。不能据此宣称复现了 BPBC_ALL 的速度或全部论文实验。

## 先运行

需要 **JDK 17** 和 **COPT 8 Java 接口及有效许可证**。本机验证环境为 Java 17.0.10、COPT 8.0.6。许可证绑定用户时，请在正常 Windows 用户终端运行。

在项目目录打开 PowerShell：

~~~powershell
# 若安装程序已配置 COPT_HOME，可以省略此行。
$env:COPT_HOME = 'C:\Program Files\copt80'

# 最简单的三船合成实例：最优值 18。
.\run.ps1 --tiny 30

# 有 LP/整数差距的合成实例：根下界 29，整数最优值 33。
.\run.ps1 --branch-demo 30

# 穷举、完整 M2 LP、整数最优值和分支交叉验证。
.\run.ps1 verify
~~~

运行作者公开数据：

~~~powershell
.\scripts\download-data.ps1
.\run.ps1 'data\official\L1-B10-V10-01.txt' 120
~~~

脚本从[作者仓库](https://github.com/LingxiaoWu2021/VSPP/tree/main/data)获取原始数据。下载目录已被 Git 忽略；仓库不附作者源码、论文 PDF、求解器或许可证。

首次实测该 10 船实例得到 **LB = UB = 4156**，2 个已求解节点，约 78 秒，单线程。具体环境、指纹和验证范围见 [验证记录](results/verification.md)。这是一项实例结果，不是整套 60 个实例的复现实验。

## 怎么读代码

先读 [Main.java](src/Main.java)，再读 [Bpbc.java](src/Bpbc.java) 的 solveNode()：这是论文 Algorithm 1，约十几行就能看到主子问题交替和加割。

| 文件 | 做什么 | 论文位置 |
|---|---|---|
| Master.java | COPT BMP，Phase I、船舶列生成、加 Benders 行 | (38)–(41)、(53)–(57) |
| PilotProblem.java | COPT PBSP，引航员列生成、对偶认证 | (45)–(52) |
| VesselPricing.java | 每船每泊位的进出时间定价 | 4.3，MPP |
| PilotPricing.java | 前向标签、后向标签、休息连接 | EC.2 |
| Restrictions.java | 泊位、时刻、班次、邻接四类分支 | 4.4，(60)–(63) |
| Bpbc.java | Benders 节点循环、best-bound 树、上下界 | Algorithm 1，4.4 |
| Routes.java | 船舶列、引航员列、对偶割 | M2 |
| Instance.java | 作者数据解析与合法时刻 | 3，(31)–(33) |
| Main.java | 参数、结果与完整方案输出 | 运行入口 |

COPT 负责解 LP；定价、Benders 迭代、搜索树均由 Java 实现。[从日志读懂算法](docs/read-code.md)提供一个短的阅读顺序，[实现与公式说明](docs/method.md)解释对偶符号和验证边界。

## 结果与正确性边界

只有 COPT 得到 LP 最优解且完整定价找不到负约化成本列后，节点值才用于下界。先在正需求网络给引航员定价，停止前再用完整网络认证对偶，防止无效割。

分支使子问题不可行时，用 Phase I 生成可行性割；不是把高罚人工变量当成真实引航员。超时输出 TIME_LIMIT、已有下界和真实可行上界；没有找到整数解时 UB=Infinity、gap=NA。

所有时间和编号从 **0** 开始。合成示例仅用于理解算法和独立验证，不冒充论文数据。当前版本按数值容差 1e-7–1e-6 判断定价、分支和最优性。

## 来源

Wu, L., Adulyasak, Y., Cordeau, J.-F., and Wang, S. (2022). *Vessel Service Planning in Seaports*. Operations Research, 70(4), 2032–2053. [DOI](https://doi.org/10.1287/opre.2021.2228)，[作者公开源码与数据](https://github.com/LingxiaoWu2021/VSPP)。

本仓库根据论文方法独立编写 Java 实现，使用作者公开数据进行验证。[COPT Java 文档](https://guide.coap.online/copt/en-doc/javainterface.html)及[对偶/约化成本说明](https://guide.coap.online/copt/en-doc/information.html)。
