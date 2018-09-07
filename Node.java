package ir.sharif.ce.partov.machine;

public class Node {
    private int PubilcPort, LocalPort;
    private byte[] PublicIP, LocalIP;
    private int Id;

    public Node(int id, int pubilcPort, int localPort, byte[] publicIP, byte[] localIP) {
        Id = id;
        PubilcPort = pubilcPort;
        LocalPort = localPort;
        PublicIP = publicIP;
        LocalIP = localIP;

    }

    public int getId() {
        return Id;
    }

    public int getPubilcPort() {
        return PubilcPort;
    }

    public int getLocalPort() {
        return LocalPort;
    }

    public byte[] getPublicIP() {
        return PublicIP;
    }

    public byte[] getLocalIP() {
        return LocalIP;
    }
}