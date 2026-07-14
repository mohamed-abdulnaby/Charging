public class CallSessionInfo {
    private String msisdn;
    private String startTime;
    private long startTimeEpoch;
    private int elapsedMinutes;
    private int udpPort;
    private String callType; // "SIP", "CLI", "WebSim"
    private double currentBalance;

    public CallSessionInfo(String msisdn, String startTime, int elapsedMinutes, int udpPort, String callType, double currentBalance) {
        this.msisdn = msisdn;
        this.startTime = startTime;
        this.startTimeEpoch = System.currentTimeMillis();
        this.elapsedMinutes = elapsedMinutes;
        this.udpPort = udpPort;
        this.callType = callType;
        this.currentBalance = currentBalance;
    }

    public String getMsisdn() { return msisdn; }
    public String getStartTime() { return startTime; }
    public long getStartTimeEpoch() { return startTimeEpoch; }
    public int getElapsedMinutes() { return elapsedMinutes; }
    public void setElapsedMinutes(int elapsedMinutes) { this.elapsedMinutes = elapsedMinutes; }
    public int getUdpPort() { return udpPort; }
    public String getCallType() { return callType; }
    public double getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }
}
