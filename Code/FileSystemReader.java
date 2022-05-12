import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;


public class FileSystemReader{

    static String workingDir = "/";
    static String imgFile;
    static long dataStart;
    static byte[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32;
    static int directoryLevel = 0;

    static BufferedInputStream fatBis;
    static BufferedInputStream bis;
    static long offset = 0;
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        imgFile = args[0];
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));

        setGlobals();
        //Should get to beginning of data section
        dataStart = rsvdSecCnt + (bytePerSec * secPerClus * Fatsz32 * numFats);
        bis.skip(dataStart);
        bis.mark(-1);
        
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
    
    //Returns the requested bytes as they are ordered in the stream
    //Does not change the pointer in the stream
    //byte array is the requested amount size
    private static byte[] getBytes(int offset, int amount) throws IOException{

        byte[] scrap = new byte[amount];
        bis.mark(offset + amount + 1);
        bis.skip(offset);
        bis.read(scrap, 0, amount);
        bis.reset();
        return scrap;
    }

    /**
    Does not change the pionter
     */
    private static void setGlobals() throws IOException{

        bytePerSec = byteToInt(getBytes(11, 2));
        secPerClus = byteToInt(getBytes(13, 1));
        rsvdSecCnt = byteToInt(getBytes(0x0E, 1));
        numFats = byteToInt(getBytes(0x10, 1));
        Fatsz32 = byteToInt(getBytes(36, 4));
        
        FAT = getBytes(rsvdSecCnt, (Fatsz32 * bytePerSec));
        System.out.println(FAT);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */
    private static int byteToInt (byte[] array){

        // int value = 0;
        // for (int i = end; i >= 0; i--){
        //     value = (value << 8) | (array[i] & 0xFF);
        // }

        //ALternatively
        String s = "";
        for(int i = array.length-1; i >= 0; i--){
            s += String.format("%02X", array[i]);
            //System.out.print(array[i]);
        }
        return Integer.parseInt(s,16);
        //System.out.println(Integer.parseInt(s, 16));
        //return value;
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
        
        if (!setDataEntryPointer(fileNameDirName)){
            System.out.println("Error: file/directory does not exist");
            return;
        }
        
        int size = byteToInt(getBytes(28, 4));
        String attributes = attrToString(byteToInt(getBytes(11, 1)));
        
        byte[] firstHalf = getBytes(20, 2);
        byte[] secondHalf = getBytes(26, 2);
        byte[] full = new byte[4];
        for (int i = 0; i < 2; i++){
            full[i] = firstHalf[i];
        }
        for (int i = 0; i < 2; i++){
            full[i+2] = 
        }
               
        


    }
    private static String attrToString(int attr){
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
    private static boolean setDataEntryPointer(String path){
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(dataStart);
        boolean absolute = path.charAt(0) == '/';
        String[] levels = path.split("/");
        if(!absolute){
            //TODO Deal with offset
            bis.skip(offset);
        }
        //Don't want to navigate last step of path, that is the job of the caller
        for(int i = 0; i < levels.length - 1; i++){
            String target = levels[i];
            if(target.equals('.')){
                continue;
            }
            if(target.equals("..")){
                //!Gotta Figure this out
                //? Directory level and pwd path string?
            }else{
                bis.mark(13);
                int next = bis.read();
                if(next == 65){
                    bis.skip(11);
                    int attr = bis.read();
                    if((attr & 0xF) == 0xF){
                        bis.skip(20);
                    }else{
                        bis.reset();
                    }
                }
                //Should be at the beginning of the short name entry
                String name = "";
                for(int i = 0; i < 11; i++){
                    next = bis.read();
                    if(next != 0){
                        name += (char)next;
                    }
                }
                if(target.equals(name)){
                    //TODO Get first cluster
                }else{
                    return false;

                

            }
            
            
        }



        if(path.equals(".") || path.equals("..") || path.equals("/")){
            size = 0;
            bis.skip(11);
            attributes = attributes(bis.read());
            firstCluster = 0x2;            
        }else{
            bis.skip(32);
            String[] path = fileNameDirName.split("/");
            char c = (char)bis.read();
            for(int i = 0; i < path.length; i++){
                if(c =='A'){
                    bis.skip(31);
                }
                String name = "";
                for(int x = 0; x < 11; x++){
                    name += (char)bis.read();
                }
                if(!name.equals(path[i])){
                    System.out.println("Error: file/directory does not exist");
                    return;
                }
                attributes = attributes(bis.read());
        }

        }

        return false;

    }
}