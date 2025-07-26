package expo.modules.callerid

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class CallDetectScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
                val response = CallResponse.Builder()
                response.setDisallowCall(false)
                response.setRejectCall(false)
                response.setSilenceCall(false)
                response.setSkipCallLog(false)
                response.setSkipNotification(false)

                CallReceiver.callServiceNumber = details.handle.schemeSpecificPart
                respondToCall(details, response.build())
            }
        }
    }
}