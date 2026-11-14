# 逆向笔记：Hpoi 2.43 与 ExHobby

这份文档记录模块开发时的逆向过程与结论，方便宿主升级后重新核对。
所有字段名与方法名都来自**真实 dex**（`tools/dex_names.py`），不是反编译器的重命名。

样本：`hpoi_2.43.apk`，package `net.hpoi`，versionCode 200135，minSdk 24，targetSdk 34，
6 个 dex，未加壳。

## 一、手办详情页

`net.hpoi.ui.hobby.HobbyDetailActivity`（在 `classes6.dex`）。

### 生命周期

```
onCreate  → setContentView(binding.getRoot()); initView(); x1(); n1(); S1();
```

- `x1()` 组装 header 列表并交给 `HeaderCommentListView.setHeader(List)`。
- `S1()` 解析 Intent，然后发详情请求。
- 详情回来时走 `i2(activity, ApiResult)`。

### Intent 约定

入口 `HobbyDetailActivity.a`（混淆名，3 个静态方法）：

| 方法 | 传的法 |
| --- | --- |
| `a(Context, long)` | `itemNodeId` |
| `b(Context, String)` | `itemData`（JSON 字符串） |
| `c(Context, String, int)` | `itemType` + `itemId` |

`S1()` 的优先级：`itemData` JSON → scheme 参数 → `itemNodeId` / `itemId` 两个 extra。
列表页最常用 `b(context, jSONObject.toString())`。

### 字段（真实名）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `s` | `JSONObject` | 完整 itemData |
| `t` | `long` | nodeId |
| `u` | `int` | itemId（就是 hobby id） |
| `v` | `String` | itemType（`"hobby"`） |
| `w` | `boolean` | 是否已收藏 |
| `k` | `ActivityHobbyDetailBinding` | 根 binding |

`i2()` 是唯一同时写 `t` / `u` / `v` 的地方，所以模块在这里读 itemId。

## 二、header 插入机制

`net.hpoi.ui.part.comment.HeaderCommentListView extends RecyclerView`：

```java
public void setHeader(List<ViewBinding> list) {
    HeaderCommentListAdapter a = new HeaderCommentListAdapter(
            new JSONArray(), getContext(), null, this.f73461h, this.f73470q);
    this.f73459f = a;
    a.b0(this.f73460g);
    this.f73459f.a0(list);      // 只是把 list 存进 adapter 字段
    setAdapter(this.f73459f);
}
```

adapter 侧：

- `getItemViewType(i)`：`if (headerList.size() >= i + 1) return i + 2;` —— header 的 viewType 从 2 起按索引递增。
- `onCreateViewHolder(parent, viewType)`：`for (i...) if (i + 2 == viewType) return new BindingHolder(headerList.get(i));`
- `onBindViewHolder`：第一段就是 header 短路，`if (holder.getItemViewType() == i + 2) return;`
- `getItemCount()`：有评论时 `comments.length() + headerList.size()`，无评论时 `headerList.size() + 1`。

结论：header 不需要实现任何绑定逻辑，塞进去就能显示。插入点在 `setHeader` 的**参数列表**上，
因为方法内部会重建 adapter，改参数比在 after 里重设 adapter 干净。

`androidx.viewbinding.ViewBinding` 只有一个方法：

```java
public interface ViewBinding { @NonNull View getRoot(); }
```

## 三、ExHobby 接口

### 1. 搜索跳转（现在的入口）

```
GET https://www.exhobby.net/search/item?type=hobby&itemId=<id>
→ 302 Location: http://www.exhobby.net/picture?link=<hex>
```

- 一条请求直达图集页，不再需要先走 `/get/pic` 拿签名链接。
- GET 与 form POST 等价；`type=album` 拿到 `-1`，`type=url` 是 404。
- **坑**：`Location` 是 `http://`，起点是 `https://`，`HttpURLConnection` 不会跨协议降级跟随 ——
  `getResponseCode()` 停在 302、`getURL()` 还是原地址、body 为空。必须 `setInstanceFollowRedirects(false)`
  读 `Location`，升回 https 再取。

浏览器打开同一个 URL 时会自动 302 进图集页，所以外链不需要任何接口响应即可拼出。

### 2. 图集页

```
GET https://www.exhobby.net/picture?link=<hex>
```

服务端直出 20 张缩略图，页面里有两样东西有用：

```html
<script>query.item = "46163";</script>   <!-- 图集内部 id，与 hobby id 无关 -->
<img src="https://res.e39x.com/pic/s/2022/10/xxx.jpg">
<a href="https://res.e39x.com/pic/n/2022/10/xxx.jpg">
```

死 id 的 `query.item` 是 `"-1"`，页面 0 张图。模块据此判定「无图」，不需要额外的兜底探测。

### 3. 分页接口（模块当前不用）

页面脚本 `/res/js/base.js` 里：

```js
$(".load-more").click(function(){ loading(true); loadMore(); });

function loadMore() {
  $.post("/picture/" + query.item, {page: query.page, item: query.item, type: query.type}, ...)
}
```

即：

```
POST https://www.exhobby.net/picture/<item>
Content-Type: application/x-www-form-urlencoded
Cookie: is18=yes

page=2&item=<item>&type=all
```

返回 `[{path,id,picId}, ...]`，每页 20 条，空数组即结束。第 1 页已经在 HTML 里，从 `page=2` 开始。

**坑**：不带 `Cookie: is18=yes` 时返回 200 + 空 body（不是 `[]`），容易被误判成「没有更多图」。

模块只显示前 10 张，首屏 HTML 的 20 张已经够用，所以不再翻页；`Cookie` 仍然保留，图集页的 18 岁门槛也靠它。

### 4. 图片 CDN

```
https://res.e39x.com/pic/s/<path>    400×533    缩略图
https://res.e39x.com/pic/n/<path>   1200×1600   预览
https://res.e39x.com/pic/raw/<path> 3072×4096   原图
```

UA 必须非空（`okhttp/4.9.0` 就够），完全不带 UA 会 403。TLS 证书正常校验即可通过。

### 5. 旧的预览接口（已弃用）

```
GET https://www.exhobby.net/get/pic?itemId=<id>&itemType=hobby
```

返回 `{"success":true,"map":{"list":[{path,id,picId}...],"url":"http://www.exhobby.net/picture?link=<hex>"}}`。
最多 6 条、`path` 成对重复，`url` 是签名图集页。模块早期版本走这条链路，因为多一次请求所以弃用；
但它仍是「兜底图集」的来源 —— 死 id 会返回一组站内热门图并共用同一个签名链接，现在靠 `query.item == "-1"`
就能直接排除，不必再探测。

## 四、实测数据

2026-11 采样，`dev/probe.sh`（模块只取前 10 张，`truncated=true` 表示站点还有更多）：

| hobby id | 模块取到 | 站点总数 | 图集 item |
| --- | --- | --- | --- |
| 3 | 7 | 7 | 46163 |
| 8 | 1 | 1 | — |
| 9 | 2 | 2 | — |
| 1617 | 10 | 15 | 47917 |
| 3194 | 10 | 41 | 49563 |
| 16867 | 10 | 35 | 63218 |
| 19455 | 10 | 67 | 64598 |
| 99999999 / 180001 / 200000 / 2400 / 12345 | 0 | 0 | — |

首屏耗时 225~315ms（首个请求含 DNS/TLS 冷启动约 940ms）；对比旧的 `get/pic` 链路（首屏 909ms、总计 1273ms）约快 3 倍。
「站点总数」一列来自此前分页实测，模块本身只读图集页首屏的 20 张，不再翻页。

## 五、宿主升级后要重新核对的点

1. `HostBridge.ACT_HOBBY` / `VIEW_LIST` 两个类名。
2. `HeaderCommentListView.setHeader(List)` 是否还在、签名是否还是 `(Ljava/util/List;)V`。
3. `HobbyDetailActivity` 的 `i2` 方法名，以及字段 `u` / `v` 是否还是 itemId / itemType。
4. `i2` 是静态（activity 在 `args[0]`）还是实例方法 —— 模块两种都兼容。
5. `getItemViewType` 的 `+2` 偏移是否还成立（不影响插入，只影响显示位置）。
