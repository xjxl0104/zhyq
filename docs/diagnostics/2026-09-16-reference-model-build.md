# 2026-09-16 首页参考照片模型重建

本次把首页建筑外壳和地面车辆改为用户提供的四张实物沙盘照片所示外观。首页继续加载 `frontend/public/models/dipark-warehouse.glb`；界面、渲染风格、七层高度、拆层和业务点位交互保持既有实现。

## 当前资产来源

本次网站 GLB 由 JavaScript 几何生成器重建：

- `frontend/src/views/twin/warehouseModel.js`：建筑、七层外壳和结构、室内、消防、屋顶。
- `frontend/src/views/twin/siteGeometry.js`：园区环境与参考照片中的车辆。
- `frontend/scripts/rebuild-warehouse-model.mjs`：最终网站 GLB 的可复现导出入口。

仓库原有 `.blend` 文件保留的是上一版本 Blender 场景，**不包含本次建筑和车辆改动**。请勿直接导出旧 `.blend` 覆盖本次网站模型。`export-warehouse-seed.mjs` 仍是 Blender 工作用的几何种子导出工具，不是本次最终资产构建入口。

## 为什么还需要现有 GLB

用户此次只要求修改建筑与底下车辆。已有入口包含 Blender 制作的中文园名、门楼和岗亭等，JavaScript 种子不能完整还原这些内容。重建脚本因此读取现有 GLB 中 `sitePart: "entrance"` 的整组对象，保留门楼、岗亭、中文网格及原父级带来的世界坐标变换，再用 `referenceEntrance.js` 升级拉丁字标。旧 PARK 与中文在同一网格中，升级只剔除明确位于中文局部原点左侧的 PARK 三角形，保留中文原始顶点、法线和变换；新字标复用建筑的几何轮廓与青绿／蓝色共享材质。版本 extras 保证重复重建不叠加文字。

入口之外的建筑、道路、景观和车辆来自当前 JavaScript 源。入口缺失或不唯一时脚本中止，避免静默丢掉已交付的入口。由此，重建依赖当前 JavaScript 源和一份包含完整入口的现有 GLB，两者都需保留。

## 重建命令

在仓库根目录执行：

```sh
node frontend/scripts/rebuild-warehouse-model.mjs
```

默认输出始终解析到该脚本所属项目的 `frontend/public/models/dipark-warehouse.glb`，与命令执行目录无关。可指定输出路径及入口来源：

```sh
node frontend/scripts/rebuild-warehouse-model.mjs /tmp/dipark-preview.glb frontend/public/models/dipark-warehouse.glb
```

第二参数省略时，入口来源就是输出路径。适合原地重建，也支持重复执行：

```sh
node frontend/scripts/rebuild-warehouse-model.mjs /tmp/dipark-preview.glb
```

脚本先完整读取、解析源 GLB，再构建模型；导出结束后先写同目录临时文件，随后重命名替换输出。源和输出相同时不会提前截断源文件。命令输出包含输出路径、入口来源、网格数量、三角形数量和文件字节数；数量统计包含隐藏室内/消防系统，实例网格按实例数计算三角面。

## GLB 中保留的交互契约

- `twinRole` 标识 `building`、`site`、`roof`。
- 七个 `floor` 组保留 `floor`、`spaceKey`；每层保留 `shell`、`structure`、`interior`、`fire` 四个子系统。
- `onlyVisible: false` 导出隐藏的室内和消防对象；`runtimeOnly` 选择框等运行时辅助对象不进入文件。
- `warehouse-photo-vehicles` 组具有 `sitePart: "vehicles"`；每辆车保留 `vehicleKind`、`vehicleId`、`referenceBodyColour` 和顶点颜色。
- 窗带和转角使用单层透明玻璃，而不是照片阴影中的黑色实板。玻璃材质 extras 保留 `surfaceRole: "architectural-glass"`；加载时关闭其深度写入及投射/接收不透明阴影，沿用现有环境反射。
- 窗后没有紧贴玻璃的实心背墙。柱梁与楼板属于 `structure`，在建筑外观模式下仍可透过玻璃看到；室内业务布置和消防系统仍按原视图切换。

## 窗格抗摩尔纹与照片字标

细窗框、转角幕墙网格、招牌竖纹和装卸卷帘纹理使用 `facadeDetails.js` 的解析材质细节。屏幕导数估算单像素覆盖面积，对窗框进行覆盖率滤波，并在密度过高时平滑淡出；墙板纹理缩远时保留平均亮度。窗框颜色按预乘透明度混合后转换回普通 alpha，保持玻璃通透和框线对比。外围主窗框仍使用真实几何。

纯 JSON 配置保存在材质 extras 的 `facadeDetail` 中。GLB 不序列化 `onBeforeCompile`，因此 `bindWarehouse` 在读取正式文件时重新安装对应 shader；直接创建和 GLB 加载路径均使用相同流程。无需增加贴图、逐帧 CPU 更新或额外渲染通道。

`referenceWordmark.js` 按照片绘制七片挤出几何：青绿部分由两个相向开槽的圆弧块和中间粗竖块组成，PARK 使用细线轮廓与真实镂空。楼顶正面为青绿图形配蓝色 PARK，转角正面及侧面为青绿图形配白色 PARK；楼顶字标位于白色女儿墙上。建筑与入口的拉丁字标均不再依赖通用字体，入口中文园名与门楼保持原样。

## 树木分区与距离细节

外围 292 棵树使用 `parkTreeLod.js` 按 96 米网格分成 23 区，树的位置、朝向、尺度及颜色保持一致。每区两组实例分别绘制树干和树冠，共享三档几何：近景 112 片叶卡／9 枝，中景 56 片／5 枝，远景 24 片／2 枝。中远景增大叶卡、收紧分布以维持树冠外轮廓。各区使用所有档位的包围球并集，避免切档导致边缘提前消失。

`landscape.update(camera)` 在控制器更新后调用，考虑镜头视角和缩放，并使用 12% 滞回防止临界距离反复切换。相机不动时直接返回，切档不重传实例矩阵或颜色；切档后重绘并使缓存阴影失效。几何、实例及纹理仍由景观对象统一释放，包括未显示的细节档位。

以原首页相机、42° 视角、1440×900 画幅做相机剔除统计，树木候选三角面从 127,312 降至约 38,608（约 70%）；树木绘制批次从 2 增至约 30。统计包含分区整体剔除，不是 GPU 时间或帧率实测。本次未修改自动环绕、画质分辨率或帧率策略。

窄窗带里原先被当作灰色实板的补段已改为透明玻璃；阳台玻璃门同时切除后方白墙并保留细框。首层卷帘、实体墙面和栏杆材质仍独立处理。

## 验证

在 `frontend` 目录执行：

```sh
node node_modules/vitest/vitest.mjs run src/views/twin/__tests__
node node_modules/vite/bin/vite.js build
node scripts/compress-models.mjs
```

资产测试直接读取网站交付的 GLB，验证七层系统、室内选择、拆层位移、业务点位跟随和参考车辆顶点色。另外在系统临时目录执行两次重建，验证输入输出为同一个文件时仍完整保留入口文字、extras 和继承的坐标变换；不会在测试中覆盖网站正式 GLB。

几何与交互测试不能替代视觉检查。交付前仍需在首页查看建筑正面、青绿转角、侧面、装卸区及车辆，并切换楼层展开和室内视图。
