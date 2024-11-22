import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
   // -- Client Communication Streams -- //
   private static PrintWriter out = null;
   private static BufferedReader in = null;
   private static BufferedReader stdIn = null;

   // -- Server Hostname -- //
   private static String hostname = "localhost";
   // -- Client Name -- //
   private static String clientName;


   //TODO: -- MAIN METHOD -- //
   public static void main(String[] args) throws IOException {
      /// NAMES
      if(args.length == 0) throw new IOException("You should specify your client's name as argument (e.g. java Client NICKNAME)!");
      else if(args.length == 1) clientName = args[0];

      //TODO: CONNECTING
      System.out.println("Trying to connect to " + hostname);
      try (Socket socket = new Socket(hostname, 12452)) {
         System.out.println("Creating communication streams");
         out = new PrintWriter(socket.getOutputStream(), true);
         in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

         Thread listenerThread = new Thread(() -> {
            try{
               String serverMessage;
               while((serverMessage = in.readLine()) != null) {
                  System.out.println(serverMessage);
               }
            }catch (IOException e) {
               System.out.println("Disconnected from the server.");
            }
         });

         listenerThread.setDaemon(true);
         listenerThread.start();


         out.println(clientName); // WE ARE SENDING NAME AND PORT INFO IMMIDIATELY AFTER CONNECTING

         //TODO: USER INPUT
         String userInput;
         // -- Buffered Reader for Keyboard (read entered message) -- //
         stdIn = new BufferedReader(new InputStreamReader(System.in));

         while ((userInput = stdIn.readLine()) != null) {
            if(userInput.equalsIgnoreCase("/exit")) {
               System.out.println("Disconnecting from the server...");
               break;
            };
            // send to the server
            out.println(userInput);
            // read the response and print it out
            System.out.println("echo: " + in.readLine());
         }

         socket.close();
         System.out.println("Connection closed.");
      } catch (UnknownHostException e) {
         System.err.println("Error occurred! Unknown hostname: " + hostname + ".");
         System.exit(-606);   // -606 -> indicates some error while connecting to server (with hostname)
      } catch (IOException e) {
         System.err.println("Error occurred! Unable to connect to: " + hostname + ".");
         System.exit(-606);
      }

      // -- Close All Communication and Reading Streams -- //
      stdIn.close();
      out.close();
      in.close();
   }
}
