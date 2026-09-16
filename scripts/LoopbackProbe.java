import java.net.*;
import java.nio.channels.*;
public class LoopbackProbe {
  public static void main(String[] args) throws Exception {
    System.out.println("Java=" + System.getProperty("java.version"));
    System.out.println("temp=" + System.getProperty("java.io.tmpdir"));
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
         Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
      System.out.println("TCP_LOOPBACK=OK");
    }
    try { Pipe p = Pipe.open(); p.source().close(); p.sink().close(); System.out.println("NIO_PIPE=OK"); }
    catch(Exception e) { System.out.println("NIO_PIPE=FAILED"); e.printStackTrace(); }
    try (Selector s = Selector.open()) { System.out.println("NIO_SELECTOR=OK"); }
    catch(Exception e) { System.out.println("NIO_SELECTOR=FAILED"); e.printStackTrace(); }
  }
}
