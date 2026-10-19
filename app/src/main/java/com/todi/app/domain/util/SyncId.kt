package com.todi.app.domain.util

import java.util.UUID

fun newSyncId(): String = UUID.randomUUID().toString().replace("-", "")
