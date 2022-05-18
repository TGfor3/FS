//package Code;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

import javax.print.StreamPrintService;


public class FileSystemReader{

    static String workingDir = "/";
    static String imgFile;
    static long dataStart;
    static int[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32; //sectors per fat
    static int rootCluster;

    static int directoryLevel = 0;

    static BufferedInputStream fatBis;
    static BufferedInputStream bis;
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        imgFile = args[0];
        //imgFile = "Code/fat32.img";
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));

        setGlobals();
        //Should get to beginning of data section
        dataStart = (rsvdSecCnt * bytePerSec) + (bytePerSec * secPerClus * Fatsz32 * numFats);
        //System.out.println("dataStart: " + dataStart);
        bis.skip(dataStart);
        bis.mark(-1);
        
        Scanner cmdScanner = new Scanner(System.in);
        String input;
        String inputArray[];
        String task;
       
        while(true){
            System.out.print(workingDir + "] ");
            input = cmdScanner.nextLine();
            input = input.toLowerCase();
            inputArray = input.split(" ");
            task = inputArray[0];

            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    if (inputArray.length > 1){
                        ls(inputArray[1]);
                    } else {
                        ls(".");
                    }
                    break;
                case "stat":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                    } else {
                        stat(inputArray[1]);
                    }
                    break;
                case "size":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        size(inputArray[1]);
                    }
                    break;
                case "cd":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        cd(inputArray[1]);
                    }
                    break;    
                case "read":
                    if(inputArray.length != 4){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        read(inputArray[1], inputArray[2], inputArray[3]);
                    }
                    break; 
                default:
                    System.out.println("Error: Improper command issued");
                    continue;
            }
        }
    }
    
    /**
     * pointer is changed to the last byte of the 4th byte in address
     * @param address
     * @return
     * @throws IOException
     */
    private static int getIntFromFat(int address) throws IOException{

        fatBis.close();
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        fatBis.skip(rsvdSecCnt * bytePerSec);

        byte[] value = new byte[4];
        fatBis.skip(address * 4);
        fatBis.read(value, 0, 4);
        return byteToInt(value);
    }

    //Returns the requested bytes as they are ordered in the stream
    //Does not change the pointer in the stream
    //byte array is the requested amount size
    //fat: true when initializing fat, false otherwise
    private static byte[] getBytes(int offset, int amount, boolean fat) throws IOException{

        byte[] scrap = new byte[amount];
    
        if(fat){
            fatBis.read(scrap, 0, amount);
        }else{
            bis.mark(offset + amount + 1);
            bis.skip(offset);
            bis.read(scrap, 0, amount);
            bis.reset();
        }
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
        Fatsz32 = byteToInt(getBytes(0x24, 4, false)); //sectors per fat
        rootCluster = byteToInt(getBytes(44, 4, false));

        fatBis.skip(rsvdSecCnt * bytePerSec);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */
    private static int byteToInt (byte[] array){

        int value = 0;
        for (int i = array.length - 1; i >= 0; i--){
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
    }
    
    public static void ls(String dirName) throws IOException{

        int FATCluster = setDataEntryPointer(dirName, true, false);

        if (FATCluster == -1){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }

        parseDirectory("", FATCluster, true);
    }

    private static void stat(String fileNameDirName) throws IOException{
        
        if (setDataEntryPointer(fileNameDirName, false, false) == -1){
            System.out.println("Error: file/directory does not exist");
            return;
        }
        
        
        int size = byteToInt(getBytes(28, 4, false));
        String attributes = attrToString(byteToInt(getBytes(11, 1, false)));
        int startCluster = getStartCluster();
        

        System.out.println("Size is " + size);
        System.out.println("Attributes " + attributes);
        System.out.println("Next cluster number is " + String.format("%X", startCluster));
    }

    /**
        summary: returns the int value of the combined high bits and low bits of the target entry

        pointer before running: set to the target entry pointer
        pointer after running: must remain at the target entry pointer
        
        returns: the int value of the start cluster
     */
    private static int getStartCluster () throws IOException{
        
        // System.out.println("\ngetStartCluster");
        // System.out.println("name: " + getDataEntryName());
        
        byte[] firstHalf = getBytes(20, 2, false);
        byte[] secondHalf = getBytes(26, 2, false);
        
        byte[] full = new byte[4];

        full[0] = firstHalf[1];
        full[1] = firstHalf[0];
        full[2] = secondHalf[1];
        full[3] = secondHalf[0];

        int value = 0;
        for (int i = 0; i < full.length; i++){
            value = (value << 8) | (full[i] & 0xFF);
        }
        return value;
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
    
    public static void size (String fileName) throws IOException {

        if (setDataEntryPointer(fileName, false, false) == -1){
            System.out.println("Error: " + fileName + " is not a file");
            return;
        }
        int size = byteToInt(getBytes(28, 4, false));
        System.out.println("Size of " + fileName + " is " + size + " bytes");
        return;
    }
    
    public static void cd (String dirName) throws IOException{
    
        
        if(setDataEntryPointer(dirName, false, true) == -1){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }
        String newWorking = processPath(dirName);
        workingDir = newWorking;
    }
    
    public static void read(String fileName, String offset, String numBytes){}


    /**
     * Returns an absolute path without periods
     * @param path
     * @return
     */
    private static String processPath (String path){
        
        //path = workingDir + / + path
        boolean absolute = path.charAt(0) == '/';
        if(!absolute){
            //? What if pwd doesnt end with a /?, Not going to split properly
            path = (workingDir + (workingDir.charAt(workingDir.length() - 1) != '/' ? '/' : "") + path);
        }

        //System.out.println("absolute path(.): " + path);

        //Preproccess path string, handling periods
        //TODO When workingdir is in the root
        String[] levels = path.split("/");
        //System.out.println("levels in absolutePath(.): " + levels.length);

        String processedPath = "/";

        for(int i = 1; i < levels.length; i++){ //starts at one because all paths start with '/', and therefore all levels[] have "" at the beginning
            String target = levels[i];
            //System.out.println("target at levels[i]: " + levels[i]);
            if(target.equals(".")){
                continue;
            }
            if(target.equals("..")){
                //Meaning not in the root
                if(workingDir.length() > 1){
                    String[] backup = processedPath.split("/");
                    String newPath = "/";
                    for(int x = 0; x < backup.length - 1; x++){
                        newPath += (backup[x] + '/');
                    }
                    processedPath = newPath;
                }
            }else{
                processedPath += (target + '/');
            }
        }
        //System.out.println("absolutePath(!.): " + processedPath);
        return processedPath;
    }

        
    /**
     * Move through stream to desired point in data section
     * @param target intended endgoal
     * @return Cluster number where target directory begins
     */
    private static int setDataEntryPointer (String path, boolean ls, boolean cd) throws IOException{
        
        // System.out.println("\nsetDataEntryPointer");
        // System.out.println("given path: " + path);
        
        setPointerCluster(rootCluster);
        
        String processedPath = processPath(path);
        String [] levels = processedPath.split("/");

        int nextFATIndex = rootCluster;
        //System.out.println("nextFatIndex: " + nextFATIndex);

        for(int i = 1; i < levels.length; i++){ //starts at one because all paths start with '/', and therefore all levels[] have "" at the beginning

            //System.out.println("calling parseDirectory on levels[i]: " + levels[i]);
            int currentFATIndex = parseDirectory(levels[i], nextFATIndex, ls);
            // System.out.println("\nsetDataEntryPointer");
            // System.out.println("Returned current cluster: " + currentFATIndex);


            if(currentFATIndex == -1){
                return currentFATIndex;
            }
            int startCluster = getStartCluster();    
            setPointerCluster(startCluster);
            nextFATIndex = startCluster;

            // System.out.println("\nsetDataEntryPointer");
            // System.out.println("Start cluster of next directory is: " + startCluster);
        }
        //Only reaches here if only "/" was passed in 
        return nextFATIndex;
    }
       
    /**
    summary: sets pointer to the entry of a target file or directory

    pointer before running: set to directory entry of directory to search
    pointer after running: set to directory or file entry of target
    
    target: file or directory to look for
    nextFATIndex: index of the FAT which points to the next cluster in the data to continue the search
    returns: cluster number in data section in which target was found; most recent FAT value
    */
    private static int parseDirectory(String target, int nextFATIndex, boolean ls) throws IOException{

        target = target.toUpperCase();
        
        // because method requires pointer be set to the entry of the directory to search, semantics are ok for getStartCluster
        //Bc we are modeling ls -a, which starts with those two directories by default
        
        // System.out.println("\nparseDirectory");
        // System.out.println("nextFATIndex: " + nextFATIndex);
        // System.out.println("Lookig for: " + target);
        
        String accumulatedName = ". .. ";
        while(nextFATIndex != 0x0ffffff7){
            int byteCounter = 0;
                while(byteCounter < (bytePerSec * secPerClus)){

                    // System.out.println("byteCounter: " + byteCounter);
                    // System.out.println("cluster size: " + bytePerSec * secPerClus);
                    bis.mark(13);

                    int firstByte = byteToInt(getBytes(0, 1, false));
                    if(firstByte == 65){ //checks if entry starts with A
                        
                        byte[] attributeByte = getBytes(11, 1, false);
                        if ((attributeByte[0] & 0xF) == 0xF){
                            bis.skip(32);
                            byteCounter+= 32;
                            continue;
                        }
                    }
                    String name = getDataEntryName();
                    //System.out.println("name: " + name);
                    if (ls){
                        //reached last entry in directory so print
                        if (name.equals("0")){
                            System.out.println(accumulatedName.stripTrailing());
                        //entry must have been deleted but could have entries after this one
                        }else if(name.equals("229")){
                            bis.skip(32);
                            byteCounter += 32;
                        }else{
                            accumulatedName += name + " ";
                        }
                    }else{
                        if(target.equals(name)){
                            return nextFATIndex;
                        }else{
                            bis.skip(32);
                            byteCounter += 32;
                        }
                    }
                }
            if(nextFATIndex >= 0x0ffffff8 && nextFATIndex <= 0x0fffffff){
                return -1;
            }

            int nextCluster = getIntFromFat(nextFATIndex);
            int newAddress = setPointerCluster(nextCluster);
            
            //System.out.println("\nparseDirectory");
            nextFATIndex = nextCluster;
            // System.out.println("nextFatIndex: " + nextFATIndex);
            // System.out.println("Address of next cluster: " + newAddress);
        }
        if(nextFATIndex == 0x0ffffff7){
            throw new IllegalStateException("Bad Cluster discovered");
        }else{
            throw new IllegalArgumentException("We got big problems: How did we get here?");
        }
    }
    //}

    /**
     * 
     * @param cluster cluster in the data section
     * @return the byte from the start of the image
     */
    private static int setPointerCluster (int cluster) throws IOException{

        //System.out.println("\nsetPointerCluster");

        int clusterInBytes = ((cluster - 2) * bytePerSec * secPerClus);

        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(dataStart);

        bis.skip(clusterInBytes);

        //System.out.println("byte address of pointer: " + (dataStart + clusterInBytes));

        return (int) (clusterInBytes + dataStart);

    }

    /**
     * 
     * @param b byte from the beginning of the image
     */
    private static void setPointerByte(int b) throws IOException{

        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(b);
    }

    /**
    Stream pointer must be set to the data entry
    */
    private static String getDataEntryName() throws IOException{

        byte[] nameBytes = getBytes(0, 11, false);
        
        //bis.mark(13);
        //bis.skip(11);
        int attr = byteToInt(getBytes(11, 1, false));
        boolean notFile = false;
        if(attr == 0x10 || attr == 0x08){
            notFile = true;
        }

        String name = "";
        if (nameBytes[0] == 0 || nameBytes[0] == 0xE5){
            return name + nameBytes[0];
        }
        for (int i = 0; i < nameBytes.length; i++){
            if (nameBytes[i] != 32){
                name += (char)nameBytes[i];
            }
            if(i == 7 && !notFile){
                name += ".";
            }
        }
        name = name.stripLeading();
        return name;
    }
}