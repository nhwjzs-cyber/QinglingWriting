/**
 * 同步引擎(docs/03 §2:OutboxDrainer, PullApplier, ConflictResolver, Worker)。
 * M0 留空;Worker 只消费已持久化的任务,不拥有正文内存缓冲(docs/03 §3);
 * 不可把模拟同步当真实同步、不可把网络成功当本地保存成功(GLM-START.md 约束)。
 */
package com.qingling.writing.sync
