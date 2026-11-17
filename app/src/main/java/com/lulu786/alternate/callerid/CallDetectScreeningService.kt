package com.lulu786.alternate.callerid

import android.telecom.Call
import android.telecom.CallScreeningService

/** Only remembers the number for [CallReceiver]; never blocks, silences or rejects a call. */
class CallDetectScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        CallReceiver.callServiceNumber = details.handle?.schemeSpecificPart
        respondToCall(details, CallResponse.Builder().build())
    }
}
