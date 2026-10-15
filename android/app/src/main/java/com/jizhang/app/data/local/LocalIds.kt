package com.jizhang.app.data.local

import kotlin.random.Random

/**
 * 客户端主键生成：毫秒时间戳左移 20 位 + 20 位随机数。
 * <p>
 * 保证离线创建（含账单批量导入）的唯一性，服务端按该 ID 做幂等写入。
 */
fun newClientId(): Long =
    (System.currentTimeMillis() shl 20) or Random.nextInt(1 shl 20).toLong()
