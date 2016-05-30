package com.chao.ftpclient;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.chao.ftpclient.FTPClient.TransferMode.port;

/**
 * Created by chao on 2016/5/30.
 */
public class FTPClient {
    BufferedReader localIn;

    BufferedWriter remoteOut;
    BufferedReader remoteIn;

    String command = "";
    String params = "";

    ServerSocket serverSocket; // 主动模式下的ServerSocket

    Socket mSocket; // master socket for command
    Socket sSocket; // slave socket for data

    TransferType transferType = TransferType.binary;
    TransferMode transferMode = TransferMode.pasv;

    String remoteAddress;
    int remotePort = 0;

    public FTPClient() {
        localIn = new BufferedReader(new InputStreamReader(System.in));
    }

    public void manageCommand() {
        String input; // 本地输入的命令
        try {
            while (true) {
                System.out.print("FtpClient>>");

                input = localIn.readLine();
                if (!input.contains(" ")) {
                    command = input;
                } else {
                    command = input.substring(0, input.indexOf(" "));
                    params = input.substring(input.indexOf(" ") + 1);
                }
                command = command.toUpperCase();

                switch (command) {
                    case "FTP":
                        handleFTP();
                        break;
                    case "PASV":
                        handlePASV();
                        break;
                    case "PORT":
                        handlePORT();
                        break;
                    case "LIST":
                        handleLIST();
                        break;
                    case "RETR":
                        handleRETR();
                        break;
                    case "STOR":
                        handleSTOR();
                        break;
                    default:
                        remoteOut.write(input + "\n");
                        remoteOut.flush();
                        System.out.println("server response: " + remoteIn.readLine());
                        break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handleFTP() {
        System.out.println(params);
        String[] address = params.split(" ");
        try {
            mSocket = new Socket(address[0], Integer.parseInt(address[1]));

            remoteIn = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            remoteOut = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));

            System.out.println("connected to " + address[0] + ":" + address[1]);
            System.out.println("server response: " + remoteIn.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        String tempAddress = mSocket.getRemoteSocketAddress().toString();
        remoteAddress = tempAddress.substring(1, tempAddress.lastIndexOf(":"));
    }

    void handlePASV() {
        try {
            remoteOut.write("PASV\n");
            remoteOut.flush();

            String tempString = remoteIn.readLine();
            System.out.println("server response: " + tempString);
            Pattern pattern = Pattern
                    .compile(".+\\(\\d+,\\d+,\\d+,\\d+,(\\d+),(\\d+)\\)");
            Matcher matcher = pattern.matcher(tempString);
            int port1 = 0;
            int port2 = 0;
            if (matcher.find()) {
                port1 = Integer.parseInt(matcher.group(1));
                port2 = Integer.parseInt(matcher.group(2));
            }
            remotePort = (port1 << 8) + port2;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handlePORT() {
        String ipAddress = "";
        byte[] address = mSocket.getLocalAddress().getAddress();
        for (int i = 0; i < 4; i++) {
            ipAddress += ((address[i] & 0xff) + ",");
        }
        try {
            serverSocket = new ServerSocket(0);
            int port1 = serverSocket.getLocalPort() >> 8;
            int port2 = serverSocket.getLocalPort() & 0xff;
            remoteOut.write("PORT " + ipAddress + port1 + "," + port2 + "\n");
            remoteOut.flush();
            System.out.println("PORT " + ipAddress + port1 + "," + port2);
            System.out.println("server response: " + remoteIn.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        transferMode = port; // 切到主动模式
    }

    void handleRETR() {
        // 如果没有指定主动或者被动模式
        if (remotePort == 0) {
            handlePASV();
        }
        try {
            remoteOut.write("RETR " + params + "\n");
            remoteOut.flush();

            if (transferMode == TransferMode.pasv) {
                // 被动模式
                sSocket = new Socket(remoteAddress, remotePort);
            } else {
                // 主动模式
                sSocket = serverSocket.accept();
            }

            if (transferType == TransferType.ascii) {
                // 字符流
                Reader in = new InputStreamReader(sSocket.getInputStream());
                Writer out = new FileWriter(params);

                int readChar;
                while ((readChar = in.read()) != -1) {
                    out.write(readChar);
                }
                out.flush();
                in.close();
                out.close();
            } else {
                // 字节流
                BufferedInputStream in = new BufferedInputStream(sSocket.getInputStream());
                OutputStream out = new FileOutputStream(params);
                int readByte;
                while ((readByte = in.read()) != -1) {
                    out.write(readByte);
                }
                out.flush();
                in.close();
                out.close();
            }
            System.out.println("server response: " + remoteIn.readLine());
            sSocket.close();
            System.out.println("server response: " + remoteIn.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handleSTOR() {
        // 如果没有指定主动或者被动模式
        if (remotePort == 0) {
            handlePASV();
        }
        try {
            remoteOut.write("STOR " + params + "\n");
            remoteOut.flush();

            if (transferMode == TransferMode.pasv) {
                // 被动模式
                sSocket = new Socket(remoteAddress, remotePort);
            } else {
                // 主动模式
                sSocket = serverSocket.accept();
            }

            if (transferType == TransferType.ascii) {
                // 字符流
                Reader in = new FileReader(params);
                Writer out = new OutputStreamWriter(sSocket.getOutputStream());

                int readChar;
                while ((readChar = in.read()) != -1) {
                    out.write(readChar);
                }
                in.close();
                out.close();
            } else {
                // 字节流
                InputStream in = new FileInputStream(params);
                OutputStream out = sSocket.getOutputStream();
                int readByte;
                while ((readByte = in.read()) != -1) {
                    out.write(readByte);
                }
                in.close();
                out.close();
            }
            System.out.println("server response: " + remoteIn.readLine());
            sSocket.close();
            System.out.println("server response: " + remoteIn.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handleLIST() {
        // 如果没有指定主动或者被动模式
        if (remotePort == 0) {
            handlePASV();
        }
        try {
            remoteOut.write("LIST\n");
            remoteOut.flush();

            if (transferMode == TransferMode.pasv) {
                // 被动模式
                sSocket = new Socket(remoteAddress, remotePort);
            } else {
                // 主动模式
                sSocket = serverSocket.accept();
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(sSocket.getInputStream()));
            String temp;
            while ((temp = in.readLine()) != null) {
                System.out.println(temp);
                if (temp.startsWith("226")) {
                    break;
                }
            }
            in.close();
            System.out.println("server response: " + remoteIn.readLine());
            sSocket.close();
            System.out.println("server response: " + remoteIn.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    enum TransferType {
        ascii,
        binary
    }

    enum TransferMode {
        pasv,
        port
    }

}