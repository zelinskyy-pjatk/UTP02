import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static java.lang.System.out;

public
   class Server {
   private final String configurationFilePath = "..\\ServerClientApplication\\server_configuration.txt";
   private static String serverHostName;
   private static int serverPort;
   private static List<String> bannedPhrasesList = new ArrayList<>();

   private static Set<clientHandleThread> connectedClients = new HashSet<>();

   /*private final String registeredClientsFilePath = "..\\ServerApplication\\registeredClients.txt";
   private File registeredClientsFile;*/




   public Server() {
      // -- Reading Server Configuration File -- //
      try {
         File configurationFile = new File(configurationFilePath);
         Scanner fileReader = new Scanner(configurationFile);
         while(fileReader.hasNextLine()){
            String line = fileReader.nextLine();
            String[] lineSplitArray = line.split(": ");
            switch (lineSplitArray[0]) {
               case "Server Host Name" -> serverHostName = lineSplitArray[1];
               case "Server Port" -> serverPort = Integer.parseInt(lineSplitArray[1]);
               case "Server Banned Phrases List" ->
                       bannedPhrasesList = Arrays.asList(lineSplitArray[1].replace(",", " ").split("\\s+"));
            }
         }
         fileReader.close();
      } catch (FileNotFoundException e) {
         System.out.println("!Error occurred! *Server configuration file not found*");
         System.exit(-111); // -111 -> error code for file not found error
      }
   }

   public static void main(String[] args) {
      new Server();
      System.out.println("Attempting to create a server socket.");
      try (ServerSocket serverSocket = new ServerSocket(serverPort);){
         System.out.println("Server Socket created successfully.");
         while (true) {
            System.out.println("Waiting for new clients to connect.");
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected");
            InetAddress address = clientSocket.getInetAddress();
            int port = clientSocket.getPort();
            System.out.println("From address " + address.toString() + ":" + port);

            (new Thread(new clientHandleThread(clientSocket))).start();
         }
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }


   public static void broadcastMessage(String msg, clientHandleThread sender) {
      synchronized (connectedClients) {
         for (clientHandleThread client : connectedClients) {
            if(client != sender){
               client.sendMessage(msg);
            }
         }
      }
   }


   private static class clientHandleThread implements Runnable {
      private Socket clientSocket;
      private String clientName;


      public clientHandleThread(Socket clientSocket) {
         this.clientSocket = clientSocket;
         clientName = clientSocket.getInetAddress().toString () + ":" + clientSocket.getPort();
      }


      public void sendMessage(String msg){
         out.println(msg);
      }

      public boolean checkMsg(String msg){
         for (String banned : bannedPhrasesList)
            if(msg.contains(banned)) {
               out.println("Message sent contained a banned phrase or more and was not sent.");
               return false;
            }
         return true;
      }

      public void disconnect(){
         try {
            synchronized (connectedClients) {
               connectedClients.remove(this);
            }
            clientSocket.close();
         } catch (IOException e) {
            System.err.println("Error closing connection with " + clientName);
         }
      }

      @Override
      public void run() {
         try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            synchronized (connectedClients) {
               connectedClients.add(this);
               Server.broadcastMessage(clientName + " has joined the server.", this);
            }

            String clientMsg;
            while((clientMsg = in.readLine()) != null) {
               if(clientName.equals("/exit")) {
                  if(clientName != null){
                     synchronized (connectedClients) {
                        connectedClients.remove(this);
                        Server.broadcastMessage(clientName + " disconnected from the server", null);
                     }
                  }
               }
               if(checkMsg(clientMsg)) out.println(clientMsg);
            }

         } catch (IOException e){
            out.println("Client Disconnected: " + clientSocket.getInetAddress().toString() + ":" + clientSocket.getPort());
         } finally {

         }
      }
   }
}
