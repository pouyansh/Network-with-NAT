package ir.sharif.ce.partov.machine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import ir.sharif.ce.partov.base.Frame;
import ir.sharif.ce.partov.base.Interface;
import ir.sharif.ce.partov.user.SimpleMachine;
import ir.sharif.ce.partov.user.SimulateMachine;
import ir.sharif.ce.partov.utils.Utility;

public class NATMachine extends SimpleMachine {
    private ArrayList<Node> nodes = new ArrayList<>();
    private int nextPort = 2000, baseStart = 2000;
    private int difIp = 1;
    private ArrayList<ArrayList<Node>> nodesSomePacketsWereSentTo = new ArrayList<>();
    private Set<Integer> blocketPorts = new HashSet<>();
    private ArrayList<ArrayList<Node>> haveSessions = new ArrayList<>();
	
	public NATMachine(SimulateMachine simulatedMachine, Interface[] iface) {
		super(simulatedMachine, iface);
	}

	public void initialize() {
	}

	public void processFrame(Frame frame, int ifaceIndex) {
	    Node currentNode = null;
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frame.data.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        if (ip.getProtocol()!=17) {
            System.out.println("invalid packet, dropped");
            return;
        }

//        for (byte aFrameData : frameData) {
//            System.out.print(Utility.byteToHex(aFrameData) + " ");
//        }
//        System.out.println();

        int srcip = ip.getSrc();
        int srcport = udp.getSrcPort();
        int dstip = ip.getDest();
        int dstport = udp.getDestPort();
        boolean check = false;
        if (ifaceIndex!=0) {
            for (int i = 0; i < nodes.size(); i++) {
                Node node1 = nodes.get(i);
                if (node1.getLocalPort() == srcport && srcip == Utility.convertBytesToInt(node1.getLocalIP())) {
                    currentNode = node1;
                    nodesSomePacketsWereSentTo.get(i).add(new Node(0, dstport, dstport, Utility.getBytes(dstip), Utility.getBytes(dstip)));
                    check = true;
                    break;
                }
            }
            if (!check) {
                if (blocketPorts.contains(srcport)) {
                    sendDrop(new Node(ifaceIndex, nextPort, srcport, Utility.getBytes(iface[0].getIp() + difIp), Utility.getBytes(srcip)), 1234);
                    return;
                } else {
//                    System.out.println(iface[0].getIp());
                    currentNode = new Node(ifaceIndex, nextPort, srcport, Utility.getBytes(iface[0].getIp() + difIp), Utility.getBytes(srcip));
                    nextPort += 100;
                    if (nextPort == baseStart+300) {
                        nextPort = baseStart;
                        difIp++;
                    }
                    nodes.add(currentNode);
                    nodesSomePacketsWereSentTo.add(new ArrayList<>());
                    haveSessions.add(new ArrayList<>());
                }
            }
            byte[] data = new byte[frameData.length-42];
            System.arraycopy(frameData, 42, data, 0, frameData.length-42);
            int type = (data[0] & 0x000000FF) / 32;
            if (type == 2){
                haveSessions.get(nodes.indexOf(currentNode)).add(new Node(0, srcport, srcport, Utility.getBytes(srcip), Utility.getBytes(srcip)));
            }
            sendOut(frame, currentNode);
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                Node node1 = nodes.get(i);
                 if (node1.getPubilcPort() == dstport && dstip == Utility.convertBytesToInt(node1.getPublicIP())) {
                    if (srcip == Utility.convertBytesToInt(new byte[] {1,1,1,1})) {
                        forward(frame, i);
                        check = true;
                        break;
                    }
                    for (int j = 0; j < nodesSomePacketsWereSentTo.get(i).size(); j++) {
                        if (nodesSomePacketsWereSentTo.get(i).get(j).getPubilcPort()==srcport && Utility.convertBytesToInt(nodesSomePacketsWereSentTo.get(i).get(j).getPublicIP())==srcip){
                            check = true;
                            forward(frame, i);
                            break;
                        }
                    }
                    break;
                }
            }
            if (!check){
                System.out.println("outer packet dropped");
                sendDrop(new Node(0, srcport, srcport, Utility.getBytes(srcip), Utility.getBytes(srcip)), 4321);
            }
        }
	}

	public void run() {
		Scanner s = new Scanner(System.in);
		while (true) {
			String input = s.nextLine();
			String[] sp = input.split(" ");
			if (input.equals("exit")) break;
            if (sp.length==5 && sp[0].equals("block") && sp[1].equals("port") && sp[2].equals("range")){
                for (int i = Integer.parseInt(sp[3]); i <= Integer.parseInt(sp[4]); i++) {
                    blocketPorts.add(i);
                }
            } else if (input.equals("reset network settings")) {
                System.out.println("please enter the base start number for port.");
                baseStart = s.nextInt();
                for (int i = 0; i < nodes.size(); i++) {
                    if(haveSessions.get(i).size()>0) {
                        sendNATUpdated(nodes.get(i));
                    }
                }
                nodesSomePacketsWereSentTo = new ArrayList<>();
                nodes = new ArrayList<>();
                nextPort = baseStart;
                difIp = 1;
                blocketPorts = new HashSet<>();
            }
		}
		s.close();
	}

	private void forward(Frame frame, int ifaceIndex){
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);
        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        UDPHeader udp = new UDPHeader(frameData, 34);
        udp.setDestPort(nodes.get(ifaceIndex).getLocalPort());
        System.arraycopy(udp.getData(), 0, frameData, 34, 8);

        IPv4Header newIp = new IPv4Header();
        newIp.setTTL(ip.getTTL()-1);
        newIp.setSrc(ip.getSrc());
        newIp.setDest(Utility.convertBytesToInt(nodes.get(ifaceIndex).getLocalIP()));
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
        sendFrame(newFrame, nodes.get(ifaceIndex).getId());
    }

    private void sendOut (Frame frame, Node node) {
        byte[] frameData = new byte[frame.data.length];
        System.arraycopy(frame.data, 0, frameData, 0, frameData.length);

        IPv4Header ip = new IPv4Header(frameData, 14, 5);
        IPv4Header newIp = new IPv4Header();
        newIp.setTTL(ip.getTTL()-1);
        newIp.setDest(ip.getDest());
        newIp.setTotalLength(ip.getTotalLength());
        newIp.setProtocol(17);
        newIp.setSrc(Utility.convertBytesToInt(node.getPublicIP()));
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

        UDPHeader udp = new UDPHeader(frameData, 34);
        udp.setSrcPort(node.getPubilcPort());
        System.arraycopy(udp.getData(), 0, frameData, 34, 8);

        Frame newFrame = new Frame(frameData);
        sendFrame(newFrame, 0);
    }

    private void sendDrop (Node node, int port) {
	    int ifaceIndex = 0;
        byte[] newData = new byte[47];
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[0].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(node.getLocalIP()));
        newIp.setTTL(64);
        newIp.setProtocol(17);
        newIp.setTotalLength(33);
        if (port == 1234) {
            ifaceIndex = node.getId();
        }
        newIp.setSrc(iface[ifaceIndex].ip);
        neweth.setSrc(iface[ifaceIndex].mac);

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
        newudp.setDestPort(node.getLocalPort());
        newudp.setSrcPort(port);
        newudp.setLen(13);

        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = 0;
        newData[43] = 'd';
        newData[44] = 'r';
        newData[45] = 'o';
        newData[46] = 'p';

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, ifaceIndex);
    }

    private void sendNATUpdated (Node node) {
        int ifaceIndex = node.getId();
        byte[] newData = new byte[43];
        EthernetHeader neweth = new EthernetHeader();
        neweth.setSrc(iface[ifaceIndex].mac);

        IPv4Header newIp = new IPv4Header();
        newIp.setDest(Utility.convertBytesToInt(node.getLocalIP()));
        newIp.setTTL(64);
        newIp.setProtocol(17);
        newIp.setTotalLength(29);
        newIp.setSrc(iface[ifaceIndex].ip);

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
        newudp.setDestPort(node.getLocalPort());
        newudp.setSrcPort(node.getPubilcPort());
        newudp.setLen(9);

        System.arraycopy(neweth.getData(), 0, newData, 0, 14);
        System.arraycopy(newIp.getData(), 0, newData, 14, 20);
        System.arraycopy(newudp.getData(), 0, newData, 34, 8);
        newData[42] = (byte) 128;

        Frame frame1 = new Frame(newData);
        sendFrame(frame1, ifaceIndex);
    }
}
