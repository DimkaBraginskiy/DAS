import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DAS {
    private int port;
    private int number;
    private volatile boolean isRunning;
    private Object lock = new Object();
    private ExecutorService executor = Executors.newFixedThreadPool(2);

    private List<Integer> receivedNumbers;
    public DAS(int port, int number){
        this.port = port;
        this.number = number;
        isRunning = true;
        this.receivedNumbers = new ArrayList<>();
    }

    private List<InetAddress> getLocalAddress(){
        List<InetAddress> broadcastAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()){
                NetworkInterface networkInterface = interfaces.nextElement();

                if(networkInterface.isLoopback() || !networkInterface.isUp()){ //slipping loopback and not active addresses
                    continue;
                }

                for(InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()){
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if(broadcast != null){
                        broadcastAddresses.add(broadcast);
                    }
                }

            }

        } catch (SocketException e) {
            System.out.println("Failed to get network addresses: " + e.getMessage());
        }
        return broadcastAddresses;
    }

    private void sendToBroadcast(String message){
        DatagramSocket broadcastSocket = null;
        try {
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);
            List<InetAddress> broadcastAddresses = getLocalAddress();
            for(InetAddress broadcast : broadcastAddresses){
                    byte[] buffer = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcast, port);
                    broadcastSocket.send(packet);
                    System.out.println("Broadcast message sent to: " +broadcast.getHostAddress());
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.out.println("Error during broadcast: " + e.getMessage());
        }
    }

    private InetAddress getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // Пропускаем loopback и неактивные интерфейсы
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                // Получаем перечисление всех IP-адресов интерфейса
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    // Проверяем, что это IPv4-адрес
                    if (inetAddress instanceof Inet4Address) {
                        return inetAddress; // Возвращаем первый найденный IP-адрес
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Error getting local IP address: " + e.getMessage());
        }
        return null; // Если не удалось получить IP
    }



    private void start(){
        //DatagramSocket socket = null;
        try {
            DatagramSocket socket = new DatagramSocket(port);
            socket.setBroadcast(true);
            System.out.println("Starting master on port: " + port);

            InetAddress masterAddress = getLocalIpAddress();
            System.out.println("Master IP: " + masterAddress.getHostAddress());

            synchronized (lock){
                receivedNumbers.add(number);
            }
            byte[] recBuff = new byte[1024];
            DatagramPacket recPacket = new DatagramPacket(recBuff, recBuff.length);

            executor.execute(()->{
                while(isRunning){
                    try {
                        socket.receive(recPacket);
                        InetAddress senderIp = recPacket.getAddress();


                        System.out.println("Received packet from sender: " +senderIp.getHostAddress());

                        if(masterAddress != null && senderIp.equals(masterAddress)){
                            System.out.println("Ignoring message from self");
                            continue;
                        }



                        String receivedNum = new String(recPacket.getData(), 0, recPacket.getLength()).trim();
                        System.out.println("Received: " + receivedNum);

                        if(receivedNum.contains(".")){
                            recPacket.setLength(recPacket.getLength());
                            continue;
                        }


                        int num = Integer.parseInt(receivedNum);

                        if(num == -1){
                            sendToBroadcast("-1");
                            isRunning = false;
                            socket.close();
                            executor.shutdown();
                            break;
                        }else if(num == 0){

                            int sum = 0;
                            synchronized (lock){
                                for(int n : receivedNumbers){
                                    sum+=n;
                                }
                                int avg = sum / receivedNumbers.size();
                                System.out.println("Average: " +avg);
                                sendToBroadcast(String.valueOf(avg));
                            }
                        }else{
                            synchronized (lock){
                                receivedNumbers.add(num);
                            }
                        }
                    } catch (IOException e) {
                        //throw new RuntimeException(e);
                        if(isRunning){
                            System.out.println("Error in communication " + e.getMessage());
                        }
                    }


                }
            });
        } catch (BindException e) {
            System.out.println("Master is already running - starting Slave");
            runSlave(); // if port is already occupied we have to run the slave
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    private void runSlave(){
        try {
            DatagramSocket socket = new DatagramSocket(); // random port for slave?
            //InetAddress address = InetAddress.getLocalHost();
            InetAddress address = InetAddress.getByName("localhost");

            //Sending number to Master
            byte[] buffer = String.valueOf(number).getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
            System.out.println("Slave sent: " + number);

        } catch (SocketException e) {
            System.out.println("Error in Slave mode: " + e.getMessage());
        } catch (UnknownHostException e) {
            System.out.println("Error in Slave mode: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Error sending data in Slave mode: " + e.getMessage());
        }

    }

    public static void main(String[] args) {
        if(args.length != 2){
            System.out.println("Usage: java DAS <port> <number>");
            return;
        }
        try{
            int port = Integer.parseInt(args[0]);
            int num = Integer.parseInt(args[1]);
            DAS server = new DAS(port, num);
            server.start();

        } catch(NumberFormatException e){
            System.out.println("Invalid input. The value you entered is not of Int type.");
        }
    }
}

