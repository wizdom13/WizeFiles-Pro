package com.wisso.wizefiles.provider.remote

import java.io.IOException

class BridgeUnavailableException : IOException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
