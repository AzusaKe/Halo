# Halo 验证基线与复现证据

审计日期：2026-10-13（Asia/Shanghai）。来源分支：`1.20.1-fabric`；审计文档分支：`review/2026-10-13`。

Halo 基线：`49b822c037606faaf0bb8ba2adba7b63ce0d62bb`；锁定 HaloCore：`a22160a842e15fffb1bc03b5b189183557f6d510`（2.5.0）。所有源码行号指向这两个提交，不指向后续修复版本。

## 本次环境与结论

Linux隔离workspace，OpenJDK17.0.20，Python3.12.14。没有桌面游戏/GL上下文、第三方发布包或online游戏会话。Java launcher最初因libjli搜索路径失败，设置LD_LIBRARY_PATH后可用；环境没有javac入口，隔离探针通过JDK内的`com.sun.tools.javac.Main`编译。

Gradle wrapper初次无法直接下载网络distribution。通过环境代理预装同版本8.8运行文件/依赖缓存后，core独立构建成功，再使用仓库wrapper离线执行core build成功。预装cache不是仓库依赖校验或全新网络构建的证明；缓存引导细节与环境失败不能当作项目缺陷。没有更改wrapper/build配置。

| 检查 | 本次结果 | 证据 / 限制 |
| --- | --- | --- |
| Halo/core锁定与干净状态 | 通过 | 克隆主线后子模块恢复精确gitlink；创建review分支；业务代码/core源码无更改 |
| core独立build | 通过 | 初次使用8.8执行成功；随后`core/./gradlew build --offline --console=plain --no-daemon`成功，20任务中5执行/15up-to-date |
| core JUnit | 通过 | 59个testsuite XML：336 tests、0 failures、0 errors、0 skipped；test source文件含nested classes所以XML数不等于源文件数 |
| verifyCoreBoundary | 通过 | 扫production bytecode禁MC/Fabric/Mixin/LWJGL及仅Gson/JOML runtime dependency |
| verifyExternalPreviewProvider | 通过 | 独立provider只编译public API；JDK+core环境运行 |
| verifyLegacyClientAdapter | 通过 | 冻结2.1.2 ClientPort调用方运行 |
| verifyLegacyRenderConsumer | 通过 | 冻结2.3.0 world/preview/output构造器调用方运行 |
| verifyLegacyMeshIndexConsumer | 通过 | 冻结2.3.1 MeshIndexWriter调用方运行 |
| Halo联合build | 环境阻断 | wrapper/同版本Gradle配置Loom时：`CurrentPlatform.isUnixDomainSocketsSupported` → `SocketException: Operation not permitted` |
| Halo Java编译/平台JUnit | 未执行 | 联合build未到编译/test阶段；不可写为“本地通过” |
| verifyDistribution / verifyLegacyAnchorApi | 本地未执行 | 同上；不能凭core成功证明最终Halo JAR |
| Python发布器单元测试 | 通过 | 48 tests，OK；使用mock，不做实际分发 |
| 专门Lint / 静态分析 | 未配置 | 未发现Checkstyle/SpotBugs/PMD/专门Lint gate；不是运行失败 |
| 类型检查 | core编译通过；适配器本地未执行 | Java编译负责类型检查，无独立TS式typecheck task |
| 覆盖率 | 无数据 | 未配置/运行JaCoCo，未编造覆盖百分比 |
| 游戏/GL/第三方组合 | 未执行 | 不将历史文档人工测试视为本次执行 |
| release模式/发布/网络攻击 | 未执行 | 没有-Prelease=true、dispatch、tag、push、真实凭据或向服务器发送复现包 |

## 命令与执行片段

标准环境下重放：

```sh
# 从Halo根目录
./core/gradlew -p core build --console=plain
./gradlew build --console=plain
python3 -m unittest discover -s scripts/release -p 'test_*.py' -v
```

本环境为保证所有临时写入位于workspace，给Java/Gradle设置如下变量（普通运行时路径，不包含凭据）；首次可用core构建使用同版本的预装Gradle并通过现有环境代理解析依赖，后续wrapper离线验证：

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export LD_LIBRARY_PATH="$JAVA_HOME/lib"
export GRADLE_USER_HOME=/workspace/scratch/893856967c50/gradle-cache
export JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/workspace/scratch/893856967c50/tmp
cd Halo/core
./gradlew build --offline --console=plain --no-daemon
# 在Halo根目录也运行了build --offline，仍在Loom配置阶段受阻
```

成功/阻断输出摘要：

```text
Core独立首次构建: BUILD SUCCESSFUL in 33s
20 actionable tasks: 20 executed
Core wrapper离线复核: BUILD SUCCESSFUL in 5s
20 actionable tasks: 5 executed, 15 up-to-date
Publisher: Ran 48 tests in 0.020s / OK
Halo构建:
Caused by: java.io.UncheckedIOException: java.net.SocketException: Operation not permitted
  at net.fabricmc.loom.util.CurrentPlatform.isUnixDomainSocketsSupported(CurrentPlatform.java:89)
Caused by: java.net.SocketException: Operation not permitted
  at net.fabricmc.loom.util.CurrentPlatform.isUnixDomainSocketsSupported(CurrentPlatform.java:84)
```

core wrapper复核复用了同一source tree的已生成JUnit结果，没有声称336项在每次wrapper调用都重新执行。发布测试的JSON stdout包括预期模拟的失败场景，不代表unittest失败；测试运行汇总是OK。

## 远端CI证据，独立于本地基线

2026-10-13只读查询GitHub Actions元数据，匹配完整Halo SHA。没有下载/执行成品或把远端结果当本地结果：

| 工作流 | Run / 时间（UTC） | 结果 |
| --- | --- | --- |
| Java CI with Gradle | [38051669952](https://github.com/AzusaKe/Halo/actions/runs/38051669952)，2026-10-10 12:21:43 | success |
| Test release publisher | [38051669941](https://github.com/AzusaKe/Halo/actions/runs/38051669941)，2026-10-10 12:21:43 | success |

Java CI build job `114211888344`中`Verify core independently`、`Build adapter and verify distribution`、`Upload JAR artifact`步骤success；`Create GitHub Release`为skipped，因为该run是branch push。dependency-submission job也为success，但该步骤配置continue-on-error，因此它不是漏洞审计成功的证据。没有读取远端逐测试结果、覆盖率或完整日志。

## 隔离复现结果

下列输出由实际锁定core类产生。count为JDK集合关键操作；quad执行实际core生成geometry上的生产访问循环；没有调用真实Fabric packet receiver或OpenGL。world/priority为未修改生产适配器类加最小platform桩，不能等同完整Minecraft测试。scope为按生产分支模拟状态机，仍列疑似风险。

```text
CASE count exit=0
count=2147483647 first add -> java.lang.OutOfMemoryError: Java heap space
count=-1 -> java.lang.IllegalArgumentException: Illegal initial capacity: -1
CASE quad exit=0
production billboard vertices=4 upload loop corners=6
upload loop -> java.lang.ArrayIndexOutOfBoundsException: Index 12 out of bounds for length 12
CASE empty exit=0
Identifier accepted 'halo:'
HaloSource rejected -> definitionId must be a lowercase namespace:path identifier
CASE null exit=0
valid second server before write=Optional.empty
valid second server preserved in output=false
{
  "c:25565": {
    "00000000-0000-0000-0000-000000000001": "halo:new"
  }
}
CASE depth exit=0
depth=1000 bytes=15033 -> uncaught StackOverflowError from reload
CASE ring exit=0
accepted ring segments=1000000 arrays before defensive cloning >= 176000128 bytes for two sides
CASE cache exit=0
published meshes=0 retained loader models=40
CASE finite exit=0
scale=Infinity linearDamping=NaN
CASE animation exit=0
mutating evaluated scale at t=0: before=1.0 next result=99.0 cached instance reused=true
CASE scope exit=0
outer living source before nested nonliving=true
outer living source after nested nonliving=false
CASE world exit=0
10000 distinct upserts scanned=49995000
2000 unowned entity lookups against 10000 owners scanned=20000000
CASE priority exit=0
WARN persistence failure recorded
set returned normally, in-memory=99 destination still directory=true
CASE depth-location exit=0
depth=1000 bytes=15033 -> uncaught StackOverflowError from reload
top frame=java.base/java.lang.StringLatin1.compareTo(StringLatin1.java:110)
CASE depth-default-stack exit=0
depth=4000 bytes=60033 -> uncaught StackOverflowError from reload
top frame=java.base/java.util.ImmutableCollections$AbstractImmutableList.iterator(ImmutableCollections.java:282)

```

| 探针 | 对应发现 | 已证明 / 未证明 |
| --- | --- | --- |
| count | 001 | 延迟首次插入巨额分配OOM、负数异常；未证明整服退出 |
| empty | 002 | 同ID被两API接受/拒绝；持久化调用顺序由源码确认 |
| quad | 003 | 4顶点被访问6次越界；GL factory/fallback影响由源码推导 |
| null | 004 | 有效第二server未加载且被后续保存删除，完整行为复现 |
| priority | 006 | 实际writer失败后正常返回且内存值变化；command反馈由源码确认 |
| depth | 007 | 较小栈1000层、默认JVM栈4000层实际reload均未捕获StackOverflowError；游戏JVM阈值未知 |
| ring | 008 | 实际parser接受百万segments、分配预算176000128字节；未申请大数组 |
| animation | 009 | 修改求值输出改变共享缓存下一次输出 |
| world | 010 | 实际列表操作扫描次数；无TPS/时延结论 |
| cache | 011 | 空published snapshot后loader保留40模型；反射仅观察 |
| scope | 012 | 模拟嵌套分支使外层submit失效；未验证真实nested renderer |
| finite | 未单独评级 | 内部config setter接受NaN/Infinity；Fabric数字参数的实际可达性未证明，不列独立用户功能故障 |

## 可重放的core/JDK探针

先成功构建core，使用core JAR加其两个声明运行依赖。`CP`需要指向实际已解析的Gson2.10和JOML1.10.5 JAR；不要用“最新”替换。探针放在仓库外的临时目录，不提交业务测试/源码。示例中的64MiB和较小线程栈用于安全隔离，不要求真实服务端使用这些参数。

```sh
java com.sun.tools.javac.Main -cp "$CP" AuditProbe.java
java -Xmx64m -Xss256k -cp "$CP:." AuditProbe count
java -Xmx64m -Xss256k -cp "$CP:." AuditProbe quad
java -Xmx64m -Xss256k -cp "$CP:." AuditProbe empty
java -Xmx64m -Xss256k -cp "$CP:." AuditProbe null
java -Xmx64m -Xss256k -cp "$CP:." AuditProbe depth 1000
java -Xmx64m -cp "$CP:." AuditProbe ring
java -Xmx64m -cp "$CP:." AuditProbe animation
java -Xmx64m -cp "$CP:." AuditProbe cache
java -Xmx64m -cp "$CP:." AuditProbe scope
```

```java
import java.util.*;
import java.io.*;
import com.google.gson.*;
import network.azusake.halo.core.*;
import network.azusake.halo.core.runtime.*;
import network.azusake.halo.core.render.*;
import network.azusake.halo.json.HaloDefinitionDeserializer;
import network.azusake.halo.shape.*;
import network.azusake.halo.render.PrimitiveGeometries;
import network.azusake.halo.api.v2.*;
public class AuditProbe {
 public static void main(String[] args) throws Exception {
  switch(args[0]) {
   case "count": {
    try { var set=new LinkedHashSet<String>(Integer.MAX_VALUE);set.add("halo:ring");System.out.println("UNEXPECTED_OK"); }
    catch(OutOfMemoryError e) { System.out.println("count=2147483647 first add -> "+e); }
    try { new LinkedHashSet<>(-1); } catch(IllegalArgumentException e) {System.out.println("count=-1 -> "+e);}
    break;
   }
   case "quad": {
    var geometry=new PrimitiveGeometries().billboard(new BillboardPrimitive(new Identifier("halo:test"),new org.joml.Vector2f(1,1)));
    var mesh=geometry.mesh();int corners=mesh.vertexCount()*3/2;
    System.out.println("production billboard vertices="+mesh.vertexCount()+" upload loop corners="+corners);
    try {for(int corner=0;corner<corners;corner++) mesh.x(corner);} catch(ArrayIndexOutOfBoundsException e){System.out.println("upload loop -> "+e);}
    break;
   }
   case "empty": {
    var id=new Identifier("halo:");System.out.println("Identifier accepted '"+id+"'");
    var source=HaloApi.registerSource("audit:world",0);
    try {source.set(UUID.randomUUID(),id.toString());}catch(IllegalArgumentException e){System.out.println("HaloSource rejected -> "+e.getMessage());}finally{source.close();}
    break;
   }
   case "null": {
    String uuid="00000000-0000-0000-0000-000000000001";
    class Store implements LocalOwnership.TextStore {
      String text="{\"a:25565\":{\""+uuid+"\":null},\"b:25565\":{\""+uuid+"\":\"halo:kept\"}}";
      public String read(){return text;} public void write(String t){text=t;}
    }
    var store=new Store();var local=new LocalOwnership(store);
    System.out.println("valid second server before write="+local.getHalo("b:25565",UUID.fromString(uuid)));
    local.showHalo("c:25565",UUID.fromString(uuid),new Identifier("halo:new"));
    System.out.println("valid second server preserved in output="+store.text.contains("b:25565"));
    System.out.println(store.text);
    break;
   }
   case "depth": {
    int depth=Integer.parseInt(args[1]);
    String text="{\"id\":\"audit:deep\",\"layers\":["+"{\"children\":[".repeat(depth)+"{}"+"]}".repeat(depth)+"]}";
    try {var problems=new DefinitionResources().reload(0,List.of(new ResourceInput(new Identifier("audit:deep.json"),"audit",text)));System.out.println("depth="+depth+" problems="+problems.size());}
    catch(StackOverflowError e){System.out.println("depth="+depth+" bytes="+text.length()+" -> uncaught StackOverflowError from reload");System.out.println("top frame="+e.getStackTrace()[0]);}
    break;
   }
   case "ring": {
    String text="{\"id\":\"audit:ring\",\"layers\":[{\"primitive\":{\"type\":\"ring\",\"texture\":\"halo:test\",\"size\":[1,0.1],\"segments\":1000000}}]}";
    var def=new HaloDefinitionDeserializer().deserialize(JsonParser.parseString(text),null,null);
    var ring=(RingPrimitive)def.model().groups().get(0).primitives().get(0);
    long n=ring.segments();long perSide=(n+1)*2*(3+3+2)*4+n*6*4;
    System.out.println("accepted ring segments="+n+" arrays before defensive cloning >= "+(2*perSide)+" bytes for two sides");
    break;
   }
   case "cache": {
    var loader=new VisualAssetLoader(1,new VisualAssetLoader.Source(){
      public String model(Identifier id){return "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3\n";}
      public VisualResources.TextureInfo texture(Identifier id){return new VisualResources.TextureInfo(1,1,true);}
    },p->{throw new RuntimeException(p.message());});
    for(int i=0;i<40;i++)loader.load(new DefinitionSnapshot.AssetDependencies(Set.of(new Identifier("audit:m"+i+".obj")),Set.of()));
    var empty=loader.load(new DefinitionSnapshot.AssetDependencies(Set.of(),Set.of()));
    var field=VisualAssetLoader.class.getDeclaredField("models");field.setAccessible(true);
    System.out.println("published meshes="+empty.meshes().size()+" retained loader models="+((Map<?,?>)field.get(loader)).size());
    break;
   }
   case "animation": {
    var prop=new network.azusake.halo.animation.TransitionAnimation.TransitionProperty(new float[]{1,1,1},new float[]{2,2,2});
    var seg=new network.azusake.halo.animation.TransitionAnimation.TransitionSegment(1,network.azusake.halo.animation.EasingType.LINEAR,null,prop,null);
    var cfg=new network.azusake.halo.animation.StartupAnimationConfig(List.of(seg),Map.of());
    var shared=cfg.getAnimationForGroup(Optional.empty());
    var output=shared.evaluate(0);float before=output.scale()[0];output.scale()[0]=99;
    System.out.println("mutating evaluated scale at t=0: before="+before+" next result="+shared.evaluate(0).scale()[0]+" cached instance reused="+(shared==cfg.getAnimationForGroup(Optional.empty())));
    break;
   }
   case "scope": {
    var source=HaloAnchorApi.register("audit:scope");var uuid=UUID.randomUUID();var world=new Object();
    network.azusake.halo.anchor.AnchorCaptureCoordinator.beginFrame(world);
    network.azusake.halo.anchor.AnchorCaptureCoordinator.beginEntityRender(uuid,1,world,new AnchorVec3(0,0,0),true);
    var pose=new AnchorPose(new AnchorVec3(0,1,0),new AnchorRotation(0,0,0,1));
    System.out.println("outer living source before nested nonliving="+source.submit(uuid,pose));
    // Adapter skips begin for non-LivingEntity but unconditionally calls end.
    network.azusake.halo.anchor.AnchorCaptureCoordinator.endEntityRender();
    System.out.println("outer living source after nested nonliving="+source.submit(uuid,pose));
    source.close();break;
   }
   case "finite": {
    var c=new network.azusake.halo.config.HaloConfig();c.setHaloScale(Double.POSITIVE_INFINITY);c.setLinearDampingFactor(Double.NaN);
    System.out.println("scale="+c.getHaloScale()+" linearDamping="+c.getLinearDampingFactor());break;
   }
  }
 }
}
```

## 适配器存储探针与桩边界

这些桩仅提供NBT/持久状态类型、Fabric默认路径和日志，不模拟Minecraft序列化或生命周期。编译对象是锁定主线的原始`HaloWorldSaveData.java`、`HaloSourcePriorityStore.java`和以下AdapterProbe。没有改生产类；world探针反射替换entries为计数ArrayList，保留原get/set循环。

```java
import java.nio.file.*;import java.util.*;import java.util.function.*;
import network.azusake.halo.core.Identifier;import network.azusake.halo.lifecycle.HaloWorldSaveData;import network.azusake.halo.config.HaloSourcePriorityStore;
public class AdapterProbe {
 static class CountList extends ArrayList<HaloWorldSaveData.HaloEntry>{long comparisons;public boolean removeIf(Predicate<? super HaloWorldSaveData.HaloEntry> p){comparisons+=size();return super.removeIf(p);}public Iterator<HaloWorldSaveData.HaloEntry>iterator(){var it=super.iterator();return new Iterator<>(){public boolean hasNext(){return it.hasNext();}public HaloWorldSaveData.HaloEntry next(){comparisons++;return it.next();}};}}
 public static void main(String[]args)throws Exception{
  if(args[0].equals("world")){
   var store=new HaloWorldSaveData();var entries=new CountList();var f=HaloWorldSaveData.class.getDeclaredField("entries");f.setAccessible(true);f.set(store,entries);
   for(int i=0;i<10000;i++)store.set(new UUID(0,i),new Identifier("halo:ring"));
   System.out.println("10000 distinct upserts scanned="+entries.comparisons);
   entries.comparisons=0;for(int i=0;i<2000;i++)store.get(new UUID(1,i));System.out.println("2000 unowned entity lookups against 10000 owners scanned="+entries.comparisons);
  }else{
   Path dir=Files.createTempDirectory("audit-priorities-");Path file=dir.resolve("priorities.json");
   HaloSourcePriorityStore.load(file);HaloSourcePriorityStore.set("audit:test",1);Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("marker"),"synthetic");
   HaloSourcePriorityStore.set("audit:test",99);System.out.println("set returned normally, in-memory="+HaloSourcePriorityStore.priorities().get("audit:test")+" destination still directory="+Files.isDirectory(file));
  }
 }
}
```

桩按以下路径写入独立`stubs`目录，各文件单独编译，避免与真实平台类混入成品：

`net/fabricmc/loader/api/FabricLoader.java`：

```java
package net.fabricmc.loader.api; public class FabricLoader {public static FabricLoader getInstance(){return new FabricLoader();}public java.nio.file.Path getConfigDir(){return java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));}}
```

`net/minecraft/nbt/NbtCompound.java`：

```java
package net.minecraft.nbt; public class NbtCompound {private final java.util.Map<String,Object>m=new java.util.HashMap<>();public void put(String k,NbtList v){m.put(k,v);}public void putString(String k,String v){m.put(k,v);}public String getString(String k){return (String)m.getOrDefault(k,"");}public boolean contains(String k){return m.containsKey(k);}public NbtList getList(String k,int t){return (NbtList)m.get(k);}}
```

`net/minecraft/nbt/NbtElement.java`：

```java
package net.minecraft.nbt; public class NbtElement {public static final int COMPOUND_TYPE=10;}
```

`net/minecraft/nbt/NbtList.java`：

```java
package net.minecraft.nbt;public class NbtList extends java.util.ArrayList<NbtCompound>{public NbtCompound getCompound(int i){return get(i);}}
```

`net/minecraft/server/world/ServerWorld.java`：

```java
package net.minecraft.server.world;public class ServerWorld{public net.minecraft.world.PersistentStateManager getPersistentStateManager(){return new net.minecraft.world.PersistentStateManager();}}
```

`net/minecraft/world/PersistentState.java`：

```java
package net.minecraft.world;public abstract class PersistentState{public abstract net.minecraft.nbt.NbtCompound writeNbt(net.minecraft.nbt.NbtCompound n);public void markDirty(){}}
```

`net/minecraft/world/PersistentStateManager.java`：

```java
package net.minecraft.world; public class PersistentStateManager {public <T extends PersistentState>T getOrCreate(java.util.function.Function<net.minecraft.nbt.NbtCompound,T>f,java.util.function.Supplier<T>s,String k){return s.get();}}
```

`network/azusake/halo/HaloMod.java`：

```java
package network.azusake.halo; public final class HaloMod { public static final Log LOGGER=new Log(); public static class Log {public void warn(String s,Object...args){System.out.println("WARN persistence failure recorded");}public void debug(String s,Object...args){}} }
```

`network/azusake/halo/manager/HaloManager.java`：

```java
package network.azusake.halo.manager; public class HaloManager {}
```

```sh
# REPO指向Halo固定基线；out、stubs和.tmp均位于隔离工作目录
mkdir -p out .tmp
java com.sun.tools.javac.Main -cp "$CP" -d out $(rg --files stubs -g '*.java')   "$REPO/src/main/java/network/azusake/halo/lifecycle/HaloWorldSaveData.java"   "$REPO/src/main/java/network/azusake/halo/config/HaloSourcePriorityStore.java" AdapterProbe.java
java -Xmx128m -Djava.io.tmpdir="$PWD/.tmp" -cp "$CP:out" AdapterProbe world
java -Xmx128m -Djava.io.tmpdir="$PWD/.tmp" -cp "$CP:out" AdapterProbe priority
```

HALO-005的断电/写中断、HALO-012实际renderer、HALO-015实际chat validator等仍需修复路线中明确的环境；本次没有伪造这些通过记录。执行后的临时probe/JAR/cache没有加入git，交付只增加五份Markdown。
