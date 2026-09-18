# kb-agent-lite API 接口文档

> 版本：1.0.0 ｜ 供前端团队对接使用
> 除特别标注外，所有接口均需携带请求头 `Authorization: Bearer <token>`（登录后获得）。
> 统一响应结构：`{"code": 0, "msg": "success", "data": ...}`，`code=0` 表示成功，非 0 为失败（`data` 为 null）。
> 401 表示未登录/登录过期，前端应清除本地 token并跳转登录页。

---

## 1. 登录鉴权

### 1.1 登录（免鉴权）

```
POST /api/auth/login
Content-Type: application/json

{"password": "访问口令"}
```

响应：
```json
{"code": 0, "msg": "success", "data": {"token": "eyJzdWIi..."}}
```

- token 为 HMAC 自包含令牌，有效期默认 72 小时（服务端配置）。
- 后续所有请求携带 `Authorization: Bearer <token>`；文件预览等直链场景可用查询参数 `?token=<token>`。

---

## 2. 知识库分类管理

### 2.1 分类树

```
GET /api/knowledge/category/tree
```

响应 `data`：分类数组（树形，子节点在 `children` 字段）。

### 2.2 分类平铺列表

```
GET /api/knowledge/category/list
```

响应 `data` 元素：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 分类ID |
| parentId | Long | 父分类ID，0 为顶级 |
| categoryName | String | 分类名称 |
| categoryCode | String | 分类编码（唯一） |
| documentCount | Integer | 文档数量 |

### 2.3 新增分类

```
POST /api/knowledge/category
{"categoryName": "必填", "categoryCode": "可空自动生成", "description": "可空", "sortOrder": 0}
```

### 2.4 更新分类

```
PUT /api/knowledge/category/{id}
{"categoryName": "可空", "description": "可空", "sortOrder": 0}
```

### 2.5 删除分类

```
DELETE /api/knowledge/category/{id}
```

分类下仍有文档时返回失败（msg 提示数量）。

---

## 3. 知识库文档管理

### 3.1 上传文档（支持多文件）

```
POST /api/knowledge/document
Content-Type: multipart/form-data

files        文件数组，必填（pdf/doc/docx/xls/xlsx/ppt/pptx/md/txt/rtf，单文件 ≤ 50MB）
categoryId   分类ID，可空
tags         标签，逗号分隔，可空
remark       备注，可空
```

响应 `data`：文档 VO 数组。上传即触发后台异步向量化，前端应轮询列表查看状态。

文档 VO：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 记录ID（预览/删除用） |
| documentId | String | 文档唯一标识（向量库关联键） |
| title / originalFileName | String | 标题 / 原始文件名 |
| fileType / fileSizeFormatted | String | MIME / 格式化大小 |
| categoryId / categoryName | - | 分类 |
| vectorStatus | Integer | **0-未开始 1-处理中 2-成功 3-失败** |
| vectorStatusText | String | 状态中文 |
| vectorCount / chunkCount | Integer | 向量数 / 分块数 |
| vectorError | String | 失败原因 |
| tags / remark / createTime | - | 其他 |

### 3.2 文档分页列表

```
GET /api/knowledge/document/list?current=1&size=10&keyword=&categoryId=&vectorStatus=
```

响应 `data`：MyBatis-Plus 分页结构 `{records: [...], total, size, current, pages}`。

### 3.3 文档预览/下载（原始文件流）

```
GET /api/knowledge/document/{id}/preview
Authorization: Bearer <token>    或    ?token=<token>（直链场景）
```

返回文件流，`Content-Type` 为上传时的 MIME，`Content-Disposition: inline`。新窗口打开即可预览。

### 3.4 删除文档

```
DELETE /api/knowledge/document/{id}
```

联动删除向量数据与磁盘文件。

### 3.5 批量删除

```
POST /api/knowledge/document/batch-delete
{"ids": [1, 2, 3]}
```

### 3.6 存储配额查询

```
GET /api/knowledge/document/quota
```

响应 `data`：`{usedBytes, quotaBytes, docCount, maxDocCount}`（quotaBytes/maxDocCount 为 0 表示不限）。可用于前端展示"已用 X MB / 总配额 Y MB"。

### 3.7 标签列表（去重）

```
GET /api/knowledge/document/tags
```

---

## 4. 智能体对话

### 4.1 对话入口（SSE 流式）★核心接口

```
POST /api/chat
Content-Type: application/json
Accept: text/event-stream

{"sessionId": "可空，为空自动创建新会话", "message": "用户问题"}
```

响应为 SSE 事件流（`text/event-stream;charset=UTF-8`），**没有 event name，全部通过 data 中的 JSON `type` 字段区分**。服务端已设置 `X-Accel-Buffering: no`，前端需用 `fetch` + `ReadableStream` 读取（**不能用 EventSource**，因为是 POST + 需要请求头），按 `\n\n` 切分帧、取 `data:` 后的 JSON 解析：

```js
const resp = await fetch('/api/chat', {method: 'POST', headers: {...}, body: JSON.stringify({sessionId, message})});
const reader = resp.body.getReader();
const decoder = new TextDecoder('utf-8');
let buffer = '';
while (true) {
  const {done, value} = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, {stream: true});
  const parts = buffer.split('\n\n');
  buffer = parts.pop();
  for (const frame of parts) {
    for (const line of frame.split('\n')) {
      if (line.startsWith('data:')) handleEvent(JSON.parse(line.substring(5)));
    }
  }
}
```

事件类型：

| type | 说明 | 关键字段 |
|---|---|---|
| session | 会话ID（首事件，后续请求带回实现多轮） | sessionId |
| status | 状态提示 | content |
| thinking | 思考中提示 | content |
| token | **增量文本**（逐段追加渲染 Markdown） | content |
| tool_call | 知识库检索开始 | tool, content |
| tool_result | 检索完成 | tool, content |
| sources | **引用来源**（检索命中后推送） | sources: [{fileName, category, score}] |
| error | 错误 | content |
| complete | 结束（content 为完整回复） | content |
| heartbeat | 心跳（思考期间约 10s 一次） | timestamp |

超时：SSE 连接最长 180s；单次问答链路 170s。

### 4.2 断线恢复

```
GET /api/chat/recover/{sessionId}
```

响应 `data`：`{sessionId, content, contentType, createTime}`（最近一条 AI 回复）。404 表示会话无效或尚无 AI 回复。

### 4.3 健康检查（免鉴权）

```
GET /api/chat/health
```

---

## 5. 会话管理

| 接口 | 方法与路径 | 说明 |
|---|---|---|
| 会话列表 | `GET /api/sessions` | 按活跃时间倒序；title 默认为最后一句用户消息 |
| 新建会话 | `POST /api/sessions` `{"title":"可空"}` | |
| 删除会话 | `DELETE /api/sessions/{sessionId}` | 级联清理消息与记忆 |
| 改标题 | `PUT /api/sessions/{sessionId}/title` `{"title":"..."}` | 自定义后不再被动态标题覆盖 |
| 历史消息 | `GET /api/sessions/{sessionId}/messages` | 已过滤系统/工具/中间消息，元素 `{id, role, content, createTime}`，role: USER / AI |

---

## 6. 对接注意事项

1. **流式渲染**：token 事件为增量文本，累积后用 Markdown 渲染（回答中可能含 Mermaid 代码块与表格）。
2. **引用展示**：sources 事件在 tool_result 之后推送，可紧贴该条 AI 回复展示引用来源（文件名/分类/相似度）。
3. **处理中状态**：上传后文档 vectorStatus=1，建议列表页对"处理中"文档做 5 秒轮询直至 2/3。
4. **会话连续性**：对话响应首个 session 事件返回会话ID；下次请求 body 带上即可延续上下文。
