# 明天怎么开始看

先运行：

~~~powershell
.\run.ps1 --tiny 30
~~~

你会看到三条船舶路线、一条引航员路线，总成本 **18**。inward 是船舶进港引航开始，outward 是离泊引航开始；泊位占用从 inward + duration 到 outward 之前。引航员在服务间穿插休息，装卸期间可以去服务其他船。

接着只打开 Bpbc.solveNode()。暂时不读数据解析器：这段循环已包含 BMP → 固定任务需求 → PBSP → 加割 → 返回完整节点 LP。

然后打开 Master.columnGeneration() 和 PilotProblem.generate()。两者都是“求 LP → 取对偶 → 定价 → 加列”，区别是列代表船舶服务方案还是引航员工作路线。

最后运行：

~~~powershell
.\run.ps1 --branch-demo 30
~~~

这个例子的根 LP 是 **29**，整数最优值是 **33**。共求解 **37 个节点**：日志中的根节点值不是整数最优值；搜索树逐步排除分数方案，找到真实可行方案并闭合差距。

理解上述循环后，再看 Restrictions.choose()：为什么先分泊位，再分时间、班次和邻接。两类定价的内部细节可以分别看，PilotPricing 对应你论文阅读笔记中的前向标签、后向标签和休息连接。

明天不需要逐字读 9 个文件。先能回答三件事：新列从哪里来、Benders 割告诉 BMP 什么、为什么根 LP 等于一个下界但未必等于整数最优值。
