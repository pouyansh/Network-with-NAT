package ir.sharif.ce.partov.machine;

import ir.sharif.ce.partov.base.Frame;
import ir.sharif.ce.partov.base.Interface;
import ir.sharif.ce.partov.user.SimpleMachine;
import ir.sharif.ce.partov.user.SimulateMachine;
import ir.sharif.ce.partov.utils.Utility;

import java.util.ArrayList;
import java.util.Scanner;

public class ClientMachine extends SimpleMachine {
    private int ServerPort;
    private int ID, lastId;
    private boolean isIdValid = false, isForwarding = false;
    private ArrayList<Node> nodes = new ArrayList<>();
    private ArrayList<Integer> IdsWeSentPingTo = new ArrayList<>();
    private ArrayList<Integer> IdsHaveConnectionWith = new ArrayList<>();


    public ClientMachine(SimulateMachine simulatedMachine, Interface[] iface) {
        super(simulatedMachine, iface);
    }

    public void initialize() {
    }

    public void processFrame(Frame frame, int ifaceIndex) {
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        if (ip.getProtocol()!=17) {
            System.out.println("invalid packet, dropped");
            return;
        }

        if (ip.getDest() != iface[0].ip || udp.getDestPort() != ServerPort) {
            isForwarding = true;
            findIface(frame);
            isForwarding = false;
        } else {
            byte[] data = new byte[frameData.length-42];
            System.arraycopy(frameData, 42, data, 0, frameData.length-42);
            int type = (data[0] & 0x000000FF) / 32;
            int srcid = (data[0] & 0x000000FF) % 32;
            switch (type){
                case 0:
                    if (data.length == 1) {
                        if (!isIdValid) {
                            ID = (data[0] & 0x000000FF) % 32;
                            isIdValid = true;
                            System.out.println("Now My ID is " + ID);
                        }
                    } else if (data.length == 5) {
                        if (udp.getSrcPort()==1234) {
                            ServerPort += 100;
                            System.out.println("connection to server failed, retrying on port " + ServerPort);
                            sendRequestAssigningID();
                        } else {
                            if (IdsHaveConnectionWith.contains(lastId)) {
                                System.out.println("connection lost, perhaps " + lastId + "'s info has changed, ask server for updates");
                            }
                        }
                    }
                    break;
                case 1:
                    if (data.length == 13) {
                        int id = (data[0] & 0x000000FF) % 32;
                        byte[] localIP = new byte[4], publicIP = new byte[4], localport = new byte[2], publicport = new byte[2];
                        System.arraycopy(data, 1, localIP, 0, 4);
                        System.arraycopy(data, 5, localport, 0, 2);
                        System.arraycopy(data, 7, publicIP, 0, 4);
                        System.arraycopy(data, 11, publicport, 0, 2);
                        int localPort = (int) Utility.convertBytesToShort(localport);
                        int publicPort = (int) Utility.convertBytesToShort(publicport);
                        nodes.add(new Node(id, publicPort, localPort, publicIP, localIP));
                        System.out.println("packet with (" + id + ", " + Utility.getIPString(localIP) + ", " + localPort
                                + ", " + Utility.getIPString(publicIP) + ", " + publicPort + ") received");
                    }
                    break;
                case 2:
                    if (data.length == 5) {
                        if (data[2]==0x6f){
                            if (IdsWeSentPingTo.contains(srcid)) {
                                System.out.println("Connected to " + srcid);
                                IdsHaveConnectionWith.add(srcid);
//                                IdsWeSentPingTo.remove(srcid);
                            } else {
                                System.out.println("invalid packet, dropped");
                            }
                        }
                        else if (data[2]==0x69) {
                            nodes.add(new Node(srcid, udp.getSrcPort(), udp.getSrcPort(), Utility.getBytes(ip.getSrc()), Utility.getBytes(ip.getSrc())));
                            System.out.println("Connected to " + srcid);
                            IdsHaveConnectionWith.add(srcid);
                            sendResponseLocalPublicSession(ip.getSrc(), udp.getSrcPort());
                        }
                    }
                    break;
                case 3:
                    byte[] msg = new byte[data.length-1];
                    System.arraycopy(data, 1, msg, 0, data.length-1);
                    StringBuilder s = new StringBuilder();
                    for (byte aMsg : msg) {
                        s.append((char) aMsg);
                    }
                    System.out.println("received msg from "  +srcid + ":" + s.toString());
                    break;
                case 4:
                    sendRequestUpdatingInfo();
                    break;
                case 7:
                    if ( data.length == 7) {
                        int flag = (data[0] & 0x000000FF) % 32;
                        if ( flag == 1 ) System.out.println("direct");
                        else System.out.println("indirect");
                    }

            }
        }


    }

    public void run() {

        Scanner s = new Scanner(System.in);
        while (true) {
            String input = s.nextLine();
            String[] sp = input.split(" ");
            if (sp.length == 8 && sp[0].equals("make") && sp[1].equals("a") && sp[2].equals("connection") && sp[3].equals("to")
                    && sp[4].equals("server") && sp[5].equals("on") && sp[6].equals("port")) {
                if (isIdValid) System.out.println("you already have an id, ignored");
                else {
                    ServerPort = Integer.parseInt(sp[7]);
                    sendRequestAssigningID();
                }
            } else if (sp.length == 4 && sp[0].equals("get") && sp[1].equals("info") && sp[2].equals("of")) {
                sendRequestGettingIP(Integer.parseInt(sp[3]));
            } else if (sp.length == 6 && sp[0].equals("make") && sp[1].equals("a") && sp[3].equals("session") && sp[4].equals("to")) {

                switch (sp[2]) {
                    case "local":
                        sendRequestLocalPublicSession(Integer.parseInt(sp[5]), true);
                        break;
                    case "public":
                        sendRequestLocalPublicSession(Integer.parseInt(sp[5]), false);
                        break;
                    default:
                        System.out.println("invalid command");
                        break;
                }
            } else if (sp.length == 4 && sp[0].equals("send") && sp[1].equals("msg") && sp[2].equals("to")) {
                String[] sp2 = sp[3].split(":");
                if (IdsHaveConnectionWith.contains(Integer.parseInt(sp2[0])))
                    sendMsg(Integer.parseInt(sp2[0]), sp2[1]);
                else
                    System.out.println("please make a session first");
            } else if (input.equals("status"))
                sendStatus();
            else if (input.equals("exit")) break;
            else System.out.println("invalid command");
        }
        s.close();
    }

    private void findIface (Frame frame){
        EthernetHeader eth = new EthernetHeader();
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
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
        boolean check;
        for (int i = 0; i < iface.length; i++) {
            check = false;
            byte[] maskBytes = Utility.getBytes(iface[i].getMask());
            byte[] thisipBytes = Utility.getBytes(iface[i].getIp());
            for (int j = 0; j < 4; j++) {
                if ( (maskBytes[j] & thisipBytes[j]) != (maskBytes[j] & ipBytes[j])) {
                    check = true;
                    break;
                }
            }
            if (!check){
                if (!isForwarding){
                    eth.setSrc(iface[i].mac);
                    System.arraycopy(eth.getData(), 0, frameData, 0, 14);
                    newFrame = new Frame(frameData);
                }
                sendFrame(newFrame, i);
                return;
            }
        }
        if (!isForwarding){
            eth.setSrc(iface[0].mac);
            System.arraycopy(eth.getData(), 0, frameData, 0, 14);
            newFrame = new Frame(frameData);
        }
        sendFrame(newFrame, 0);
    }

    private void sendRequestAssigningID(){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(new byte[]{1, 1, 1, 1}));
        newIp.setSrc(iface[0].ip);
        newIp.setTTL(64);
        newIp.setProtocol(17);
        newIp.setTotalLength(35);
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
        newudp.setDestPort(1234);
        newudp.setSrcPort(ServerPort);
        newudp.setLen(15);

        byte[] newData = new byte[49];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        System.arraycopy(Utility.getBytes(iface[0].ip), 0, newData, 43, 4);
        System.arraycopy(Utility.getBytes((short) ServerPort), 0, newData, 47, 2);
        newData[42] = 0;

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, 0);
    }

    private void sendRequestGettingIP (int ID){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(new byte[]{1, 1, 1, 1}));
        newIp.setSrc(iface[0].ip);
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
        newudp.setDestPort(1234);
        newudp.setSrcPort(ServerPort);
        newudp.setLen(9);

        byte[] newData = new byte[43];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) (ID + 32);

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, 0);
    }

    private void sendRequestLocalPublicSession (int ID, boolean isLocal){
        IdsWeSentPingTo.add(ID);
        lastId = ID;
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setSrc(iface[0].ip);
        newIp.setTTL(65);
        newIp.setProtocol(17);

        UDPHeader newudp = new UDPHeader();
        newudp.setSrcPort(ServerPort);
        newudp.setLen(13);

        boolean check = false;
        for (Node node : nodes) {
            if (node.getId() == ID) {
                check = true;
                if (isLocal) {
                    newudp.setDestPort(node.getLocalPort());
                    newIp.setDest(Utility.convertBytesToInt(node.getLocalIP()));
                } else {
                    newudp.setDestPort(node.getPubilcPort());
                    newIp.setDest(Utility.convertBytesToInt(node.getPublicIP()));
                }
                break;
            }
        }
        newIp.setTotalLength(33);
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

        if (!check) {
            System.out.println("info of node " + ID + " was not received");
            return;
        }

        byte[] newData = new byte[47];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) (this.ID + 64);
        newData[43] = 'p';
        newData[44] = 'i';
        newData[45] = 'n';
        newData[46] = 'g';


        Frame frame1 = new Frame(newData);
        findIface(frame1);

    }

    private void sendResponseLocalPublicSession(int DstIp, int DstPort){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setSrc(iface[0].ip);
        newIp.setTTL(65);
        newIp.setDest(DstIp);
        newIp.setTotalLength(33);
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
        newudp.setSrcPort(ServerPort);
        newudp.setLen(13);
        newudp.setDestPort(DstPort);

        byte[] newData = new byte[47];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) (this.ID + 64);
        newData[43] = 'p';
        newData[44] = 'o';
        newData[45] = 'n';
        newData[46] = 'g';


        Frame frame1 = new Frame(newData);
        findIface(frame1);
    }

    private void sendMsg(int ID, String msg){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setSrc(iface[0].ip);
        newIp.setTTL(65);
        newIp.setProtocol(17);

        UDPHeader newudp = new UDPHeader();
        newudp.setSrcPort(ServerPort);
        newudp.setLen(9+msg.length());

        for (Node node : nodes) {
            if (node.getId() == ID) {
                newudp.setDestPort(node.getPubilcPort());
                newIp.setDest(Utility.convertBytesToInt(node.getPublicIP()));
                break;
            }
        }
        newIp.setTotalLength(29+msg.length());
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

        byte[] newData = new byte[43+msg.length()];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) (this.ID + 96);
        for (int i = 0; i < msg.length(); i++) {
            newData[43+i] = (byte) msg.charAt(i);
        }


        Frame frame1 = new Frame(newData);
        findIface(frame1);
    }

    private void sendRequestUpdatingInfo (){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(new byte[]{1, 1, 1, 1}));
        newIp.setSrc(iface[0].ip);
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
        newudp.setDestPort(1234);
        newudp.setSrcPort(ServerPort);
        newudp.setLen(15);

        byte[] newData = new byte[49];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        System.arraycopy(Utility.getBytes(iface[0].ip), 0, newData, 43, 4);
        System.arraycopy(Utility.getBytes(ServerPort), 0, newData, 47, 2);
        newData[42] = (byte) 160;

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, 0);
    }

    private void sendStatus(){
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(new byte[]{1, 1, 1, 1}));
        newIp.setSrc(iface[0].ip);
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
        newudp.setDestPort(1234);
        newudp.setSrcPort(ServerPort);
        newudp.setLen(15);

        byte[] newData = new byte[49];
        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        System.arraycopy(Utility.getBytes(iface[0].ip), 0, newData, 43, 4);
        System.arraycopy(Utility.getBytes(ServerPort), 0, newData, 47, 2);
        newData[42] = (byte) 192;

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, 0);
    }
}