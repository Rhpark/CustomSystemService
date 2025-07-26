package kr.open.library.systemmanager.info.network.telephony.data.state

public data class TelephonyNetworkState(
    public val networkTypeState: TelephonyNetworkType,
    public val networkTypeDetailState: TelephonyNetworkDetailType
) {
}