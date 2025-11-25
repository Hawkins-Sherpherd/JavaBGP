package net.sherpherd.bgp.utils;
// import java.io.BufferedInputStream;
// import java.io.FileInputStream;
// import java.io.InputStream;

// import org.javamrt.mrt.*;

public class Main {   
    // public static void testMRTRead() throws Exception{
    //     InputStream input = new FileInputStream("E:\\Download\\Compressed\\bview.20251124.0000");
    //         BGPFileReader input_mrt =
    //             new BGPFileReader(new BufferedInputStream(input));
    //         while (true){
    //             MRTRecord mrtRecord = input_mrt.readNext();
    //             if (mrtRecord == null){
    //                 break;
    //             }
    //             System.out.println("Prefix: "+mrtRecord.getPrefix()+" AS_PATH: "+mrtRecord.getASPath());
    //             if(mrtRecord.toString().split("\\|")[0].equals("BGP4MP") == false){
    //                 System.out.println(mrtRecord.toString());
    //                 System.out.println(mrtRecord.getType());
    //             }
    //             Thread.currentThread();
    //             // Thread.sleep(1000);
    //         }
    //         input_mrt.close();
    // }

    public static void main(String[] args) throws Exception{
        // testMRTRead();
    }
}
