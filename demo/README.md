# Xjie 健捷 · Web Demo

网页版演示，用于在浏览器中展示 Xjie 移动端的主要功能，后端直连生产环境
`https://www.jianjieaitech.com/api`。

## 演示账号

- 手机号：`18956082283`
- 密码：`Test1234!`

登录页已预填，点击「登录」即可。

## 本地启动

```bash
cd demo
python3 -m http.server 5173
# 然后浏览器打开 http://localhost:5173/
```

> 直接双击 `index.html`（file://）部分浏览器会拦截 fetch，请使用 http 方式。

## 覆盖功能

- 首页：主动提醒轮播（18 条文案）、AI 今日简报、24h 血糖指标与曲线
- 健康数据：AI 体检综合摘要、关注指标 + 最近取值、文档列表
- 餐食：最近 7 天餐食记录 + 卡路里
- 多组学：代谢 / CGM / 心脏 三维耦合与 AI 解读
- AI 对话：与真实后端 LLM 通讯（Moonshot Kimi）
- 心情：最近 14 天评分
- 账户设置：profile、干预级别、同意项

## 注意

Demo 只做只读展示 + 对话；请勿用演示账号修改生产数据。
