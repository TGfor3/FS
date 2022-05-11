import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;


public class FileSystemReader{

    static String workingDir = "/"; 
    static byte[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32;

    static BufferedInputStream fatBis;
    static BufferedInputStream dataBis;
    static long offset;
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        
        fatBis = new BufferedInputStream(new FileInputStream(new File(args[0])));
        dataBis = new BufferedInputStream(new FileInputStream(new File(args[0])));

        setGlobals();
        
        Scanner cmdScanner = new Scanner(System.in);
        String task;
       
        while(true){
            System.out.print(workingDir + "] ");
            task = cmdScanner.next();
            task.toLowerCase();
            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    ls(task);
                    break;
                case "stat":
                    stat(cmdScanner.next());
                    break;
                case "size":
                    size(cmdScanner.next());
                    break;
                case "cd":
                    cd(cmdScanner.next());
                    break;
                case "read":
                    read(cmdScanner.next(), cmdScanner.next(), cmdScanner.next());
                    break;
            }
        }



        
    }

    private static void setGlobals() throws IOException{

        byte[] scrap;
        fatBis.mark(-1);

        scrap = new byte[2];
        fatBis.skip(11);
        fatBis.read(scrap, 0, 2);
        fatBis.reset(); 
        bytePerSec = byteToInt(scrap, 1);

        scrap = new byte[1];
        fatBis.skip(13);
        fatBis.read(scrap, 0, 1);
        fatBis.reset(); 
        secPerClus = byteToInt(scrap, 0);

        scrap = new byte[2];
        fatBis.skip(0x0E);
        fatBis.read(scrap, 0, 1);
        fatBis.reset(); 
        rsvdSecCnt = byteToInt(scrap, 0);

        scrap = new byte[1];
        fatBis.skip(0x10);
        fatBis.read(scrap, 0, 1);
        fatBis.reset(); 
        numFats = byteToInt(scrap, 0);

        scrap = new byte[4];
        fatBis.skip(36);
        fatBis.read(scrap, 0, 4);
        fatBis.reset(); 
        Fatsz32 = byteToInt(scrap, 3);
        
        FAT = new byte[Fatsz32 * bytePerSec];  
        fatBis.skip(rsvdSecCnt);
        fatBis.read(FAT, 0, (Fatsz32 * bytePerSec));
        //fatBis.reset(); 
        System.out.println(FAT);

        //Should get to beginning of data section
        long dataStart = rsvdSecCnt + (bytePerSec * secPerClus * Fatsz32 * numFats);
        dataBis.skip(dataStart);
        dataBis.mark(-1);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */

    

    private static int byteToInt (byte[] array, int end){

        int value = 0;
        for (int i = end; i >= 0; i--){
            value = (value << 8) | (array[i] & 0xFF);
        }

        return value;
    }


    public static void info(){

        System.out.println("BPB_BytesPerSec is 0x" + String.format("%X", bytePerSec) + ", " + bytePerSec);
        System.out.println("BPB_SecPerClus is 0x" + String.format("%X", secPerClus)+ ", " + secPerClus);
        System.out.println("BPB_RsvdSecCnt is 0x" + String.format("%X", rsvdSecCnt) + ", " + rsvdSecCnt);
        System.out.println("BPB_NumFATS is 0x" + String.format("%X", numFats) + ", " + numFats);
        System.out.println("BPB_FATSz32 is 0x" + String.format("%X", Fatsz32) + ", " + Fatsz32);
        //System.out.println("FAT:\n" + Arrays.toString(FAT));
    }
    public static void ls(String dirName){

    }
    public static void stat(String fileNameDirName) throws IOException{
        int size;
        String attributes;
        int firstCluster;
        if(fileNameDirName.equals(".") || fileNameDirName.equals("..") || fileNameDirName.equals("/")){
            size = 0;
            dataBis.skip(11);
            attributes = attributes(dataBis.read());
            firstCluster = 0x2;            
        }else{
            dataBis.skip(32);
            String[] path = fileNameDirName.split("/");
            char c = (char)dataBis.read();
            for(int i = 0; i < path.length; i++){
                if(c =='A'){
                    dataBis.skip(31);
                }
                String name = "";
                for(int x = 0; x < 11; x++){
                    name += (char)dataBis.read();
                }
                if(!name.equals(path[i])){
                    System.out.println("Error: file/directory does not exist");
                    return;
                }
                attributes = attributes(dataBis.read());
        }

        }
        


    }
    private static String attributes(int attr){
        String attributes = "";
        if((attr & 0x20) == 0x20){
            attributes += " ATTR_ARCHIVE"; 
        }
        if((attr & 0x10) == 0x10){
            attributes += " DIRECTORY";
        }
        if((attr & 0x8) == 0x8){
            attributes += " VOLUME_ID";
        }
        if((attr & 0x4) == 0x4){
            attributes += " SYSTEM";
        }
        if((attr & 0x2) == 0x2){
            attributes += " HIDDEN";
        }
        if((attr & 0x1) == 0x1){
            attributes += " READ_ONLY";
        }
        return attributes;        
    }
    public static void size(String fileName){

    }
    public static void cd(String dirName){

    }
    public static void read(String fileName, String offset, String numBytes){

    }


    /**
     * Move through stream to desired point in data section
     * @param target intended endgoal
     * @return true is target location was reached, false otherwise
     */
    private static boolean setDataEntry(String path){

        return false;

    }
}