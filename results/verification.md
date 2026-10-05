# 验证记录：2026-10-05

环境：Windows、Oracle Java 17.0.10、COPT 8.0.6（2026-08-07 build），LP 单线程、dual simplex。下列均为实际运行结果。

## 独立交叉验证

命令：

~~~powershell
.\run.ps1 verify
~~~

结果：PASS 77178 independent pricing/LP/integer/branch checks。

计数包括资源系数、对偶不等式等逐项断言，不代表 77178 个独立实例。完整算法的五个小型合成实例如下：

| 合成实例 | 完整枚举 M2 LP / BPBC 根下界 | 独立整数枚举 / BPBC 最优值 | BPBC 已求解节点 |
|---|---:|---:|---:|
| 1 船，seed=1 | 11 | 11 | 1 |
| 2 船，seed=1 | 15 | 15 | 1 |
| 3 船，seed=1 | 18 | 18 | 1 |
| 3 船，分支实例 seed=-1 | 29 | 33 | 37 |
| 2 船，航道冲突 seed=-2 | 23.666667 | 26 | 23 |

此外，对四类强制分支分别比较两侧的整数最优值；检查不可行分支、限制继承、LP 超时状态和所有枚举引航员列的对偶可行性。

## 作者公开实例

来源：[L1-B10-V10-01.txt](https://github.com/LingxiaoWu2021/VSPP/blob/07fa8c226600bae30b4c39ba65da5523c1619723/data/L1-B10-V10-01.txt)。

作者仓库版本：07fa8c226600bae30b4c39ba65da5523c1619723。

原始字节数 23105。SHA-256：

~~~text
e555c945cf93668c9c2c6b5574558f99f4d8b8d0c538b33c334b3af555e727ef
~~~

命令：

~~~powershell
.\scripts\download-data.ps1
.\run.ps1 'data\official\L1-B10-V10-01.txt' 120
~~~

最终代码运行：OPTIMAL，LB=UB=4156，gap=0，root LP=4156，2 个已求解节点，76.888 秒。34429 次 LP，173 个节点内 Benders 割，累计生成 852 个船舶列、62606 个引航员列。后两个列数累计包含不同子问题/节点的重新生成，不能理解成单个 RMP 的规模。

[完整运行日志与整数方案](official-run.txt)保留了 10 条船舶路线和 8 条引航员路线。

方案还经过直接约束检查：

~~~powershell
.\run.ps1 verify 'data\official\L1-B10-V10-01.txt' 'results\official-run.txt'
~~~

结果：PASS official primal check: 10 vessels, 8 pilots, total 4156.000000 (100 checks)。

该检查直接验证任务时窗、初始泊位、装卸时长、所有 headway/非同时性约束、休息与换岗、班次、每个任务恰好一次以及成本汇总，而不依赖 RMP 的资源行。

## 范围

这是基础 BPBC 的正确性与一个公开实例的运行验证。未完成作者的 60 实例实验，也未实现五项加速；没有把不同求解器和硬件下的时间当作论文性能复现。
