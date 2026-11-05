package com.qingling.writing.domain

/**
 * 领域错误(docs/05 §2:错误使用明确类型,不得 catch(Exception) 吞掉后返回成功/空列表)。
 * M1 范围:标题校验失败与目标不存在;磁盘满/存储失效等 IO 类错误在 M2 保存链路引入。
 */
class TitleValidationException(val error: com.qingling.writing.domain.validation.TitleError) :
    Exception("Title rejected: $error")

/** 目标(书/卷/章/批次)不存在或已被删除。 */
class NotFoundException(val what: String, val id: String) :
    Exception("$what not found or already deleted: $id")
