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
    static int[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32;
    static int rootCluster;

    static int directoryLevel = 0;

    static BufferedInputStream fatBis;
    static BufferedInputStream bis;
    
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

            boolean hasNext = cmdScanner.hasNext();
            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    if (hasNext){
                        ls(cmdScanner.next());
                    } else {
                        ls(".");
                    }
                    break;
                case "stat":
                    if(!hasNext){
                        System.out.println("Error: Please provide an argument");  
                        continue; 
                    }
                    stat(cmdScanner.next());
                    break;
                case "size":
                    if(!hasNext){
                     System.out.println("Error: Please provide an argument");  
                     continue; 
                    }
                    size(cmdScanner.next());
                    break;
                case "cd":
                    if(!hasNext){
                        System.out.println("Error: Please provide an argument");  
                        continue; 
                    }
                    cd(cmdScanner.next());
                    break;
                case "read":
                    String[] inputs = new String[3];
                    boolean cont = false;
                    for(int i = 0; i < 3; i++){
                        if(cmdScanner.hasNext()){
                            inputs[i] = cmdScanner.next();
                        }else{
                            System.out.println("Error: Please provide the proper number of arguments");
                            cont = true;
                            break;
                        }
                    }
                    if(cont){
                        continue;
                    }
                    read(inputs[0], inputs[1], inputs[2]);
                    break;
                default:
                    System.out.println("Error: Improper command issued");
                    continue;
            }
        }
    }
    
    //Returns the requested bytes as they are ordered in the stream
    //Does not change the pointer in the stream
    //byte array is the requested amount size
    private static byte[] getBytes(int offset, int amount, boolean fat) throws IOException{

        byte[] scrap = new byte[amount];
        bis.mark(offset + amount + 1);
        bis.skip(offset);
        if(fat){
            int byteCounter = 0;
            int index = 0;
            byte[] fatEntry = new byte[4];
            while(byteCounter < amount){
                bis.read(fatEntry, 0, 4);
                FAT[index++] = byteToInt(fatEntry);
                byteCounter += 4;
            }
        }else{
            bis.read(scrap, 0, amount);
        }
        bis.reset();
        return scrap;
    }

    /**
    Does not change the pointer
     */
    private static void setGlobals() throws IOException{

        bytePerSec = byteToInt(getBytes(11, 2, false));
        secPerClus = byteToInt(getBytes(13, 1, false));
        rsvdSecCnt = byteToInt(getBytes(0x0E, 1, false));
        numFats = byteToInt(getBytes(0x10, 1, false));
        Fatsz32 = byteToInt(getBytes(36, 4, false));
        rootCluster = byteToInt(getBytes(44, 4, false));
        
        getBytes(rsvdSecCnt, (Fatsz32 * bytePerSec), true);
        System.out.println(FAT);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */
    private static int byteToInt (byte[] array){
        String s = "";
        for(int i = array.length-1; i >= 0; i--){
            s += String.format("%02X", array[i]);
        }
        return Integer.parseInt(s,16);
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

        int FATCluster = setDataEntryPointer(dirName, true);

        if (FATCluster == -1){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }

        parseDirectory("", FATCluster, true);
    }

    public static void stat(String fileNameDirName) throws IOException{
        
        if (setDataEntryPointer(fileNameDirName, false) == -1){
            System.out.println("Error: file/directory does not exist");
            return;
        }
        
        int size = byteToInt(getBytes(28, 4, false));
        String attributes = attrToString(byteToInt(getBytes(11, 1, false)));
        
        byte[] firstHalf = getBytes(20, 2, false);
        byte[] secondHalf = getBytes(26, 2, false);
        
        byte[] full = new byte[4];
        for (int i = 0; i < 2; i++){
            full[i] = firstHalf[i];
        }
        for (int i = 0; i < 2; i++){
            full[i+2] = secondHalf[i];
        }
        int nextCluser = byteToInt(full);

        System.out.println("Size is " + size);
        System.out.println("Attributes " + attributes);
        System.out.println("Next cluster number is " + String.format("%X", nextCluser));
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

        if (setDataEntryPointer(fileName, false) == -1){
            System.out.println("Error: " + fileName + " is not a file");
            return;
        }
        int size = byteToInt(getBytes(28, 4, false));
        System.out.println("Size of " + fileName + " is " + size + " bytes");
        return;
    }
    public static void cd(String dirName){

        

    }
    public static void read(String fileName, String offset, String numBytes){

        

    }


        
    /**
     * Move through stream to desired point in data section
     * @param target intended endgoal
     * @return current cluster of pointer, -1 if the target path doesnt exist
     */
    private static int setDataEntryPointer(String path, boolean ls){
        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(dataStart);
        boolean absolute = path.charAt(0) == '/';
        if(!absolute){
            //? What if pwd doesnt end with a /?, Not going to split properly
            path = (workingDir + (workingDir.charAt(workingDir.length() - 1) != '/' ? '/' : "") + path);
        }
        //Preproccess path string, handling periods
        //TODO When workingdir is in the root
        String[] levels = path.split("/");
        String processedPath = "/";
        for(int i = 0; i < levels.length; i++){
            String target = levels[i];
            if(target.equals('.')){
                continue;
            }
            if(target.equals("..")){
                //Meaning not in the root
                if(workingDir.length() > 1){
                    String[] backup = processedPath.split("/");
                    String newPath = "";
                    for(int x = 0; x < backup.length - 1; x++){
                        newPath += ("/" + backup[x]);
                    }
                    processedPath = newPath;
                }
            }else{
                processedPath += target;
            }
            //? Does it matter if has / at the end?
            processedPath += '/';
        }
        levels = processedPath.split("/");
        int startCluster = 2;
        for(int i = 0; i < levels.length; i++){
            startCluster = parseDirectory(levels[i], startCluster, ls);
            if(startCluster == -1){
                return startCluster;
            }
        }
        return startCluster;
    }
    /**
    file stream pointer needs to be set to the relavent directory
    prints everything out
    */
    private static int parseDirectory(String target, int startCluster, boolean ls){
        
        String accumulatedName = "";
        
        while(startCluster < 0x0ffffff7){
                int byteCounter = 0;
                while(byteCounter < (bytePerSec * secPerClus)){
                    bis.mark(13);
                    int next = bis.read();
                    if(next == 65){
                        bis.skip(11);
                        int attr = bis.read();
                        if((attr & 0xF) == 0xF){
                            bis.skip(20);
                            byteCounter += 32;
                        }else{
                            bis.reset();
                        }
                    }
                    String name = getDataEntryName();
                    if (ls){
                        if (name.length() == 0){
                            System.out.println(accumulatedName + name);
                        } else {
                            accumulatedName += name + " ";
                        }
                    }else{
                        if(target.equals(name)){
                            return startCluster;
                        }else{
                            bis.skip(32);
                            byteCounter += 32;
                        }
                    }  
                }
                int nextCluster = FAT[startCluster];
                bis.skip((Math.abs(nextCluster - startCluster) * bytePerSec * secPerClus));
        }
        if(startCluster == 0x0ffffff7){
            throw new IllegalStateException("Bad Cluster discovered");
        }else if(startCluster > 0x0ffffff7 && startCluster <= 0x0fffffff){
            return -1;
        }else{
            throw new IllegalArgumentException("We got big problems: Cluster value too large");
        }
    }
    //}

    /**
    Stream pointer must be set to the data entry
    */
    private static String getDataEntryName(){

        byte[] nameBytes = getBytes(0, 11, false);
        String name = "";

        for (int i = 0; i < nameBytes.length; i++){
            //?WHy not pull this out of loop?
            if (nameBytes[0] == 0 || nameBytes[0] == 0xE5){
                return name;
            }
            //?Why not namebytes[i]?
            if (nameBytes[1] != 0){
                name += (char)nameBytes[i];
            }
        }

        return name;
    }
}