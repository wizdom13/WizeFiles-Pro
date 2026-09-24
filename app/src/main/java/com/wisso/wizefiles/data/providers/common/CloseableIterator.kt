package com.wisso.wizefiles.provider.common

import java.io.Closeable

interface CloseableIterator<T> : Iterator<T>, Closeable
