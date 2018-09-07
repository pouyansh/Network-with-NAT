package ir.sharif.ce.partov.machine;

import ir.sharif.ce.partov.base.Frame;
import ir.sharif.ce.partov.base.Interface;
import ir.sharif.ce.partov.user.SimpleMachine;
import ir.sharif.ce.partov.user.SimulateMachine;
import ir.sharif.ce.partov.utils.Utility;

import java.util.ArrayList;

public class ServerMachine extends SimpleMachine {

    private ArrayList<Node> nodes = new ArrayList<>();
    private int leastFreeID = 1;
    private int ifaceindex;

    public ServerMachine(SimulateMachine simulatedMachine, Interface[] iface) {
        super(simulatedMachine, iface);
    }

    public void initialize() {

    }

    public void processFrame(Frame frame, int ifaceIndex) {
        this.ifaceindex = ifaceIndex;
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frame.data.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        if (ip.getProtocol()!=17) {
            System.out.println("invalid packet, dropped");
            return;
        }
//        byte[] ipdata = ip.getData();
//        int checksum = ip.getChecksum();
//        int sum = 0, sum1;
//        for (int i = 0; i < ipdata.length; i+=2) {
//            if (i != 10) {
//                sum += Utility.convertBytesToShort(new byte[]{(byte) (ipdata[i] & 255), (byte) (ipdata[i + 1] & 255)});
//            }
//        }
//        while (sum / (1<<16) > 0) {
//            sum1 = sum % (1<<16);
//            sum = sum / (1<<16) + sum1;
//        }
//        sum = -1*sum -1;
//        if ( checksum != sum ){
//            System.out.println("invalid packet, dropped checksum " + sum + " " + checksum);
//            return;
//        }
        int srcip = ip.getSrc();
        int srcport = udp.getSrcPort();
        byte[] destip = Utility.getBytes(ip.getDest());
        int port = udp.getDestPort();
        if (destip[0]==1 && destip[1]==1 && destip[2]==1 && destip[3]==1 && port == 1234){
            byte[] data = new byte[frameData.length-42];
            System.arraycopy(frameData, 42, data, 0, frameData.length-42);
            int type = (data[0] & 0x000000FF) / 32;
            boolean check = false;
            for (Node node : nodes) {
                if (node.getPubilcPort() == srcport && Utility.convertBytesToInt(node.getPublicIP()) == srcip) {
                    check = true;
                    break;
                }
            }
            switch (type){
                case 0:
                    System.out.println("new id " + leastFreeID + " assigned to " + Utility.getIPString(srcip)+":"+srcport);
                    sendResponseAssigningID(frame);
                    break;
                case 1:
                    if (!check) {
                        System.out.println("id not exist, dropped");
                        return;
                    }
                    sendResponseGettingIP(frame);
                    break;
                case 5:
                    if (!check) {
                        System.out.println("id not exist, dropped");
                        return;
                    }
                    System.out.println("id "+ leastFreeID + " infos updated to " + Utility.getIPString(srcip)+":"+srcport);
                    sendResponseAssigningID(frame);
                    break;
                case 6:
                    if (!check) {
                        System.out.println("id not exist, dropped");
                        return;
                    }
                    sendStatusRespond(frame);
                    break;
            }
        } else {
            IPv4Header newIp = new IPv4Header();
            newIp.setTTL(ip.getTTL()-1);
            newIp.setSrc(ip.getSrc());
            newIp.setDest(ip.getDest());
            newIp.setTotalLength(ip.getTotalLength());
            newIp.setProtocol(17);
            byte[] ipdata = newIp.getData();
            int sum = 0;
            for (int i = 0; i < ipdata.length; i+=2) {
                int tmp = Utility.convertBytesToShort(new byte[] {(byte) (ipdata[i] & 255), (byte) (ipdata[i+1] & 255)});
                sum += tmp < 0 ? (1<<16) + tmp : tmp;
            }
            while (sum / (1<<16) > 0) {
                sum = sum / (1<<16) + sum % (1<<16);
            }
            byte[] b = Utility.getBytes((short) sum);
            b[0] ^= 0xFF;
            b[1] ^= 0xFF;
            newIp.setChecksum(Utility.convertBytesToShort(b));
            System.arraycopy(newIp.getData(), 0, frameData, 14, 20);
            Frame newFrame = new Frame(frameData);

            byte[] ipBytes = Utility.getBytes(ip.getDest());
            for (int i = 0; i < iface.length; i++) {
                boolean check = false;
                int mask = iface[i].getMask();
                int thisip = iface[i].getIp();
                byte[] maskBytes = Utility.getBytes(mask);
                byte[] thisipBytes = Utility.getBytes(thisip);
                for (int j = 0; j < 4; j++) {
                    if ( (maskBytes[j] & thisipBytes[j]) != (maskBytes[j] & ipBytes[j])) {
                        check = true;
                        break;
                    }
                }
                if (!check){
                    sendFrame(newFrame, i);
                    return;
                }
            }

        }

    }
    public void run() {
    }

    private void sendResponseAssigningID (Frame frame){
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        for (Node node : nodes) {
            if (node.getPubilcPort() == udp.getSrcPort() && Utility.convertBytesToInt(node.getPublicIP()) == ip.getSrc()) {
                System.out.println("you already have an id, ignored");
                return;
            }
        }
        nodes.add(new Node(leastFreeID, udp.getSrcPort(), Utility.convertBytesToShort(new byte[] {frameData[47]
                , frameData[48]}), Utility.getBytes(ip.getSrc()), new byte[] {frameData[43], frameData[44],frameData[45], frameData[46]}));

        EthernetHeader neweth = new EthernetHeader();
//        neweth.setType(0);
        neweth.setSrc(iface[ifaceindex].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(ip.getSrc());
        newIp.setSrc(ip.getDest());
        newIp.setTTL(64);
        newIp.setTotalLength(29);
        newIp.setProtocol(17);
        byte[] ipdata = newIp.getData();
        int sum = 0;
        for (int i = 0; i < ipdata.length; i+=2) {
            int tmp = Utility.convertBytesToShort(new byte[] {(byte) (ipdata[i] & 255), (byte) (ipdata[i+1] & 255)});
            sum += tmp < 0 ? (1<<16) + tmp : tmp;
        }
        while (sum / (1<<16) > 0) {
            sum = sum / (1<<16) + sum % (1<<16);
        }
        byte[] b = Utility.getBytes((short) sum);
        b[0] ^= 0xFF;
        b[1] ^= 0xFF;
        newIp.setChecksum(Utility.convertBytesToShort(b));

        UDPHeader newudp = new UDPHeader();
        newudp.setDestPort(udp.getSrcPort());
        newudp.setSrcPort(udp.getDestPort());
        newudp.setLen(9);

        byte[] newData = new byte[43];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) leastFreeID;

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, ifaceindex);
        leastFreeID ++;
    }

    private void sendResponseGettingIP (Frame frame){
        byte[] frameData = new byte[frame.data.length];
        byte[] newData = new byte[55];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);

        int destId = frameData[frameData.length-1] - 32;
        int srcport = udp.getSrcPort();
        int id = 0;
        for (Node node1 : nodes) {
            if (node1.getPubilcPort() == srcport && Utility.convertBytesToInt(node1.getPublicIP()) == ip.getSrc()) {
                id = node1.getId();
                break;
            }
        }
        System.out.println(id + " wants info of node " + destId);

        newData[42] = (byte) (32 + destId);
        boolean check = false;
        for (Node node : nodes) {
            if (node.getId() == destId) {
                check = true;
                System.arraycopy(node.getLocalIP(), 0, newData, 43, 4);
                System.arraycopy(Utility.getBytes((short) node.getLocalPort()), 0, newData, 47, 2);
                System.arraycopy(node.getPublicIP(), 0, newData, 49, 4);
                System.arraycopy(Utility.getBytes((short) node.getPubilcPort()), 0, newData, 53, 2);
                break;
            }
        }
        if (!check) {
            System.out.println("id not exist, dropped");
            return;
        }

        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[ifaceindex].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(ip.getSrc());
        newIp.setSrc(ip.getDest());
        newIp.setTTL(64);
        newIp.setTotalLength(41);
        newIp.setProtocol(17);
        byte[] ipdata = newIp.getData();
        int sum = 0;
        for (int i = 0; i < ipdata.length; i+=2) {
            int tmp = Utility.convertBytesToShort(new byte[] {(byte) (ipdata[i] & 255), (byte) (ipdata[i+1] & 255)});
            sum += tmp < 0 ? (1<<16) + tmp : tmp;
        }
        while (sum / (1<<16) > 0) {
            sum = sum / (1<<16) + sum % (1<<16);
        }
        byte[] b = Utility.getBytes((short) sum);
        b[0] ^= 0xFF;
        b[1] ^= 0xFF;
        newIp.setChecksum(Utility.convertBytesToShort(b));

        UDPHeader newudp = new UDPHeader();
        newudp.setDestPort(udp.getSrcPort());
        newudp.setSrcPort(udp.getDestPort());
        newudp.setLen(21);

        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, ifaceindex);
    }

    private void sendStatusRespond (Frame frame){
        byte[] frameData = new byte[frame.data.length];
        byte[] newData = new byte[49];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        int id = ip.getID();
        int srcip = ip.getSrc();

        for (Node node : nodes) {
            if (node.getId() == id) {
                if (Utility.convertBytesToInt(node.getLocalIP())!= srcip) {
                    newData[42] = (byte) 224;
                } else {
                    newData[42] = (byte) 225;
                }
            }
        }

        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[ifaceindex].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(ip.getSrc());
        newIp.setSrc(ip.getDest());
        newIp.setTTL(64);
        newIp.setTotalLength(35);
        newIp.setProtocol(17);
        byte[] ipdata = newIp.getData();
        int sum = 0;
        for (int i = 0; i < ipdata.length; i+=2) {
            int tmp = Utility.convertBytesToShort(new byte[] {(byte) (ipdata[i] & 255), (byte) (ipdata[i+1] & 255)});
            sum += tmp < 0 ? (1<<16) + tmp : tmp;
        }
        while (sum / (1<<16) > 0) {
            sum = sum / (1<<16) + sum % (1<<16);
        }
        byte[] b = Utility.getBytes((short) sum);
        b[0] ^= 0xFF;
        b[1] ^= 0xFF;
        newIp.setChecksum(Utility.convertBytesToShort(b));

        UDPHeader newudp = new UDPHeader();
        newudp.setDestPort(udp.getSrcPort());
        newudp.setSrcPort(udp.getDestPort());
        newudp.setLen(15);

        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        System.arraycopy(new byte[] {0,0,0,0,0,0}, 0, newData, 43, 6);

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, ifaceindex);
    }
}